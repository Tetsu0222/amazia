package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.notification.service.BatchAlertNotifier;
import com.example.salesreport.entity.MonthlySalesReport;
import com.example.salesreport.entity.YearlySalesReport;
import com.example.salesreport.repository.MonthlySalesReportRepository;
import com.example.salesreport.repository.YearlySalesReportRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * フェーズ17 Step 4-3: 年次売上レポート生成（設計書 §3.3 ①）。
 *
 * <p>毎年 1 月 1 日 05:00 JST、前年 1 〜 12 月の {@code monthly_sales_reports} を
 * 年単位で再集計し {@code yearly_sales_reports} に UPSERT する。集計軸は月次と同じ
 * （商品 / 決済 / 配送 / 予約 / 総合計）で、月の値を「合計」する形。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class YearlySalesReportJob extends AbstractBatchJob {

    public static final String JOB_NAME = "YearlySalesReportJob";
    public static final String SUBSCRIPTION_TAG = "sales_alerts";

    private final MonthlySalesReportRepository monthlyRepository;
    private final YearlySalesReportRepository yearlyRepository;
    private final BatchAlertNotifier alertNotifier;
    private final Clock clock;

    public YearlySalesReportJob(MonthlySalesReportRepository monthlyRepository,
                                YearlySalesReportRepository yearlyRepository,
                                BatchAlertNotifier alertNotifier,
                                Clock clock) {
        this.monthlyRepository = monthlyRepository;
        this.yearlyRepository = yearlyRepository;
        this.alertNotifier = alertNotifier;
        this.clock = clock;
    }

    @Scheduled(cron = "${amazia.batch.yearly.cron}", zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        short targetYear = (short) (LocalDate.now(clock).getYear() - 1);
        return aggregateAndPersist(targetYear);
    }

    /** テスト容易性のため対象年を引数で受ける形を分離。 */
    public BatchResult aggregateAndPersist(short targetYear) {
        Map<AxisKey, long[]> bucket = new HashMap<>();
        for (short m = 1; m <= 12; m++) {
            for (MonthlySalesReport r : monthlyRepository.findByYearAndMonth(targetYear, m)) {
                AxisKey key = new AxisKey(r.getProductId(), r.getPaymentMethodId(),
                        r.getShippingMethodId(), r.getIsPreorder());
                long[] sum = bucket.computeIfAbsent(key, k -> new long[]{0L, 0L});
                sum[0] += r.getTotalAmount();
                sum[1] += r.getTotalQuantity();
            }
        }

        int upserts = 0;
        for (Map.Entry<AxisKey, long[]> e : bucket.entrySet()) {
            AxisKey k = e.getKey();
            long amount = e.getValue()[0];
            long quantity = e.getValue()[1];

            YearlySalesReport entity = yearlyRepository.findByAxes(
                    targetYear, k.productId, k.paymentMethodId, k.shippingMethodId, k.isPreorder)
                    .orElseGet(YearlySalesReport::new);

            entity.setYear(targetYear);
            entity.setProductId(k.productId);
            entity.setPaymentMethodId(k.paymentMethodId);
            entity.setShippingMethodId(k.shippingMethodId);
            entity.setIsPreorder(k.isPreorder);
            entity.setTotalAmount(amount);
            entity.setTotalQuantity((int) quantity);
            yearlyRepository.save(entity);
            upserts++;
        }

        alertNotifier.dispatch("INFO", SUBSCRIPTION_TAG,
                String.format("年次売上レポート生成完了 (%d)", targetYear),
                String.format("%d 年分の集計が完了しました（%d 軸レコード UPSERT）。",
                        targetYear, upserts),
                "yearly:" + targetYear,
                JOB_NAME, null);

        return BatchResult.of(bucket.size(), upserts, 0);
    }

    /** 軸の組合せキー。NULL も等値判定に含める。 */
    private record AxisKey(Long productId, Long paymentMethodId,
                           Long shippingMethodId, Boolean isPreorder) {
        @Override public boolean equals(Object o) {
            if (!(o instanceof AxisKey other)) return false;
            return Objects.equals(productId, other.productId)
                && Objects.equals(paymentMethodId, other.paymentMethodId)
                && Objects.equals(shippingMethodId, other.shippingMethodId)
                && Objects.equals(isPreorder, other.isPreorder);
        }
        @Override public int hashCode() {
            return Objects.hash(productId, paymentMethodId, shippingMethodId, isPreorder);
        }
    }

}
