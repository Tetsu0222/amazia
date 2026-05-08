package com.example.notification.repository;

import com.example.notification.entity.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    List<NotificationSubscription> findBySubscriptionTagAndEmailEnabledTrue(String subscriptionTag);

    List<NotificationSubscription> findBySubscriptionTagAndInAppEnabledTrue(String subscriptionTag);

    Optional<NotificationSubscription> findByUserIdAndSubscriptionTag(Long userId, String subscriptionTag);

    List<NotificationSubscription> findByUserId(Long userId);
}
