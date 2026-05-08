package com.example.notification.service;

import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.NotificationSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * フェーズ17 Step 6-4 / 設計書 §13.0 / N-9：
 * users のロール変更（作成・昇格・降格）に応じて {@code notification_subscriptions} を同期する。
 *
 * <ul>
 *   <li>自動購読対象ロール（{@code amazia.batch.notifications.auto-subscribe-roles}）に該当する場合：
 *       設定済みタグ一覧（{@code amazia.batch.notifications.subscription-tags}）すべてに対し
 *       {@code email_enabled = true / in_app_enabled = true} で UPSERT</li>
 *   <li>非対象ロールの場合（admin → user 降格を含む）：
 *       既存の購読行を {@code email_enabled = false / in_app_enabled = false} に降格</li>
 * </ul>
 *
 * <p>タグ一覧・自動購読対象ロールの双方を {@code @Value} 経由で取得することで
 * 規約 4-1（環境変数で外出し管理）と規約 1-2（config 駆動）に整合させる。
 */
@Service
public class SyncNotificationSubscriptionsService {

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final Set<String> autoSubscribeRoles;
    private final List<String> subscriptionTags;

    public SyncNotificationSubscriptionsService(
            NotificationSubscriptionRepository subscriptionRepository,
            @Value("${amazia.batch.notifications.auto-subscribe-roles}") String autoSubscribeRolesCsv,
            @Value("${amazia.batch.notifications.subscription-tags}") String subscriptionTagsCsv) {
        this.subscriptionRepository = subscriptionRepository;
        this.autoSubscribeRoles = parseCsv(autoSubscribeRolesCsv);
        this.subscriptionTags = List.copyOf(parseCsv(subscriptionTagsCsv));
    }

    /**
     * 指定ユーザの購読状態を、現在のロールに合わせて同期する。
     *
     * @param userId   対象ユーザ ID
     * @param roleCode 現在のロールコード（例：{@code admin}, {@code user}）
     */
    @Transactional
    public void applyForUserRole(Long userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return;
        }
        if (autoSubscribeRoles.contains(roleCode)) {
            upsertAllTagsEnabled(userId);
        } else {
            disableAll(userId);
        }
    }

    private void upsertAllTagsEnabled(Long userId) {
        for (String tag : subscriptionTags) {
            NotificationSubscription sub = subscriptionRepository
                    .findByUserIdAndSubscriptionTag(userId, tag)
                    .orElseGet(() -> {
                        NotificationSubscription created = new NotificationSubscription();
                        created.setUserId(userId);
                        created.setSubscriptionTag(tag);
                        return created;
                    });
            sub.setEmailEnabled(Boolean.TRUE);
            sub.setInAppEnabled(Boolean.TRUE);
            subscriptionRepository.save(sub);
        }
    }

    private void disableAll(Long userId) {
        List<NotificationSubscription> existing = subscriptionRepository.findByUserId(userId);
        for (NotificationSubscription sub : existing) {
            sub.setEmailEnabled(Boolean.FALSE);
            sub.setInAppEnabled(Boolean.FALSE);
            subscriptionRepository.save(sub);
        }
    }

    private static Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }
}
