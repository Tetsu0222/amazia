package com.example.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * お知らせ本体（フェーズ19 r2 / 設計書 §DB 設計）。
 *
 * <p>{@code deleted_at} が NULL ならアクティブ、NOT NULL なら論理削除済（YAGNI / R19 設計書 §`deleted_flag` 廃止）。
 * 公開期間は JST タイムゾーンで保存し、Service 層は {@code now() BETWEEN publish_start AND publish_end} で判定する。
 * {@code author_id} は users.id（DB 上 BIGINT UNSIGNED）を参照するが、Java 側は Long で受ける。
 *
 * <p>{@code created_at} / {@code updated_at} は @PrePersist / @PreUpdate で Java 側から設定する
 * （H2 が ON UPDATE CURRENT_TIMESTAMP を解釈しないため。test_insights カテゴリ7-2）。
 */
@Entity
@Table(name = "notices")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "publish_start", nullable = false)
    private LocalDateTime publishStart;

    @Column(name = "publish_end", nullable = false)
    private LocalDateTime publishEnd;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public LocalDateTime getPublishStart() { return publishStart; }
    public void setPublishStart(LocalDateTime publishStart) { this.publishStart = publishStart; }
    public LocalDateTime getPublishEnd() { return publishEnd; }
    public void setPublishEnd(LocalDateTime publishEnd) { this.publishEnd = publishEnd; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
