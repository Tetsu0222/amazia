package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1: batch_executions Entity / Repository の永続化検証。
 * 起動時クリーンアップ（OrphanedRunningSweeper）が依存する
 * findByStatusAndStartedAtBefore の動作も合わせて確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class BatchExecutionRepositoryTest {

    @Autowired
    private BatchExecutionRepository repository;

    @Test
    void save_すると_id_と_created_at_が自動採番される() {
        BatchExecution exec = newExec("InventoryConsistencyCheckJob", "RUNNING",
                LocalDateTime.now());

        BatchExecution saved = repository.saveAndFlush(exec);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals("RUNNING", saved.getStatus());
    }

    @Test
    void findByStatus_で_status_絞り込みができる() {
        repository.saveAndFlush(newExec("JobA", "RUNNING",  LocalDateTime.now()));
        repository.saveAndFlush(newExec("JobB", "SUCCESS",  LocalDateTime.now()));
        repository.saveAndFlush(newExec("JobC", "FAILED",   LocalDateTime.now()));

        List<BatchExecution> running = repository.findByStatus("RUNNING");
        assertEquals(1, running.size());
        assertEquals("JobA", running.get(0).getJobName());
    }

    @Test
    void findByStatusAndStartedAtBefore_で_OrphanedRunningSweeper_の対象抽出ができる() {
        LocalDateTime old   = LocalDateTime.now().minusHours(25);
        LocalDateTime fresh = LocalDateTime.now();

        repository.saveAndFlush(newExec("OldJob",   "RUNNING", old));
        repository.saveAndFlush(newExec("FreshJob", "RUNNING", fresh));

        List<BatchExecution> orphans = repository
                .findByStatusAndStartedAtBefore("RUNNING", LocalDateTime.now().minusHours(24));

        assertEquals(1, orphans.size());
        assertEquals("OldJob", orphans.get(0).getJobName());
    }

    private BatchExecution newExec(String jobName, String status, LocalDateTime startedAt) {
        BatchExecution e = new BatchExecution();
        e.setJobName(jobName);
        e.setStatus(status);
        e.setStartedAt(startedAt);
        e.setTriggeredBy("scheduler");
        return e;
    }
}
