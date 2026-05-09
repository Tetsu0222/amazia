package com.example.inquiry.exception;

/**
 * 問い合わせドメインの入力バリデーションエラー（フェーズ18 / 設計書 §5.3）。
 *
 * <p>件名・本文の長さ上限超過、target_type の未知値、is_internal_note の Service 層拒否など
 * Service 内部で発生する入力検証違反を表現する。{@code GlobalExceptionHandler} が HTTP 400 にマッピング。
 */
public class InquiryValidationException extends RuntimeException {

    public InquiryValidationException(String message) {
        super(message);
    }
}
