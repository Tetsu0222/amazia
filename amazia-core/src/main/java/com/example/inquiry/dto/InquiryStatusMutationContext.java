package com.example.inquiry.dto;

/**
 * Service 内部のステータス変更コンテキスト（フェーズ18 / RV-11）。
 *
 * <p>将来 {@code assignedUserId} を追加可能なように Java Record で明示フィールドを持たせる。
 */
public record InquiryStatusMutationContext(
        Long inquiryId,
        String newStatus,
        String reason,
        Long actingUserId
        // 将来：Long assignedUserId
) {
}
