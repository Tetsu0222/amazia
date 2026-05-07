package com.example.product;

import com.example.product.dto.PreorderProductItem;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.product.service.ListPreorderProductsService;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ16 Step 2 / Step A: 予約商品一覧 Service の単体テスト。
 *
 * 設計書 phase16_ui_ux_improvement.md §2-9 TDD テストケース。
 * Clock は固定値（FIXED_TODAY = 2026-05-07 JST）に差し替えてテスト時刻を再現可能にする。
 */
@SpringBootTest
@Import({TestAwsConfig.class, ListPreorderProductsServiceTest.FixedClockConfig.class})
@ActiveProfiles("test")
@Transactional
class ListPreorderProductsServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 5, 7);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        public Clock fixedClock() {
            return Clock.fixed(FIXED_TODAY.atStartOfDay(JST).toInstant(), JST);
        }
    }

    @Autowired private ListPreorderProductsService service;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuPriceRepository priceRepository;
    @Autowired private SalesRepository salesRepository;

    @Test
    void 予約商品が0件のとき_空配列を返す() {
        List<PreorderProductItem> items = service.list();
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    void is_active_falseの商品は予約一覧から除外される() {
        // is_active=false かつ PRE_ORDER 期間内
        createPreorderProduct("非公開商品", FIXED_TODAY.plusDays(7), false);

        assertTrue(service.list().isEmpty());
    }

    @Test
    void release_dateを過ぎた商品は予約一覧から除外される() {
        // release_date 経過 → ON_SALE / SOLD_OUT に該当（PRE_ORDER ではない）
        createPreorderProduct("発売済み商品", FIXED_TODAY.minusDays(1), true);

        assertTrue(service.list().isEmpty());
    }

    @Test
    void 予約数量と金額はis_preorder_trueのsalesのみで集計される() {
        Long pid = createPreorderProduct("予約商品A", FIXED_TODAY.plusDays(7), true);
        Long skuId = createSku(pid);

        // 予約 sales（含まれる）
        createSales(skuId, 3, 9000, true);
        createSales(skuId, 2, 6000, true);
        // 通常 sales（含まれない）
        createSales(skuId, 5, 15000, false);

        List<PreorderProductItem> items = service.list();
        assertEquals(1, items.size());
        PreorderProductItem item = items.get(0);
        assertEquals(5L, item.getPreorderQuantity());
        assertEquals(15000L, item.getPreorderAmount());
    }

    @Test
    void 発売日昇順で返却される() {
        Long pidLater = createPreorderProduct("後発", FIXED_TODAY.plusDays(30), true);
        Long pidEarly = createPreorderProduct("先発", FIXED_TODAY.plusDays(7), true);
        Long pidMid   = createPreorderProduct("中間", FIXED_TODAY.plusDays(14), true);

        List<PreorderProductItem> items = service.list();
        assertEquals(3, items.size());
        assertEquals(pidEarly, items.get(0).getProductId());
        assertEquals(pidMid,   items.get(1).getProductId());
        assertEquals(pidLater, items.get(2).getProductId());
    }

    @Test
    void daysUntilReleaseが正しく計算される() {
        Long pid = createPreorderProduct("予約商品", FIXED_TODAY.plusDays(10), true);

        List<PreorderProductItem> items = service.list();
        assertEquals(1, items.size());
        assertEquals(10L, items.get(0).getDaysUntilRelease());
    }

    @Test
    void レスポンス項目が完全に詰められる() {
        Long pid = createPreorderProduct("予約商品", FIXED_TODAY.plusDays(7), true);

        List<PreorderProductItem> items = service.list();
        assertEquals(1, items.size());
        PreorderProductItem item = items.get(0);
        assertEquals(pid, item.getProductId());
        // ヘルパーが name に nanoTime サフィックスを付けて UNIQUE 衝突を回避しているため、前方一致で確認する
        assertTrue(item.getProductName().startsWith("予約商品"),
                "actual=" + item.getProductName());
        assertNotNull(item.getReleaseDate());
        assertNotNull(item.getPreorderStartDate());
        assertTrue(item.isAcceptPreorder());
        assertTrue(item.isActive());
    }

    @Test
    void minPriceとmaxPriceが_SKU価格の最小最大で集計される() {
        Long pid = createPreorderProduct("予約商品", FIXED_TODAY.plusDays(7), true);
        Long sku1 = createSku(pid);
        Long sku2 = createSku(pid);
        Long sku3 = createSku(pid);
        createPrice(sku1, 1500);
        createPrice(sku2, 3000);
        createPrice(sku3, 2200);

        List<PreorderProductItem> items = service.list();
        assertEquals(1, items.size());
        assertEquals(1500, items.get(0).getMinPrice());
        assertEquals(3000, items.get(0).getMaxPrice());
    }

    @Test
    void SKU価格未登録の予約商品はminPriceとmaxPriceがnull() {
        Long pid = createPreorderProduct("予約商品", FIXED_TODAY.plusDays(7), true);
        createSku(pid); // 価格は付けない

        List<PreorderProductItem> items = service.list();
        assertEquals(1, items.size());
        assertNull(items.get(0).getMinPrice());
        assertNull(items.get(0).getMaxPrice());
    }

    // ---- helpers --------------------------------------------------------------

    /** PRE_ORDER ステータス相当の商品を作る（is_active 任意 / release_date 任意） */
    private Long createPreorderProduct(String name, LocalDate releaseDate, boolean active) {
        Product p = new Product();
        p.setName(name + "-" + System.nanoTime());
        p.setDescription("");
        p.setPrice(3000);
        p.setStock(0);
        p.setStatusCode("RESERVATION");
        p.setPublishStart(FIXED_TODAY.minusDays(10).atStartOfDay());
        p.setPreorderStartDate(FIXED_TODAY.minusDays(1));
        p.setReleaseDate(releaseDate);
        p.setAcceptPreorder(true);
        p.setActive(active);
        return productRepository.save(p).getId();
    }

    private Long createSku(Long productId) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-" + System.nanoTime());
        sku.setColor("赤");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        return skuRepository.save(sku).getId();
    }

    private void createPrice(Long skuId, int price) {
        ProductSkuPrice p = new ProductSkuPrice();
        p.setSkuId(skuId);
        p.setPrice(price);
        p.setStartDate(FIXED_TODAY);
        priceRepository.save(p);
    }

    private void createSales(Long skuId, int quantity, int amount, boolean preorder) {
        Sales s = new Sales();
        s.setUserId(1L);
        s.setSkuId(skuId);
        s.setQuantity(quantity);
        s.setAmount(amount);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(1L);
        s.setShippingAddressId(1L);
        s.setShippingStatusId(1L);
        s.setPaymentId("PAY-" + System.nanoTime());
        s.setPreorder(preorder);
        s.setSalesDate(FIXED_TODAY);
        salesRepository.save(s);
    }
}
