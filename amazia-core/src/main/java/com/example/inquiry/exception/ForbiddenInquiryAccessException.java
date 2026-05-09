package com.example.inquiry.exception;

/**
 * 他顧客の問い合わせ・対象資源への不正アクセスを拒否するときに送出（フェーズ18 / 設計書 §5.4 / RV-5）。
 *
 * <p>{@code GlobalExceptionHandler} が HTTP 403 にマッピングする。
 */
public class ForbiddenInquiryAccessException extends RuntimeException {

    public ForbiddenInquiryAccessException(String message) {
        super(message);
    }
}
