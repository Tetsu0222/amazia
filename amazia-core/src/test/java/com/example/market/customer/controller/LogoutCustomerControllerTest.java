package com.example.market.customer.controller;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.filter.MarketCsrfFilter;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LogoutCustomerControllerTest {

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
    void 有効セッションでログアウトすると200_セッションが削除され_Cookie失効ヘッダが返ること() throws Exception {
        Customer c = createCustomer("logout@example.com");
        createSession(c.getId(), "sid-logout", "csrf-logout");

        mockMvc.perform(post("/api/customer/logout")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "sid-logout"))
                .header(MarketCsrfFilter.HEADER_NAME, "csrf-logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(MarketSessionAuthFilter.COOKIE_NAME, 0));

        assertTrue(sessionRepository.findById("sid-logout").isEmpty());
    }

    @Test
    void セッション無しでログアウトすると401が返ること() throws Exception {
        mockMvc.perform(post("/api/customer/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void セッション有りでもCSRFトークンが無いと403が返ること() throws Exception {
        Customer c = createCustomer("nocsrf@example.com");
        createSession(c.getId(), "sid-nocsrf", "csrf-nocsrf");

        mockMvc.perform(post("/api/customer/logout")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, "sid-nocsrf")))
                .andExpect(status().isForbidden());
    }
}
