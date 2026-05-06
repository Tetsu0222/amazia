package com.example.product;

import com.example.product.entity.PreorderStatus;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.product.service.PreorderStatusService;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14.5 Step C-2: 予約ステータス判定 Service の単体テスト。
 *
 * 設計書 phase14_5_preorder_status.md §2-2 の判定ロジックを 6 ステータス + 境界値 + 異常系で網羅する。
 * Clock は固定値（FIXED_TODAY = 2026-05-07 JST）に差し替えてテスト時刻を再現可能にする。
 */
@SpringBootTest
@Import({TestAwsConfig.class, PreorderStatusServiceTest.FixedClockConfig.class})
@ActiveProfiles("test")
@Transactional
class PreorderStatusServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 5, 7);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        public Clock fixedClock() {
            return Clock.fixed(
                    FIXED_TODAY.atStartOfDay(JST).toInstant(),
                    JST);
        }
    }

    @Autowired private PreorderStatusService service;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository stockRepository;

    // ---- 正常系: 6 ステータス -------------------------------------------------

    @Test
    void 公開開始日が未来なら_NOT_PUBLIC() {
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.plusDays(1).atStartOfDay());
        });

        assertEquals(PreorderStatus.NOT_PUBLIC, service.judge(pid));
    }

    @Test
    void 予約開始日が未来なら_PRE_ORDER_NOT_STARTED() {
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(10).atStartOfDay());
            p.setPreorderStartDate(FIXED_TODAY.plusDays(3));
            p.setReleaseDate(FIXED_TODAY.plusDays(10));
        });

        assertEquals(PreorderStatus.PRE_ORDER_NOT_STARTED, service.judge(pid));
    }

    @Test
    void 予約開始済みで発売日が未来なら_PRE_ORDER() {
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(10).atStartOfDay());
            p.setPreorderStartDate(FIXED_TODAY.minusDays(1));
            p.setReleaseDate(FIXED_TODAY.plusDays(7));
            p.setAcceptPreorder(true);
        });

        assertEquals(PreorderStatus.PRE_ORDER, service.judge(pid));
    }

    @Test
    void 発売日経過_かつ在庫ありなら_ON_SALE() {
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(30).atStartOfDay());
            p.setReleaseDate(FIXED_TODAY.minusDays(7));
        });
        createSkuWithStock(pid, "赤", "M", 5);

        assertEquals(PreorderStatus.ON_SALE, service.judge(pid));
    }

    @Test
    void 発売日経過_在庫ゼロ_予約継続フラグONなら_BACK_ORDER() {
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(30).atStartOfDay());
            p.setReleaseDate(FIXED_TODAY.minusDays(1));
            p.setAcceptBackorder(true);
        });
        createSkuWithStock(pid, "青", "L", 0);

        assertEquals(PreorderStatus.BACK_ORDER, service.judge(pid));
    }

    @Test
    void 発売日経過_在庫ゼロ_予約継続フラグOFFなら_SOLD_OUT() {
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(30).atStartOfDay());
            p.setReleaseDate(FIXED_TODAY.minusDays(1));
            p.setAcceptBackorder(false);
        });
        createSkuWithStock(pid, "黒", "S", 0);

        assertEquals(PreorderStatus.SOLD_OUT, service.judge(pid));
    }

    // ---- 境界値 ---------------------------------------------------------------

    @Test
    void 公開開始日が今日0時ちょうどなら_NOT_PUBLIC_ではない() {
        // today < publishStart は false（等しい場合は公開済み扱い）
        Long pid = createProduct(p -> {
            p.setPublishStart(LocalDateTime.of(FIXED_TODAY, LocalTime.MIDNIGHT));
            p.setReleaseDate(FIXED_TODAY.minusDays(1));
        });
        createSkuWithStock(pid, "赤", "M", 3);

        assertEquals(PreorderStatus.ON_SALE, service.judge(pid));
    }

    @Test
    void 予約開始日が今日ちょうどなら_PRE_ORDER_NOT_STARTED_ではない() {
        // today < preorderStartDate は false → 次の判定（release_date）に進む
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(10).atStartOfDay());
            p.setPreorderStartDate(FIXED_TODAY);
            p.setReleaseDate(FIXED_TODAY.plusDays(7));
            p.setAcceptPreorder(true);
        });

        assertEquals(PreorderStatus.PRE_ORDER, service.judge(pid));
    }

    @Test
    void 発売日が今日ちょうどなら_PRE_ORDER_ではなく_ON_SALE_系() {
        // today < releaseDate は false → 在庫判定へ
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(10).atStartOfDay());
            p.setReleaseDate(FIXED_TODAY);
        });
        createSkuWithStock(pid, "白", "M", 1);

        assertEquals(PreorderStatus.ON_SALE, service.judge(pid));
    }

    @Test
    void publishStartがNULLなら_NOT_PUBLIC判定をスキップ() {
        // 設計書 §2-5: publish_start IS NULL は「制限なし」扱い、NOT_PUBLIC にはならない
        Long pid = createProduct(p -> {
            // publishStart を未設定 のまま
            p.setReleaseDate(FIXED_TODAY.plusDays(5));
            p.setPreorderStartDate(FIXED_TODAY.minusDays(1));
            p.setAcceptPreorder(true);
        });

        assertEquals(PreorderStatus.PRE_ORDER, service.judge(pid));
    }

    @Test
    void releaseDateがNULLなら_PRE_ORDER判定をスキップして在庫判定() {
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(10).atStartOfDay());
            // releaseDate / preorderStartDate どちらも未設定
        });
        createSkuWithStock(pid, "緑", "L", 10);

        assertEquals(PreorderStatus.ON_SALE, service.judge(pid));
    }

    @Test
    void 複数SKUの在庫合計で判定されること() {
        Long pid = createProduct(p -> {
            p.setPublishStart(FIXED_TODAY.minusDays(30).atStartOfDay());
            p.setReleaseDate(FIXED_TODAY.minusDays(1));
        });
        createSkuWithStock(pid, "赤", "M", 0);
        createSkuWithStock(pid, "赤", "L", 2);
        createSkuWithStock(pid, "青", "M", 0);

        assertEquals(PreorderStatus.ON_SALE, service.judge(pid));
    }

    // ---- 異常系 ---------------------------------------------------------------

    @Test
    void 存在しない_productId_のとき_404() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.judge(999_999_999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ---- helpers --------------------------------------------------------------

    private Long createProduct(java.util.function.Consumer<Product> customizer) {
        Product p = new Product();
        p.setName("テスト商品-" + System.nanoTime());
        p.setDescription("");
        p.setPrice(1000);
        p.setStock(0);
        p.setStatusCode("ON_SALE");
        customizer.accept(p);
        return productRepository.save(p).getId();
    }

    private Long createSkuWithStock(Long productId, String color, String size, int quantity) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-" + System.nanoTime());
        sku.setColor(color);
        sku.setSize(size);
        sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(quantity);
        stockRepository.save(stock);

        return skuId;
    }
}
