package com.example.inquiry.dto;

import java.time.LocalDateTime;

/**
 * 個別メッセージのレスポンス DTO（フェーズ18）。
 *
 * <p>Market API では {@code isInternalNote=true} のメッセージは Repository 段階で除外されるため、
 * レスポンスに含まれない（DTO 上は同型でも実値は false のみ）。
 */
public record InquiryMessageResponse(
        Long id,
        String senderType,
        Long senderId,
        String senderName,
        String message,
        boolean isInternalNote,
        LocalDateTime createdAt
) {
}
