package com.example.inquiry.exception;

/**
 * 問い合わせ／関連エンティティが見つからないときに送出（フェーズ18 / 設計書 §5.5）。
 *
 * <p>{@code GlobalExceptionHandler} が HTTP 404 にマッピングする。
 */
public class InquiryNotFoundException extends RuntimeException {

    public InquiryNotFoundException(String message) {
        super(message);
    }
}
