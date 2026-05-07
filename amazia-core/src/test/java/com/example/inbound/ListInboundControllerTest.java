package com.example.inbound;

import com.example.inbound.entity.Inbound;
import com.example.inbound.repository.InboundRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズ15 Step B-6-α: GET /api/inbounds の検証。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ListInboundControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private InboundRepository inboundRepository;
    @Autowired private ProductRepository productRepository;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    @Test
    void 全件一覧を取得できる() throws Exception {
        Long pid = persistProduct("入荷一覧A");
        persistInbound(pid, 5);
        persistInbound(pid, 3);

        mockMvc.perform(get("/api/inbounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void productIdフィルタで絞り込める() throws Exception {
        Long target = persistProduct("入荷フィルタ対象");
        Long other = persistProduct("入荷フィルタ対象外");
        persistInbound(target, 5);
        persistInbound(other, 7);

        mockMvc.perform(get("/api/inbounds?productId=" + target))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId",
                        org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is(target.intValue()))));
    }

    private Long persistProduct(String name) {
        Product p = new Product();
        p.setName(name);
        p.setStatusCode("ON_SALE");
        return productRepository.save(p).getId();
    }

    private void persistInbound(Long productId, int quantity) {
        Inbound i = new Inbound();
        i.setProductId(productId);
        i.setWarehouseId(defaultWarehouseId);
        i.setQuantity(quantity);
        i.setInboundedAt(LocalDate.of(2026, 5, 7));
        inboundRepository.saveAndFlush(i);
    }
}
