package com.example.product;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * フェーズ14.5 Step C-3: 予約ステータス取得 Controller の検証。
 * Clock は固定値（2026-05-07 JST）に差し替えてレスポンスの再現性を担保する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestAwsConfig.class, GetPreorderStatusControllerTest.FixedClockConfig.class})
@ActiveProfiles("test")
@Transactional
class GetPreorderStatusControllerTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 5, 7);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        public Clock fixedClock() {
            return Clock.fixed(FIXED_TODAY.atStartOfDay(JST).toInstant(), JST);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository stockRepository;

    @Test
    void 予約受付中の商品ステータスと4カラムが返ること() throws Exception {
        Product p = new Product();
        p.setName("予約商品");
        p.setStatusCode("ON_SALE");
        p.setPublishStart(FIXED_TODAY.minusDays(10).atStartOfDay());
        p.setPreorderStartDate(FIXED_TODAY.minusDays(1));
        p.setReleaseDate(FIXED_TODAY.plusDays(7));
        p.setAcceptPreorder(true);
        p.setAcceptBackorder(false);
        Long pid = productRepository.save(p).getId();

        mockMvc.perform(get("/api/products/{id}/preorder-status", pid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(pid))
                .andExpect(jsonPath("$.status").value("PRE_ORDER"))
                .andExpect(jsonPath("$.releaseDate").value(FIXED_TODAY.plusDays(7).toString()))
                .andExpect(jsonPath("$.preorderStartDate").value(FIXED_TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$.acceptPreorder").value(true))
                .andExpect(jsonPath("$.acceptBackorder").value(false));
    }

    @Test
    void 在庫がある商品はON_SALEで返ること() throws Exception {
        Product p = new Product();
        p.setName("販売中商品");
        p.setStatusCode("ON_SALE");
        p.setPublishStart(FIXED_TODAY.minusDays(30).atStartOfDay());
        p.setReleaseDate(FIXED_TODAY.minusDays(7));
        Long pid = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(pid);
        sku.setSkuCode("SKU-" + System.nanoTime());
        sku.setColor("赤");
        sku.setSize("M");
        Long skuId = skuRepository.save(sku).getId();
        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(5);
        stockRepository.save(stock);

        mockMvc.perform(get("/api/products/{id}/preorder-status", pid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"));
    }

    @Test
    void 存在しないIDは404が返ること() throws Exception {
        mockMvc.perform(get("/api/products/{id}/preorder-status", 999_999_999L))
                .andExpect(status().isNotFound());
    }
}
