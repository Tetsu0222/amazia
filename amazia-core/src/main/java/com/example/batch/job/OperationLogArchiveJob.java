package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.entity.OperationLogArchive;
import com.example.operationlog.repository.OperationLogArchiveRepository;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * フェーズ17 Step 4-4: 古い {@code operation_logs} のアーカイブ（設計書 §3.3 ②）。
 *
 * <p>毎年 1 月 1 日 05:00 JST、{@code created_at < NOW() - 1 YEAR} のレコードを
 * {@code operation_logs_archive} に移送（INSERT → DELETE を 1 トランザクション）。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class OperationLogArchiveJob extends AbstractBatchJob {

    public static final String JOB_NAME = "OperationLogArchiveJob";

    private final OperationLogRepository operationLogRepository;
    private final OperationLogArchiveRepository archiveRepository;
    private final Clock clock;

    public OperationLogArchiveJob(OperationLogRepository operationLogRepository,
                                  OperationLogArchiveRepository archiveRepository,
                                  Clock clock) {
        this.operationLogRepository = operationLogRepository;
        this.archiveRepository = archiveRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${amazia.batch.yearly.cron}", zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    @Transactional
    protected BatchResult execute() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusYears(1);
        return archiveBefore(threshold);
    }

    /** テスト容易性のため閾値を引数で受ける形を分離。 */
    @Transactional
    public BatchResult archiveBefore(LocalDateTime threshold) {
        List<OperationLog> targets = operationLogRepository.findByCreatedAtBefore(threshold);
        if (targets.isEmpty()) {
            return BatchResult.empty();
        }

        LocalDateTime archivedAt = LocalDateTime.now(clock);
        for (OperationLog log : targets) {
            OperationLogArchive arch = new OperationLogArchive();
            arch.setId(log.getId());
            arch.setUserId(log.getUserId());
            arch.setAction(log.getAction());
            arch.setTargetType(log.getTargetType());
            arch.setTargetId(log.getTargetId());
            arch.setScreenName(log.getScreenName());
            arch.setApiName(log.getApiName());
            arch.setComment(log.getComment());
            arch.setCreatedAt(log.getCreatedAt());
            arch.setArchivedAt(archivedAt);
            archiveRepository.save(arch);
        }
        operationLogRepository.deleteAllInBatch(targets);

        return BatchResult.of(targets.size(), targets.size(), 0);
    }
}
