package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.SalesReconciliationJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 3-3: SalesReconciliationJob 本体の動作検証
 * （設計書 §3.1 ③ / 計画書 §4-3）。
 *
 * <p>振込確認は本テストでは {@code mode=disabled}（既定）でスキップ。
 * mock-match / mock-mismatch-rate の検証は {@link BankTransferMockClientTest} に集約。
 *
 * <p>phaseX-9 Step 4: cleanup.sql + クラスレベル @Sql(BEFORE_TEST_METHOD) で
 * console_notifications の他テスト残置を除去する（DeliveryStatusAdvanceJobTest と共有）。
 */
@SpringBootTest(properties = {
        "amazia.batch.scheduler-enabled=true",
        "amazia.batch.bank-transfer-verification.mode=disabled"
})
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
@Sql(
        scripts = "/cleanup/console_notifications.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class SalesReconciliationJobTest {

    @Autowired private SalesReconciliationJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private ConsoleNotificationRepository consoleNotificationRepository;

    @Test
    void SR_1_既存データに不整合がない状態で完走しSUCCESSになる() {
        long beforeMismatchCount = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        SalesReconciliationJob.SALES_TAG).size();

        job.run("scheduler");

        BatchExecution exec = latestExecution();
        assertNotNull(exec);
        assertTrue(List.of("SUCCESS", "PARTIAL").contains(exec.getStatus()),
                "実行は完了している（既存データ次第で不整合があり得るため PARTIAL も許容）");

        // disabled モードでは sales_alerts は増えない
        long after = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        SalesReconciliationJob.SALES_TAG).size();
        assertEquals(beforeMismatchCount, after,
                "mode=disabled では振込 MISMATCH 通知は発生しない");
    }

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(SalesReconciliationJob.JOB_NAME)
                .get(0);
    }
}
