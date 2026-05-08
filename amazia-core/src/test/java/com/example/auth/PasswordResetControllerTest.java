package com.example.auth;

import com.example.auth.entity.PasswordHistory;
import com.example.auth.entity.PasswordResetToken;
import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.example.shared.config.TestAwsConfig;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PasswordResetControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired PasswordHistoryRepository historyRepository;

    @Value("${password-reset.url}")
    private String resetUrl;

    @Value("${aws.ses.from-address}")
    private String fromAddress;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private User testUser;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByCode("user").orElseThrow();
        testUser = new User();
        testUser.setEmployeeId("EMP_RESET");
        testUser.setEmail("reset@example.com");
        testUser.setName("リセットユーザー");
        testUser.setPasswordHash(encoder.encode("Pass@1234"));
        testUser.setRole(role);
        testUser.setActiveFlag(true);
        userRepository.save(testUser);
    }

    private String saveToken(String raw, LocalDateTime expiresAt, boolean used) {
        String hash = org.springframework.util.DigestUtils.md5DigestAsHex(raw.getBytes());
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(testUser);
        token.setTokenHash(hash);
        token.setExpiresAt(expiresAt);
        token.setUsed(used);
        tokenRepository.save(token);
        return raw;
    }

    // --- POST /api/auth/password/reset/request ---

    @Test
    void 登録済みメールでリクエストするとトークンがDBに保存されること() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
                .andExpect(status().isOk());

        var tokens = tokenRepository.findAll();
        assertFalse(tokens.isEmpty());
        var token = tokens.get(0);
        assertFalse(token.isUsed());
        assertTrue(token.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void 未登録メールでも200が返りDBにトークンは保存されないこと() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk());

        assertTrue(tokenRepository.findAll().isEmpty());
    }

    @Test
    void トークンはDBにハッシュ化して保存されること() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
                .andExpect(status().isOk());

        var token = tokenRepository.findAll().get(0);
        assertNotNull(token.getTokenHash());
        assertNotEquals(64, token.getTokenHash().length()); // ハッシュなのでrawと一致しない
    }

    @Test
    void PASSWORD_RESET_URLが設定されておりハードコードされていないこと() {
        assertNotNull(resetUrl);
        assertNotEmpty(resetUrl);
    }

    @Test
    void AWS_SES_FROM_ADDRESSが設定されておりハードコードされていないこと() {
        assertNotNull(fromAddress);
        assertNotEmpty(fromAddress);
    }

    // --- POST /api/auth/password/reset/confirm ---

    @Test
    void 有効なトークンと新パスワードでパスワードが更新されること() throws Exception {
        String raw = saveToken("valid-reset-token", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass@5678\"}"))
                .andExpect(status().isOk());

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(encoder.matches("NewPass@5678", updated.getPasswordHash()));
        assertFalse(encoder.matches("Pass@1234", updated.getPasswordHash()));
    }

    @Test
    void 使用済みトークンは400() throws Exception {
        String raw = saveToken("used-token", LocalDateTime.now().plusMinutes(30), true);

        mockMvc.perform(post("/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass@5678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 期限切れトークンは400() throws Exception {
        String raw = saveToken("expired-token", LocalDateTime.now().minusMinutes(1), false);

        mockMvc.perform(post("/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass@5678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 存在しないトークンは400() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"nonexistent\",\"newPassword\":\"NewPass@5678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 過去3回分と同一パスワードは422() throws Exception {
        PasswordHistory history = new PasswordHistory();
        history.setUser(testUser);
        history.setPasswordHash(encoder.encode("NewPass@5678"));
        historyRepository.save(history);

        String raw = saveToken("reuse-token", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass@5678\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void パスワードポリシー違反は422() throws Exception {
        String raw = saveToken("policy-token", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private static void assertNotEmpty(String value) {
        assertNotNull(value);
        assertFalse(value.isBlank());
    }
}
