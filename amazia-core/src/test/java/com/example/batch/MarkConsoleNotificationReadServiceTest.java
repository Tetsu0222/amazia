package com.example.batch;

import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.notification.service.MarkConsoleNotificationReadService;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 6-0: 通知既読化 Service の TDD（設計書 §13.7.1）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class MarkConsoleNotificationReadServiceTest {

    @Autowired private MarkConsoleNotificationReadService service;
    @Autowired private ConsoleNotificationRepository notificationRepository;
    @Autowired private NotificationSubscriptionRepository subscriptionRepository;

    private static final Long USER_ID = 8001L;
    private static final Long OTHER_USER_ID = 8002L;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    @Test
    void 自分宛通知を既読化できる() {
        ConsoleNotification n = save(notif("private_alerts", USER_ID));
        service.markRead(n.getId(), USER_ID);

        ConsoleNotification reloaded = notificationRepository.findById(n.getId()).orElseThrow();
        assertEquals(USER_ID, reloaded.getReadByUserId());
        assertNotNull(reloaded.getReadAt());
    }

    @Test
    void 購読中タグ宛通知を既読化できる() {
        subscribe(USER_ID, "inventory_alerts");
        ConsoleNotification n = save(notif("inventory_alerts", null));
        service.markRead(n.getId(), USER_ID);

        ConsoleNotification reloaded = notificationRepository.findById(n.getId()).orElseThrow();
        assertEquals(USER_ID, reloaded.getReadByUserId());
    }

    @Test
    void 他ユーザ宛通知の既読化は403() {
        ConsoleNotification n = save(notif("private_alerts", OTHER_USER_ID));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markRead(n.getId(), USER_ID));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void 未存在IDは404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markRead(99999L, USER_ID));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void 二重既読化は冪等_最初の既読者と時刻を保持() {
        subscribe(USER_ID, "inventory_alerts");
        ConsoleNotification n = save(notif("inventory_alerts", null));
        service.markRead(n.getId(), USER_ID);
        ConsoleNotification firstRead = notificationRepository.findById(n.getId()).orElseThrow();
        var firstReadAt = firstRead.getReadAt();

        service.markRead(n.getId(), USER_ID); // 二度目
        ConsoleNotification secondRead = notificationRepository.findById(n.getId()).orElseThrow();
        assertEquals(USER_ID, secondRead.getReadByUserId());
        assertEquals(firstReadAt, secondRead.getReadAt());
    }

    private void subscribe(Long userId, String tag) {
        NotificationSubscription s = new NotificationSubscription();
        s.setUserId(userId);
        s.setSubscriptionTag(tag);
        s.setEmailEnabled(true);
        s.setInAppEnabled(true);
        subscriptionRepository.saveAndFlush(s);
    }

    private ConsoleNotification notif(String tag, Long targetUserId) {
        ConsoleNotification n = new ConsoleNotification();
        n.setTargetSubscriptionTag(tag);
        n.setTargetUserId(targetUserId);
        n.setLevel("INFO");
        n.setTitle("t");
        n.setBody("b");
        n.setPayloadHash("h-" + System.nanoTime());
        n.setSuppressed(false);
        return n;
    }

    private ConsoleNotification save(ConsoleNotification n) {
        return notificationRepository.saveAndFlush(n);
    }
}
