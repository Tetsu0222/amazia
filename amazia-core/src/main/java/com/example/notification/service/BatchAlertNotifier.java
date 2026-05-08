package com.example.notification.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.shared.mail.MailTemplate;
import com.example.shared.mail.MailTemplateLoader;
import com.example.shared.mail.SesMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * フェーズ17 Step 3 + Step 7: バッチからのアラート発火を一手に担う Service（設計書 §6 / §3.5）。
 *
 * <p>役割：
 * <ol>
 *   <li>{@code console_notifications} への INSERT（独立トランザクション {@code REQUIRES_NEW}：
 *       バッチ本体がロールバックしても通知は残す。設計書 §3.5 / §6.4 と整合）</li>
 *   <li>WARN / ERROR の場合は購読者全員（{@code notification_subscriptions.email_enabled=true}）に
 *       個別 to で SES 送信。INFO は送信しない（§6.2.2）</li>
 *   <li>SES 送信失敗は {@code MailSendException} として上位へ伝播し、
 *       {@code BatchRetryClassifier} のリトライ経路に乗せる（§3 共通制御 R-6）</li>
 * </ol>
 *
 * <p>抑制（{@code suppressed = true} レコードを残す重複抑制：§6.4.1）と
 * ダイジェスト発火（{@code DigestNotificationDispatchJob}：§6.4.2）は本フェーズ Step 7 では未実装。
 * 当 Service の SES 送出は「INFO スキップ」のみで重複抑制は行わないため、同一 payload が
 * 連続発火した場合は同数のメールが飛ぶ。Step 8 / J-3 / R-10 の本実装で
 * {@code (job_name, subscription_tag, payload_hash)} の抑制を被せる前提（K-1 / K-5）。
 */
