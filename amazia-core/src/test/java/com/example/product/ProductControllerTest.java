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
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 商品一覧が取得できること() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A\",\"description\":\"説明A\",\"price\":1000,\"stock\":100}"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("商品A"));
    }

    @Test
    void 商品が登録できること() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A\",\"description\":\"説明A\",\"price\":1000,\"stock\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("商品A"))
                .andExpect(jsonPath("$.price").value(1000))
                .andExpect(jsonPath("$.stock").value(100));
    }

    @Test
    void 必須項目が欠けているとき400が返ること() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"説明のみ\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 存在しない商品IDを指定したとき404が返ること() throws Exception {
        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 商品が更新できること() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A\",\"description\":\"説明A\",\"price\":1000,\"stock\":100}"))
                .andReturn().getResponse().getContentAsString();

        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(put("/api/products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A改\",\"description\":\"説明A改\",\"statusCode\":null,\"publishStart\":null,\"publishEnd\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("商品A改"))
                .andExpect(jsonPath("$.description").value("説明A改"));
    }

    @Test
    void 存在しない商品を更新しようとしたとき404が返ること() throws Exception {
        mockMvc.perform(put("/api/products/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品X\",\"description\":\"説明X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 商品が削除できること() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A\",\"description\":\"説明A\",\"price\":1000,\"stock\":100}"))
                .andReturn().getResponse().getContentAsString();

        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void 存在しない商品を削除しようとしたとき404が返ること() throws Exception {
        mockMvc.perform(delete("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 複数商品IDを指定して一括削除できること() throws Exception {
        String created1 = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A\",\"description\":\"説明A\",\"price\":1000,\"stock\":100}"))
                .andReturn().getResponse().getContentAsString();
        String created2 = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品B\",\"description\":\"説明B\",\"price\":2000,\"stock\":50}"))
                .andReturn().getResponse().getContentAsString();

        long id1 = Long.parseLong(created1.replaceAll(".*\"id\":(\\d+).*", "$1"));
        long id2 = Long.parseLong(created2.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(delete("/api/products")
                .param("ids", id1 + "," + id2))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + id1)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/" + id2)).andExpect(status().isNotFound());
    }

    @Test
    void 一括在庫更新が反映されること() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"商品A\",\"description\":\"説明A\",\"price\":1000,\"stock\":100}"))
                .andReturn().getResponse().getContentAsString();

        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/api/products/bulk-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"id\":" + id + ",\"stock\":999}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stock").value(999));
    }
}
