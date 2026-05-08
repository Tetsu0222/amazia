package com.example.scheduledprice;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.scheduledprice.service.DeleteScheduledSkuPriceService;
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
 * フェーズ17 Step 5.5-1e: DeleteScheduledSkuPriceService の物理削除動作の検証（設計書 §13.5.1）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class DeleteScheduledSkuPriceServiceTest {

    @Autowired private DeleteScheduledSkuPriceService service;
    @Autowired private ProductSkuScheduledPriceRepository scheduledRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void DEL_1_pending_行が物理削除される() {
        long skuId = persistSku();
        ProductSkuScheduledPrice s =
                persistScheduled(skuId, 1500, LocalDate.now().plusDays(3), Boolean.TRUE);

        Optional<ProductSkuScheduledPrice> deleted = service.delete(skuId);

        assertTrue(deleted.isPresent());
        assertEquals(s.getId(), deleted.get().getId());
        assertTrue(scheduledRepository.findById(s.getId()).isEmpty(),
                "is_pending=TRUE は物理削除される");
    }

    @Test
    void DEL_2_pending_が無い場合は_empty_を返す() {
        long skuId = persistSku();

        Optional<ProductSkuScheduledPrice> deleted = service.delete(skuId);
        assertTrue(deleted.isEmpty());
    }

    @Test
    void DEL_3_適用済_pending_FALSE_は対象外で残る() {
        long skuId = persistSku();
        ProductSkuScheduledPrice applied =
                persistScheduled(skuId, 1500, LocalDate.now().plusDays(3), Boolean.FALSE);

        service.delete(skuId);

        assertTrue(scheduledRepository.findById(applied.getId()).isPresent(),
                "適用済（is_pending=FALSE）は履歴として残る");
    }

    @Test
    void DEL_4_未登録_SKU_は_404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.delete(9_999_999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    private long persistSku() {
        Product p = new Product();
        p.setName("scheduled-delete-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-DL-" + System.nanoTime());
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
