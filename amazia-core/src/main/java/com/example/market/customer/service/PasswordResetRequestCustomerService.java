package com.example.market.customer.service;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.CustomerPasswordResetToken;
import com.example.market.customer.repository.CustomerPasswordResetTokenRepository;
import com.example.market.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Market 顧客向けパスワード再発行リクエスト処理。
 *
 * 設計書 phase13 §6 に準拠。
 *  - メールアドレスが未登録でも 200 OK を返す（アカウント存在の漏洩防止）
 *  - 連打対策として、同一顧客の未使用トークンは新規発行前に invalidate する
 *  - トークン本体はメール内 URL にだけ存在し、DB には SHA-256 ハッシュを保存
 *  - SES 送信失敗時はリトライ（指数バックオフ 1s → 2s → 4s）。3 回失敗時はログのみ
 */
@Service
public class PasswordResetRequestCustomerService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRequestCustomerService.class);
    private static final int TOKEN_EXPIRE_MINUTES = 30;
    private static final int SES_MAX_ATTEMPTS = 3;

    private final CustomerRepository customerRepository;
    private final CustomerPasswordResetTokenRepository tokenRepository;
    private final SesClient sesClient;
    private final String fromAddress;
    private final String resetUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetRequestCustomerService(CustomerRepository customerRepository,
                                               CustomerPasswordResetTokenRepository tokenRepository,
                                               SesClient sesClient,
                                               @Value("${aws.ses.from-address}") String fromAddress,
                                               @Value("${password-reset.url}") String resetUrl) {
        this.customerRepository = customerRepository;
        this.tokenRepository = tokenRepository;
        this.sesClient = sesClient;
        this.fromAddress = fromAddress;
        this.resetUrl = resetUrl;
    }

    @Transactional
    public void request(String email) {
        Optional<Customer> opt = customerRepository.findByEmail(email);
        if (opt.isEmpty()) {
            log.info("market password reset requested for unknown email={}", maskEmail(email));
            return;
        }

        Customer customer = opt.get();
        if (!customer.isActiveFlag()) {
            log.info("market password reset requested for inactive id={} email={}",
                    customer.getId(), maskEmail(customer.getEmail()));
            return;
        }

        tokenRepository.invalidateActiveTokensByCustomerId(customer.getId());

        String raw = generateRawToken();
        String hash = sha256Hex(raw);

        CustomerPasswordResetToken token = new CustomerPasswordResetToken();
        token.setCustomerId(customer.getId());
        token.setTokenHash(hash);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRE_MINUTES));
        token.setUsed(false);
        tokenRepository.save(token);

        sendEmailWithRetry(customer.getEmail(), raw);
        log.info("market password reset requested id={} email={}",
                customer.getId(), maskEmail(customer.getEmail()));
    }

    private void sendEmailWithRetry(String to, String rawToken) {
        long backoffMillis = 1000L;
        for (int attempt = 1; attempt <= SES_MAX_ATTEMPTS; attempt++) {
            try {
                sendEmail(to, rawToken);
                return;
            } catch (Exception e) {
                log.warn("SES send failed attempt={}/{} email={} error={}",
                        attempt, SES_MAX_ATTEMPTS, maskEmail(to), e.getMessage());
                if (attempt == SES_MAX_ATTEMPTS) break;
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoffMillis *= 2;
            }
        }
        log.error("SES send giving up email={}", maskEmail(to));
    }

    private void sendEmail(String to, String rawToken) {
        String url = resetUrl + "?token=" + rawToken;
        String subject = "[Amazia Market] パスワード再設定のご案内";
        String body =
                "Amazia Market をご利用いただきありがとうございます。\n\n"
                + "下記の URL からパスワードを再設定してください。\n"
                + url + "\n\n"
                + "※ 本リンクの有効期限は 30 分です。\n"
                + "※ お心当たりがない場合は本メールを破棄してください。\n";

        sesClient.sendEmail(SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .text(Content.builder().data(body).charset("UTF-8").build())
                                .build())
                        .build())
                .build());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String maskEmail(String email) {
        if (email == null) return "***";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }
}
