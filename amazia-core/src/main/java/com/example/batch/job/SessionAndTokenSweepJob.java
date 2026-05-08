package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.market.customer.repository.CustomerPasswordResetTokenRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * フェーズ17 Step 3-5: セッション・トークンの掃除（設計書 §3.1 ⑤）。
 *
 * <p>{@code market_sessions} の期限切れと、{@code market_customers_password_reset_tokens}
 * の期限切れ・使用済みを物理削除する。t3.micro 環境の DB 容量を圧迫させないための保守ジョブ。
 *
 * <p>通知なし（CloudWatch Logs に件数のみ記録）。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class SessionAndTokenSweepJob extends AbstractBatchJob {

    private static final Logger log = LoggerFactory.getLogger(SessionAndTokenSweepJob.class);
    public static final String JOB_NAME = "SessionAndTokenSweepJob";

    private final MarketSessionRepository sessionRepository;
    private final CustomerPasswordResetTokenRepository tokenRepository;

    public SessionAndTokenSweepJob(MarketSessionRepository sessionRepository,
                                   CustomerPasswordResetTokenRepository tokenRepository) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
    }

    @Scheduled(cron = "${amazia.batch.daily.cron}", zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        LocalDateTime now = LocalDateTime.now();
        int sessions = sessionRepository.deleteAllExpired(now);
        int tokens = tokenRepository.deleteExpiredOrUsed(now);
        log.info("[{}] swept sessions={}, tokens={}", JOB_NAME, sessions, tokens);
        int total = sessions + tokens;
        return BatchResult.of(total, total, 0);
    }
}
