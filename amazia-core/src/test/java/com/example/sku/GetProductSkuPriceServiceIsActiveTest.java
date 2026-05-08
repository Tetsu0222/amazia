package com.example.sku;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.service.GetProductSkuPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 5.5-1a: GetProductSkuPriceService が is_active=TRUE のみを返す検証
 * （設計書 §13.5.1）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class GetProductSkuPriceServiceIsActiveTest {

    @Autowired private GetProductSkuPriceService service;
    @Autowired private ProductSkuPriceRepository priceRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void GET_1_active_行のみが返り_inactive_は無視される() {
        long skuId = persistSku();
        persistPrice(skuId, 800, LocalDate.now().minusDays(60),
                LocalDate.now().minusDays(31), Boolean.FALSE);
        persistPrice(skuId, 1500, LocalDate.now().minusDays(30), null, Boolean.TRUE);

        ProductSkuPrice current = service.get(skuId);

        assertNotNull(current);
        assertEquals(1500, current.getPrice());
        assertEquals(Boolean.TRUE, current.getIsActive());
    }

    @Test
    void GET_2_active_が無ければ_null_を返す() {
        long skuId = persistSku();
        persistPrice(skuId, 800, LocalDate.now().minusDays(60),
                LocalDate.now().minusDays(31), Boolean.FALSE);

        assertNull(service.get(skuId), "active 行が無ければ null");
    }

    private long persistSku() {
        Product p = new Product();
        p.setName("get-price-active-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-GP-" + System.nanoTime());
        sku.setColor("blue");
        sku.setSize("L");
        return skuRepository.save(sku).getId();
    }

    private ProductSkuPrice persistPrice(long skuId, int price, LocalDate startDate,
                                         LocalDate endDate, Boolean isActive) {
        ProductSkuPrice p = new ProductSkuPrice();
        p.setSkuId(skuId);
        p.setPrice(price);
        p.setStartDate(startDate);
        p.setEndDate(endDate);
        p.setIsActive(isActive);
        return priceRepository.saveAndFlush(p);
    }
}
