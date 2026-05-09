package com.example.inquiry.dto;

import java.time.LocalDateTime;

/**
 * Console / Market 共通の問い合わせ一覧アイテム（フェーズ18 / 設計書 §4）。
 *
 * <p>{@code targetLabel} は config の target-labels テンプレートを Service が展開したもの。
 */
public record InquiryListResponse(
        Long id,
        Long userId,
        String userName,
        String subject,
        String status,
        String targetType,
        Long targetId,
        String targetLabel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
