package com.example.notification.service;

import com.example.notification.entity.ConsoleNotification;
import com.example.notification.repository.ConsoleNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * フェーズ17 Step 8-1：ダイジェスト発火 1 タグぶんの「テンプレ送出 + digest_sent_at UPDATE」を
 * 担う Service（設計書 §6.4.2 / M-6）。{@code DigestNotificationDispatchJob} から
 * REQUIRES_NEW 経由で呼ばれる。Job 側で {@code @Transactional} を持たせると
 * {@code AbstractBatchJob} の field 注入と AOP proxy が衝突するため、本 Service に分離する。
 */
@Service
public class DigestDispatchService {

    private static final Logger log = LoggerFactory.getLogger(DigestDispatchService.class);
    private static final DateTimeFormatter WINDOW_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_REPRESENTATIVE_TITLES = 3;
    private static final String TEMPLATE_ID = "batch_digest";

    private final ConsoleNotificationRepository repository;
    private final BatchAlertNotifier notifier;

    public DigestDispatchService(ConsoleNotificationRepository repository,
                                 BatchAlertNotifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    /**
     * §6.4.2 の擬似コード：1 タグに対して
     * (1) ダイジェスト 1 通を BatchAlertNotifier 経由で発火（テンプレ展開 + タグ購読者全員へ個別 to）
     * (2) 集約対象レコードに対してタグ単位 1 回の UPDATE で digest_sent_at をセット（M-6）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchOneTag(String tag, List<ConsoleNotification> records) {
        LocalDateTime windowStart = records.stream()
                .map(ConsoleNotification::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        LocalDateTime windowEnd = LocalDateTime.now();

        String representative = records.stream()
                .limit(MAX_REPRESENTATIVE_TITLES)
                .map(n -> "- " + n.getTitle())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("- (なし)");

        Map<String, String> values = Map.of(
                "subscriptionTag",      tag,
                "suppressedCount",      String.valueOf(records.size()),
                "windowStart",          windowStart.format(WINDOW_FORMAT),
                "windowEnd",            windowEnd.format(WINDOW_FORMAT),
                "representativeTitles", representative,
                "consoleUrl",           "(Console URL)"
        );

        // 重複抑制キーは dispatch 経路で必ず再計算される。Digest 自身は同 payload_hash を
        // 短時間に再発火させる構造的事情がないため、payloadIdentity に集計ウィンドウ情報を入れる。
        String payloadIdentity = "tag=" + tag + ",windowStart=" + windowStart;
        notifier.dispatchTemplate(TEMPLATE_ID, payloadIdentity, "DigestNotificationDispatchJob",
                null, values);

        // 抑制行を fresh に取り直して digest_sent_at を埋める。引数で受け取った records は
        // 別トランザクションで読まれた entity の可能性があり、@Version などの楽観ロックや
        // detached state の影響を回避するため、ID で再取得して更新する。
        List<Long> ids = records.stream().map(ConsoleNotification::getId).toList();
        List<ConsoleNotification> fresh = repository.findAllById(ids);
        LocalDateTime now = LocalDateTime.now();
        for (ConsoleNotification n : fresh) {
            n.setDigestSentAt(now);
        }
        repository.saveAll(fresh);
        log.info("[DigestDispatchService] dispatched digest tag={} count={}", tag, records.size());
    }
}
