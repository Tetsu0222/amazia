package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.notification.service.BatchAlertNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * フェーズ17 Step 3-4: 配送ステータスの自動更新（設計書 §3.1 ④）。
 *
 * <p>**遷移はしない**：{@code SHIPPED} のまま 7 日以上経過した配送を遅延候補として抽出し、
 * {@code delivery_alerts} 購読者に通知のみ行う。
 *
 * <p>{@code PENDING → SHIPPED} / {@code SHIPPED → DELIVERED} はオペレータの責務（設計書注記）。
 * バッチが状態を勝手に進めると、phase14 r3 の予約購入の出荷時在庫減算フックが意図せず発火するため避ける。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class DeliveryStatusAdvanceJob extends AbstractBatchJob {

    public static final String JOB_NAME = "DeliveryStatusAdvanceJob";
    public static final String SUBSCRIPTION_TAG = "delivery_alerts";
    /** 配送遅延と判定する経過日数（設計書 §3.1 ④）。 */
    public static final long DELAY_THRESHOLD_DAYS = 7L;

    private final DeliveryRepository deliveryRepository;
    private final BatchAlertNotifier alertNotifier;

    @Value("${amazia.sales.shipping-statuses.shipped-id}")
    private long shippedStatusId;

    public DeliveryStatusAdvanceJob(DeliveryRepository deliveryRepository,
                                    BatchAlertNotifier alertNotifier) {
        this.deliveryRepository = deliveryRepository;
        this.alertNotifier = alertNotifier;
    }

    @Scheduled(cron = "${amazia.batch.daily.cron}", zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        LocalDate threshold = LocalDate.now().minusDays(DELAY_THRESHOLD_DAYS);
        List<Delivery> delayed = deliveryRepository
                .findByShippingStatusIdAndDeliveredDateIsNullAndScheduledDateBefore(
                        shippedStatusId, threshold);

        for (Delivery d : delayed) {
            alertNotifier.dispatch("WARN", SUBSCRIPTION_TAG,
                    "配送遅延の疑い",
                    String.format("配送 ID %d は予定日 %s を 7 日以上経過しても未配達です。",
                            d.getId(), d.getScheduledDate()),
                    "delivery_id=" + d.getId(),
                    JOB_NAME, null);
        }

        return BatchResult.of(delayed.size(), delayed.size(), 0);
    }
}
