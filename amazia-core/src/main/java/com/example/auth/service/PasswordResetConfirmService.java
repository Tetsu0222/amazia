package com.example.auth.service;

import com.example.auth.entity.PasswordHistory;
import com.example.auth.entity.PasswordResetToken;
import com.example.auth.entity.User;
import com.example.auth.repository.PasswordHistoryRepository;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PasswordResetConfirmService {

    private static final Pattern PW_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).{8,}$");

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordHistoryRepository historyRepository;
    private final BCryptPasswordEncoder encoder;

    public PasswordResetConfirmService(PasswordResetTokenRepository tokenRepository,
                                       UserRepository userRepository,
                                       PasswordHistoryRepository historyRepository,
                                       BCryptPasswordEncoder encoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository  = userRepository;
        this.historyRepository = historyRepository;
        this.encoder = encoder;
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        String hash = org.springframework.util.DigestUtils.md5DigestAsHex(rawToken.getBytes());

        PasswordResetToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));

        if (token.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token already used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expired");
        }

        if (!PW_PATTERN.matcher(newPassword).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Password policy violation");
        }

        User user = token.getUser();
        List<PasswordHistory> histories = historyRepository.findTop3ByUserIdOrderByCreatedAtDesc(user.getId());
        for (PasswordHistory h : histories) {
            if (encoder.matches(newPassword, h.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Cannot reuse recent passwords");
            }
        }

        PasswordHistory history = new PasswordHistory();
        history.setUser(user);
        history.setPasswordHash(user.getPasswordHash());
        historyRepository.save(history);

        user.setPasswordHash(encoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);
    }
}
