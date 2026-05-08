package com.example.batch.service;

import com.example.batch.config.BatchResult;
import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * フェーズ17 Step 2-2: {@code batch_executions} の状態遷移を独立トランザクション
 * （{@code REQUIRES_NEW}）で記録する Service（設計書 §3 共通制御 1）。
 *
 * <p>本体トランザクションがロールバックしても RUNNING → SUCCESS / FAILED / PARTIAL の
 * 状態記録は失われないことを保証する。
 */
@Service
public class BatchExecutionRecorder {

    /** error_summary は TEXT で MySQL 上限近くまで許容するが、ノイズ防止で 4000 文字に切り詰める。 */
    private static final int ERROR_SUMMARY_MAX_LENGTH = 4000;

    private final BatchExecutionRepository repository;

    public BatchExecutionRecorder(BatchExecutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchExecution start(String jobName, String triggeredBy) {
        BatchExecution exec = new BatchExecution();
        exec.setJobName(jobName);
        exec.setStatus("RUNNING");
        exec.setStartedAt(LocalDateTime.now());
        exec.setTriggeredBy(triggeredBy);
        return repository.saveAndFlush(exec);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(BatchExecution exec, BatchResult result) {
        BatchExecution managed = repository.findById(exec.getId()).orElse(exec);
        managed.setStatus(result != null && result.hasFailure() ? "PARTIAL" : "SUCCESS");
        managed.setFinishedAt(LocalDateTime.now());
        if (result != null) {
            managed.setTargetCount(result.targetCount());
            managed.setSuccessCount(result.successCount());
            managed.setFailureCount(result.failureCount());
        }
        repository.saveAndFlush(managed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(BatchExecution exec, Throwable error) {
        BatchExecution managed = repository.findById(exec.getId()).orElse(exec);
        managed.setStatus("FAILED");
        managed.setFinishedAt(LocalDateTime.now());
        managed.setErrorSummary(summarize(error));
        repository.saveAndFlush(managed);
    }

    private String summarize(Throwable error) {
        if (error == null) return null;
        String message = error.getClass().getSimpleName()
                + (error.getMessage() != null ? ": " + error.getMessage() : "");
        if (message.length() > ERROR_SUMMARY_MAX_LENGTH) {
            return message.substring(0, ERROR_SUMMARY_MAX_LENGTH);
        }
        return message;
    }
}
