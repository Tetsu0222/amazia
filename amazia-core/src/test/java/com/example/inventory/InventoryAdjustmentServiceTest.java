package com.example.inventory;

import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.inventory.service.InventoryAdjustmentService;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 2: InventoryAdjustmentService の符号契約・三者同期検証
 * （ADJ-1 / ADJ-2 / H-4）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class InventoryAdjustmentServiceTest {

    @Autowired private InventoryAdjustmentService service;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuStockTransactionRepository txRepository;
    @Autowired private InventoriesRepository inventoriesRepository;

    @Value("${amazia.delivery.default-warehouse-id}") private long defaultWarehouseId;
    @Value("${amazia.sales.sku-stock-tx-types.adjust}") private String adjustType;

    @Test
    void ADJ_1_skuに5加算でSKUTX_SKU在庫_inventoriesが揃って同期される() {
        long skuId = persistProductWithSku(10);
        Long productId = skuRepository.findById(skuId).orElseThrow().getProductId();
        persistInventories(productId, 10);

        ProductSkuStockTransaction tx = service.adjust(skuId, 5, "manual_correction",
                null, 99L, "adjust test +5");

        assertNotNull(tx.getId());
        assertEquals(adjustType, tx.getType());
        assertEquals(5, tx.getQuantity());
        assertEquals("manual_correction", tx.getReferenceType());
        assertEquals(99L, tx.getCreatedByUserId());

        ProductSkuStock stock = skuStockRepository.findBySkuId(skuId).orElseThrow();
        assertEquals(15, stock.getQuantity(), "SKU 在庫が +5 反映");

        Inventories inv = inventoriesRepository
                .findByProductIdAndWarehouseId(productId, defaultWarehouseId).orElseThrow();
        assertEquals(15, inv.getQuantity(), "inventories が +5 反映");

        List<ProductSkuStockTransaction> txs = txRepository.findBySkuIdOrderByCreatedAtDesc(skuId);
        assertEquals(1, txs.size());
    }

    @Test
    void ADJ_2_quantity_0は_IllegalArgumentException() {
        long skuId = persistProductWithSku(10);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.adjust(skuId, 0, "manual_correction", null, 1L, "zero is illegal"));
        assertTrue(ex.getMessage().contains("must not be 0"));

        // 副作用が発生していないことを確認
        ProductSkuStock stock = skuStockRepository.findBySkuId(skuId).orElseThrow();
        assertEquals(10, stock.getQuantity());
        assertEquals(0, txRepository.findBySkuIdOrderByCreatedAtDesc(skuId).size());
    }

    // ---- helpers ----

    private long persistProductWithSku(int initialQuantity) {
        Product p = new Product();
        p.setName("Adjust テスト商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-ADJ-" + System.nanoTime());
        sku.setColor("白");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(initialQuantity);
        skuStockRepository.save(stock);

        return skuId;
    }

    private void persistInventories(Long productId, int quantity) {
        Inventories inv = new Inventories();
        inv.setProductId(productId);
        inv.setWarehouseId(defaultWarehouseId);
        inv.setQuantity(quantity);
        inventoriesRepository.saveAndFlush(inv);
    }
}
