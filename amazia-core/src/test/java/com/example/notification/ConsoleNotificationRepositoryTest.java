package com.example.notification;

import com.example.notification.entity.ConsoleNotification;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1: console_notifications Entity / Repository の永続化検証。
 * payload_hash NOT NULL（M-9）と suppressed の既定 FALSE を確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ConsoleNotificationRepositoryTest {

    @Autowired
    private ConsoleNotificationRepository repository;

    @BeforeEach
    void cleanupPriorNotifications() {
        // BatchAlertNotifier は REQUIRES_NEW で console_notifications に書き込むため、
        // 他テスト（InventoryConsistencyCheckJobTest 等）の job.run が
        // テストロールバックを貫通して同 tag のレコードを残す（051 派生③の続き）。
        // 件数アサーションを行う本テストの直前に inventory_alerts タグの残置を掃除する。
        repository.deleteAll(repository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inventory_alerts"));
    }

    @Test
    void save_すると_id_created_at_suppressed_既定値が設定される() {
        ConsoleNotification cn = newCn("inventory_alerts", "INFO", "test title");

        ConsoleNotification saved = repository.saveAndFlush(cn);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(Boolean.FALSE, saved.getSuppressed());
    }

    @Test
    void findByTargetSubscriptionTagAndReadByUserIdIsNull_で未読のみ抽出できる() {
        ConsoleNotification unread = newCn("inventory_alerts", "INFO", "未読");
        ConsoleNotification read   = newCn("inventory_alerts", "INFO", "既読");
        read.setReadByUserId(1L);
        repository.saveAndFlush(unread);
        repository.saveAndFlush(read);

        List<ConsoleNotification> result = repository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inventory_alerts");

        assertEquals(1, result.size());
        assertEquals("未読", result.get(0).getTitle());
    }

    private ConsoleNotification newCn(String tag, String level, String title) {
        ConsoleNotification cn = new ConsoleNotification();
        cn.setLevel(level);
        cn.setTargetSubscriptionTag(tag);
        cn.setTitle(title);
        cn.setBody("body");
        cn.setPayloadHash("h-" + title);
        return cn;
    }
}
