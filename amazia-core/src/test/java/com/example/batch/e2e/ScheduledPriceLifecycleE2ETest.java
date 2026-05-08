package com.example.batch.e2e;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.ApplyScheduledPricesJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.scheduledprice.service.RegisterScheduledSkuPriceService;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * phase17 Step 8 / E2E-9（設計書 §12.3 / r7）：
 * 価格スケジュールの「予約 → 適用 → 履歴反映」の一気通貫シナリオ。
 *
 * <p>設計書 E2E-9 は「{@code apply_date = TOMORROW} で予約 → 翌日 03:30 待機」だが、
 * 待機部分は時計操作が必要なため、待機相当の挙動として
 * 「未来日は当日のジョブで適用されない」を観測する（{@code ApplyScheduledPricesJobTest.APP_2}
 * と検証意図は同じだが、本テストは Service 経由の登録 → ジョブ実行 → 履歴の物理反映 の
 * E2E ワークフローとして書き直す）。
 *
 * <ul>
 *   <li>E2E-9 (a)：apply_date = TODAY 登録 → ジョブ実行 → 旧 active 価格が is_active=FALSE、
 *                   新 active 価格が現行になり、予約レコードが is_pending=FALSE / applied_at セット</li>
 *   <li>E2E-9 (b)：apply_date = TOMORROW 登録 → ジョブ実行 → 切り替えされず、予約は
 *                   is_pending=TRUE のまま据え置き</li>
 * </ul>
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
@Sql(
        scripts = "/cleanup/scheduled_prices.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class ScheduledPriceLifecycleE2ETest {

    @Autowired private ApplyScheduledPricesJob job;
    @Autowired private RegisterScheduledSkuPriceService registerService;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuPriceRepository priceRepository;
    @Autowired private ProductSkuScheduledPriceRepository scheduledRepository;

    @Test
    void E2E_9a_apply_date_TODAY_の予約を登録しジョブ実行で現行価格が切替わり履歴に旧価格が残る() {
        long skuId = persistProductWithSku();
        ProductSkuPrice oldPrice = persistActivePrice(skuId, 1000, LocalDate.now().minusDays(30));

        // Console UI 相当の登録経路（Service 直接呼び出し）
        ProductSkuScheduledPrice scheduled =
                registerService.upsert(skuId, 1500, LocalDate.now());
        assertEquals(Boolean.TRUE, scheduled.getIsPending());
        assertNull(scheduled.getAppliedAt());

        job.run("scheduler");

        // 旧 active が is_active=false に降格
        ProductSkuPrice oldAfter = priceRepository.findById(oldPrice.getId()).orElseThrow();
        assertEquals(Boolean.FALSE, oldAfter.getIsActive());

        // 新 active が現行になり、price と start_date が予約と一致
        List<ProductSkuPrice> ofMine = priceRepository.findBySkuIdIn(List.of(skuId));
        ProductSkuPrice activeNow = ofMine.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .findFirst().orElseThrow();
        assertEquals(1500, activeNow.getPrice());
        assertEquals(LocalDate.now(), activeNow.getStartDate());

        // 予約レコードが消化済（is_pending=false）
        ProductSkuScheduledPrice scheduledAfter =
                scheduledRepository.findById(scheduled.getId()).orElseThrow();
        assertEquals(Boolean.FALSE, scheduledAfter.getIsPending());
        assertNotNull(scheduledAfter.getAppliedAt());

        // バッチ自体は SUCCESS
        BatchExecution exec = latestExecution();
        assertEquals("SUCCESS", exec.getStatus());
    }

    @Test
    void E2E_9b_apply_date_TOMORROW_の予約は今日のジョブでは未適用のまま残る() {
        long skuId = persistProductWithSku();
        persistActivePrice(skuId, 1000, LocalDate.now().minusDays(30));

        ProductSkuScheduledPrice scheduled =
                registerService.upsert(skuId, 1500, LocalDate.now().plusDays(1));

        job.run("scheduler");

        // 予約は据え置き（待機相当）
        ProductSkuScheduledPrice scheduledAfter =
                scheduledRepository.findById(scheduled.getId()).orElseThrow();
        assertEquals(Boolean.TRUE, scheduledAfter.getIsPending(),
                "TOMORROW 適用日は当日のジョブでは未適用のまま");
        assertNull(scheduledAfter.getAppliedAt());

        // 旧 active 価格は据え置き（切替なし）
        long activeWith1000 = priceRepository.findBySkuIdIn(List.of(skuId)).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> p.getPrice() == 1000)
                .count();
        assertEquals(1L, activeWith1000, "旧 active 価格 1000 が現行のまま");
    }

    private long persistProductWithSku() {
        Product p = new Product();
        p.setName("E2E-9 テスト商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-E2E9-" + System.nanoTime());
        sku.setColor("白"); sku.setSize("M"); sku.setStatus("ACTIVE");
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

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(ApplyScheduledPricesJob.JOB_NAME)
                .get(0);
    }
}
