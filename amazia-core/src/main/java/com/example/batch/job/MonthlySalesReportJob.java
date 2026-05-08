package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.batch.repository.SalesAggregationRepository;
import com.example.notification.service.BatchAlertNotifier;
import com.example.salesreport.entity.MonthlySalesReport;
import com.example.salesreport.repository.MonthlySalesReportRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * フェーズ17 Step 4-2: 月次売上レポート生成（設計書 §3.2 ②）。
 *
 * <p>毎月 1 日 04:30 JST、前月分の {@code sales} を 4 軸（商品 / 決済 / 配送 / 予約）＋
 * 総合計（軸 NULL）で集計し {@code monthly_sales_reports} に UPSERT する（R-15）。
 * 完了後、{@code sales_alerts} 購読者へ INFO 通知（Console URL は Step 6 で本実装）。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class MonthlySalesReportJob extends AbstractBatchJob {

    public static final String JOB_NAME = "MonthlySalesReportJob";
    public static final String SUBSCRIPTION_TAG = "sales_alerts";

    private final SalesAggregationRepository aggregationRepository;
    private final MonthlySalesReportRepository reportRepository;
    private final BatchAlertNotifier alertNotifier;
    private final Clock clock;

    public MonthlySalesReportJob(SalesAggregationRepository aggregationRepository,
                                 MonthlySalesReportRepository reportRepository,
                                 BatchAlertNotifier alertNotifier,
                                 Clock clock) {
        this.aggregationRepository = aggregationRepository;
        this.reportRepository = reportRepository;
        this.alertNotifier = alertNotifier;
        this.clock = clock;
    }

    @Scheduled(cron = "${amazia.batch.monthly.postal-check-cron}",
               zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        YearMonth target = YearMonth.from(LocalDate.now(clock)).minusMonths(1);
        return aggregateAndPersist(target);
    }

    /** テスト容易性のため対象月を引数で受ける形を分離。 */
    public BatchResult aggregateAndPersist(YearMonth target) {
        LocalDate from = target.atDay(1);
        LocalDate to = target.atEndOfMonth();
        List<Object[]> rows = aggregationRepository.aggregateMonthly(from, to);

        int upserts = 0;
        for (Object[] r : rows) {
            // 総合計ブランチで対象月の sales が空のとき、SUM(...) は NULL を返す → スキップ
            if (r[4] == null || r[5] == null) continue;

            Long productId        = toLong(r[0]);
            Long paymentMethodId  = toLong(r[1]);
            Long shippingMethodId = toLong(r[2]);
            Boolean isPreorder    = toBoolean(r[3]);
            long totalAmount      = ((Number) r[4]).longValue();
            int totalQuantity     = ((Number) r[5]).intValue();

            MonthlySalesReport entity = reportRepository.findByAxes(
                    (short) target.getYear(), (short) target.getMonthValue(),
                    productId, paymentMethodId, shippingMethodId, isPreorder)
                    .orElseGet(MonthlySalesReport::new);

            entity.setYear((short) target.getYear());
            entity.setMonth((short) target.getMonthValue());
            entity.setProductId(productId);
            entity.setPaymentMethodId(paymentMethodId);
            entity.setShippingMethodId(shippingMethodId);
            entity.setIsPreorder(isPreorder);
            entity.setTotalAmount(totalAmount);
            entity.setTotalQuantity(totalQuantity);
            reportRepository.save(entity);
            upserts++;
        }

        alertNotifier.dispatch("INFO", SUBSCRIPTION_TAG,
                String.format("月次売上レポート生成完了 (%d-%02d)", target.getYear(), target.getMonthValue()),
                String.format("%d-%02d 月分の集計が完了しました（%d 軸レコード UPSERT）。Console の売上レポート画面でご確認ください。",
                        target.getYear(), target.getMonthValue(), upserts),
                "monthly:" + target,
                JOB_NAME, null);

        return BatchResult.of(upserts, upserts, 0);
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static Boolean toBoolean(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return ((Number) o).intValue() != 0;
    }
}
