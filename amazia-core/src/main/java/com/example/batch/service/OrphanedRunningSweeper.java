package com.example.batch.service;

import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * フェーズ17 Step 2-4: JVM 異常終了で {@code RUNNING} 状態のまま孤立した
 * {@code batch_executions} を起動時にクリーンアップする
 * （設計書 §3 共通制御 3 / R-7）。
 *
 * <p>{@link ApplicationReadyEvent} を起点に、{@code started_at} が
 * 24 時間以上前の {@code RUNNING} 行を {@code FAILED} に強制遷移し、
 * {@code error_summary} に {@code [recovery] orphaned by JVM restart} を残す。
 *
 * <p>24 時間という閾値は、現状のジョブ最長想定（売上再計算・年次レポート）でも
 * 余裕を持って完了できるしきい値として設計書 §3 で確定済み。
 */
@Component
public class OrphanedRunningSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrphanedRunningSweeper.class);
    private static final long ORPHAN_THRESHOLD_HOURS = 24L;
    private static final String RECOVERY_MESSAGE = "[recovery] orphaned by JVM restart";

    private final BatchExecutionRepository repository;

    public OrphanedRunningSweeper(BatchExecutionRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sweepOnStartup() {
        sweep(LocalDateTime.now().minusHours(ORPHAN_THRESHOLD_HOURS));
    }

    /**
     * テストから直接叩けるよう public で切り出す。閾値もパラメタ化する。
     */
    @Transactional
    public int sweep(LocalDateTime threshold) {
        List<BatchExecution> orphans = repository
                .findByStatusAndStartedAtBefore("RUNNING", threshold);
        for (BatchExecution exec : orphans) {
            exec.setStatus("FAILED");
            exec.setFinishedAt(LocalDateTime.now());
            exec.setErrorSummary(RECOVERY_MESSAGE);
        }
        if (!orphans.isEmpty()) {
            repository.saveAllAndFlush(orphans);
            log.warn("[OrphanedRunningSweeper] swept {} orphaned RUNNING executions", orphans.size());
        }
        return orphans.size();
    }
}
