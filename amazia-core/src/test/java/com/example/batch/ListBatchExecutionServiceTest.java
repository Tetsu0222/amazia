package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.batch.service.ListBatchExecutionService;
import com.example.batch.service.ListBatchExecutionService.PageResult;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 6-0: バッチ実行履歴一覧 Service の TDD。
 * 設計書 §13.7.1 GET /api/console/batch/executions のロジック。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ListBatchExecutionServiceTest {

    @Autowired private ListBatchExecutionService service;
    @Autowired private BatchExecutionRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void started_at_DESC_で並ぶ() {
        LocalDateTime t1 = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 5, 1, 11, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 5, 1, 12, 0);
        repository.saveAndFlush(newExec("JobA", "SUCCESS", t1));
        repository.saveAndFlush(newExec("JobB", "SUCCESS", t2));
        repository.saveAndFlush(newExec("JobC", "SUCCESS", t3));

        PageResult result = service.list(null, null, 0, 10);

        assertEquals(3, result.total());
        assertEquals("JobC", result.items().get(0).getJobName());
        assertEquals("JobB", result.items().get(1).getJobName());
        assertEquals("JobA", result.items().get(2).getJobName());
    }

    @Test
    void job_name_でフィルタできる() {
        repository.saveAndFlush(newExec("JobA", "SUCCESS", LocalDateTime.now()));
        repository.saveAndFlush(newExec("JobB", "SUCCESS", LocalDateTime.now()));
        repository.saveAndFlush(newExec("JobA", "FAILED",  LocalDateTime.now()));

        PageResult result = service.list("JobA", null, 0, 10);

        assertEquals(2, result.total());
        assertTrue(result.items().stream().allMatch(e -> "JobA".equals(e.getJobName())));
    }

    @Test
    void status_でフィルタできる() {
        repository.saveAndFlush(newExec("JobA", "RUNNING", LocalDateTime.now()));
        repository.saveAndFlush(newExec("JobB", "SUCCESS", LocalDateTime.now()));
        repository.saveAndFlush(newExec("JobC", "FAILED",  LocalDateTime.now()));

        PageResult result = service.list(null, "RUNNING", 0, 10);

        assertEquals(1, result.total());
        assertEquals("JobA", result.items().get(0).getJobName());
    }

    @Test
    void 空結果でもtotal0と空配列を返す() {
        PageResult result = service.list(null, null, 0, 10);
        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void ページング_offset_と_size_が効く() {
        for (int i = 0; i < 25; i++) {
            repository.saveAndFlush(newExec("Job" + i, "SUCCESS",
                    LocalDateTime.of(2026, 5, 1, 0, 0).plusMinutes(i)));
        }
        PageResult page1 = service.list(null, null, 0, 10);
        PageResult page3 = service.list(null, null, 20, 10);

        assertEquals(25, page1.total());
        assertEquals(10, page1.items().size());
        assertEquals(25, page3.total());
        assertEquals(5, page3.items().size());
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
