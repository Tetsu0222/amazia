package com.example.scheduledprice;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1: product_sku_scheduled_prices Entity / Repository の永続化検証。
 * CHECK (scheduled_price >= 0) と pending 抽出クエリの動作を確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductSkuScheduledPriceRepositoryTest {

    @Autowired
    private ProductSkuScheduledPriceRepository repository;
    @Autowired
    private ProductSkuRepository skuRepository;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void save_すると_id_と_既定値_TRUE_が反映される() {
        Long skuId = createSku("scheduled-default");

        ProductSkuScheduledPrice saved = repository.saveAndFlush(
                newScheduled(skuId, 1500, LocalDate.now().plusDays(7)));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(Boolean.TRUE, saved.getIsPending());
        assertNull(saved.getAppliedAt());
    }

    @Test
    void scheduled_price_が負数なら_CHECK_制約で拒否される() {
        Long skuId = createSku("scheduled-neg");
        ProductSkuScheduledPrice bad = newScheduled(skuId, -1, LocalDate.now().plusDays(1));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(bad));
    }

    @Test
    void findFirstBySkuIdAndIsPendingTrue_で_pending_行のみ返る() {
        Long skuId = createSku("scheduled-pending");

        ProductSkuScheduledPrice applied = newScheduled(skuId, 100, LocalDate.now().minusDays(1));
        applied.setIsPending(false);
        repository.saveAndFlush(applied);
        repository.saveAndFlush(newScheduled(skuId, 200, LocalDate.now().plusDays(1)));

        Optional<ProductSkuScheduledPrice> pending =
                repository.findFirstBySkuIdAndIsPendingTrue(skuId);

        assertTrue(pending.isPresent());
        assertEquals(200, pending.get().getScheduledPrice());
    }

    @Test
    void findByApplyDateLessThanEqualAndIsPendingTrue_で_ApplyScheduledPricesJob_対象を抽出() {
        Long skuId = createSku("scheduled-due");
        repository.saveAndFlush(newScheduled(skuId, 500, LocalDate.now().minusDays(1)));   // 期限到来
        repository.saveAndFlush(newScheduled(skuId, 700, LocalDate.now().plusDays(10)));  // 将来

        List<ProductSkuScheduledPrice> due = repository
                .findByApplyDateLessThanEqualAndIsPendingTrue(LocalDate.now());

        assertEquals(1, due.size());
        assertEquals(500, due.get(0).getScheduledPrice());
    }

    private Long createSku(String suffix) {
        Product p = new Product();
        p.setName("scheduled-" + suffix);
        p.setStatusCode("ON_SALE");
        Product savedProduct = productRepository.saveAndFlush(p);

        ProductSku sku = new ProductSku();
        sku.setProductId(savedProduct.getId());
        sku.setSkuCode("SC-" + System.nanoTime() % 100000);
        sku.setColor("red");
        sku.setSize("M");
        return skuRepository.saveAndFlush(sku).getId();
    }

    private ProductSkuScheduledPrice newScheduled(Long skuId, int price, LocalDate applyDate) {
        ProductSkuScheduledPrice s = new ProductSkuScheduledPrice();
        s.setSkuId(skuId);
        s.setScheduledPrice(price);
        s.setApplyDate(applyDate);
        return s;
    }
}
