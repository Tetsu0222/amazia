package com.example.inquiry.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 詳細レスポンス（messages 配列を含む / フェーズ18）。
 */
public record InquiryDetailResponse(
        Long id,
        Long userId,
        String userName,
        String subject,
        String status,
        String targetType,
        Long targetId,
        String targetLabel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<InquiryMessageResponse> messages
) {
}
