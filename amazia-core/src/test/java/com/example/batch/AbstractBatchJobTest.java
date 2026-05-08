package com.example.batch;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 2: AbstractBatchJob テンプレートメソッドの動作検証
 * （ABJ-1 / ABJ-2 / ABJ-3）。
 */
@SpringBootTest
@Import({TestAwsConfig.class, AbstractBatchJobTest.JobBeans.class})
@ActiveProfiles("test")
class AbstractBatchJobTest {

    @Autowired private SuccessJob successJob;
    @Autowired private RetryThenFailJob retryJob;
    @Autowired private DataIntegrityFailJob dataIntegrityJob;
    @Autowired private BatchExecutionRepository repository;

    @AfterEach
    void resetCounters() {
        retryJob.attempts.set(0);
    }

    @Test
    void ABJ_1_execute成功で_batch_executions_は_SUCCESS_に遷移() {
        successJob.run("scheduler");

        List<BatchExecution> rows = repository.findByJobNameOrderByStartedAtDesc("SuccessJob");
        assertEquals(1, rows.size());
        BatchExecution row = rows.get(0);
        assertEquals("SUCCESS", row.getStatus());
        assertNotNull(row.getFinishedAt());
        assertEquals(10, row.getTargetCount());
        assertEquals(10, row.getSuccessCount());
        assertEquals(0, row.getFailureCount());
    }

    @Test
    void ABJ_2_リトライ可能例外は3回試行後にFAILEDになる() {
        retryJob.run("scheduler");

        assertEquals(3, retryJob.attempts.get(), "リトライ可能例外は MAX_ATTEMPTS=3 まで試行");
        BatchExecution row = repository
                .findByJobNameOrderByStartedAtDesc("RetryThenFailJob").get(0);
        assertEquals("FAILED", row.getStatus());
        assertNotNull(row.getErrorSummary());
        assertTrue(row.getErrorSummary().contains("ResourceAccessException"));
    }

    @Test
    void ABJ_3_DataIntegrityViolationExceptionは1回でFAILEDになる() {
        dataIntegrityJob.run("scheduler");

        assertEquals(1, dataIntegrityJob.attempts.get(), "リトライ不可例外は 1 回で打ち切る");
        BatchExecution row = repository
                .findByJobNameOrderByStartedAtDesc("DataIntegrityFailJob").get(0);
        assertEquals("FAILED", row.getStatus());
        assertTrue(row.getErrorSummary().contains("DataIntegrityViolationException"));
    }

    // ---- テスト専用ジョブ（ステートレス前提に注意：単体テスト内でのみ使用） ----

    @TestConfiguration
    static class JobBeans {
        @Bean SuccessJob successJob() { return new SuccessJob(); }
        @Bean RetryThenFailJob retryThenFailJob() { return new RetryThenFailJob(); }
        @Bean DataIntegrityFailJob dataIntegrityFailJob() { return new DataIntegrityFailJob(); }
    }

    static class SuccessJob extends AbstractBatchJob {
        @Override protected String jobName() { return "SuccessJob"; }
        @Override protected BatchResult execute() { return BatchResult.of(10, 10, 0); }
    }

    static class RetryThenFailJob extends AbstractBatchJob {
        final AtomicInteger attempts = new AtomicInteger(0);
        @Override protected String jobName() { return "RetryThenFailJob"; }
        @Override protected BatchResult execute() {
            attempts.incrementAndGet();
            throw new ResourceAccessException("simulated I/O transient");
        }
    }

    static class DataIntegrityFailJob extends AbstractBatchJob {
        final AtomicInteger attempts = new AtomicInteger(0);
        @Override protected String jobName() { return "DataIntegrityFailJob"; }
        @Override protected BatchResult execute() {
            attempts.incrementAndGet();
            throw new DataIntegrityViolationException("constraint violation");
        }
    }
}
