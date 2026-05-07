package com.example.inbound;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズ15 Step B-6-α: POST /api/inbounds の検証。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegisterInboundControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;

    private Long productId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        Product p = new Product();
        p.setName("入荷API テスト商品");
        p.setStatusCode("ON_SALE");
        productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-API-" + System.nanoTime());
        sku.setColor("青");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(0);
        skuStockRepository.save(stock);
    }

    @Test
    void 正常リクエストで201を返しInboundResponseを返す() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("skuId", skuId);
        body.put("quantity", 5);
        body.put("inboundedAt", LocalDate.of(2026, 5, 7).toString());

        mockMvc.perform(post("/api/inbounds")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.warehouseId").value(1));
    }

    @Test
    void 必須項目欠落で422を返す() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        // skuId 欠落
        body.put("quantity", 5);
        body.put("inboundedAt", LocalDate.of(2026, 5, 7).toString());

        // 本プロジェクトの GlobalExceptionHandler は @Valid 違反を 422 にマップする
        mockMvc.perform(post("/api/inbounds")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void quantityが0以下で422を返す() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("skuId", skuId);
        body.put("quantity", 0);
        body.put("inboundedAt", LocalDate.of(2026, 5, 7).toString());

        mockMvc.perform(post("/api/inbounds")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }
}
