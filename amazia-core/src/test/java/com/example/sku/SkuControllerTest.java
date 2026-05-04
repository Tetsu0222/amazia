package com.example.sku;

import com.example.product.repository.ProductRepository;
import com.example.product.entity.Product;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class SkuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSkuRepository skuRepository;

    @Autowired
    private ProductSkuPriceRepository priceRepository;

    @Autowired
    private ProductSkuStockRepository stockRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        Product p = new Product();
        p.setName("テスト商品");
        p.setDescription("説明");
        p.setPrice(1000);
        p.setStock(10);
        productId = productRepository.save(p).getId();
    }

    // ─── SKU ───────────────────────────────────────────

    @Test
    void SKUを登録できること() throws Exception {
        mockMvc.perform(post("/api/products/{id}/skus", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"M\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("Red"))
                .andExpect(jsonPath("$.size").value("M"))
                .andExpect(jsonPath("$.skuCode").isNotEmpty());
    }

    @Test
    void 同一商品内で色とサイズが重複しているとき400が返ること() throws Exception {
        mockMvc.perform(post("/api/products/{id}/skus", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"M\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/{id}/skus", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"M\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 商品のSKU一覧が取得できること() throws Exception {
        mockMvc.perform(post("/api/products/{id}/skus", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"M\"}"));
        mockMvc.perform(post("/api/products/{id}/skus", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Blue\",\"size\":\"L\"}"));

        mockMvc.perform(get("/api/products/{id}/skus", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 存在しない商品へのSKU登録は404を返すこと() throws Exception {
        mockMvc.perform(post("/api/products/{id}/skus", 9999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"M\"}"))
                .andExpect(status().isNotFound());
    }

    // ─── 価格 ───────────────────────────────────────────

    @Test
    void SKU価格を登録できること() throws Exception {
        Long skuId = createSku();

        mockMvc.perform(post("/api/skus/{id}/prices", skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":2000,\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(2000));
    }

    @Test
    void SKU現行価格を取得できること() throws Exception {
        Long skuId = createSku();
        createPrice(skuId, 3000);

        mockMvc.perform(get("/api/skus/{id}/prices", skuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(3000));
    }

    @Test
    void 価格未登録のSKUの価格取得は404を返すこと() throws Exception {
        Long skuId = createSku();
        mockMvc.perform(get("/api/skus/{id}/prices", skuId))
                .andExpect(status().isNotFound());
    }

    // ─── 在庫 ───────────────────────────────────────────

    @Test
    void SKU在庫を入荷できること() throws Exception {
        Long skuId = createSku();

        mockMvc.perform(post("/api/skus/{id}/stocks/receive", skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    void SKU在庫を複数回入荷すると加算されること() throws Exception {
        Long skuId = createSku();

        mockMvc.perform(post("/api/skus/{id}/stocks/receive", skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":30}"));
        mockMvc.perform(post("/api/skus/{id}/stocks/receive", skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    void SKU現在在庫を取得できること() throws Exception {
        Long skuId = createSku();
        createStock(skuId, 100);

        mockMvc.perform(get("/api/skus/{id}/stocks", skuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    void SKU在庫の入荷履歴を取得できること() throws Exception {
        Long skuId = createSku();

        mockMvc.perform(post("/api/skus/{id}/stocks/receive", skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":10}"));

        mockMvc.perform(get("/api/skus/{id}/stocks/history", skuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    // ─── ヘルパー ────────────────────────────────────────

    private Long createSku() throws Exception {
        String json = mockMvc.perform(post("/api/products/{id}/skus", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"color\":\"Red\",\"size\":\"M\"}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void createPrice(Long skuId, int price) {
        ProductSkuPrice p = new ProductSkuPrice();
        p.setSkuId(skuId);
        p.setPrice(price);
        p.setStartDate(LocalDate.now());
        priceRepository.save(p);
    }

    private void createStock(Long skuId, int quantity) {
        ProductSkuStock s = new ProductSkuStock();
        s.setSkuId(skuId);
        s.setQuantity(quantity);
        stockRepository.save(s);
    }
}
