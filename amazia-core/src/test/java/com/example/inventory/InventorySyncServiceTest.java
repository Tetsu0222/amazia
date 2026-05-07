package com.example.inventory;

import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.inventory.service.InventorySyncService;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step B-5: InventorySyncService 単体検証（RRRR-2 / RRR-8）。
 *
 * 並行運用フックの基本動作を確認：
 *   1. 既存 inventories 行に対する加減算
 *   2. 行欠落時の auto-provision（SUM(SKU stock) で初期化）
 *   3. CHECK(quantity >= 0) 違反の伝播
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class InventorySyncServiceTest {

    @Autowired private InventorySyncService service;
    @Autowired private InventoriesRepository inventoriesRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    @Test
    void 既存inventories行に対して加算が反映される() {
        Long productId = persistProductWithSku(0); // SKU 在庫 0
        persistInventories(productId, 10);

        service.applyDelta(productId, defaultWarehouseId, 5);

        Inventories inv = inventoriesRepository
                .findByProductIdAndWarehouseId(productId, defaultWarehouseId).orElseThrow();
        assertEquals(15, inv.getQuantity());
    }

    @Test
    void 既存inventories行に対して減算が反映される() {
        Long productId = persistProductWithSku(0);
        persistInventories(productId, 10);

        service.applyDelta(productId, defaultWarehouseId, -3);

        Inventories inv = inventoriesRepository
                .findByProductIdAndWarehouseId(productId, defaultWarehouseId).orElseThrow();
        assertEquals(7, inv.getQuantity());
    }

    @Test
    void 行欠落時はSUM_SKU_stockで自動補完される() {
        Long productId = persistProductWithSku(20); // SKU 在庫 20

        service.applyDelta(productId, defaultWarehouseId, -2);

        Inventories inv = inventoriesRepository
                .findByProductIdAndWarehouseId(productId, defaultWarehouseId).orElseThrow();
        assertEquals(18, inv.getQuantity(), "SUM(SKU stock=20) + delta(-2) = 18");
    }

    @Test
    void quantityが負数になる更新は_CHECK制約違反で例外が伝播される() {
        Long productId = persistProductWithSku(0);
        persistInventories(productId, 1);

        assertThrows(DataIntegrityViolationException.class,
                () -> {
                    service.applyDelta(productId, defaultWarehouseId, -5);
                    inventoriesRepository.flush();
                },
                "CHECK(quantity >= 0) で拒否されるはず");
    }

    // ---- helpers ----

    private Long persistProductWithSku(int skuQuantity) {
        Product p = new Product();
        p.setName("InvSync テスト商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-IS-" + System.nanoTime());
        sku.setColor("白");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(skuQuantity);
        skuStockRepository.save(stock);

        return productId;
    }

    private void persistInventories(Long productId, int quantity) {
        Inventories inv = new Inventories();
        inv.setProductId(productId);
        inv.setWarehouseId(defaultWarehouseId);
        inv.setQuantity(quantity);
        inventoriesRepository.saveAndFlush(inv);
    }
}