@Service
public class BatchAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(BatchAlertNotifier.class);

    /** payload_hash の最大長（schema.sql / Entity 側の {@code VARCHAR(64)}）。SHA-256 hex そのもの。 */
    private static final int PAYLOAD_HASH_MAX_LENGTH = 64;

    private final ConsoleNotificationRepository repository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SesMailSender sesMailSender;
    private final MailTemplateLoader templateLoader;

    public BatchAlertNotifier(ConsoleNotificationRepository repository,
                              NotificationSubscriptionRepository subscriptionRepository,
                              UserRepository userRepository,
                              SesMailSender sesMailSender,
                              MailTemplateLoader templateLoader) {
        this.repository = repository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.sesMailSender = sesMailSender;
        this.templateLoader = templateLoader;
    }

    /**
     * 指定の購読タグ向けに通知を 1 件登録し、WARN/ERROR なら購読者全員にも SES を送出する。
     *
     * @param level                  {@code INFO} / {@code WARN} / {@code ERROR}
     * @param subscriptionTag        購読タグ（{@code inventory_alerts} / {@code sales_alerts} 等）
     * @param title                  件名（200 文字以内）。SES の subject にもそのまま流す
     * @param body                   本文（TEXT）。SES の body にもそのまま流す
     * @param payloadIdentity        重複抑制キーの基（例：{@code product_id=123}）。空なら job_name フォールバック（J-5）
     * @param sourceJob              ソースジョブ名
     * @param sourceBatchExecutionId 紐づく {@code batch_executions.id}（任意）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConsoleNotification dispatch(String level, String subscriptionTag,
                                        String title, String body,
                                        String payloadIdentity,
                                        String sourceJob,
                                        Long sourceBatchExecutionId) {
        ConsoleNotification saved = persistNotification(
                level, subscriptionTag, title, body, payloadIdentity, sourceJob, sourceBatchExecutionId);
        sendEmailToSubscribers(level, subscriptionTag, title, body);
        return saved;
    }

    /**
     * テンプレートIDで指定して送る（推奨経路）。subject / body をテンプレから生成する。
     * console_notifications に INSERT する title / body もテンプレ展開後の値を使う。
     *
     * @param templateId      {@link MailTemplateLoader} の登録ID
     * @param payloadIdentity 重複抑制キーの基（J-5）
     * @param sourceJob       ソースジョブ名
     * @param sourceBatchExecutionId 紐づく {@code batch_executions.id}
     * @param values          テンプレ差込値
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConsoleNotification dispatchTemplate(String templateId,
                                                String payloadIdentity,
                                                String sourceJob,
                                                Long sourceBatchExecutionId,
                                                Map<String, String> values) {
        MailTemplate template = templateLoader.get(templateId);
        String subject = templateLoader.render(template.subject(), values);
        String body = templateLoader.render(template.body(), values);
        String tag = template.subscriptionTag();
        // batch_digest テンプレのように subscription-tag 自体がプレースホルダの場合は差込値で展開する
        if (tag != null && tag.contains("{{")) {
            tag = templateLoader.render(tag, values);
        }
        return dispatch(template.level(), tag, subject, body,
                payloadIdentity, sourceJob, sourceBatchExecutionId);
    }

    private ConsoleNotification persistNotification(String level, String subscriptionTag,
                                                    String title, String body,
                                                    String payloadIdentity,
                                                    String sourceJob,
                                                    Long sourceBatchExecutionId) {
        ConsoleNotification n = new ConsoleNotification();
        n.setLevel(level);
        n.setTargetSubscriptionTag(subscriptionTag);
        n.setTitle(title);
        n.setBody(body);
        n.setPayloadHash(buildPayloadHash(subscriptionTag, payloadIdentity, sourceJob));
        n.setSourceJob(sourceJob);
        n.setSourceBatchExecutionId(sourceBatchExecutionId);
        n.setCreatedAt(LocalDateTime.now());
        return repository.save(n);
    }

    /**
     * §6.2.2 の解決アルゴリズム：購読タグから {@code email_enabled=true} の購読者一覧を取り、
     * 各人の email を解決して SES に個別 to で送る。BCC 集約はしない（M-6）。
     * INFO レベルは送らない（早期 return）。
     */
    private void sendEmailToSubscribers(String level, String subscriptionTag,
                                        String subject, String body) {
        if (isInfoLevel(level)) {
            return;
        }
        if (subscriptionTag == null || subscriptionTag.isBlank()) {
            log.warn("BatchAlertNotifier: subscriptionTag is empty, skip SES");
            return;
        }
        List<NotificationSubscription> subs =
                subscriptionRepository.findBySubscriptionTagAndEmailEnabledTrue(subscriptionTag);
        if (subs.isEmpty()) {
            log.debug("BatchAlertNotifier: no email subscribers for tag={}", subscriptionTag);
            return;
        }
        // テンプレ展開後の subject / body を「そのまま投入」するための恒等テンプレ。
        // SesMailSender 経由でレベル判定（INFO スキップ）と例外整形を統一するため、
        // 一時的に in-line テンプレートを書くのではなく、subject/body を恒等プレースホルダで通す。
        Map<String, String> values = Map.of();
        for (NotificationSubscription sub : subs) {
            Optional<User> user = userRepository.findById(sub.getUserId());
            if (user.isEmpty()) continue;
            String email = user.get().getEmail();
            if (email == null || email.isBlank()) continue;
            sendInline(email, level, subject, body, values);
        }
    }

    /**
     * subject / body が既にレンダリング済みのケースで使う直接送信。
     * 既存 dispatch 経路（テンプレIDを持たない）と整合させる。
     */
    private void sendInline(String to, String level, String subject, String body,
                            Map<String, String> values) {
        // SesMailSender の send(templateId, to, values) は YAML のテンプレIDを必要とするため、
        // 既存 dispatch 経路では擬似的に subject/body を直接 SES へ流す内部メソッドを使う。
        sesMailSender.sendRaw(to, level, subject, body);
    }

    private boolean isInfoLevel(String level) {
        return level != null && "INFO".equals(level.toUpperCase(Locale.ROOT));
    }

    /**
     * J-5 / M-9: payload_hash は NOT NULL。{@code payloadIdentity} が空なら
     * {@code SHA-256("no-payload:" + job_name)} で連続失敗を抑制対象にする。
     */
    public String buildPayloadHash(String subscriptionTag, String payloadIdentity, String sourceJob) {
        String seed;
        if (payloadIdentity != null && !payloadIdentity.isBlank()) {
            seed = subscriptionTag + ":" + payloadIdentity;
        } else {
            seed = "no-payload:" + (sourceJob != null ? sourceJob : "unknown");
        }
        return sha256Hex(seed);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(PAYLOAD_HASH_MAX_LENGTH);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }
}
