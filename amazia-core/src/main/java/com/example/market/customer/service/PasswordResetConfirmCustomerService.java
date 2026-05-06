package com.example.market.customer.service;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.CustomerPasswordHistory;
import com.example.market.customer.entity.CustomerPasswordResetToken;
import com.example.market.customer.repository.CustomerPasswordHistoryRepository;
import com.example.market.customer.repository.CustomerPasswordResetTokenRepository;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Market 顧客向けパスワード再設定（確定）処理。
 *
 * 設計書 phase13 §6 / §11 に準拠。
 *  - トークン: 不一致 / 使用済み / 期限切れはすべて 400
 *  - パスワードポリシー違反は 422
 *  - 過去 5 回分（{@code market.account.password-history-size}）と同一は 422
 *  - パスワード更新成功時は当該顧客のセッションを全件破棄（盗難セッション失効）
 *  - 自動ログインはせず、フロント側でログイン画面に誘導する
 */
@Service
public class PasswordResetConfirmCustomerService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetConfirmCustomerService.class);

    private final CustomerPasswordResetTokenRepository tokenRepository;
    private final CustomerRepository customerRepository;
    private final CustomerPasswordHistoryRepository historyRepository;
    private final MarketSessionRepository sessionRepository;
    private final BCryptPasswordEncoder encoder;
    private final int passwordMinLength;
    private final Pattern passwordPattern;

    public PasswordResetConfirmCustomerService(CustomerPasswordResetTokenRepository tokenRepository,
                                               CustomerRepository customerRepository,
                                               CustomerPasswordHistoryRepository historyRepository,
                                               MarketSessionRepository sessionRepository,
                                               BCryptPasswordEncoder encoder,
                                               @Value("${market.account.password-min-length:8}") int passwordMinLength) {
        this.tokenRepository = tokenRepository;
        this.customerRepository = customerRepository;
        this.historyRepository = historyRepository;
        this.sessionRepository = sessionRepository;
        this.encoder = encoder;
        this.passwordMinLength = passwordMinLength;
        this.passwordPattern = Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).{" + passwordMinLength + ",}$");
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        String hash = sha256Hex(rawToken);

        CustomerPasswordResetToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid token"));

        if (token.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token already used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token expired");
        }

        if (!passwordPattern.matcher(newPassword).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "weak password");
        }

        Customer customer = customerRepository.findById(token.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid token"));

        List<CustomerPasswordHistory> histories =
                historyRepository.findTop5ByCustomerIdOrderByCreatedAtDesc(customer.getId());
        for (CustomerPasswordHistory h : histories) {
            if (encoder.matches(newPassword, h.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "cannot reuse recent passwords");
            }
        }

        CustomerPasswordHistory history = new CustomerPasswordHistory();
        history.setCustomerId(customer.getId());
        history.setPasswordHash(customer.getPasswordHash());
        historyRepository.save(history);

        customer.setPasswordHash(encoder.encode(newPassword));
        customer.setFailedAttempts(0);
        customer.setLockedUntil(null);
        customerRepository.save(customer);

        token.setUsed(true);
        tokenRepository.save(token);

        sessionRepository.deleteByCustomerId(customer.getId());

        log.info("market password reset confirmed id={} email={}",
                customer.getId(), maskEmail(customer.getEmail()));
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
