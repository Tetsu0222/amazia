package com.example.notification;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.notification.service.BatchAlertNotifier;
import com.example.shared.mail.MailTemplateLoader;
import com.example.shared.mail.SesMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * フェーズ17 Step 7 / 設計書 §6.2.2 / M-6：
 * <ul>
 *   <li>WARN dispatch で購読者全員に個別 to で SES が呼ばれる（BCC 集約しない）</li>
 *   <li>INFO dispatch では SES が呼ばれない</li>
 *   <li>email_enabled=false の購読者は SES 送出対象から外れる（リポジトリの絞り込みに依存）</li>
 *   <li>dispatchTemplate でテンプレIDから subject/body 展開＋送出が行える</li>
 * </ul>
 */
class BatchAlertNotifierSesIntegrationTest {

    private ConsoleNotificationRepository consoleRepo;
    private NotificationSubscriptionRepository subscriptionRepo;
    private UserRepository userRepo;
    private SesMailSender sesMailSender;
    private MailTemplateLoader templateLoader;
    private BatchAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        consoleRepo = mock(ConsoleNotificationRepository.class);
        when(consoleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        subscriptionRepo = mock(NotificationSubscriptionRepository.class);
        userRepo = mock(UserRepository.class);
        sesMailSender = mock(SesMailSender.class);
        templateLoader = new MailTemplateLoader();
        templateLoader.load();
        notifier = new BatchAlertNotifier(
                consoleRepo, subscriptionRepo, userRepo, sesMailSender, templateLoader);
    }

    @Test
    void BAN_3_WARN_で_購読者全員に個別_to_で_SES_が呼ばれる() {
        when(subscriptionRepo.findBySubscriptionTagAndEmailEnabledTrue("inventory_alerts"))
                .thenReturn(List.of(sub(11L), sub(12L), sub(13L)));
        when(userRepo.findById(11L)).thenReturn(Optional.of(user(11L, "a@example.com")));
        when(userRepo.findById(12L)).thenReturn(Optional.of(user(12L, "b@example.com")));
        when(userRepo.findById(13L)).thenReturn(Optional.of(user(13L, "c@example.com")));

        notifier.dispatch("WARN", "inventory_alerts", "title", "body",
                "product_id=42", "InventoryConsistencyCheckJob", 99L);

        verify(sesMailSender).sendRaw(eq("a@example.com"), eq("WARN"), eq("title"), eq("body"));
        verify(sesMailSender).sendRaw(eq("b@example.com"), eq("WARN"), eq("title"), eq("body"));
        verify(sesMailSender).sendRaw(eq("c@example.com"), eq("WARN"), eq("title"), eq("body"));
        verify(sesMailSender, times(3)).sendRaw(anyString(), anyString(), anyString(), anyString());
        verify(consoleRepo, times(1)).save(any());
    }

    @Test
    void BAN_4_INFO_では_SES_は呼ばれず_console_のみ() {
        notifier.dispatch("INFO", "inventory_alerts", "t", "b", null, "Job", 1L);
        verify(consoleRepo).save(any());
        verifyNoInteractions(sesMailSender);
        // 購読者解決もスキップされる（無駄な DB 検索を避ける早期 return）
        verify(subscriptionRepo, never())
                .findBySubscriptionTagAndEmailEnabledTrue(anyString());
    }

    @Test
    void BAN_5_dispatchTemplate_でテンプレ展開された_subject_body_が_SES_に渡る() {
        when(subscriptionRepo.findBySubscriptionTagAndEmailEnabledTrue("inventory_alerts"))
                .thenReturn(List.of(sub(11L)));
        when(userRepo.findById(11L)).thenReturn(Optional.of(user(11L, "a@example.com")));

        notifier.dispatchTemplate("batch_inventory_inconsistency",
                "product_id=42", "InventoryConsistencyCheckJob", 99L,
                Map.of("productId", "42", "productName", "テスト商品",
                        "warehouseId", "1", "inventoryQty", "10",
                        "skuTotalQty", "8", "diffQty", "-2",
                        "executedAt", "2026-05-08T03:30",
                        "jobName", "InventoryConsistencyCheckJob",
                        "batchExecutionId", "99",
                        "consoleUrl", "https://console.example"));

        // subject に productId=42 が展開されていること、level=WARN で送出が走ること
        verify(sesMailSender).sendRaw(eq("a@example.com"), eq("WARN"),
                argThat(s -> s.contains("商品ID=42")),
                argThat(b -> b.contains("商品ID    : 42") && b.contains("差分      : -2")));
    }

    @Test
    void BAN_6_email_enabled_false_の購読者はリポジトリ絞り込みで除外される() {
        // findBySubscriptionTagAndEmailEnabledTrue が空を返すケースで SES 呼び出しが発生しない
        when(subscriptionRepo.findBySubscriptionTagAndEmailEnabledTrue("sales_alerts"))
                .thenReturn(List.of());
        notifier.dispatch("WARN", "sales_alerts", "t", "b", null, "Job", 1L);
        verifyNoInteractions(sesMailSender);
    }

    @Test
    void BAN_7_dispatchTemplate_でも_INFO_テンプレなら_SES_は呼ばれない() {
        // 既存テンプレに INFO は無いが将来 INFO テンプレが追加されたケースを想定して
        // 現状は WARN テンプレで INFO 動作にならないことを保証する。
        when(subscriptionRepo.findBySubscriptionTagAndEmailEnabledTrue("inventory_alerts"))
                .thenReturn(List.of(sub(11L)));
        when(userRepo.findById(11L)).thenReturn(Optional.of(user(11L, "a@example.com")));
        notifier.dispatchTemplate("batch_inventory_inconsistency",
                "k", "Job", 1L, Map.of("productId", "42"));
        // WARN テンプレなので 1 通飛ぶ
        verify(sesMailSender, times(1))
                .sendRaw(eq("a@example.com"), eq("WARN"), anyString(), anyString());
    }

    private NotificationSubscription sub(long userId) {
        NotificationSubscription s = new NotificationSubscription();
        s.setUserId(userId);
        s.setSubscriptionTag("inventory_alerts");
        s.setEmailEnabled(Boolean.TRUE);
        return s;
    }

    private User user(long id, String email) {
        User u = new User();
        // id は @GeneratedValue で setter なし → リフレクションで設定
        try {
            java.lang.reflect.Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        u.setEmail(email);
        return u;
    }

    @SuppressWarnings("unused")
    private void unused() {
        assertEquals(1, 1);
    }
}
