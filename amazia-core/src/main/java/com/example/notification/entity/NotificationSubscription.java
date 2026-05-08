package com.example.notification.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ns_user_tag",
                columnNames = {"user_id", "subscription_tag"}))
public class NotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "subscription_tag", nullable = false, length = 50)
    private String subscriptionTag;

    @NotNull
    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = Boolean.TRUE;

    @NotNull
    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled = Boolean.TRUE;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (emailEnabled == null) emailEnabled = Boolean.TRUE;
        if (inAppEnabled == null) inAppEnabled = Boolean.TRUE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSubscriptionTag() { return subscriptionTag; }
    public void setSubscriptionTag(String subscriptionTag) { this.subscriptionTag = subscriptionTag; }
    public Boolean getEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(Boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public Boolean getInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(Boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
