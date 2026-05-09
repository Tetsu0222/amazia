package com.example.inquiry.exception;

/**
 * 許容されないステータス遷移を試みたときに送出（フェーズ18 / 設計書 §5.2 / I-6）。
 *
 * <p>{@code GlobalExceptionHandler} が HTTP 400 にマッピングする。
 */
public class IllegalInquiryStatusTransitionException extends RuntimeException {

    public IllegalInquiryStatusTransitionException(String message) {
        super(message);
    }
}
