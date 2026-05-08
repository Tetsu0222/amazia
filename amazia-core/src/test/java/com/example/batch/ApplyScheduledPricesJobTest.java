package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.ApplyScheduledPricesJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 3-6: ApplyScheduledPricesJob の TDD（設計書 §3.1 ⑥ / 計画書 §4-6）。
 *
 * <p>計画書記載 TDD：
 * <ul>
 *   <li>未来日 {@code apply_date} で {@code is_pending = TRUE} → 翌日のバッチで現行価格に昇格</li>
 *   <li>{@code apply_date = TODAY} → 当日のバッチで適用</li>
 *   <li>2 度連続実行 → 2 度目は対象 0 件</li>
 * </ul>
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ApplyScheduledPricesJobTest {

    @Autowired private ApplyScheduledPricesJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuPriceRepository priceRepository;
    @Autowired private ProductSkuScheduledPriceRepository scheduledRepository;

    @BeforeEach
    void cleanupPendingSchedulesForToday() {
        // 同 ApplicationContext 共有の H2（DB_CLOSE_DELAY=-1）に他テストが残した
        // is_pending=true && apply_date<=today レコードを掃除する自衛コード（051 派生②）。
        // クラス @Transactional 内でロールバック対象なので他テストへの副作用はない。
        scheduledRepository.deleteAll(
                scheduledRepository.findByApplyDateLessThanEqualAndIsPendingTrue(LocalDate.now()));
    }

    @Test
    void APP_1_apply_date_today_の予約は適用され_現行価格は非アクティブ化される() {
        long skuId = persistProductWithSku();
        ProductSkuPrice oldPrice = persistActivePrice(skuId, 1000, LocalDate.now().minusDays(30));
        Long scheduledId = persistScheduled(skuId, 1500, LocalDate.now()).getId();

        job.run("scheduler");

        // 旧価格が非アクティブ化
        ProductSkuPrice oldAfter = priceRepository.findById(oldPrice.getId()).orElseThrow();
        assertEquals(Boolean.FALSE, oldAfter.getIsActive());
        assertEquals(LocalDate.now().minusDays(1), oldAfter.getEndDate());

        // 新しい active 価格が存在
        List<ProductSkuPrice> all = priceRepository.findBySkuIdIn(List.of(skuId));
        ProductSkuPrice activeNow = all.stream().filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .findFirst().orElseThrow();
        assertEquals(1500, activeNow.getPrice());
        assertEquals(LocalDate.now(), activeNow.getStartDate());
        assertNull(activeNow.getEndDate());

        // 予約レコードが is_pending=false / applied_at にタイムスタンプ
        ProductSkuScheduledPrice scheduledAfter = scheduledRepository.findById(scheduledId).orElseThrow();
        assertEquals(Boolean.FALSE, scheduledAfter.getIsPending());
        assertNotNull(scheduledAfter.getAppliedAt());

        BatchExecution exec = latestExecution();
        assertEquals("SUCCESS", exec.getStatus());
    }

    @Test
    void APP_2_未来日の予約は今日のバッチでは対象外() {
        long skuId = persistProductWithSku();
        persistActivePrice(skuId, 1000, LocalDate.now().minusDays(30));
        Long futureId = persistScheduled(skuId, 1500, LocalDate.now().plusDays(2)).getId();

        job.run("scheduler");

        ProductSkuScheduledPrice futureAfter = scheduledRepository.findById(futureId).orElseThrow();
        assertEquals(Boolean.TRUE, futureAfter.getIsPending(), "未来日は未適用のまま");
        assertNull(futureAfter.getAppliedAt());
    }

    @Test
    void APP_3_2度実行しても_2回目は対象0件で冪等() {
        long skuId = persistProductWithSku();
        persistActivePrice(skuId, 1000, LocalDate.now().minusDays(30));
        persistScheduled(skuId, 1500, LocalDate.now());

        job.run("scheduler");
        job.run("scheduler");

        // 2 度目は対象 0 件 → 最新 batch_executions の target_count = 0
        BatchExecution exec = latestExecution();
        assertEquals(0, exec.getTargetCount(), "2 回目は is_pending=true が無く対象 0 件");

        // 価格 active 行は 1 つだけ
        long activeCount = priceRepository.findBySkuIdIn(List.of(skuId)).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive())).count();
        assertEquals(1L, activeCount);
    }

    private long persistProductWithSku() {
        Product p = new Product();
        p.setName("Apply テスト商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-AP-" + System.nanoTime());
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

    private ProductSkuScheduledPrice persistScheduled(long skuId, int price, LocalDate applyDate) {
        ProductSkuScheduledPrice s = new ProductSkuScheduledPrice();
        s.setSkuId(skuId);
        s.setScheduledPrice(price);
        s.setApplyDate(applyDate);
        s.setIsPending(Boolean.TRUE);
        return scheduledRepository.saveAndFlush(s);
    }

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(ApplyScheduledPricesJob.JOB_NAME)
                .get(0);
    }
}
