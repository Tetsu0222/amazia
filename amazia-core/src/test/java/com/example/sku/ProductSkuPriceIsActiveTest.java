package com.example.sku;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1: product_sku_prices.is_active 追加（設計書 §3.1 ⑥ / §13.5）の
 * Entity 永続化検証。@PrePersist で TRUE が既定値として入ることを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductSkuPriceIsActiveTest {

    @Autowired
    private ProductSkuPriceRepository priceRepository;
    @Autowired
    private ProductSkuRepository skuRepository;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void is_active_未指定なら_TRUE_で永続化される() {
        Long skuId = createSku();

        ProductSkuPrice price = new ProductSkuPrice();
        price.setSkuId(skuId);
        price.setPrice(1000);
        ProductSkuPrice saved = priceRepository.saveAndFlush(price);

        assertNotNull(saved.getId());
        assertEquals(Boolean.TRUE, saved.getIsActive());
    }

    @Test
    void is_active_を_FALSE_に明示指定できる() {
        Long skuId = createSku();

        ProductSkuPrice price = new ProductSkuPrice();
        price.setSkuId(skuId);
        price.setPrice(1500);
        price.setIsActive(false);
        ProductSkuPrice saved = priceRepository.saveAndFlush(price);

        assertEquals(Boolean.FALSE, saved.getIsActive());
    }

    private Long createSku() {
        Product p = new Product();
        p.setName("price-active-test");
        p.setStatusCode("ON_SALE");
        Product savedProduct = productRepository.saveAndFlush(p);

        ProductSku sku = new ProductSku();
        sku.setProductId(savedProduct.getId());
        sku.setSkuCode("PA-" + System.nanoTime() % 100000);
        sku.setColor("blue");
        sku.setSize("L");
        return skuRepository.saveAndFlush(sku).getId();
    }
}
