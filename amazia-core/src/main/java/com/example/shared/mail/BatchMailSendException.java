package com.example.shared.mail;

/**
 * フェーズ17 Step 7：SES 送信失敗を表すリトライ可能例外。
 *
 * <p>spring-context-support の {@code org.springframework.mail.MailSendException} を依存に
 * 追加せず、本フェーズ範囲内で完結させるための独自例外。{@code BatchRetryClassifier} は
 * 本クラスをリトライ対象として認識する（設計書 §3 共通制御 R-6）。
 */
public class BatchMailSendException extends RuntimeException {
    public BatchMailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
