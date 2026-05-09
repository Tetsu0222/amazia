package com.example.batch.e2e;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.batch.job.DigestNotificationDispatchJob;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * phase17 Step 8 / E2E-7（設計書 §12.3 / K-4 / N-7）：
 * 「ダイジェスト OFF 期間に蔓延した抑制通知が、ON に戻したら 1 通のダイジェストにまとめて送出される」
 * を再起動跨ぎを跨いだ永続化前提として検証する。
 *
 * <p>本テストは「OFF 期間 = ダイジェストジョブが動かない時間帯」を
 * 「{@code suppressed=true} かつ {@code digest_sent_at IS NULL} かつ
 * {@code created_at < NOW() - suppressionMinutes}」のレコードを直接 INSERT して再現し、
 * その状態で {@link DigestNotificationDispatchJob#run} を呼んで 1 通の SES が飛ぶことを確認する。
 *
 * <p>「再起動なし」を厳密に同一プロセス内で再現することは {@code @SpringBootTest} の制約上難しいが、
 * 設計書 N-7 の本質「再起動跨ぎでも蓄積分が消えず ON 復帰時に集約送信される」は本検証で担保される。
 */
@SpringBootTest(properties = {
        "amazia.batch.notifications.digest-enabled=true",
        "amazia.batch.rate-limit.suppression-minutes=60",
        // 053: @Scheduled 初期 tick とテスト fixture / 手動 run の競合を避けるため
        // テスト期間中は scheduled tick が発火しないよう間隔を 24h に延ばす（initialDelay 防御の二重化）。
        "amazia.batch.notifications.digest-interval-ms=86400000"
})
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Sql(
        scripts = "/cleanup/e2e_full.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class DigestRestartCatchUpE2ETest {

    @Autowired private DigestNotificationDispatchJob digestJob;
    @Autowired private ConsoleNotificationRepository consoleRepository;
    @Autowired private NotificationSubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private SesClient sesClient;

    @Test
    void E2E_7_OFF期間に蔓延した抑制通知3件が_ON復帰時に1通のダイジェストにまとめて送出される() {
        // OFF 期間相当：1 時間半前に suppressed=true で 3 件 INSERT（windowStart 計算用に時刻を散らす）
        LocalDateTime base = LocalDateTime.now().minusMinutes(90);
        persistSuppressed("inventory_alerts", "在庫不一致 #1", base.minusMinutes(15));
        persistSuppressed("inventory_alerts", "在庫不一致 #2", base);
        persistSuppressed("inventory_alerts", "在庫不一致 #3", base.plusMinutes(15));

        // ON 復帰：購読者を 1 名作成（admin / inventory_alerts 購読 / email_enabled=true）
        long subscriberId = persistAdminWithSubscription("e2e7@example.com");

        // 集約と送出（5 分間隔の自動実行に相当）
        digestJob.run("scheduler");

        // SES に 1 通だけ・件名にカウント数 3 が入る
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, atLeastOnce()).sendEmail(captor.capture());
        List<SendEmailRequest> sentToSubscriber = captor.getAllValues().stream()
                .filter(r -> r.destination().toAddresses().contains("e2e7@example.com"))
                .toList();
        assertEquals(1, sentToSubscriber.size(),
                "OFF 期間蓄積 3 件は ON 復帰時に 1 通のダイジェストへ集約される（再起動跨ぎでも欠損ゼロ）");
        String subject = sentToSubscriber.get(0).message().subject().data();
        assertTrue(subject.contains("3"), "件数 3 がメール件名に出る：" + subject);

        // suppressed 行の digest_sent_at が全件埋まる（タグ単位 1 回 UPDATE / M-6）
        long unsent = consoleRepository
                .findBySuppressedTrueAndDigestSentAtIsNullAndCreatedAtBefore(LocalDateTime.now())
                .size();
        assertEquals(0L, unsent, "ダイジェスト送出後は digest_sent_at が埋まり、未送出ゼロ");

        // 後始末
        subscriptionRepository.deleteAll(subscriptionRepository.findByUserId(subscriberId));
        userRepository.deleteById(subscriberId);
    }

    private void persistSuppressed(String tag, String title, LocalDateTime createdAt) {
        ConsoleNotification n = new ConsoleNotification();
        n.setLevel("WARN");
        n.setTargetSubscriptionTag(tag);
        n.setTitle(title);
        n.setBody("body of " + title);
        n.setSuppressed(Boolean.TRUE);
        // payload_hash は NOT NULL かつ VARCHAR(64)。ユニーク値で衝突を避けつつ 64 文字以内に収める。
        n.setPayloadHash(String.format("%016x%016x", System.nanoTime(),
                java.util.concurrent.ThreadLocalRandom.current().nextLong()));
        n.setSourceJob("InventoryConsistencyCheckJob");
        n.setCreatedAt(createdAt);
        consoleRepository.save(n);
    }

    private long persistAdminWithSubscription(String email) {
        Role admin = roleRepository.findByCode("admin").orElseThrow();
        User u = new User();
        u.setEmail(email);
        u.setName("E2E-7 admin");
        u.setPasswordHash("$2y$dummy");
        u.setRole(admin);
        u.setActiveFlag(true);
        Long userId = userRepository.save(u).getId();

        NotificationSubscription sub = new NotificationSubscription();
        sub.setUserId(userId);
        sub.setSubscriptionTag("inventory_alerts");
        sub.setEmailEnabled(Boolean.TRUE);
        sub.setInAppEnabled(Boolean.TRUE);
        subscriptionRepository.save(sub);
        return userId;
    }
}
