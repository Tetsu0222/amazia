package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.ConsoleNotificationArchive;
import com.example.notification.repository.ConsoleNotificationArchiveRepository;
import com.example.notification.repository.ConsoleNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * フェーズ17 Step 4-5: 古い {@code console_notifications} のアーカイブ（設計書 §3.3 ③ / J-2）。
 *
 * <p>毎年 1 月 1 日 05:00 JST、以下いずれかの条件のレコードを
 * {@code console_notifications_archive} に移送：
 * <ul>
 *   <li>{@code read_at} から 1 年経過</li>
 *   <li>{@code suppressed = TRUE} かつ {@code digest_sent_at} から 1 年経過</li>
 *   <li>無条件 1 年経過（抑制中で digest_sent_at NULL もここで救済）</li>
 * </ul>
 *
 * <p>件数のみ INFO ログで出力。SES 通知はしない（設計書 §3.3 ③）。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class ConsoleNotificationsArchiveJob extends AbstractBatchJob {

    public static final String JOB_NAME = "ConsoleNotificationsArchiveJob";

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotificationsArchiveJob.class);

    private final ConsoleNotificationRepository notificationRepository;
    private final ConsoleNotificationArchiveRepository archiveRepository;
    private final Clock clock;

    public ConsoleNotificationsArchiveJob(ConsoleNotificationRepository notificationRepository,
                                          ConsoleNotificationArchiveRepository archiveRepository,
                                          Clock clock) {
        this.notificationRepository = notificationRepository;
        this.archiveRepository = archiveRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${amazia.batch.yearly.cron}", zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    @Transactional
    protected BatchResult execute() {
        LocalDateTime now = LocalDateTime.now(clock);
        return archiveAt(now);
    }

    /** テスト容易性のため基準時刻を引数で受ける形を分離。 */
    @Transactional
    public BatchResult archiveAt(LocalDateTime now) {
        LocalDateTime oneYearAgo = now.minusYears(1);
        List<ConsoleNotification> targets =
                notificationRepository.findArchiveCandidates(oneYearAgo, oneYearAgo, oneYearAgo);
        if (targets.isEmpty()) {
            log.info("[{}] no archive targets", JOB_NAME);
            return BatchResult.empty();
        }

        for (ConsoleNotification src : targets) {
            ConsoleNotificationArchive arch = new ConsoleNotificationArchive();
            arch.setId(src.getId());
            arch.setLevel(src.getLevel());
            arch.setTargetSubscriptionTag(src.getTargetSubscriptionTag());
            arch.setTargetUserId(src.getTargetUserId());
            arch.setTitle(src.getTitle());
            arch.setBody(src.getBody());
            arch.setPayloadHash(src.getPayloadHash());
            arch.setSuppressed(src.getSuppressed());
            arch.setDigestSentAt(src.getDigestSentAt());
            arch.setReadByUserId(src.getReadByUserId());
            arch.setReadAt(src.getReadAt());
            arch.setSourceJob(src.getSourceJob());
            arch.setSourceBatchExecutionId(src.getSourceBatchExecutionId());
            arch.setCreatedAt(src.getCreatedAt());
            arch.setArchivedAt(now);
            archiveRepository.save(arch);
        }
        notificationRepository.deleteAllInBatch(targets);

        log.info("[{}] archived {} console_notifications (threshold={})",
                JOB_NAME, targets.size(), oneYearAgo);
        return BatchResult.of(targets.size(), targets.size(), 0);
    }
}
