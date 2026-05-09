package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.service.DigestDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * フェーズ17 Step 8-1: 通知ダイジェスト送出ジョブ（設計書 §6.4.2 / N-7 / M-6）。
 *
 * <p>抑制された通知（{@code suppressed = true} かつ {@code digest_sent_at IS NULL} かつ
 * {@code created_at < NOW() - suppressionMinutes}）を購読タグ単位で集計し、
 * タグ購読者全員に個別 to で SES を送る。送信完了したレコードはタグ単位 1 回の UPDATE で
 * {@code digest_sent_at = NOW()} を埋める（再起動跨ぎ二重送信防止）。
 *
 * <p>{@code BATCH_SCHEDULER_ENABLED} とは独立した {@code BATCH_DIGEST_ENABLED} で ON/OFF。
 * Bean 非登録時は抑制レコードが蓄積し、フラグを {@code true} に戻して再起動すると
 * 蓄積分が 1 通のダイジェストに集約される（K-4）。
 *
 * <p>{@link com.example.batch.config.OnDemandJob} は実装しない（K-5：手動起動 API なし）。
 */
@Component(DigestNotificationDispatchJob.JOB_NAME)
@ConditionalOnProperty(name = "amazia.batch.notifications.digest-enabled",
                       havingValue = "true", matchIfMissing = true)
public class DigestNotificationDispatchJob extends AbstractBatchJob {

    private static final Logger log = LoggerFactory.getLogger(DigestNotificationDispatchJob.class);
    public static final String JOB_NAME = "DigestNotificationDispatchJob";
    public static final String TEMPLATE_ID = "batch_digest";

    private final ConsoleNotificationRepository repository;
    private final DigestDispatchService dispatchService;

    @Value("${amazia.batch.rate-limit.suppression-minutes}")
    private long suppressionMinutes;

    @Value("${amazia.batch.notifications.subscription-tags}")
    private String subscriptionTagsCsv;

    public DigestNotificationDispatchJob(ConsoleNotificationRepository repository,
                                         DigestDispatchService dispatchService) {
        this.repository = repository;
        this.dispatchService = dispatchService;
    }

    // 053: initialDelay 未指定だと context 起動完了直後に initial tick が走り、
    // テスト fixture / 起動直後の手動 run と BatchJobLockRegistry を競合する。
    // initial tick を fixedRate 1 周期ぶん後ろにずらすことで競合を回避する
    // （本番運用でも「起動直後の認証・DB 起動が落ち着く前に dispatch を走らせる」リスクが減る）。
    @Scheduled(
            fixedRateString = "${amazia.batch.notifications.digest-interval-ms:300000}",
            initialDelayString = "${amazia.batch.notifications.digest-interval-ms:300000}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(suppressionMinutes);
        List<ConsoleNotification> suppressed =
                repository.findBySuppressedTrueAndDigestSentAtIsNullAndCreatedAtBefore(threshold);
        if (suppressed.isEmpty()) {
            return BatchResult.of(0, 0, 0);
        }

        // 設計書 6.4.2 の単一情報源は config('notifications.subscription_tags')。
        // タグごとに集計し、購読タグの順序を維持しつつ未知タグも検出可能にするため、
        // 設定タグを軸にしたマップで処理する（未知タグは末尾でログのみ出す）。
        List<String> configuredTags = Arrays.stream(subscriptionTagsCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        Map<String, List<ConsoleNotification>> grouped = new HashMap<>();
        for (ConsoleNotification n : suppressed) {
            grouped.computeIfAbsent(n.getTargetSubscriptionTag(), k -> new ArrayList<>()).add(n);
        }

        int success = 0;
        int failure = 0;
        for (String tag : configuredTags) {
            List<ConsoleNotification> records = grouped.remove(tag);
            if (records == null || records.isEmpty()) continue;
            try {
                dispatchService.dispatchOneTag(tag, records);
                success++;
            } catch (RuntimeException ex) {
                failure++;
                log.error("[{}] failed to dispatch digest for tag={}", JOB_NAME, tag, ex);
            }
        }
        for (Map.Entry<String, List<ConsoleNotification>> e : grouped.entrySet()) {
            log.warn("[{}] suppressed records found for unknown tag={} count={} (skip)",
                    JOB_NAME, e.getKey(), e.getValue().size());
        }
        return BatchResult.of(suppressed.size(), success, failure);
    }
}
