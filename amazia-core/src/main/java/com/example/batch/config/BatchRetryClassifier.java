package com.example.batch.config;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;

/**
 * フェーズ17 Step 2-5: リトライ可否判定（設計書 §3 共通制御 R-6 / 4）。
 *
 * <p>I/O 一過性例外のみリトライ可能とし、それ以外（業務例外・整合性違反）は
 * {@link AbstractBatchJob} 側で 1 回で {@code FAILED} に倒す。
 */
@Component
public class BatchRetryClassifier {

    /**
     * 例外チェーンを上から走査し、リトライ可能な型が含まれていれば {@code true}。
     * 5xx のレスポンスコードを持つ {@link RestClientResponseException} のみ可と判定する。
     */
    /** spring-context-support 依存を本フェーズで増やさないため、クラス名文字列で判定する。 */
    private static final String MAIL_SEND_EXCEPTION_FQCN = "org.springframework.mail.MailSendException";

    public boolean shouldRetry(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) return true;
            if (t instanceof ResourceAccessException) return true;
            if (t instanceof TransientDataAccessException) return true;
            if (t instanceof CannotAcquireLockException) return true;
            if (MAIL_SEND_EXCEPTION_FQCN.equals(t.getClass().getName())) return true;
            if (t instanceof RestClientResponseException rcre) {
                int status = rcre.getStatusCode().value();
                if (status >= 500 && status < 600) return true;
            }
        }
        return false;
    }
}
