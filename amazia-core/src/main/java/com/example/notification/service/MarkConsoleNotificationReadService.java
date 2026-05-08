package com.example.notification.service;

import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * フェーズ17 Step 6-0: Console 通知の既読化 Service（設計書 §13.7.1）。
 *
 * <p>権限：自身宛（target_user_id = userId）または購読中タグ宛のみ既読化可。
 * 他ユーザ宛は 403 を返す。二重既読化は冪等（既読時刻は維持）。
 */
@Service
public class MarkConsoleNotificationReadService {

    private final ConsoleNotificationRepository notificationRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;

    public MarkConsoleNotificationReadService(ConsoleNotificationRepository notificationRepository,
                                              NotificationSubscriptionRepository subscriptionRepository) {
        this.notificationRepository = notificationRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        ConsoleNotification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "notification not found: " + notificationId));

        if (!isVisibleToUser(n, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not allowed");
        }

        if (n.getReadByUserId() != null) {
            return;
        }
        n.setReadByUserId(userId);
        n.setReadAt(LocalDateTime.now());
        notificationRepository.saveAndFlush(n);
    }

    private boolean isVisibleToUser(ConsoleNotification n, Long userId) {
        if (userId.equals(n.getTargetUserId())) {
            return true;
        }
        Set<String> tags = subscriptionRepository.findByUserId(userId).stream()
                .filter(NotificationSubscription::getInAppEnabled)
                .map(NotificationSubscription::getSubscriptionTag)
                .collect(Collectors.toSet());
        return tags.contains(n.getTargetSubscriptionTag());
    }
}
