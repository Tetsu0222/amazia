package com.example.notification.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "console_notifications")
public class ConsoleNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, length = 10)
    private String level;

    @NotNull
    @Column(name = "target_subscription_tag", nullable = false, length = 50)
    private String targetSubscriptionTag;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @NotNull
    @Column(nullable = false, length = 200)
    private String title;

    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @NotNull
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @NotNull
    @Column(nullable = false)
    private Boolean suppressed = Boolean.FALSE;

    @Column(name = "digest_sent_at")
    private LocalDateTime digestSentAt;

    @Column(name = "read_by_user_id")
    private Long readByUserId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "source_job", length = 100)
    private String sourceJob;

    @Column(name = "source_batch_execution_id")
    private Long sourceBatchExecutionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (suppressed == null) suppressed = Boolean.FALSE;
    }

    public Long getId() { return id; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getTargetSubscriptionTag() { return targetSubscriptionTag; }
    public void setTargetSubscriptionTag(String targetSubscriptionTag) { this.targetSubscriptionTag = targetSubscriptionTag; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public Boolean getSuppressed() { return suppressed; }
    public void setSuppressed(Boolean suppressed) { this.suppressed = suppressed; }
    public LocalDateTime getDigestSentAt() { return digestSentAt; }
    public void setDigestSentAt(LocalDateTime digestSentAt) { this.digestSentAt = digestSentAt; }
    public Long getReadByUserId() { return readByUserId; }
    public void setReadByUserId(Long readByUserId) { this.readByUserId = readByUserId; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public String getSourceJob() { return sourceJob; }
    public void setSourceJob(String sourceJob) { this.sourceJob = sourceJob; }
    public Long getSourceBatchExecutionId() { return sourceBatchExecutionId; }
    public void setSourceBatchExecutionId(Long sourceBatchExecutionId) { this.sourceBatchExecutionId = sourceBatchExecutionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
