package com.example.notification.repository;

import com.example.notification.entity.ConsoleNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsoleNotificationRepository extends JpaRepository<ConsoleNotification, Long> {

    Optional<ConsoleNotification> findFirstByPayloadHashAndSourceJobOrderByCreatedAtDesc(
            String payloadHash, String sourceJob);

    List<ConsoleNotification> findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
            String tag);

    List<ConsoleNotification> findBySuppressedTrueAndDigestSentAtIsNullAndCreatedAtBefore(
            LocalDateTime threshold);
}
