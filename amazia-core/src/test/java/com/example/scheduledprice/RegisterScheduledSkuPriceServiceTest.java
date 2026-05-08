package com.example.scheduledprice;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.scheduledprice.service.RegisterScheduledSkuPriceService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 5.5-1d: RegisterScheduledSkuPriceService の UPSERT 動作とバリデーション検証
 * （設計書 §13.5.1）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class RegisterScheduledSkuPriceServiceTest {

    @Autowired private RegisterScheduledSkuPriceService service;
    @Autowired private ProductSkuScheduledPriceRepository scheduledRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void RG_1_予約が無いとき_新規_INSERT_される() {
        long skuId = persistSku();

        ProductSkuScheduledPrice saved = service.upsert(skuId, 1500, LocalDate.now().plusDays(7));

        assertNotNull(saved.getId());
        assertEquals(1500, saved.getScheduledPrice());
        assertEquals(Boolean.TRUE, saved.getIsPending());

        List<ProductSkuScheduledPrice> all =
                scheduledRepository.findBySkuIdOrderByApplyDateDesc(skuId);
        assertEquals(1, all.size());
    }

    @Test
    void RG_2_既存_pending_があれば_UPDATE_される_行は増えない() {
        long skuId = persistSku();
        ProductSkuScheduledPrice first =
                service.upsert(skuId, 1500, LocalDate.now().plusDays(7));

        ProductSkuScheduledPrice second =
                service.upsert(skuId, 2000, LocalDate.now().plusDays(14));

        assertEquals(first.getId(), second.getId(), "同一行が UPDATE される");
        assertEquals(2000, second.getScheduledPrice());
        assertEquals(LocalDate.now().plusDays(14), second.getApplyDate());

        List<ProductSkuScheduledPrice> all =
                scheduledRepository.findBySkuIdOrderByApplyDateDesc(skuId);
        assertEquals(1, all.size(), "新規 INSERT されない");
    }

    @Test
    void RG_3_apply_date_が過去日なら_422() {
        long skuId = persistSku();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsert(skuId, 1500, LocalDate.now().minusDays(1)));
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    void RG_4_apply_date_が今日は許容() {
        long skuId = persistSku();

        ProductSkuScheduledPrice saved = service.upsert(skuId, 1500, LocalDate.now());
        assertNotNull(saved.getId());
    }

    @Test
    void RG_5_scheduledPrice_が_null_なら_422() {
        long skuId = persistSku();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsert(skuId, null, LocalDate.now().plusDays(7)));
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    void RG_6_scheduledPrice_が_負数なら_422() {
        long skuId = persistSku();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsert(skuId, -100, LocalDate.now().plusDays(7)));
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    void RG_7_未登録_SKU_は_404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsert(9_999_999L, 1500, LocalDate.now().plusDays(1)));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void RG_8_apply_date_が_null_なら_422() {
        long skuId = persistSku();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsert(skuId, 1500, null));
        assertEquals(422, ex.getStatusCode().value());
    }

    private long persistSku() {
        Product p = new Product();
        p.setName("scheduled-upsert-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-RG-" + System.nanoTime());
        sku.setColor("white");
        sku.setSize("M");
        return skuRepository.save(sku).getId();
    }
}
