package com.example.sku;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.service.ListSkuPriceHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 5.5-1f: ListSkuPriceHistoryService の動作検証（設計書 §13.5.1）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ListSkuPriceHistoryServiceTest {

    @Autowired private ListSkuPriceHistoryService service;
    @Autowired private ProductSkuPriceRepository priceRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void HIS_1_start_date_DESC_で全件_active_inactive_を返す() {
        long skuId = persistSku();
        persistPrice(skuId, 800, LocalDate.now().minusDays(60), Boolean.FALSE);
        persistPrice(skuId, 1500, LocalDate.now().minusDays(30), Boolean.TRUE);

        List<ProductSkuPrice> hist = service.list(skuId);

        assertEquals(2, hist.size());
        assertEquals(1500, hist.get(0).getPrice(), "新しい順で先頭は active 行");
        assertEquals(800, hist.get(1).getPrice());
    }

    @Test
    void HIS_2_未登録_SKU_は_404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.list(9_999_999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    private long persistSku() {
        Product p = new Product();
        p.setName("history-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-HS-" + System.nanoTime());
        sku.setColor("white");
        sku.setSize("M");
        return skuRepository.save(sku).getId();
    }

    private ProductSkuPrice persistPrice(long skuId, int price,
                                         LocalDate startDate, Boolean isActive) {
        ProductSkuPrice p = new ProductSkuPrice();
        p.setSkuId(skuId);
        p.setPrice(price);
        p.setStartDate(startDate);
        p.setIsActive(isActive);
        return priceRepository.saveAndFlush(p);
    }
}
