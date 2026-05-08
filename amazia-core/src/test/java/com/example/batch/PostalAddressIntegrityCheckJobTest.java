package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.PostalAddressIntegrityCheckJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.market.postal.entity.PostalAddress;
import com.example.market.postal.repository.PostalAddressRepository;
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
 * フェーズ17 Step 4-1: PostalAddressIntegrityCheckJob の TDD（設計書 §3.2 ① / 計画書 §5-1）。
 *
 * <p>テスト用 {@code postal_addresses} は空に近い状態（H2 / test-data.sql）のため、
 * 件数下限（120,000）に対しては必ず NG になる。3 観点（件数下限 / 鮮度 / サンプル）が
 * 通知件数に反映されることを確認する。
 *
 * <p>phaseX-9 Step 4: 自衛コード（@AfterEach cleanup）を cleanup.sql + クラスレベル
 * @Sql(BEFORE_TEST_METHOD) 方式へ置換（test_insights.md カテゴリ 7-2 規約準拠）。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
@Sql(
        scripts = "/cleanup/postal_addresses.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class PostalAddressIntegrityCheckJobTest {

    @Autowired private PostalAddressIntegrityCheckJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private PostalAddressRepository postalAddressRepository;
    @Autowired private ConsoleNotificationRepository consoleNotificationRepository;

    @Test
    void POS_1_件数下限未満ならpostal_alertsへ_WARN通知が出る() {
        long beforeWarn = countPostalWarn();

        job.run("scheduler");

        BatchExecution exec = latestExecution();
        assertEquals("PARTIAL", exec.getStatus(),
                "件数下限未満 / サンプル不在で failure>0 となり PARTIAL");
        assertNotNull(exec.getFailureCount());
        assertTrue(exec.getFailureCount() >= 1);

        long afterWarn = countPostalWarn();
        assertTrue(afterWarn > beforeWarn,
                "postal_alerts 向けの WARN 通知が増えていること");
    }

    @Test
    void POS_2_全サンプルコードを投入すれば件数下限以外は通る() {
        // テスト用 postal_addresses にサンプルコードを投入（鮮度は @PrePersist で当日に揃う）
        seed("100-0001"); seed("530-0001"); seed("060-0001"); seed("810-0001"); seed("980-0001");

        job.run("scheduler");

        BatchExecution exec = latestExecution();
        // 件数下限（12 万件）は H2 環境では到達不能なため、必ず failure >= 1
        assertEquals("PARTIAL", exec.getStatus());
        assertEquals(3, exec.getTargetCount());
        assertTrue(exec.getFailureCount() >= 1);
    }

    private long countPostalWarn() {
        List<ConsoleNotification> notifications = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        PostalAddressIntegrityCheckJob.SUBSCRIPTION_TAG);
        return notifications.stream().filter(n -> "WARN".equals(n.getLevel())).count();
    }

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(PostalAddressIntegrityCheckJob.JOB_NAME)
                .get(0);
    }

    private void seed(String code) {
        PostalAddress p = new PostalAddress();
        p.setPostalCode(code);
        p.setPrefecture("テスト都");
        p.setCity("テスト区");
        p.setTown("テスト町");
        postalAddressRepository.saveAndFlush(p);
    }
}
