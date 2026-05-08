package com.example.market.customer.filter;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import com.example.shared.config.TestAwsConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MarketCsrfFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;
    @Autowired MarketSessionRepository sessionRepository;

    private Customer createCustomer(String email) {
        Customer c = new Customer();
        c.setNameLast("山田");
        c.setNameFirst("太郎");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail(email);
        c.setPasswordHash("$2a$10$dummyhash");
        c.setPaymentMethod("credit_card");
        return customerRepository.save(c);
    }

    private MarketSession createSession(Long customerId, String sessionId, String csrfToken) {
        MarketSession s = new MarketSession();
        s.setSessionId(sessionId);
        s.setCustomerId(customerId);
        s.setCsrfToken(csrfToken);
        s.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return sessionRepository.save(s);
    }

    @Test
    void セッション無しで保護APIにPOSTすると401が返ること() throws Exception {
        mockMvc.perform(post("/api/customer/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void セッション有りでもXCSRFTokenが無いと403が返ること() throws Exception {
        Customer c = createCustomer("nocsrf@example.com");
        createSession(c.getId(), "sid-nocsrf", "csrf-token-001");

        mockMvc.perform(post("/api/customer/test/protected")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "sid-nocsrf")))
                .andExpect(status().isForbidden());
    }

    @Test
    void XCSRFTokenが不一致だと403が返ること() throws Exception {
        Customer c = createCustomer("badcsrf@example.com");
        createSession(c.getId(), "sid-badcsrf", "csrf-token-002");

        mockMvc.perform(post("/api/customer/test/protected")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "sid-badcsrf"))
                .header(MarketCsrfFilter.HEADER_NAME, "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 正しいセッションとXCSRFTokenの組み合わせで200が返ること() throws Exception {
        Customer c = createCustomer("ok@example.com");
        createSession(c.getId(), "sid-ok", "csrf-token-ok");

        mockMvc.perform(post("/api/customer/test/protected")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "sid-ok"))
                .header(MarketCsrfFilter.HEADER_NAME, "csrf-token-ok"))
                .andExpect(status().isOk());
    }

    @Test
    void 保護対象外パスはセッション無しでもCSRF検証されないこと() throws Exception {
        // /api/customer/login は EXCLUDED_PATHS に含まれる。Controller 自体は未実装（404）だが、
        // CSRF Filter 層では 401/403 を返さず Controller 層まで到達することを確認する。
        mockMvc.perform(post("/api/customer/login"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401 || s == 403) {
                        throw new AssertionError("CSRF filter blocked excluded path: " + s);
                    }
                });
    }

    @Test
    void 保護プレフィックス外のパスはCSRF検証されないこと() throws Exception {
        // /api/auth/login は Console 系で、Market CSRF Filter の保護対象外。
        // CSRF 層は素通りする（Controller の実体応答は問わない）。
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 403 && "invalid csrf token".equals(result.getResponse().getErrorMessage())) {
                        throw new AssertionError("Market CSRF Filter unexpectedly intercepted /api/auth/*");
                    }
                });
    }
}
