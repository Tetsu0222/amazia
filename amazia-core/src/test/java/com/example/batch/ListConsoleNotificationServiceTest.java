package com.example.batch;

import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.notification.service.ListConsoleNotificationService;
import com.example.notification.service.ListConsoleNotificationService.PageResult;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 6-0: 通知センター取得 Service の TDD。
 * 設計書 §13.7.1 / §13.7.2。
 *
 * <p>可視化ルール：
 * - target_user_id = X-User-Id  または  target_subscription_tag IN (購読中タグ)
 * - read_by_user_id IS NULL（未読のみ。既定）
 * - suppressed = false（ダイジェスト経路で吸収済を除外）
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ListConsoleNotificationServiceTest {

    @Autowired private ListConsoleNotificationService service;
    @Autowired private ConsoleNotificationRepository notificationRepository;
    @Autowired private NotificationSubscriptionRepository subscriptionRepository;

    private static final Long USER_ID = 9001L;
    private static final Long OTHER_USER_ID = 9002L;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    @Test
    void 購読タグ未登録でユーザ宛も無ければ_total0を返す() {
        // 別タグの通知だけ存在
        save(notif("inventory_alerts", null, "INFO", false, null));

        PageResult result = service.list(USER_ID, null, null, false, 0, 10);

        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void 購読中タグの未読が抽出される() {
        subscribe(USER_ID, "inventory_alerts");
        save(notif("inventory_alerts", null, "WARN", false, null));
        save(notif("sales_alerts", null, "WARN", false, null)); // 未購読

        PageResult result = service.list(USER_ID, null, null, false, 0, 10);

        assertEquals(1, result.total());
        assertEquals("inventory_alerts", result.items().get(0).getTargetSubscriptionTag());
    }

    @Test
    void target_user_id一致の通知も抽出される() {
        save(notif("private_alerts", USER_ID, "INFO", false, null));
        save(notif("private_alerts", OTHER_USER_ID, "INFO", false, null));

        PageResult result = service.list(USER_ID, null, null, false, 0, 10);

        assertEquals(1, result.total());
        assertEquals(USER_ID, result.items().get(0).getTargetUserId());
    }

    @Test
    void suppressed_trueはUI一覧から除外される() {
        subscribe(USER_ID, "inventory_alerts");
        save(notif("inventory_alerts", null, "WARN", true, null));
        save(notif("inventory_alerts", null, "WARN", false, null));

        PageResult result = service.list(USER_ID, null, null, false, 0, 10);

        assertEquals(1, result.total());
        assertFalse(result.items().get(0).getSuppressed());
    }

    @Test
    void 既定では未読のみ返す() {
        subscribe(USER_ID, "inventory_alerts");
        save(notif("inventory_alerts", null, "WARN", false, USER_ID)); // 既読
        save(notif("inventory_alerts", null, "WARN", false, null));    // 未読

        PageResult result = service.list(USER_ID, null, null, false, 0, 10);

        assertEquals(1, result.total());
        assertNull(result.items().get(0).getReadByUserId());
    }

    @Test
    void include_read_trueで既読も含む() {
        subscribe(USER_ID, "inventory_alerts");
        save(notif("inventory_alerts", null, "WARN", false, USER_ID));
        save(notif("inventory_alerts", null, "WARN", false, null));

        PageResult result = service.list(USER_ID, null, null, true, 0, 10);

        assertEquals(2, result.total());
    }

    @Test
    void levelフィルタが効く() {
        subscribe(USER_ID, "inventory_alerts");
        save(notif("inventory_alerts", null, "INFO",  false, null));
        save(notif("inventory_alerts", null, "WARN",  false, null));
        save(notif("inventory_alerts", null, "ERROR", false, null));

        PageResult result = service.list(USER_ID, "ERROR", null, false, 0, 10);

        assertEquals(1, result.total());
        assertEquals("ERROR", result.items().get(0).getLevel());
    }

    @Test
    void tagフィルタが効く() {
        subscribe(USER_ID, "inventory_alerts");
        subscribe(USER_ID, "sales_alerts");
        save(notif("inventory_alerts", null, "WARN", false, null));
        save(notif("sales_alerts",     null, "WARN", false, null));

        PageResult result = service.list(USER_ID, null, "sales_alerts", false, 0, 10);

        assertEquals(1, result.total());
        assertEquals("sales_alerts", result.items().get(0).getTargetSubscriptionTag());
    }

    private void subscribe(Long userId, String tag) {
        NotificationSubscription s = new NotificationSubscription();
        s.setUserId(userId);
        s.setSubscriptionTag(tag);
        s.setEmailEnabled(true);
        s.setInAppEnabled(true);
        subscriptionRepository.saveAndFlush(s);
    }

    private ConsoleNotification notif(String tag, Long targetUserId, String level,
                                      boolean suppressed, Long readBy) {
        ConsoleNotification n = new ConsoleNotification();
        n.setTargetSubscriptionTag(tag);
        n.setTargetUserId(targetUserId);
        n.setLevel(level);
        n.setTitle("t");
        n.setBody("b");
        n.setPayloadHash("h-" + System.nanoTime());
        n.setSuppressed(suppressed);
        n.setReadByUserId(readBy);
        return n;
    }

    private void save(ConsoleNotification n) {
        notificationRepository.saveAndFlush(n);
    }
}
