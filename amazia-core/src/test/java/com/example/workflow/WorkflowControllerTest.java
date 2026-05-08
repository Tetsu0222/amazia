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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 基本的な CRUD と承認フロー（直列）の通り抜けテスト。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ワークフローを申請できること() throws Exception {
        long productId = createProduct();

        String body = """
            {
              "targetType": "product",
              "targetId": %d,
              "fields": [
                {"field":"statusCode","before":null,"after":"ON_SALE"}
              ],
              "meta": {"reason":"公開申請"}
            }""".formatted(productId);

        mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.details[0].stepNumber").value(1))
                .andExpect(jsonPath("$.details[0].targetRole").value("admin"))
                .andExpect(jsonPath("$.details[0].status").value("pending"));
    }

    @Test
    void 単一ステップを承認すると親が承認済になり完了日時がセットされること() throws Exception {
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
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long workflowId = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/1/approve")
                .header("X-User-Id", 1L)
                .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void 重複申請は409が返ること() throws Exception {
        long productId = createProduct();

        String body = """
            {
              "targetType": "product",
              "targetId": %d,
              "fields": [{"field":"statusCode","before":null,"after":"ON_SALE"}]
            }""".formatted(productId);

        mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void 取り下げで全detailがcanceledになること() throws Exception {
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

        mockMvc.perform(post("/api/workflows/" + workflowId + "/cancel")
                .header("X-User-Id", 1L)
                .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("canceled"))
                .andExpect(jsonPath("$.details[0].status").value("canceled"));
    }

    @Test
    void 存在しないワークフローは404を返すこと() throws Exception {
        mockMvc.perform(get("/api/workflows/9999"))
                .andExpect(status().isNotFound());
    }

    private long createProduct() throws Exception {
        String body = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A\",\"description\":\"d\",\"price\":1000,\"stock\":10}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }
}
