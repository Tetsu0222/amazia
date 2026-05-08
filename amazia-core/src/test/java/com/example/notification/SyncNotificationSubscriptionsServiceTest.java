package com.example.notification;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.notification.service.SyncNotificationSubscriptionsService;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 6-4 / 設計書 §13.0 / N-9：
 * users のロール変更フックが notification_subscriptions に反映されることを検証する。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SyncNotificationSubscriptionsServiceTest {

    private static final List<String> ALL_TAGS = List.of(
            "inventory_alerts", "sales_alerts", "delivery_alerts",
            "postal_alerts", "batch_failure");

    @Autowired
    private SyncNotificationSubscriptionsService service;

    @Autowired
    private NotificationSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void admin_ロールなら全タグが_email_in_app_共に有効で_UPSERT_される() {
        Long userId = createUser("sync-admin");

        service.applyForUserRole(userId, "admin");

        List<NotificationSubscription> subs = subscriptionRepository.findByUserId(userId);
        assertEquals(ALL_TAGS.size(), subs.size());
        for (NotificationSubscription s : subs) {
            assertTrue(ALL_TAGS.contains(s.getSubscriptionTag()),
                    "unexpected tag: " + s.getSubscriptionTag());
            assertEquals(Boolean.TRUE, s.getEmailEnabled());
            assertEquals(Boolean.TRUE, s.getInAppEnabled());
        }
    }

    @Test
    void senior_admin_ロールも全タグが有効化される() {
        Long userId = createUser("sync-senior");

        service.applyForUserRole(userId, "senior_admin");

        assertEquals(ALL_TAGS.size(),
                subscriptionRepository.findByUserId(userId).size());
    }

    @Test
    void eternal_advisor_ロールも全タグが有効化される() {
        Long userId = createUser("sync-eternal");

        service.applyForUserRole(userId, "eternal_advisor");

        assertEquals(ALL_TAGS.size(),
                subscriptionRepository.findByUserId(userId).size());
    }

    @Test
    void 自動購読対象でないロールはレコードを作成しない() {
        Long userId = createUser("sync-user-only");

        service.applyForUserRole(userId, "user");

        assertTrue(subscriptionRepository.findByUserId(userId).isEmpty());
    }

    @Test
    void admin_化_は_既存の無効化レコードを_email_と_in_app_を_TRUE_に戻して_UPSERT_する() {
        Long userId = createUser("sync-reactivate");
        NotificationSubscription preexisting = new NotificationSubscription();
        preexisting.setUserId(userId);
        preexisting.setSubscriptionTag("inventory_alerts");
        preexisting.setEmailEnabled(false);
        preexisting.setInAppEnabled(false);
        subscriptionRepository.save(preexisting);

        service.applyForUserRole(userId, "admin");

        NotificationSubscription updated = subscriptionRepository
                .findByUserIdAndSubscriptionTag(userId, "inventory_alerts").orElseThrow();
        assertEquals(Boolean.TRUE, updated.getEmailEnabled());
        assertEquals(Boolean.TRUE, updated.getInAppEnabled());
        assertEquals(ALL_TAGS.size(),
                subscriptionRepository.findByUserId(userId).size());
    }

    @Test
    void admin_から_user_へ降格すると_全行が_email_in_app_共に_FALSE_になる() {
        Long userId = createUser("sync-demote");
        service.applyForUserRole(userId, "admin");
        assertEquals(ALL_TAGS.size(),
                subscriptionRepository.findByUserId(userId).size());

        service.applyForUserRole(userId, "user");

        List<NotificationSubscription> subs = subscriptionRepository.findByUserId(userId);
        assertEquals(ALL_TAGS.size(), subs.size(), "降格しても行は物理削除しない");
        for (NotificationSubscription s : subs) {
            assertEquals(Boolean.FALSE, s.getEmailEnabled());
            assertEquals(Boolean.FALSE, s.getInAppEnabled());
        }
    }

    @Test
    void user_への降格は二重実行しても冪等() {
        Long userId = createUser("sync-idempotent");
        service.applyForUserRole(userId, "admin");

        service.applyForUserRole(userId, "user");
        service.applyForUserRole(userId, "user");

        List<NotificationSubscription> subs = subscriptionRepository.findByUserId(userId);
        for (NotificationSubscription s : subs) {
            assertEquals(Boolean.FALSE, s.getEmailEnabled());
        }
    }

    @Test
    void userId_または_roleCode_が_NULL_なら_何もしない() {
        Long userId = createUser("sync-null");

        service.applyForUserRole(null, "admin");
        service.applyForUserRole(userId, null);

        assertTrue(subscriptionRepository.findByUserId(userId).isEmpty());
    }

    private Long createUser(String prefix) {
        User u = new User();
        long suffix = Math.abs(System.nanoTime() % 100000);
        u.setEmployeeId(prefix + suffix);
        u.setEmail(prefix + "-" + System.nanoTime() + "@example.com");
        u.setName(prefix);
        u.setPasswordHash("dummy");
        u.setActiveFlag(true);
        return userRepository.save(u).getId();
    }
}
