package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.SessionAndTokenSweepJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.market.customer.entity.CustomerPasswordResetToken;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.repository.CustomerPasswordResetTokenRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 3-5: SessionAndTokenSweepJob の TDD（設計書 §3.1 ⑤ / 計画書 §4-5）。
 *
 * <p>phaseX-9 Step 4: 削除検証は @Transactional ロールバックと両立しない（削除そのものをアサート）。
 * cleanup.sql + クラスレベル @Sql(BEFORE_TEST_METHOD) で market_sessions /
 * customer_password_reset_tokens の他テスト残置を確実にクリアし、検証意図を保ったまま分離する。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Sql(
        scripts = "/cleanup/session_and_tokens.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class SessionAndTokenSweepJobTest {

    @Autowired private SessionAndTokenSweepJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private MarketSessionRepository sessionRepository;
    @Autowired private CustomerPasswordResetTokenRepository tokenRepository;

    @Test
    void STS_1_期限切れセッションは削除され_有効なセッションは残る() {
        String expiredId = "expired-" + UUID.randomUUID();
        String validId = "valid-" + UUID.randomUUID();
        sessionRepository.save(newSession(expiredId, LocalDateTime.now().minusHours(1)));
        sessionRepository.save(newSession(validId, LocalDateTime.now().plusHours(1)));

        job.run("scheduler");

        assertFalse(sessionRepository.findById(expiredId).isPresent(), "期限切れは削除");
        assertTrue(sessionRepository.findById(validId).isPresent(), "有効分は残る");

        BatchExecution exec = latestExecution();
        assertEquals("SUCCESS", exec.getStatus());
    }

    @Test
    void STS_2_期限切れトークンと使用済みトークンは削除され_有効未使用トークンは残る() {
        Long expiredId = tokenRepository.save(newToken("ex-" + UUID.randomUUID(),
                LocalDateTime.now().minusMinutes(10), false)).getId();
        Long usedId = tokenRepository.save(newToken("used-" + UUID.randomUUID(),
                LocalDateTime.now().plusHours(1), true)).getId();
        Long validId = tokenRepository.save(newToken("v-" + UUID.randomUUID(),
                LocalDateTime.now().plusHours(1), false)).getId();

        job.run("scheduler");

        assertFalse(tokenRepository.findById(expiredId).isPresent(), "期限切れは削除");
        assertFalse(tokenRepository.findById(usedId).isPresent(), "使用済みは削除");
        assertTrue(tokenRepository.findById(validId).isPresent(), "有効未使用は残る");
    }

    private MarketSession newSession(String id, LocalDateTime expiresAt) {
        MarketSession s = new MarketSession();
        s.setSessionId(id);
        s.setCustomerId(1L);
        s.setCsrfToken("csrf-" + id);
        s.setExpiresAt(expiresAt);
        return s;
    }

    private CustomerPasswordResetToken newToken(String hash, LocalDateTime expiresAt, boolean used) {
        CustomerPasswordResetToken t = new CustomerPasswordResetToken();
        t.setCustomerId(1L);
        t.setTokenHash(hash);
        t.setExpiresAt(expiresAt);
        t.setUsed(used);
        return t;
    }

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(SessionAndTokenSweepJob.JOB_NAME)
                .get(0);
    }
}
