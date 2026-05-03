package com.example.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
