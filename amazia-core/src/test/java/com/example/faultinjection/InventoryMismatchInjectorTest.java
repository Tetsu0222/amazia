package com.example.faultinjection;

import com.example.batch.service.RandomGeneratorAdapter;
import com.example.faultinjection.entity.FaultInjectionLog;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.faultinjection.service.InventoryMismatchInjector;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * フェーズ17 Step 5-3: InventoryMismatchInjector の動作検証（H-7）。
 *
 * <ul>
 *   <li>IMI_1：注入後に SKU TX が {@code [fault_injection][inventory]} 接頭辞で残る</li>
 *   <li>IMI_2：{@code delta=0} は契約違反のため避けられる（強制的に +1 に矯正される）</li>
 *   <li>IMI_3：対象 SKU が無ければ 0 を返してサイレントスキップ</li>
 *   <li>IMI_4：{@code tryInject} は確率発火（enabled=false なら発火しない）</li>
 * </ul>
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class InventoryMismatchInjectorTest {

    @Autowired private InventoryMismatchInjector injector;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuStockTransactionRepository txRepository;
    @Autowired private FaultInjectionLogRepository faultLogRepository;

    @MockBean private RandomGeneratorAdapter random;

    @BeforeEach
    void cleanupPriorLogs() {
        faultLogRepository.deleteAll(faultLogRepository
                .findByInjectorNameOrderByCreatedAtDesc(InventoryMismatchInjector.INJECTOR_NAME));
    }

    @Test
    void IMI_1_注入で_SKU_TX_が_fault_injection_inventory_接頭辞で残る() {
        long productId = persistProductWithSkuAndStock(10);
        when(random.nextIntBetween(anyInt(), anyInt())).thenReturn(2);
        ReflectionTestUtils.setField(injector, "enabled", true);

        int delta = injector.inject(productId, "manual:test");

        assertEquals(2, delta);
        long skuId = skuRepository.findByProductId(productId).get(0).getId();
        List<ProductSkuStockTransaction> txs = txRepository.findBySkuIdOrderByCreatedAtDesc(skuId);
        assertFalse(txs.isEmpty());
        ProductSkuStockTransaction latest = txs.get(0);
        assertEquals("adjust", latest.getType());
        assertEquals(2, latest.getQuantity());
        assertEquals("fault_injection", latest.getReferenceType());
        assertNotNull(latest.getComment());
        assertTrue(latest.getComment().startsWith("[fault_injection][inventory]"),
                "comment は [fault_injection][inventory] 接頭辞で始まる必要がある");

        // fault_injection_logs にも残る
        List<FaultInjectionLog> logs = faultLogRepository
                .findByInjectorNameOrderByCreatedAtDesc(InventoryMismatchInjector.INJECTOR_NAME);
        assertEquals(1, logs.size());
    }

    @Test
    void IMI_2_random_が_0_を返しても_delta_は_0_にならない() {
        long productId = persistProductWithSkuAndStock(10);
        when(random.nextIntBetween(anyInt(), anyInt())).thenReturn(0);

        int delta = injector.inject(productId, "scheduler");

        assertEquals(1, delta, "Service 層契約に従い delta=0 は +1 に矯正される");
    }

    @Test
    void IMI_3_対象_SKU_が無い商品は_0_を返してサイレントスキップ() {
        Product orphan = new Product();
        orphan.setName("空商品-" + System.nanoTime());
        orphan.setStatusCode("ON_SALE");
        long productId = productRepository.save(orphan).getId();

        int delta = injector.inject(productId, "scheduler");

        assertEquals(0, delta);
    }

    @Test
    void IMI_4_tryInject_は_enabled_false_なら発火しない() {
        long productId = persistProductWithSkuAndStock(10);
        ReflectionTestUtils.setField(injector, "enabled", false);
        when(random.nextDouble()).thenReturn(0.001);

        boolean fired = injector.tryInject(productId, "scheduler");

        assertFalse(fired);
        long skuId = skuRepository.findByProductId(productId).get(0).getId();
        assertEquals(0, txRepository.findBySkuIdOrderByCreatedAtDesc(skuId).size(),
                "enabled=false の状態では SKU TX も増えない");
    }

    private long persistProductWithSkuAndStock(int initialStock) {
        Product p = new Product();
        p.setName("IMI テスト商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-IMI-" + System.nanoTime());
        sku.setColor("白");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(initialStock);
        skuStockRepository.save(stock);

        return productId;
    }
}
