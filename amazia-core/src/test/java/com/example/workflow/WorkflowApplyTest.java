package com.example.workflow;

import com.example.shared.config.TestAwsConfig;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 反映処理（二重検証 = version + before 比較）のテスト。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class WorkflowApplyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSkuRepository skuRepository;

    @Autowired
    private ProductSkuStockRepository stockRepository;

    @Autowired
    private ProductSkuPriceRepository priceRepository;

    @Test
    void 価格変更が全ステップ承認で反映されること() throws Exception {
        long productId = createProduct(2000, 10);
        long skuId     = skuRepository.findByProductId(productId).get(0).getId();

        // 価格変更 申請
        String created = mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetType": "price",
                      "targetId": %d,
                      "fields": [{"field":"price","before":2000,"after":2500}]
                    }""".formatted(skuId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long workflowId = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        // step1 supervisor → step2 admin
        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/1/approve")
                .header("X-User-Id", 100L).header("X-User-Role", "supervisor"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/2/approve")
                .header("X-User-Id", 1L).header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.completedAt").exists());

        assertEquals(2500, priceRepository.findBySkuId(skuId).orElseThrow().getPrice());
    }

    @Test
    void 価格反映時にbeforeが現値と乖離していると409が返ること() throws Exception {
        long productId = createProduct(2000, 10);
        long skuId     = skuRepository.findByProductId(productId).get(0).getId();

        String created = mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetType": "price",
                      "targetId": %d,
                      "fields": [{"field":"price","before":9999,"after":2500}]
                    }""".formatted(skuId)))
                .andReturn().getResponse().getContentAsString();
        long workflowId = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/1/approve")
                .header("X-User-Id", 100L).header("X-User-Role", "supervisor"))
                .andExpect(status().isOk());

        // 最終承認 = 反映 → before 不一致で 409
        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/2/approve")
                .header("X-User-Id", 1L).header("X-User-Role", "admin"))
                .andExpect(status().isConflict());

        assertEquals(2000, priceRepository.findBySkuId(skuId).orElseThrow().getPrice(),
            "反映に失敗したので元の価格のまま");
    }

    @Test
    void 即時反映エンドポイントで権限者は直接反映できること() throws Exception {
        long productId = createProduct(2000, 10);
        long skuId     = skuRepository.findByProductId(productId).get(0).getId();

        mockMvc.perform(post("/api/workflows/immediate-apply")
                .header("X-User-Role", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetType": "stock",
                      "targetId": %d,
                      "fields": [{"field":"quantity","before":10,"after":50}]
                    }""".formatted(skuId)))
                .andExpect(status().isOk());

        assertEquals(50, stockRepository.findBySkuId(skuId).orElseThrow().getQuantity());
    }

    @Test
    void 即時反映エンドポイントは権限者以外403を返すこと() throws Exception {
        long productId = createProduct(2000, 10);
        long skuId     = skuRepository.findByProductId(productId).get(0).getId();

        mockMvc.perform(post("/api/workflows/immediate-apply")
                .header("X-User-Role", "user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetType": "stock",
                      "targetId": %d,
                      "fields": [{"field":"quantity","before":10,"after":50}]
                    }""".formatted(skuId)))
                .andExpect(status().isForbidden());
    }

    private long createProduct(int price, int stock) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P\",\"description\":\"d\",\"price\":" + price + ",\"stock\":" + stock + "}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }
}
