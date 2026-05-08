package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.market.postal.service.ImportPostalCsvService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * フェーズ17 Step 4-0: 郵便番号 KEN_ALL.CSV 取込本体（設計書 §3.2 ① 取込本体）。
 *
 * <p>毎月 1 日 03:00 JST に動作。{@link ImportPostalCsvService#execute()} を呼び出し、
 * {@code postal_addresses} テーブルを全件洗い替えする。取込結果の整合性チェックは
 * {@link PostalAddressIntegrityCheckJob}（同日 04:30 JST）で別ジョブとして実施。
 *
 * <p>phase13 に実装済の {@link ImportPostalCsvService} を再利用し、本ジョブは
 * 「{@code @Scheduled} の引き金」と「{@code batch_executions} への記録」のみを担当する。
 * 手動起動経路（{@code --import-postal-csv} フラグ）は phase17 完了後も残し、運用救済として併存させる。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class PostalCsvImportJob extends AbstractBatchJob {

    public static final String JOB_NAME = "PostalCsvImportJob";

    private final ImportPostalCsvService importService;

    public PostalCsvImportJob(ImportPostalCsvService importService) {
        this.importService = importService;
    }

    @Scheduled(cron = "${amazia.batch.monthly.postal-import-cron}",
               zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        int saved = importService.execute();
        return BatchResult.of(saved, saved, 0);
    }
}
