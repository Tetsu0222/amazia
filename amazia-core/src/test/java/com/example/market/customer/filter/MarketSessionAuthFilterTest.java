package com.example.market.customer.filter;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import com.example.shared.config.TestAwsConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MarketSessionAuthFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;
    @Autowired MarketSessionRepository sessionRepository;

    @Value("${market.session.ttl-seconds}") long sessionTtlSeconds;

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

    private MarketSession createSession(Long customerId, String sessionId, LocalDateTime expiresAt) {
        MarketSession s = new MarketSession();
        s.setSessionId(sessionId);
        s.setCustomerId(customerId);
        s.setCsrfToken("csrf-" + sessionId);
        s.setExpiresAt(expiresAt);
        return sessionRepository.save(s);
    }

    @Test
    void Cookieが無い場合は未認証として通過すること() throws Exception {
        mockMvc.perform(get("/api/customer/test/whoami"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void 不明なセッションIDのCookieは未認証扱いとなること() throws Exception {
        mockMvc.perform(get("/api/customer/test/whoami")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "ghost-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void 有効なセッションIDのCookieで認証され顧客IDが取得できること() throws Exception {
        Customer customer = createCustomer("authed@example.com");
        createSession(customer.getId(), "valid-sid-001", LocalDateTime.now().plusMinutes(10));

        mockMvc.perform(get("/api/customer/test/whoami")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "valid-sid-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.customerId").value(customer.getId()));
    }

    @Test
    void 期限切れセッションIDのCookieは未認証扱いとなること() throws Exception {
        Customer customer = createCustomer("expired@example.com");
        createSession(customer.getId(), "expired-sid", LocalDateTime.now().minusMinutes(1));

        mockMvc.perform(get("/api/customer/test/whoami")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "expired-sid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void アクセスごとにlastAccessedAtとexpiresAtがslidingで更新されること() throws Exception {
        Customer customer = createCustomer("sliding@example.com");
        LocalDateTime originalExpires = LocalDateTime.now().plusSeconds(60);
        createSession(customer.getId(), "sliding-sid", originalExpires);

        mockMvc.perform(get("/api/customer/test/whoami")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "sliding-sid")))
                .andExpect(status().isOk());

        MarketSession after = sessionRepository.findById("sliding-sid").orElseThrow();
        // sliding 後の有効期限は ttl 秒先まで延びる（=60 秒先より未来）
        assertTrue(after.getExpiresAt().isAfter(originalExpires),
                "expiresAt は元の値より未来へ延長されるべき");
        // last_accessed_at もほぼ now に更新される
        assertTrue(after.getLastAccessedAt().isAfter(LocalDateTime.now().minusSeconds(5)),
                "lastAccessedAt は現在時刻に更新されるべき");
    }

    @Test
    void 空文字のCookieは未認証扱いとなること() throws Exception {
        mockMvc.perform(get("/api/customer/test/whoami")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }
}
