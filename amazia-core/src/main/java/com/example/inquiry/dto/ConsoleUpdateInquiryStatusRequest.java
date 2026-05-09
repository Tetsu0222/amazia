package com.example.inquiry.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Console 管理者のステータス変更リクエスト（フェーズ18 / I-6）。
 *
 * <p>{@code reason} は任意。operation_logs.comment に埋め込む。
 */
public record ConsoleUpdateInquiryStatusRequest(
        @NotBlank String newStatus,
        String reason
) {
}
