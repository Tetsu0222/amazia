package com.example.inventory;

import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step A: Inventories Entity の永続化検証（RRRR-1 / RRR-8）。
 * 並行運用書き込み正本としての CHECK(quantity >= 0) / UNIQUE(product_id, warehouse_id) を確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class InventoriesEntityTest {

    @Autowired
    private InventoriesRepository inventoriesRepository;
    @Autowired
    private ProductRepository productRepository;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    @Test
    void inventories_は商品と倉庫の組合せで一意に保存できる() {
        Product product = persistProduct("在庫テスト商品A");

        Inventories inv = newInventories(product.getId(), 10);
        Inventories saved = inventoriesRepository.saveAndFlush(inv);

        assertNotNull(saved.getId());
        assertNotNull(saved.getUpdatedAt());

        Optional<Inventories> found = inventoriesRepository
                .findByProductIdAndWarehouseId(product.getId(), defaultWarehouseId);
        assertTrue(found.isPresent());
        assertEquals(10, found.get().getQuantity());
    }

    @Test
    void 同じ商品と倉庫の組合せで2件登録するとUNIQUE違反になる() {
        Product product = persistProduct("在庫テスト商品B");
        inventoriesRepository.saveAndFlush(newInventories(product.getId(), 1));

        Inventories dup = newInventories(product.getId(), 99);
        assertThrows(DataIntegrityViolationException.class,
                () -> inventoriesRepository.saveAndFlush(dup));
    }

    @Test
    void quantityを負数で保存すると_CHECK_制約違反で拒否される() {
        Product product = persistProduct("在庫テスト商品C");
        Inventories bad = newInventories(product.getId(), -1);
        assertThrows(DataIntegrityViolationException.class,
                () -> inventoriesRepository.saveAndFlush(bad),
                "CHECK(quantity >= 0) で拒否されるはず");
    }

    // 注: 存在しない product_id での FK 違反は Service 層（InventorySyncService）で
    //     事前バリデーションする（Step B-5）。Entity 層では CHECK / UNIQUE のみ担保。

    @Test
    void findByProductIdAndWarehouseIdForUpdateで取得できる() {
        Product product = persistProduct("在庫テスト商品D");
        inventoriesRepository.saveAndFlush(newInventories(product.getId(), 5));

        Optional<Inventories> found = inventoriesRepository
                .findByProductIdAndWarehouseIdForUpdate(product.getId(), defaultWarehouseId);
        assertTrue(found.isPresent());
        assertEquals(5, found.get().getQuantity());
    }

    private Product persistProduct(String name) {
        Product p = new Product();
        p.setName(name);
        p.setStatusCode("ON_SALE");
        return productRepository.saveAndFlush(p);
    }

    private Inventories newInventories(Long productId, int quantity) {
        Inventories inv = new Inventories();
        inv.setProductId(productId);
        inv.setWarehouseId(defaultWarehouseId);
        inv.setQuantity(quantity);
        return inv;
    }
}
