package com.example.auth.service;

import com.example.auth.entity.PasswordResetToken;
import com.example.auth.entity.User;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class PasswordResetRequestService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRequestService.class);
    private static final int TOKEN_EXPIRE_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final SesClient sesClient;

    @Value("${aws.ses.from-address}")
    private String fromAddress;

    @Value("${password-reset.url}")
    private String resetUrl;

    public PasswordResetRequestService(UserRepository userRepository,
                                       PasswordResetTokenRepository tokenRepository,
                                       SesClient sesClient) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.sesClient = sesClient;
    }

    @Transactional
    public void request(String email) {
        Optional<User> optUser = userRepository.findByEmail(email);

        if (optUser.isEmpty()) {
            return;
        }

        User user = optUser.get();
        String raw  = generateRawToken();
        String hash = hashToken(raw);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRE_MINUTES));
        token.setUsed(false);
        tokenRepository.save(token);

        sendEmail(user.getEmail(), raw);
    }

    private void sendEmail(String to, String rawToken) {
        String url  = resetUrl + "?token=" + rawToken;
        String body = "パスワード再設定はこちら: " + url;

        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(fromAddress)
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder().data("パスワード再設定").build())
                            .body(Body.builder()
                                    .text(Content.builder().data(body).build())
                                    .build())
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("SES send failed: {}", e.getMessage());
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String raw) {
        return org.springframework.util.DigestUtils.md5DigestAsHex(raw.getBytes());
    }
}
