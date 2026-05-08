package com.example.notification.service;

import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * フェーズ17 Step 6-0: 通知センター取得 Service（設計書 §13.7.1 / §13.7.2）。
 *
 * <p>可視化条件（重要）：
 * <ul>
 *   <li>{@code target_user_id = userId} または {@code target_subscription_tag IN (購読中タグ)}</li>
 *   <li>{@code suppressed = false}（ダイジェスト経路で吸収済の抑制レコードを UI 一覧から除外）</li>
 *   <li>{@code includeRead = false} のとき {@code read_by_user_id IS NULL}（未読のみ）</li>
 * </ul>
 */
@Service
public class ListConsoleNotificationService {

    private final ConsoleNotificationRepository notificationRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;

    public ListConsoleNotificationService(ConsoleNotificationRepository notificationRepository,
                                          NotificationSubscriptionRepository subscriptionRepository) {
        this.notificationRepository = notificationRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public PageResult list(Long userId, String level, String tagFilter,
                           boolean includeRead, int offset, int size) {
        int page = (size > 0) ? offset / size : 0;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Set<String> subscribedTags = subscriptionRepository.findByUserId(userId).stream()
                .filter(NotificationSubscription::getInAppEnabled)
                .map(NotificationSubscription::getSubscriptionTag)
                .collect(Collectors.toSet());

        Page<ConsoleNotification> result = subscribedTags.isEmpty()
                ? notificationRepository.searchVisibleNoTags(userId,
                        emptyToNull(level), emptyToNull(tagFilter), includeRead, pageable)
                : notificationRepository.searchVisible(userId, subscribedTags,
                        emptyToNull(level), emptyToNull(tagFilter), includeRead, pageable);

        return new PageResult(result.getContent(), result.getTotalElements(), offset, size);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    public record PageResult(List<ConsoleNotification> items, long total, int offset, int size) {}
}
