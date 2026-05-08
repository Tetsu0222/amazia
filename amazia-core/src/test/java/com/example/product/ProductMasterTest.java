package com.example.product;

import com.example.shared.config.TestAwsConfig;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ProductMasterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 公開期間外の商品はMarket向け一覧に含まれないこと() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"終了商品\",\"description\":\"\",\"price\":1000,\"stock\":10," +
                        "\"statusCode\":\"ON_SALE\"," +
                        "\"publishStart\":\"2020-01-01T00:00:00\"," +
                        "\"publishEnd\":\"2020-12-31T23:59:59\"}"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void 公開期間内の商品のみMarket向け一覧に返ること() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"公開中商品\",\"description\":\"\",\"price\":2000,\"stock\":5," +
                        "\"statusCode\":\"ON_SALE\"," +
                        "\"publishStart\":\"2020-01-01T00:00:00\"}"));

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"未来商品\",\"description\":\"\",\"price\":3000,\"stock\":1," +
                        "\"statusCode\":\"RESERVATION\"," +
                        "\"publishStart\":\"2099-01-01T00:00:00\"}"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("公開中商品"));
    }

    @Test
    void 管理画面向け全件取得は公開期間外も含む() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"終了商品\",\"description\":\"\",\"price\":1000,\"stock\":10," +
                        "\"statusCode\":\"ON_SALE\"," +
                        "\"publishEnd\":\"2020-12-31T23:59:59\"}"));

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"公開中商品\",\"description\":\"\",\"price\":2000,\"stock\":5," +
                        "\"statusCode\":\"ON_SALE\"}"));

        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 管理者向け商品一覧にSKUサマリーが含まれること() throws Exception {
        // 商品登録時にデフォルトSKU（price=0, stock=0）が自動生成される
        String productJson = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"テスト商品\",\"description\":\"\",\"price\":0,\"stock\":0,\"statusCode\":\"ON_SALE\"}"))
                .andReturn().getResponse().getContentAsString();
        Long productId = Long.parseLong(productJson.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String skuJson = mockMvc.perform(post("/api/products/{id}/skus", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"M\"}"))
                .andReturn().getResponse().getContentAsString();
        Long skuId = Long.parseLong(skuJson.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/skus/{id}/prices", skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":1980,\"startDate\":\"2026-01-01\"}"));

        mockMvc.perform(post("/api/skus/{id}/stocks/receive", skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":50}"));

        // デフォルトSKU(1個) + 手動登録SKU(1個) = 2個
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCount").value(2))
                .andExpect(jsonPath("$[0].minPrice").value(0))
                .andExpect(jsonPath("$[0].maxPrice").value(1980))
                .andExpect(jsonPath("$[0].totalStock").value(50));
    }

    @Test
    void 商品登録時はデフォルトSKUが1件自動生成されること() throws Exception {
        // デフォルトSKU自動生成により、SKUなしの商品は登録直後から skuCount=1 になる
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"新商品\",\"description\":\"\",\"price\":0,\"stock\":0,\"statusCode\":\"WAITING\"}"));

        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCount").value(1))
                .andExpect(jsonPath("$[0].minPrice").value(0))
                .andExpect(jsonPath("$[0].totalStock").value(0));
    }

    @Test
    void ステータスコードが不正な値のとき400が返ること() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A\",\"description\":\"\",\"price\":1000,\"stock\":10," +
                        "\"statusCode\":\"INVALID_CODE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ステータスマスタ一覧が取得できること() throws Exception {
        mockMvc.perform(get("/api/product-statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
