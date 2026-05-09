package com.example.inquiry.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 問い合わせスレッドメッセージ（フェーズ18 r3 / 設計書 §3.2）。
 *
 * <p>{@code sender_type} は market_customer / admin_user の多態参照。
 * {@code is_internal_note=TRUE} は admin のみ許容（DB CHECK + Service 二重防御）。
 */
@Entity
@Table(name = "inquiry_messages")
public class InquiryMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_internal_note", nullable = false)
    private Boolean isInternalNote = Boolean.FALSE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isInternalNote == null) isInternalNote = Boolean.FALSE;
    }

    public Long getId() { return id; }
    public Long getInquiryId() { return inquiryId; }
    public void setInquiryId(Long inquiryId) { this.inquiryId = inquiryId; }
    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Boolean getIsInternalNote() { return isInternalNote; }
    public void setIsInternalNote(Boolean isInternalNote) { this.isInternalNote = isInternalNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
