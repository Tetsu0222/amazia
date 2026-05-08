package com.example.market.customer.controller;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerPasswordHistoryRepository;
import com.example.market.customer.repository.CustomerRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RegisterCustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;
    @Autowired CustomerPasswordHistoryRepository historyRepository;

    private String validBody(String email) {
        return "{"
                + "\"nameLast\":\"山田\","
                + "\"nameFirst\":\"太郎\","
                + "\"postalCode\":\"1000001\","
                + "\"address\":\"東京都千代田区千代田1-1\","
                + "\"birthday\":\"1990-01-01\","
                + "\"email\":\"" + email + "\","
                + "\"password\":\"Pass1234\","
                + "\"passwordConfirm\":\"Pass1234\","
                + "\"paymentMethod\":\"credit_card\""
                + "}";
    }

    @Test
    void 有効な入力で201と作成された顧客が返ること() throws Exception {
        mockMvc.perform(post("/api/customer/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("ok@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("ok@example.com"));

        Customer saved = customerRepository.findByEmail("ok@example.com").orElseThrow();
        assertEquals("山田", saved.getNameLast());
        assertNotEquals("Pass1234", saved.getPasswordHash());
        assertEquals(1, historyRepository.findTop5ByCustomerIdOrderByCreatedAtDesc(saved.getId()).size());
    }

    @Test
    void パスワードと確認が不一致なら400が返ること() throws Exception {
        String body = validBody("mismatch@example.com").replace("\"passwordConfirm\":\"Pass1234\"",
                "\"passwordConfirm\":\"Pass9999\"");
        mockMvc.perform(post("/api/customer/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void パスワードポリシー違反は400が返ること() throws Exception {
        String body = validBody("weak@example.com")
                .replace("\"password\":\"Pass1234\",\"passwordConfirm\":\"Pass1234\"",
                        "\"password\":\"alllower\",\"passwordConfirm\":\"alllower\"");
        mockMvc.perform(post("/api/customer/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 未成年は400が返ること() throws Exception {
        String body = validBody("young@example.com")
                .replace("\"birthday\":\"1990-01-01\"",
                        "\"birthday\":\"" + LocalDate.now().minusYears(17).toString() + "\"");
        mockMvc.perform(post("/api/customer/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void メール重複は409が返ること() throws Exception {
        mockMvc.perform(post("/api/customer/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("dup@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customer/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("dup@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void 必須項目欠落は422が返ること() throws Exception {
        String body = "{\"email\":\"missing@example.com\",\"password\":\"Pass1234\"}";
        mockMvc.perform(post("/api/customer/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 郵便番号フォーマット違反は422が返ること() throws Exception {
        String body = validBody("badzip@example.com").replace("\"postalCode\":\"1000001\"",
                "\"postalCode\":\"100-0001\"");
        mockMvc.perform(post("/api/customer/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isUnprocessableEntity());
    }
}
