package com.example.product;

import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.shared.config.TestAwsConfig;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSkuRepository skuRepository;

    @Autowired
    private ProductSkuPriceRepository priceRepository;

    @Autowired
    private ProductSkuStockRepository stockRepository;

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
    void 必須項目が欠けているとき422が返ること() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"説明のみ\"}"))
                .andExpect(status().isUnprocessableEntity());
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
    void 商品登録時にデフォルトSKUと価格と在庫が自動生成されること() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"SKU自動生成商品\",\"description\":\"説明\",\"price\":1500,\"stock\":20}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        var skus = skuRepository.findByProductId(id);
        assertEquals(1, skus.size());

        long skuId = skus.get(0).getId();
        var price = priceRepository.findBySkuId(skuId);
        assertTrue(price.isPresent());
        assertEquals(1500, price.get().getPrice());

        var stock = stockRepository.findBySkuId(skuId);
        assertTrue(stock.isPresent());
        assertEquals(20, stock.get().getQuantity());
    }

    @Test
    void 商品登録後にMarket一覧APIで商品が取得できること() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Market表示商品\",\"description\":\"説明\",\"price\":2000,\"stock\":5}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Market表示商品"))
                .andExpect(jsonPath("$[0].minPrice").value(2000))
                .andExpect(jsonPath("$[0].totalStock").value(5));
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

    // ---- フェーズ16 Step1: is_active スイッチ ----------------------------------

    @Test
    void 新規商品はデフォルトで_is_active_true_でレスポンスされる() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"既定有効\",\"description\":\"\",\"price\":1000,\"stock\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void is_active_false_で更新でき_Market一覧に出ない() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"非表示テスト\",\"description\":\"\",\"price\":1000,\"stock\":1}"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(put("/api/products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"非表示テスト\",\"description\":\"\",\"statusCode\":null,"
                        + "\"publishStart\":null,\"publishEnd\":null,\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Market 露出 API（/api/products）には is_active=false の商品は含まれない
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void is_active_true_に戻すとMarket一覧に再表示される() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"再表示テスト\",\"description\":\"\",\"price\":1000,\"stock\":1}"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(put("/api/products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"再表示テスト\",\"description\":\"\",\"statusCode\":null,"
                        + "\"publishStart\":null,\"publishEnd\":null,\"isActive\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"再表示テスト\",\"description\":\"\",\"statusCode\":null,"
                        + "\"publishStart\":null,\"publishEnd\":null,\"isActive\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isActive").value(true));
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
