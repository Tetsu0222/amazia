package com.example.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * お知らせ分類マスタ（フェーズ19 r2 / 設計書 §DB 設計）。
 *
 * <p>初期値は schema.sql の INSERT IGNORE で投入される（important / normal）。
 * code は UNIQUE 制約あり。Service / Controller 層からは config 経由で ID を解決する
 * （`amazia.notice.categories.important-id` / `normal-id`）。
 */
@Entity
@Table(name = "notice_categories")
public class NoticeCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
