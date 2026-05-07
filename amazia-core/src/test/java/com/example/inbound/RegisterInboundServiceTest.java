package com.example.inbound;

import com.example.inbound.dto.RegisterInboundRequest;
import com.example.inbound.entity.Inbound;
import com.example.inbound.repository.InboundRepository;
import com.example.inbound.service.RegisterInboundService;
import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step B-3: RegisterInboundService 検証。
 *
 * 案A：B-3 段階では「inbounds INSERT + product_sku_stocks 加算 + operation_logs 記録」までを検証。
 * inventories 同期（B-5）と deliveries 再計算（B-4）は依存先 Service 完成後に統合テストを足す。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class RegisterInboundServiceTest {

    @Autowired private RegisterInboundService service;
    @Autowired private InboundRepository inboundRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private InventoriesRepository inventoriesRepository;
    @Autowired private OperationLogRepository operationLogRepository;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    private Long actorUserId;
    private Long productId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        actorUserId = 1L;

        Product p = new Product();
        p.setName("入荷登録テスト商品");
        p.setStatusCode("ON_SALE");
        productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-INB-" + System.nanoTime());
        sku.setColor("黒");
        sku.setSize("S");
        sku.setStatus("ACTIVE");
        skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(0);
        skuStockRepository.save(stock);

        // 並行運用：inventories 行を事前投入（B-4 の recalculateForProduct が FOR UPDATE で取得するため必須）
        Inventories inv = new Inventories();
        inv.setProductId(productId);
        inv.setWarehouseId(defaultWarehouseId);
        inv.setQuantity(0);
        inventoriesRepository.saveAndFlush(inv);
    }

    @Test
    void 入荷登録でinbounds_INSERT_と_sku在庫加算_と_operation_logsが同一トランザクションで実行される() {
        RegisterInboundRequest req = buildRequest(productId, skuId, 5);

        Inbound saved = service.register(req, actorUserId);

        // inbounds INSERT
        assertNotNull(saved.getId());
        Inbound loaded = inboundRepository.findById(saved.getId()).orElseThrow();
        assertEquals(productId, loaded.getProductId());
        assertEquals(defaultWarehouseId, loaded.getWarehouseId());
        assertEquals(5, loaded.getQuantity());
        assertEquals(LocalDate.of(2026, 5, 7), loaded.getInboundedAt());

        // SKU 在庫加算（0 + 5 = 5）
        ProductSkuStock stock = skuStockRepository.findBySkuId(skuId).orElseThrow();
        assertEquals(5, stock.getQuantity());

        // operation_logs 記録
        List<OperationLog> logs = operationLogRepository.findAll().stream()
                .filter(l -> "register_inbound".equals(l.getAction())
                          && saved.getId().equals(l.getTargetId()))
                .toList();
        assertEquals(1, logs.size());
        OperationLog log = logs.get(0);
        assertEquals("inbounds", log.getTargetType());
        assertEquals(actorUserId, log.getUserId());
        assertNotNull(log.getComment());
    }

    @Test
    void inboundedAt未指定なら本日付が自動セットされる() {
        // phase16 Step3.1：未来日入荷防止のため、リクエストに inboundedAt を含めない場合は
        // Service 側で LocalDate.now() を強制セットする。
        RegisterInboundRequest req = new RegisterInboundRequest();
        req.setProductId(productId);
        req.setSkuId(skuId);
        req.setQuantity(3);
        // inboundedAt をあえてセットしない

        Inbound saved = service.register(req, actorUserId);

        assertEquals(LocalDate.now(), saved.getInboundedAt());
    }

    @Test
    void warehouse_idはリクエストに含まれず_default_warehouse_id_が自動セットされる() {
        // RegisterInboundRequest は warehouseId フィールドを持たない設計（RRRR-5）。
        // 登録結果が config('amazia.delivery.default-warehouse-id') に一致することを確認。
        RegisterInboundRequest req = buildRequest(productId, skuId, 1);

        Inbound saved = service.register(req, actorUserId);

        assertEquals(defaultWarehouseId, saved.getWarehouseId());
    }

    @Test
    void 存在しないproduct_idは404で拒否される() {
        RegisterInboundRequest req = buildRequest(999_999L, skuId, 1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.register(req, actorUserId));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void 存在しないsku_idは404で拒否される() {
        RegisterInboundRequest req = buildRequest(productId, 999_999L, 1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.register(req, actorUserId));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void product_idと_sku_idの親子整合性が崩れる場合は400で拒否される() {
        // 別商品の SKU を指定する
        Product other = new Product();
        other.setName("別商品");
        other.setStatusCode("ON_SALE");
        Long otherProductId = productRepository.save(other).getId();

        ProductSku otherSku = new ProductSku();
        otherSku.setProductId(otherProductId);
        otherSku.setSkuCode("SKU-OTHER-" + System.nanoTime());
        otherSku.setColor("白");
        otherSku.setSize("M");
        otherSku.setStatus("ACTIVE");
        Long otherSkuId = skuRepository.save(otherSku).getId();

        RegisterInboundRequest req = buildRequest(productId, otherSkuId, 1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.register(req, actorUserId));
        assertEquals(400, ex.getStatusCode().value());
    }

    private RegisterInboundRequest buildRequest(Long productId, Long skuId, int quantity) {
        RegisterInboundRequest r = new RegisterInboundRequest();
        r.setProductId(productId);
        r.setSkuId(skuId);
        r.setQuantity(quantity);
        r.setInboundedAt(LocalDate.of(2026, 5, 7));
        return r;
    }
}
