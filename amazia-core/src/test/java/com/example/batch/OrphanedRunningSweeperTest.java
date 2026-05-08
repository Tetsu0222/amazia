package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.batch.service.OrphanedRunningSweeper;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 2: OrphanedRunningSweeper の起動時クリーンアップ（SWEEP-1）。
 *
 * <p>{@link org.springframework.boot.context.event.ApplicationReadyEvent} ベースの
 * 自動発火は SpringBootTest の起動完了時に既に走っているため、本テストでは
 * {@link OrphanedRunningSweeper#sweep(LocalDateTime)} を直接呼んで観測する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class OrphanedRunningSweeperTest {

    @Autowired private OrphanedRunningSweeper sweeper;
    @Autowired private BatchExecutionRepository repository;

    @Test
    void SWEEP_1_24時間以上前のRUNNINGはFAILEDに遷移し_新しいRUNNINGは残る() {
        LocalDateTime old = LocalDateTime.now().minusHours(25);
        LocalDateTime fresh = LocalDateTime.now().minusMinutes(10);

        BatchExecution oldRow = repository.saveAndFlush(newExec("OldOrphan", "RUNNING", old));
        BatchExecution freshRow = repository.saveAndFlush(newExec("FreshActive", "RUNNING", fresh));

        int swept = sweeper.sweep(LocalDateTime.now().minusHours(24));

        assertEquals(1, swept);
        BatchExecution oldAfter = repository.findById(oldRow.getId()).orElseThrow();
        assertEquals("FAILED", oldAfter.getStatus());
        assertNotNull(oldAfter.getFinishedAt());
        assertEquals("[recovery] orphaned by JVM restart", oldAfter.getErrorSummary());

        BatchExecution freshAfter = repository.findById(freshRow.getId()).orElseThrow();
        assertEquals("RUNNING", freshAfter.getStatus());
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
