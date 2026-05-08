package com.example.market.customer.controller;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import com.example.shared.config.TestAwsConfig;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LoginCustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;
    @Autowired MarketSessionRepository sessionRepository;

    @Value("${market.cookie.secure}") boolean cookieSecure;
    @Value("${market.cookie.domain:}") String cookieDomain;
    @Value("${market.account.max-failed-attempts}") int maxFailedAttempts;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Customer createCustomer(String email, String rawPassword, boolean active,
                                    int failedAttempts, LocalDateTime lockedUntil) {
        Customer c = new Customer();
        c.setNameLast("山田");
        c.setNameFirst("太郎");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail(email);
        c.setPasswordHash(encoder.encode(rawPassword));
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(active);
        c.setFailedAttempts(failedAttempts);
        c.setLockedUntil(lockedUntil);
        return customerRepository.save(c);
    }

    @Test
    void 有効な認証情報で200とCSRFトークンが返り_セッションCookieがセットされること() throws Exception {
        createCustomer("login@example.com", "Pass1234", true, 0, null);

        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@example.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(cookie().exists(MarketSessionAuthFilter.COOKIE_NAME))
                .andExpect(cookie().httpOnly(MarketSessionAuthFilter.COOKIE_NAME, true))
                .andExpect(cookie().secure(MarketSessionAuthFilter.COOKIE_NAME, cookieSecure));
    }

    @Test
    void ログイン成功でDBにセッションが保存されfailedAttemptsが0にリセットされること() throws Exception {
        Customer c = createCustomer("reset@example.com", "Pass1234", true, 3, null);

        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isOk());

        Customer updated = customerRepository.findById(c.getId()).orElseThrow();
        assertEquals(0, updated.getFailedAttempts());
        assertEquals(1, sessionRepository.findAll().stream()
                .filter(s -> s.getCustomerId().equals(c.getId())).count());
    }

    @Test
    void 存在しないメールで401が返ること() throws Exception {
        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void パスワード不一致で401かつfailedAttemptsがインクリメントされること() throws Exception {
        Customer c = createCustomer("wrong@example.com", "Pass1234", true, 0, null);

        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrong@example.com\",\"password\":\"WrongPass\"}"))
                .andExpect(status().isUnauthorized());

        Customer updated = customerRepository.findById(c.getId()).orElseThrow();
        assertEquals(1, updated.getFailedAttempts());
    }

    @Test
    void パスワード不一致を上限回数繰り返すとロックされること() throws Exception {
        Customer c = createCustomer("lock@example.com", "Pass1234", true, 0, null);

        for (int i = 0; i < maxFailedAttempts; i++) {
            mockMvc.perform(post("/api/customer/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"lock@example.com\",\"password\":\"WrongPass\"}"))
                    .andExpect(status().isUnauthorized());
        }

        Customer updated = customerRepository.findById(c.getId()).orElseThrow();
        assertEquals(maxFailedAttempts, updated.getFailedAttempts());
        assertNotNull(updated.getLockedUntil());
        assertTrue(updated.getLockedUntil().isAfter(LocalDateTime.now()));
    }

    @Test
    void activeFlagがfalseのユーザーは403が返ること() throws Exception {
        createCustomer("inactive@example.com", "Pass1234", false, 0, null);

        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"inactive@example.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ロック中のユーザーは423が返ること() throws Exception {
        createCustomer("locked@example.com", "Pass1234", true, 5, LocalDateTime.now().plusMinutes(10));

        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"locked@example.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void ロック期限切れのユーザーは正常認証できること() throws Exception {
        createCustomer("expired@example.com", "Pass1234", true, 5, LocalDateTime.now().minusMinutes(1));

        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"expired@example.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 連続ログインで既存セッションが破棄されてセッション固定攻撃が防がれること() throws Exception {
        Customer c = createCustomer("fix@example.com", "Pass1234", true, 0, null);

        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fix@example.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fix@example.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isOk());

        long count = sessionRepository.findAll().stream()
                .filter(s -> s.getCustomerId().equals(c.getId()))
                .count();
        assertEquals(1, count);
    }
}
