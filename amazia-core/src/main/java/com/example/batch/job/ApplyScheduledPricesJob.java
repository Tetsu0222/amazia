package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.batch.config.OnDemandJob;
import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.scheduledprice.service.ApplyScheduledPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * フェーズ17 Step 3-6: 価格スケジュール適用バッチ（設計書 §3.1 ⑥ / r7）。
 *
 * <p>{@code apply_date <= 今日} かつ {@code is_pending = TRUE} の予約価格を全件適用する。
 * 各 SKU の処理は {@link ApplyScheduledPriceService#applyOne} が独立トランザクションで担う。
 *
 * <p>{@link OnDemandJob} を実装し、Bean 名 = jobName で
 * {@link com.example.batch.controller.BatchManualTriggerController} 経由でも起動可能（設計書 §3.4）。
 */
@Component(ApplyScheduledPricesJob.JOB_NAME)
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class ApplyScheduledPricesJob extends AbstractBatchJob implements OnDemandJob {

    private static final Logger log = LoggerFactory.getLogger(ApplyScheduledPricesJob.class);
    public static final String JOB_NAME = "ApplyScheduledPricesJob";

    private final ProductSkuScheduledPriceRepository scheduledRepository;
    private final ApplyScheduledPriceService applyService;

    public ApplyScheduledPricesJob(ProductSkuScheduledPriceRepository scheduledRepository,
                                   ApplyScheduledPriceService applyService) {
        this.scheduledRepository = scheduledRepository;
        this.applyService = applyService;
    }

    @Scheduled(cron = "${amazia.batch.daily.cron}", zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    public String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        LocalDate today = LocalDate.now();
        List<ProductSkuScheduledPrice> targets = scheduledRepository
                .findByApplyDateLessThanEqualAndIsPendingTrue(today);

        int success = 0;
        int failure = 0;
        for (ProductSkuScheduledPrice s : targets) {
            try {
                applyService.applyOne(s);
                success++;
            } catch (RuntimeException ex) {
                failure++;
                log.error("[{}] failed to apply scheduled price id={}, sku_id={}",
                        JOB_NAME, s.getId(), s.getSkuId(), ex);
            }
        }
        log.info("[{}] applied {}/{}, failed {}", JOB_NAME, success, targets.size(), failure);
        return BatchResult.of(targets.size(), success, failure);
    }
}
