package com.example.inbound;

import com.example.inbound.entity.Inbound;
import com.example.inbound.repository.InboundRepository;
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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step A: Inbound Entity の永続化検証（R-3 / RRR-10）。
 * CHECK(quantity > 0) 制約 / FK 制約 / 並行運用ダミー倉庫のデフォルト適用を確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class InboundEntityTest {

    @Autowired
    private InboundRepository inboundRepository;
    @Autowired
    private ProductRepository productRepository;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    @Test
    void inbound_は商品IDと数量と入荷日を保存できる() {
        Product product = persistProduct("入荷テスト商品A");

        Inbound inbound = newInbound(product.getId(), 5);
        Inbound saved = inboundRepository.saveAndFlush(inbound);

        assertNotNull(saved.getId());
        Inbound loaded = inboundRepository.findById(saved.getId()).orElseThrow();
        assertEquals(product.getId(), loaded.getProductId());
        assertEquals(defaultWarehouseId, loaded.getWarehouseId());
        assertEquals(5, loaded.getQuantity());
        assertEquals(LocalDate.of(2026, 5, 7), loaded.getInboundedAt());
        assertNotNull(loaded.getCreatedAt());
    }

    @Test
    void inbound_quantity_0は_CHECK_制約違反で拒否される() {
        Product product = persistProduct("入荷テスト商品B");
        Inbound bad = newInbound(product.getId(), 0);
        assertThrows(DataIntegrityViolationException.class,
                () -> inboundRepository.saveAndFlush(bad),
                "CHECK(quantity > 0) で拒否されるはず");
    }

    @Test
    void inbound_quantity_負数は_CHECK_制約違反で拒否される() {
        Product product = persistProduct("入荷テスト商品C");
        Inbound bad = newInbound(product.getId(), -3);
        assertThrows(DataIntegrityViolationException.class,
                () -> inboundRepository.saveAndFlush(bad),
                "CHECK(quantity > 0) で拒否されるはず");
    }

    // 注: 存在しない product_id での FK 違反は Service 層（RegisterInboundService）で
    //     事前バリデーションする（Step B-3）。Entity 層では CHECK / UNIQUE のみ担保。

    private Product persistProduct(String name) {
        Product p = new Product();
        p.setName(name);
        p.setStatusCode("ON_SALE");
        return productRepository.saveAndFlush(p);
    }

    private Inbound newInbound(Long productId, int quantity) {
        Inbound i = new Inbound();
        i.setProductId(productId);
        i.setWarehouseId(defaultWarehouseId);
        i.setQuantity(quantity);
        i.setInboundedAt(LocalDate.of(2026, 5, 7));
        return i;
    }
}
