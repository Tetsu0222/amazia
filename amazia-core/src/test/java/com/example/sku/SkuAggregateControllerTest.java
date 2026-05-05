package com.example.sku;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sku.entity.*;
import com.example.sku.repository.*;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class SkuAggregateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuPriceRepository priceRepository;
    @Autowired private ProductSkuStockRepository stockRepository;
    @Autowired private ProductSkuImageRepository imageRepository;

    private Long productId;
    private Long skuId1;
    private Long skuId2;

    @BeforeEach
    void setUp() {
        Product p = new Product();
        p.setName("テスト商品");
        p.setDescription("説明");
        p.setPrice(0);
        p.setStock(0);
        productId = productRepository.save(p).getId();

        ProductSku sku1 = new ProductSku();
        sku1.setProductId(productId);
        sku1.setSkuCode("SKU001");
        sku1.setColor("Red");
        sku1.setSize("M");
        skuId1 = skuRepository.save(sku1).getId();

        ProductSku sku2 = new ProductSku();
        sku2.setProductId(productId);
        sku2.setSkuCode("SKU002");
        sku2.setColor("Blue");
        sku2.setSize("L");
        skuId2 = skuRepository.save(sku2).getId();

        ProductSkuPrice price1 = new ProductSkuPrice();
        price1.setSkuId(skuId1);
        price1.setPrice(1000);
        price1.setStartDate(LocalDate.now());
        priceRepository.save(price1);

        ProductSkuPrice price2 = new ProductSkuPrice();
        price2.setSkuId(skuId2);
        price2.setPrice(2000);
        price2.setStartDate(LocalDate.now());
        priceRepository.save(price2);

        ProductSkuStock stock1 = new ProductSkuStock();
        stock1.setSkuId(skuId1);
        stock1.setQuantity(10);
        stockRepository.save(stock1);

        ProductSkuStock stock2 = new ProductSkuStock();
        stock2.setSkuId(skuId2);
        stock2.setQuantity(5);
        stockRepository.save(stock2);

        ProductSkuImage image = new ProductSkuImage();
        image.setSkuId(skuId1);
        image.setImagePath("skus/" + skuId1 + "/main.png");
        image.setSortOrder(1);
        imageRepository.save(image);
    }

    @Test
    void SKU集約商品一覧が取得できること() throws Exception {
        mockMvc.perform(get("/api/products/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(productId))
                .andExpect(jsonPath("$[0].minPrice").value(1000))
                .andExpect(jsonPath("$[0].totalStock").value(15))
                .andExpect(jsonPath("$[0].mainImage").isNotEmpty());
    }

    @Test
    void 在庫が0の商品はSKU集約一覧に含まれないこと() throws Exception {
        ProductSku sku3 = new ProductSku();
        sku3.setSkuCode("SKU003");
        sku3.setColor("Green");
        sku3.setSize("S");

        Product noStockProduct = new Product();
        noStockProduct.setName("在庫なし商品");
        noStockProduct.setDescription("説明");
        noStockProduct.setPrice(0);
        noStockProduct.setStock(0);
        Long noStockId = productRepository.save(noStockProduct).getId();

        sku3.setProductId(noStockId);
        Long sku3Id = skuRepository.save(sku3).getId();

        ProductSkuPrice p3 = new ProductSkuPrice();
        p3.setSkuId(sku3Id);
        p3.setPrice(500);
        p3.setStartDate(LocalDate.now());
        priceRepository.save(p3);

        ProductSkuStock s3 = new ProductSkuStock();
        s3.setSkuId(sku3Id);
        s3.setQuantity(0);
        stockRepository.save(s3);

        mockMvc.perform(get("/api/products/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void SKUが1件もない商品はSKU集約一覧に含まれないこと() throws Exception {
        Product emptyProduct = new Product();
        emptyProduct.setName("SKUなし商品");
        emptyProduct.setDescription("説明");
        emptyProduct.setPrice(0);
        emptyProduct.setStock(0);
        productRepository.save(emptyProduct);

        mockMvc.perform(get("/api/products/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void SKU集約商品詳細が取得できること() throws Exception {
        mockMvc.perform(get("/api/products/{id}/market", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.id").value(productId))
                .andExpect(jsonPath("$.skus.length()").value(2))
                .andExpect(jsonPath("$.skus[0].color").value("Red"))
                .andExpect(jsonPath("$.skus[0].price").value(1000))
                .andExpect(jsonPath("$.skus[0].stock").value(10));
    }

    @Test
    void 存在しない商品の集約詳細は404を返すこと() throws Exception {
        mockMvc.perform(get("/api/products/{id}/market", 9999L))
                .andExpect(status().isNotFound());
    }
}
