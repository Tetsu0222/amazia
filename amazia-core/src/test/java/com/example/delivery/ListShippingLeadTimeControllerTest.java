package com.example.delivery;

import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズX-5：GET /api/shipping-lead-times の検証。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ListShippingLeadTimeControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void クエリ無しなら全141件返る() throws Exception {
        mockMvc.perform(get("/api/shipping-lead-times"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(141));
    }

    @Test
    void shippingMethodIdクエリで47件にフィルタされる() throws Exception {
        mockMvc.perform(get("/api/shipping-lead-times").param("shippingMethodId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(47))
                .andExpect(jsonPath("$[0].shippingMethodId").value(1));
    }

    @Test
    void IDで個別取得できる() throws Exception {
        mockMvc.perform(get("/api/shipping-lead-times/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void 存在しないIDは404を返す() throws Exception {
        mockMvc.perform(get("/api/shipping-lead-times/999999"))
                .andExpect(status().isNotFound());
    }
}
