package com.example.scheduledprice;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.scheduledprice.service.GetScheduledSkuPriceService;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 5.5-1c: GetScheduledSkuPriceService の動作検証（設計書 §13.5.1）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class GetScheduledSkuPriceServiceTest {

    @Autowired private GetScheduledSkuPriceService service;
    @Autowired private ProductSkuScheduledPriceRepository scheduledRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void GS_1_is_pending_TRUE_の予約変更が_1_件返る() {
        long skuId = persistSku();
        persistScheduled(skuId, 1500, LocalDate.now().plusDays(7), Boolean.TRUE);

        Optional<ProductSkuScheduledPrice> hit = service.get(skuId);

        assertTrue(hit.isPresent());
        assertEquals(1500, hit.get().getScheduledPrice());
        assertEquals(Boolean.TRUE, hit.get().getIsPending());
    }

    @Test
    void GS_2_is_pending_FALSE_は無視される() {
        long skuId = persistSku();
        persistScheduled(skuId, 1500, LocalDate.now().plusDays(7), Boolean.FALSE);

        assertTrue(service.get(skuId).isEmpty());
    }

    @Test
    void GS_3_未登録_SKU_は_404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.get(9_999_999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    private long persistSku() {
        Product p = new Product();
        p.setName("scheduled-get-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-GS-" + System.nanoTime());
        sku.setColor("white");
        sku.setSize("M");
        return skuRepository.save(sku).getId();
    }

    private ProductSkuScheduledPrice persistScheduled(long skuId, int price,
                                                      LocalDate applyDate, Boolean isPending) {
        ProductSkuScheduledPrice s = new ProductSkuScheduledPrice();
        s.setSkuId(skuId);
        s.setScheduledPrice(price);
        s.setApplyDate(applyDate);
        s.setIsPending(isPending);
        return scheduledRepository.saveAndFlush(s);
    }
}
