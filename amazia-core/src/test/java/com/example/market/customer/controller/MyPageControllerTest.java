package com.example.market.customer.controller;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.filter.MarketSessionAuthFilter;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MyPageControllerTest {

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

    private void createSession(Long customerId, String sessionId) {
        MarketSession s = new MarketSession();
        s.setSessionId(sessionId);
        s.setCustomerId(customerId);
        s.setCsrfToken("csrf-test");
        s.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        sessionRepository.save(s);
    }

    @Test
    void 有効セッションで200と顧客情報が返ること() throws Exception {
        Customer c = createCustomer("me@example.com");
        createSession(c.getId(), "sid-me");

        mockMvc.perform(get("/api/customer/me")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "sid-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.id").value(c.getId()));
    }

    @Test
    void Cookie無しなら401が返ること() throws Exception {
        mockMvc.perform(get("/api/customer/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 期限切れセッションでは401が返ること() throws Exception {
        Customer c = createCustomer("expired@example.com");
        MarketSession s = new MarketSession();
        s.setSessionId("sid-expired");
        s.setCustomerId(c.getId());
        s.setCsrfToken("csrf-expired");
        s.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        sessionRepository.save(s);

        mockMvc.perform(get("/api/customer/me")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "sid-expired")))
                .andExpect(status().isUnauthorized());
    }
}
