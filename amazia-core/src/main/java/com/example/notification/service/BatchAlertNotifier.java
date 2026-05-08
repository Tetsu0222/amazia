package com.example.notification.service;

import com.example.notification.entity.ConsoleNotification;
import com.example.notification.repository.ConsoleNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * フェーズ17 Step 3: バッチからのアラート発火を一手に担う Service（設計書 §6 / §3.5 の最小実装）。
 *
 * <p>本フェーズでは Console 通知（{@code console_notifications}）への INSERT のみ実装。
 * SES 送信・購読者解決（§6.2）・ダイジェスト集約（§6.4）は Step 6 / Step 7 で本実装する。
 * バッチ本体が「通知を投げる」呼び出し方を確定し、Step 6 / 7 で送信実体を裏に挿せる構造を作る。
 *
 * <p>独立トランザクション（{@code REQUIRES_NEW}）：バッチ本体がロールバックしても通知は残す
 * （設計書 §3.5 / §6.4 と整合）。
 */
@Service
public class BatchAlertNotifier {

    /** payload_hash の最大長（schema.sql / Entity 側の {@code VARCHAR(64)}）。SHA-256 hex そのもの。 */
    private static final int PAYLOAD_HASH_MAX_LENGTH = 64;

    private final ConsoleNotificationRepository repository;

    public BatchAlertNotifier(ConsoleNotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * 指定の購読タグ向けに通知を 1 件登録する。SES 送出は Step 7 で本実装に置換予定。
     *
     * @param level                  {@code INFO} / {@code WARN} / {@code ERROR}
     * @param subscriptionTag        購読タグ（{@code inventory_alerts} / {@code sales_alerts} 等）
     * @param title                  件名（200 文字以内）
     * @param body                   本文（TEXT）
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
