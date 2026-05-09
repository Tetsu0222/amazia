package com.example.inquiry.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Market 顧客の問い合わせ返信リクエスト（フェーズ18 / RV-9）。
 *
 * <p>{@code is_internal_note} を構造的に持たない（Console 用 {@link ConsoleReplyInquiryRequest} と分離）。
 */
public record MarketReplyInquiryRequest(
        @NotBlank String message
) {
}
