package com.example.inquiry.dto;

import java.time.LocalDateTime;

/**
 * Console 一覧のフィルタ条件（フェーズ18 / 設計書 §2.1.2）。
 *
 * <p>すべて Optional（null 可）。Service が Repository クエリの WHERE 条件に渡す。
 */
public record InquiryListFilter(
        String status,
        String targetType,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        String userNameLike
) {
    public static InquiryListFilter empty() {
        return new InquiryListFilter(null, null, null, null, null);
    }
}
