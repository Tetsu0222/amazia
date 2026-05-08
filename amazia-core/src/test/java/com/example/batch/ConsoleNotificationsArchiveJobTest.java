package com.example.batch;

import com.example.batch.config.BatchResult;
import com.example.batch.job.ConsoleNotificationsArchiveJob;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.ConsoleNotificationArchive;
import com.example.notification.repository.ConsoleNotificationArchiveRepository;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.shared.config.TestAwsConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 4-5: ConsoleNotificationsArchiveJob の TDD（設計書 §3.3 ③ / 計画書 §5-5）。
 *
 * <p>{@code archiveAt(now)} を直接呼び、3 つのアーカイブ条件
 * （read_at 1 年経過 / suppressed+digest_sent_at 1 年経過 / 無条件 1 年経過）が
 * 期待通り適用されることを検証する。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class ConsoleNotificationsArchiveJobTest {

    @Autowired private ConsoleNotificationsArchiveJob job;
    @Autowired private ConsoleNotificationRepository notificationRepository;
    @Autowired private ConsoleNotificationArchiveRepository archiveRepository;
    @Autowired private TransactionTemplate tx;

    @PersistenceContext private EntityManager em;

    @Test
    void CNA_1_既読から1年経過した通知はアーカイブされる() {
        LocalDateTime now = LocalDateTime.now();
        Long id = persistNotification("INFO", false, null, now.minusYears(2));
        // 既読を 14 ヶ月前に設定
        Long persisted = id;
        tx.executeWithoutResult(status -> {
            em.createNativeQuery("UPDATE console_notifications SET read_at = :t, read_by_user_id = 1 WHERE id = :id")
                    .setParameter("t", now.minusMonths(14))
                    .setParameter("id", persisted)
                    .executeUpdate();
            em.clear();
        });

        BatchResult result = job.archiveAt(now);

        assertTrue(result.targetCount() >= 1);
        assertTrue(notificationRepository.findById(id).isEmpty());
        Optional<ConsoleNotificationArchive> archived = archiveRepository.findById(id);
        assertTrue(archived.isPresent());
        assertNotNull(archived.get().getArchivedAt());
    }

    @Test
    void CNA_2_作成から1年経過なら無条件でアーカイブされる() {
        LocalDateTime now = LocalDateTime.now();
        Long id = persistNotification("WARN", false, null, now.minusYears(2));

        BatchResult result = job.archiveAt(now);

        assertTrue(result.targetCount() >= 1);
        assertTrue(notificationRepository.findById(id).isEmpty(), "作成 1 年経過は強制アーカイブ");
    }

    @Test
    void CNA_3_抑制中で_digest_sent_at_NULLでも作成1年経過すれば救済される() {
        LocalDateTime now = LocalDateTime.now();
        Long id = persistNotification("INFO", true, null, now.minusYears(2));

        BatchResult result = job.archiveAt(now);

        assertTrue(notificationRepository.findById(id).isEmpty(),
                "suppressed=true / digest_sent_at NULL でも created_at 1 年経過なら救済（J-2）");
        assertNotNull(result);
    }

    @Test
    void CNA_4_作成1年未満の通知はアーカイブされない() {
        LocalDateTime now = LocalDateTime.now();
        Long id = persistNotification("INFO", false, null, now.minusMonths(6));

        job.archiveAt(now);

        assertTrue(notificationRepository.findById(id).isPresent(),
                "新しい通知は維持される");
    }

    Long persistNotification(String level, boolean suppressed, LocalDateTime digestSentAt,
                             LocalDateTime createdAt) {
        return tx.execute(status -> {
            ConsoleNotification n = new ConsoleNotification();
            n.setLevel(level);
            n.setTargetSubscriptionTag("sales_alerts");
            n.setTitle("title");
            n.setBody("body");
            n.setPayloadHash("h" + System.nanoTime());
            n.setSuppressed(suppressed);
            n.setDigestSentAt(digestSentAt);
            n.setSourceJob("CNA_TEST");
            ConsoleNotification saved = notificationRepository.saveAndFlush(n);
            em.createNativeQuery("UPDATE console_notifications SET created_at = :t WHERE id = :id")
                    .setParameter("t", createdAt)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
            em.clear();
            return saved.getId();
        });
    }
}
