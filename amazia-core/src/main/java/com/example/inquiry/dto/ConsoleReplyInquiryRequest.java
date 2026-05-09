package com.example.inquiry.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Console 管理者の問い合わせ返信リクエスト（フェーズ18）。
 *
 * <p>{@code isInternalNote} を持つ（Market 側 {@link MarketReplyInquiryRequest} には存在しない）。
 * null は false 扱い。
 */
public record ConsoleReplyInquiryRequest(
        @NotBlank String message,
        Boolean isInternalNote
) {
}
