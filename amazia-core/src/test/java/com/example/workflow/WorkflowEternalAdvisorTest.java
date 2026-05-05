package com.example.workflow;

import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * eternal_advisor は target_role 不問で全ステップを代理承認できる。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class WorkflowEternalAdvisorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void eternalAdvisorはadminロール対象のステップも代理承認できること() throws Exception {
        long productId = createProduct();

        String created = mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetType": "product",
                      "targetId": %d,
                      "fields": [{"field":"statusCode","before":null,"after":"ON_SALE"}]
                    }""".formatted(productId)))
                .andReturn().getResponse().getContentAsString();
        long workflowId = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        // step1 は admin 対象だが、eternal_advisor が承認できる
        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/1/approve")
                .header("X-User-Id", 999L)
                .header("X-User-Role", "eternal_advisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
    }

    @Test
    void 一般ユーザーは承認できず403が返ること() throws Exception {
        long productId = createProduct();

        String created = mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetType": "product",
                      "targetId": %d,
                      "fields": [{"field":"statusCode","before":null,"after":"ON_SALE"}]
                    }""".formatted(productId)))
                .andReturn().getResponse().getContentAsString();
        long workflowId = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/1/approve")
                .header("X-User-Id", 500L)
                .header("X-User-Role", "user"))
                .andExpect(status().isForbidden());
    }

    private long createProduct() throws Exception {
        String body = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P\",\"description\":\"d\",\"price\":1000,\"stock\":10}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }
}
