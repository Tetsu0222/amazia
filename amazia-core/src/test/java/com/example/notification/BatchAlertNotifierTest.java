package com.example.notification;

import com.example.auth.repository.UserRepository;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.notification.service.BatchAlertNotifier;
import com.example.shared.mail.MailTemplateLoader;
import com.example.shared.mail.SesMailSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * フェーズ17 Step 3: BatchAlertNotifier の payload_hash 生成規則のユニット検証（J-5 / M-9）。
 */
class BatchAlertNotifierTest {

    private final BatchAlertNotifier notifier =
            new BatchAlertNotifier(
                    mock(ConsoleNotificationRepository.class),
                    mock(NotificationSubscriptionRepository.class),
                    mock(UserRepository.class),
                    mock(SesMailSender.class),
                    mock(MailTemplateLoader.class));

    @Test
    void BAN_1_payloadIdentity_があれば_tag_と_identity_の_SHA256_になる() {
        String hash = notifier.buildPayloadHash("inventory_alerts", "product_id=42", "Job");
        assertEquals(64, hash.length());
        assertEquals(hashOf("inventory_alerts:product_id=42"), hash);
    }

    @Test
    void BAN_2_payloadIdentity_が空なら_J_5_の_no_payload_job_name_フォールバックになる() {
        String hash = notifier.buildPayloadHash("inventory_alerts", null, "InventoryConsistencyCheckJob");
        assertEquals(hashOf("no-payload:InventoryConsistencyCheckJob"), hash);

        String hash2 = notifier.buildPayloadHash("inventory_alerts", "  ", "Job2");
        assertEquals(hashOf("no-payload:Job2"), hash2);
    }

    private String hashOf(String seed) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
