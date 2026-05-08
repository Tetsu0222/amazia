package com.example.sku;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.service.CreateProductSkuPriceService;
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
 * フェーズ17 Step 5.5-1b: CreateProductSkuPriceService が
 * 「既存 active を is_active=FALSE に降格 → 新規 INSERT を is_active=TRUE」のトランザクション
 * 処理を行うことの検証（設計書 §13.5.1）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class CreateProductSkuPriceServiceIsActiveTest {

    @Autowired private CreateProductSkuPriceService service;
    @Autowired private ProductSkuPriceRepository priceRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void CRE_1_既存_active_は_inactive_に降格され_新規_active_が_INSERT_される() {
        long skuId = persistSku();
        ProductSkuPrice old = persistActivePrice(skuId, 1000, LocalDate.now().minusDays(30));

        ProductSkuPrice request = new ProductSkuPrice();
        request.setPrice(1500);
        ProductSkuPrice saved = service.create(skuId, request);

        // 旧行は inactive 化
        ProductSkuPrice oldAfter = priceRepository.findById(old.getId()).orElseThrow();
        assertEquals(Boolean.FALSE, oldAfter.getIsActive());
        assertEquals(LocalDate.now().minusDays(1), oldAfter.getEndDate());

        // 新行は active
        assertNotNull(saved.getId());
        assertEquals(1500, saved.getPrice());
        assertEquals(Boolean.TRUE, saved.getIsActive());
        assertEquals(LocalDate.now(), saved.getStartDate());
        assertNull(saved.getEndDate());

        // active は 1 件のみ
        long activeCount = priceRepository.findBySkuIdIn(List.of(skuId)).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive())).count();
        assertEquals(1L, activeCount);
    }

    @Test
    void CRE_2_既存_active_が無い場合も新規_active_が_1_件作成される() {
        long skuId = persistSku();

        ProductSkuPrice request = new ProductSkuPrice();
        request.setPrice(2000);
        ProductSkuPrice saved = service.create(skuId, request);

        assertEquals(Boolean.TRUE, saved.getIsActive());
        long activeCount = priceRepository.findBySkuIdIn(List.of(skuId)).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive())).count();
        assertEquals(1L, activeCount);
    }

    @Test
    void CRE_3_未登録_SKU_は_404() {
        ProductSkuPrice request = new ProductSkuPrice();
        request.setPrice(1000);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(9_999_999L, request));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void CRE_4_2_回連続実行しても_active_は常に_1_件_履歴は累積する() {
        long skuId = persistSku();

        ProductSkuPrice req1 = new ProductSkuPrice();
        req1.setPrice(1000);
        service.create(skuId, req1);

        ProductSkuPrice req2 = new ProductSkuPrice();
        req2.setPrice(1500);
        service.create(skuId, req2);

        List<ProductSkuPrice> all = priceRepository.findBySkuIdIn(List.of(skuId));
        assertEquals(2, all.size(), "履歴は物理削除されないため累積");
        long activeCount = all.stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).count();
        assertEquals(1L, activeCount, "active は常に 1 件");
    }

    private long persistSku() {
        Product p = new Product();
        p.setName("create-price-active-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-CR-" + System.nanoTime());
        sku.setColor("white");
        sku.setSize("M");
        return skuRepository.save(sku).getId();
    }

    private ProductSkuPrice persistActivePrice(long skuId, int price, LocalDate startDate) {
        ProductSkuPrice p = new ProductSkuPrice();
        p.setSkuId(skuId);
        p.setPrice(price);
        p.setStartDate(startDate);
        p.setIsActive(Boolean.TRUE);
        return priceRepository.saveAndFlush(p);
    }
}
