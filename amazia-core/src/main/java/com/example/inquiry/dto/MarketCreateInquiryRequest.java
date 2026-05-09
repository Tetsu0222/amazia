package com.example.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Market 顧客の問い合わせ新規作成リクエスト（フェーズ18 / RV-9）。
 *
 * <p>{@code is_internal_note} を構造的に持たない。Mass Assignment 攻撃面を Controller 入口で塞ぐ目的（RV-9）。
 * 件名／本文の長さ上限は {@code amazia.inquiry.subject-max-length} / {@code message-max-length} で
 * config 駆動だが、最低限のフレームワーク標準バリデーションも掛ける（@Size の値は config と同期する）。
 */
public record MarketCreateInquiryRequest(
        @NotBlank @Size(max = 100) String subject,
        @NotBlank String message,
        String targetType,
        Long targetId
) {
}
