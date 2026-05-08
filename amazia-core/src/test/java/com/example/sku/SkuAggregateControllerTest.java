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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
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
                .andExpect(jsonPath("$[0].mainImage").isNotEmpty())
                .andExpect(jsonPath("$[0].preorderStatus").value("ON_SALE"));
    }

    // フェーズ14.5 §4-2: 在庫切れ商品も Market 一覧に含める（ステータスで分岐）
    @Test
    void 在庫が0の商品もSKU集約一覧に含まれSOLD_OUTで返ること() throws Exception {
        Product noStockProduct = new Product();
        noStockProduct.setName("在庫なし商品");
        noStockProduct.setDescription("説明");
        noStockProduct.setPrice(0);
        noStockProduct.setStock(0);
        Long noStockId = productRepository.save(noStockProduct).getId();

        ProductSku sku3 = new ProductSku();
        sku3.setSkuCode("SKU003");
        sku3.setColor("Green");
        sku3.setSize("S");
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
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.productId == " + noStockId + ")].preorderStatus")
                        .value(org.hamcrest.Matchers.contains("SOLD_OUT")));
    }

    // フェーズ14.5 §4-2: 公開期間外の商品は NOT_PUBLIC として一覧から除外される
    @Test
    void 公開開始前の商品はNOT_PUBLICとして一覧から除外されること() throws Exception {
        Product future = new Product();
        future.setName("未公開商品");
        future.setDescription("");
        future.setPrice(0);
        future.setStock(0);
        future.setPublishStart(java.time.LocalDateTime.now().plusDays(7));
        Long futureId = productRepository.save(future).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(futureId);
        sku.setSkuCode("SKU-FUTURE");
        sku.setColor("White");
        sku.setSize("M");
        Long fSkuId = skuRepository.save(sku).getId();

        ProductSkuStock s = new ProductSkuStock();
        s.setSkuId(fSkuId);
        s.setQuantity(10);
        stockRepository.save(s);

        mockMvc.perform(get("/api/products/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(productId));
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

    // 040: 価格未登録（全 SKU で minPrice=null）の商品は Market から除外される
    @Test
    void 価格未登録の商品は一覧から除外されること() throws Exception {
        Product noPriceProduct = new Product();
        noPriceProduct.setName("価格未登録商品");
        noPriceProduct.setDescription("");
        Long noPriceId = productRepository.save(noPriceProduct).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(noPriceId);
        sku.setSkuCode("SKU-NO-PRICE");
        sku.setColor("DEFAULT");
        sku.setSize("FREE");
        Long npSkuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(npSkuId);
        stock.setQuantity(5);
        stockRepository.save(stock);
        // 価格は登録しない

        mockMvc.perform(get("/api/products/market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(productId));
    }

    @Test
    void 価格未登録の商品は詳細でも404が返ること() throws Exception {
        Product noPriceProduct = new Product();
        noPriceProduct.setName("価格未登録商品");
        Long noPriceId = productRepository.save(noPriceProduct).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(noPriceId);
        sku.setSkuCode("SKU-NO-PRICE-DETAIL");
        sku.setColor("DEFAULT");
        sku.setSize("FREE");
        skuRepository.save(sku);

        mockMvc.perform(get("/api/products/{id}/market", noPriceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void SKU集約商品詳細が取得できること() throws Exception {
        mockMvc.perform(get("/api/products/{id}/market", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.id").value(productId))
                .andExpect(jsonPath("$.skus.length()").value(2))
                .andExpect(jsonPath("$.skus[0].color").value("Red"))
                .andExpect(jsonPath("$.skus[0].price").value(1000))
                .andExpect(jsonPath("$.skus[0].stock").value(10))
                .andExpect(jsonPath("$.preorderStatus").value("ON_SALE"));
    }

    @Test
    void 存在しない商品の集約詳細は404を返すこと() throws Exception {
        mockMvc.perform(get("/api/products/{id}/market", 9999L))
                .andExpect(status().isNotFound());
    }
}
