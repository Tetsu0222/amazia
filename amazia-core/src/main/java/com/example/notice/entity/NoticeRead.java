package com.example.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * お知らせ既読履歴（フェーズ19 r2 / 設計書 §DB 設計）。
 *
 * <p>(notice_id, market_customer_id) は UNIQUE 制約で重複既読を物理担保する（重複登録は冪等扱い）。
 * お知らせ論理削除時も本テーブルは残す（参照履歴維持 / CASCADE DELETE 不採用）。
 * {@code market_customer_id} は market_customers.id（DB 上 BIGINT UNSIGNED）を Java 側 Long で受ける。
 */
@Entity
@Table(
        name = "notice_reads",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notice_reads_notice_customer",
                columnNames = {"notice_id", "market_customer_id"}
        )
)
public class NoticeRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "market_customer_id", nullable = false)
    private Long marketCustomerId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (readAt == null) readAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public Long getMarketCustomerId() { return marketCustomerId; }
    public void setMarketCustomerId(Long marketCustomerId) { this.marketCustomerId = marketCustomerId; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
