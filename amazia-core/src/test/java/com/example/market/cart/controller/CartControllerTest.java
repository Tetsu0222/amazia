package com.example.market.cart.controller;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.filter.MarketCsrfFilter;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CartControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CustomerRepository customerRepository;
    @Autowired MarketSessionRepository sessionRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductSkuRepository skuRepository;
    @Autowired ProductSkuStockRepository skuStockRepository;
    @Autowired ProductSkuPriceRepository skuPriceRepository;

    private Long customerId;
    private Long skuId;
    private String sessionId = "sid-cart-test";
    private String csrf = "csrf-cart-test";

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setNameLast("山田");
        c.setNameFirst("太郎");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("cart-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        customerId = customerRepository.save(c).getId();

        MarketSession s = new MarketSession();
        s.setSessionId(sessionId);
        s.setCustomerId(customerId);
        s.setCsrfToken(csrf);
        s.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        sessionRepository.save(s);

        Product p = new Product();
        p.setName("テスト商品");
        p.setDescription("説明");
        p.setPrice(3000);
        p.setStock(0);
        p.setStatusCode("ON_SALE");
        p.setPublishStart(LocalDateTime.now().minusDays(1));
        p.setPublishEnd(LocalDateTime.now().plusYears(1));
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-" + System.nanoTime());
        sku.setColor("赤");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(10);
        skuStockRepository.save(stock);

        ProductSkuPrice price = new ProductSkuPrice();
        price.setSkuId(skuId);
        price.setPrice(3000);
        skuPriceRepository.save(price);
    }

    @Test
    void Cookie無しでGET_meは401() throws Exception {
        mockMvc.perform(get("/api/customer/carts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 有効セッションでGET_meは200と空カートを返す() throws Exception {
        mockMvc.perform(get("/api/customer/carts/me")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void POST_itemsはCSRFトークン無しなら403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "skuId", skuId, "quantity", 1, "preorder", false));
        mockMvc.perform(post("/api/customer/carts/me/items")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void 正しいCSRFでPOST_itemsは200で数量とSKUを返す() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "skuId", skuId, "quantity", 2, "preorder", false));
        mockMvc.perform(post("/api/customer/carts/me/items")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, sessionId))
                .header(MarketCsrfFilter.HEADER_NAME, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[0].skuId").value(skuId))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(6000));
    }

    @Test
    void DELETE_meは204でカートをクリアする() throws Exception {
        // まず追加
        String body = objectMapper.writeValueAsString(Map.of(
                "skuId", skuId, "quantity", 1, "preorder", false));
        mockMvc.perform(post("/api/customer/carts/me/items")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, sessionId))
                .header(MarketCsrfFilter.HEADER_NAME, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/customer/carts/me")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, sessionId))
                .header(MarketCsrfFilter.HEADER_NAME, csrf))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/customer/carts/me")
                .cookie(new Cookie(MarketSessionAuthFilter.COOKIE_NAME, sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }
}
