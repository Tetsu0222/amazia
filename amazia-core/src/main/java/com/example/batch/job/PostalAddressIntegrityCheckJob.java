package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.market.postal.repository.PostalAddressRepository;
import com.example.notification.service.BatchAlertNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * フェーズ17 Step 4-1: 郵便番号データ整合性チェック（設計書 §3.2 ①）。
 *
 * <p>取込本体（KEN_ALL.CSV 全件洗い替え）は phase13 が 03:00 JST に先行実行。
 * 本ジョブは取込結果の検査のみ担当（毎月 1 日 04:30 JST）。
 *
 * <p>チェック項目：
 * <ol>
 *   <li>{@code postal_addresses} の総件数が想定下限以上であること（{@code MIN_COUNT_THRESHOLD}）</li>
 *   <li>{@code MAX(updated_at)} が当日（取込日）以内であること</li>
 *   <li>サンプル郵便番号（{@code config/batch/postal_sample_codes.yml}）の各コードが引けること</li>
 * </ol>
 *
 * <p>NG 時は {@code postal_alerts} 購読者へ WARN 通知。自動ロールバックは行わない
 * （phase13 の取込が全件洗い替え方式のため、人手復旧が前提）。
 *
 * <p>「直近 12 ヶ月の中央値 ± 5%」は履歴テーブル不在のため簡素化し、12 万件下限のみ閾値とする
 * （設計書 §3.2 ① の意図：急減検知。本邦の郵便番号レコード総数 ≒ 12 万件）。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class PostalAddressIntegrityCheckJob extends AbstractBatchJob {

    public static final String JOB_NAME = "PostalAddressIntegrityCheckJob";
    public static final String SUBSCRIPTION_TAG = "postal_alerts";

    /** 急減検知の下限。日本国内の郵便番号総数 ≒ 12 万件を想定。 */
    static final long MIN_COUNT_THRESHOLD = 120_000L;

    private final PostalAddressRepository postalAddressRepository;
    private final BatchAlertNotifier alertNotifier;
    private final Clock clock;

    @Value("${amazia.batch.postal-check.sample-codes}")
    private List<String> sampleCodes;

    public PostalAddressIntegrityCheckJob(PostalAddressRepository postalAddressRepository,
                                          BatchAlertNotifier alertNotifier,
                                          Clock clock) {
        this.postalAddressRepository = postalAddressRepository;
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
        int target = 3;
        int failure = 0;

        long count = postalAddressRepository.count();
        if (count < MIN_COUNT_THRESHOLD) {
            failure++;
            alertNotifier.dispatch("WARN", SUBSCRIPTION_TAG,
                    "郵便番号データ整合性：件数が閾値未満",
                    String.format("postal_addresses 総件数 %d 件 < 下限 %d 件",
                            count, MIN_COUNT_THRESHOLD),
                    "count_low",
                    JOB_NAME, null);
        }

        LocalDateTime maxUpdatedAt = postalAddressRepository.findMaxUpdatedAt();
        LocalDate today = LocalDate.now(clock);
        if (maxUpdatedAt == null || maxUpdatedAt.toLocalDate().isBefore(today)) {
            failure++;
            alertNotifier.dispatch("WARN", SUBSCRIPTION_TAG,
                    "郵便番号データ整合性：更新日時が当日以内ではない",
                    String.format("MAX(updated_at)=%s / today=%s", maxUpdatedAt, today),
                    "stale_updated_at",
                    JOB_NAME, null);
        }

        List<String> missing = sampleCodes.stream()
                .filter(code -> postalAddressRepository.findByPostalCode(code).isEmpty())
                .toList();
        if (!missing.isEmpty()) {
            failure++;
            alertNotifier.dispatch("WARN", SUBSCRIPTION_TAG,
                    "郵便番号データ整合性：サンプルコード引けず",
                    "引けなかった郵便番号: " + String.join(", ", missing),
                    "sample_missing:" + String.join("|", missing),
                    JOB_NAME, null);
        }

        return BatchResult.of(target, target - failure, failure);
    }
}
