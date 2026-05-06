package com.example.market.customer.controller;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.CustomerPasswordHistory;
import com.example.market.customer.entity.CustomerPasswordResetToken;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.repository.CustomerPasswordHistoryRepository;
import com.example.market.customer.repository.CustomerPasswordResetTokenRepository;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PasswordResetCustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;
    @Autowired CustomerPasswordResetTokenRepository tokenRepository;
    @Autowired CustomerPasswordHistoryRepository historyRepository;
    @Autowired MarketSessionRepository sessionRepository;

    @Value("${aws.ses.from-address}") String fromAddress;
    @Value("${password-reset.url}") String resetUrl;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setNameLast("山田");
        customer.setNameFirst("太郎");
        customer.setPostalCode("1000001");
        customer.setAddress("東京都千代田区千代田1-1");
        customer.setBirthday(LocalDate.of(1990, 1, 1));
        customer.setEmail("reset@example.com");
        customer.setPasswordHash(encoder.encode("OldPass1234"));
        customer.setPaymentMethod("credit_card");
        customer.setActiveFlag(true);
        customer = customerRepository.save(customer);
    }

    private String saveToken(String raw, LocalDateTime expiresAt, boolean used) {
        CustomerPasswordResetToken token = new CustomerPasswordResetToken();
        token.setCustomerId(customer.getId());
        token.setTokenHash(sha256Hex(raw));
        token.setExpiresAt(expiresAt);
        token.setUsed(used);
        tokenRepository.save(token);
        return raw;
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- POST /api/customer/password/reset ---

    @Test
    void 登録済みメールでリクエストするとトークンがDBにハッシュ保存されること() throws Exception {
        mockMvc.perform(post("/api/customer/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
                .andExpect(status().isOk());

        List<CustomerPasswordResetToken> tokens = tokenRepository.findAll();
        assertEquals(1, tokens.size());
        CustomerPasswordResetToken token = tokens.get(0);
        assertFalse(token.isUsed());
        assertTrue(token.getExpiresAt().isAfter(LocalDateTime.now()));
        assertEquals(64, token.getTokenHash().length());
    }

    @Test
    void 未登録メールでも200が返りトークンは発行されないこと() throws Exception {
        mockMvc.perform(post("/api/customer/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk());

        assertTrue(tokenRepository.findAll().isEmpty());
    }

    @Test
    void 退会済みアカウントは200を返すがトークンは発行されないこと() throws Exception {
        customer.setActiveFlag(false);
        customerRepository.save(customer);

        mockMvc.perform(post("/api/customer/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
                .andExpect(status().isOk());

        assertTrue(tokenRepository.findAll().isEmpty());
    }

    @Test
    void 連続リクエストで既存の未使用トークンはinvalidateされること() throws Exception {
        saveToken("old-token", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/customer/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\"}"))
                .andExpect(status().isOk());

        List<CustomerPasswordResetToken> tokens = tokenRepository.findAll();
        assertEquals(2, tokens.size());
        long usedCount = tokens.stream().filter(CustomerPasswordResetToken::isUsed).count();
        assertEquals(1, usedCount);
    }

    @Test
    void メール形式が不正な場合は422() throws Exception {
        mockMvc.perform(post("/api/customer/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void SES設定が環境変数経由で読み込めること() {
        assertNotNull(fromAddress);
        assertFalse(fromAddress.isBlank());
        assertNotNull(resetUrl);
        assertFalse(resetUrl.isBlank());
    }

    // --- POST /api/customer/password/reset/confirm ---

    @Test
    void 有効なトークンと新パスワードでパスワードが更新されること() throws Exception {
        String raw = saveToken("valid-token", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass5678\"}"))
                .andExpect(status().isOk());

        Customer updated = customerRepository.findById(customer.getId()).orElseThrow();
        assertTrue(encoder.matches("NewPass5678", updated.getPasswordHash()));
        assertFalse(encoder.matches("OldPass1234", updated.getPasswordHash()));
    }

    @Test
    void パスワード再設定後にトークンがusedになること() throws Exception {
        String raw = saveToken("once-only", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass5678\"}"))
                .andExpect(status().isOk());

        CustomerPasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(raw)).orElseThrow();
        assertTrue(token.isUsed());
    }

    @Test
    void パスワード再設定で旧パスワードが履歴に保存されること() throws Exception {
        String raw = saveToken("history-token", LocalDateTime.now().plusMinutes(30), false);
        String oldHash = customer.getPasswordHash();

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass5678\"}"))
                .andExpect(status().isOk());

        List<CustomerPasswordHistory> histories =
                historyRepository.findTop5ByCustomerIdOrderByCreatedAtDesc(customer.getId());
        assertEquals(1, histories.size());
        assertEquals(oldHash, histories.get(0).getPasswordHash());
    }

    @Test
    void パスワード再設定で当該顧客のセッションが全件破棄されること() throws Exception {
        MarketSession session = new MarketSession();
        session.setSessionId("dummy-session-id-1234567890");
        session.setCustomerId(customer.getId());
        session.setCsrfToken("dummy-csrf");
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setLastAccessedAt(now);
        session.setExpiresAt(now.plusMinutes(30));
        sessionRepository.save(session);

        String raw = saveToken("invalidate-session", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass5678\"}"))
                .andExpect(status().isOk());

        assertEquals(0, sessionRepository.findAll().stream()
                .filter(s -> s.getCustomerId().equals(customer.getId())).count());
    }

    @Test
    void 失敗カウンタとロック状態がリセットされること() throws Exception {
        customer.setFailedAttempts(5);
        customer.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        customerRepository.save(customer);

        String raw = saveToken("unlock-token", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass5678\"}"))
                .andExpect(status().isOk());

        Customer updated = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(0, updated.getFailedAttempts());
        assertNull(updated.getLockedUntil());
    }

    @Test
    void 使用済みトークンは400() throws Exception {
        String raw = saveToken("used-token", LocalDateTime.now().plusMinutes(30), true);

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass5678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 期限切れトークンは400() throws Exception {
        String raw = saveToken("expired-token", LocalDateTime.now().minusMinutes(1), false);

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass5678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 存在しないトークンは400() throws Exception {
        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"nonexistent-token\",\"newPassword\":\"NewPass5678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void パスワードポリシー違反は422() throws Exception {
        String raw = saveToken("policy-token", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 過去のパスワードと同一は422() throws Exception {
        CustomerPasswordHistory h = new CustomerPasswordHistory();
        h.setCustomerId(customer.getId());
        h.setPasswordHash(encoder.encode("ReusePass99"));
        historyRepository.save(h);

        String raw = saveToken("reuse-token", LocalDateTime.now().plusMinutes(30), false);

        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"ReusePass99\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 必須項目が欠けている場合は422() throws Exception {
        mockMvc.perform(post("/api/customer/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\",\"newPassword\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
