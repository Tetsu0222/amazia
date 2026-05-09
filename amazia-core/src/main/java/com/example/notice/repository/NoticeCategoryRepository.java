package com.example.notice.repository;

import com.example.notice.entity.NoticeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * お知らせ分類マスタ Repository（フェーズ19 r2 / 設計書 §DB 設計）。
 *
 * <p>code は UNIQUE。Service 層からの ID 解決は config 経由（amazia.notice.categories.*-id）が
 * 第一手段で、本 Repository は分類マスタ取得 API（GET /api/notice-categories）からの
 * 全件取得（display_order 昇順）に主に用いる。
 */
public interface NoticeCategoryRepository extends JpaRepository<NoticeCategory, Long> {

    Optional<NoticeCategory> findByCode(String code);
}
