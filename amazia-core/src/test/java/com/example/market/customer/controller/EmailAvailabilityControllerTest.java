package com.example.market.customer.controller;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class EmailAvailabilityControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;

    private void createCustomer(String email) {
        Customer c = new Customer();
        c.setNameLast("山田");
        c.setNameFirst("太郎");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail(email);
        c.setPasswordHash("$2a$10$dummyhash");
        c.setPaymentMethod("credit_card");
        customerRepository.save(c);
    }

    @Test
    void 未登録メールはavailableがtrueで返ること() throws Exception {
        mockMvc.perform(get("/api/customer/email-availability").param("email", "free@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 登録済メールはavailableがfalseで返ること() throws Exception {
        createCustomer("taken@example.com");
        mockMvc.perform(get("/api/customer/email-availability").param("email", "taken@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
