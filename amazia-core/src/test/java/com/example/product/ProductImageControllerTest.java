package com.example.product;

import com.example.product.repository.ProductImageRepository;
import com.example.product.repository.ProductRepository;
import com.example.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ProductImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

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

    private MockMultipartFile validPng() {
        // 最小PNGバイナリ（PNG署名 + IHDRチャンク）
        byte[] pngBytes = new byte[]{
            (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte)0x90, 0x77, 0x53,
            (byte)0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte)0xD7, 0x63, (byte)0xF8, (byte)0xCF, (byte)0xC0, 0x00,
            0x00, 0x00, 0x02, 0x00, 0x01, (byte)0xE2, 0x21, (byte)0xBC,
            0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
            0x44, (byte)0xAE, 0x42, 0x60, (byte)0x82
        };
        return new MockMultipartFile("image", "test.png", "image/png", pngBytes);
    }

    @Test
    void PNG画像が正常に登録できること() throws Exception {
        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                .file(validPng()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test
    void 複数画像が登録できること() throws Exception {
        mockMvc.perform(multipart("/api/products/{id}/images", productId).file(validPng()))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/products/{id}/images", productId).file(validPng()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(2));
    }

    @Test
    void 画像一覧がsort_order昇順で取得できること() throws Exception {
        mockMvc.perform(multipart("/api/products/{id}/images", productId).file(validPng()));
        mockMvc.perform(multipart("/api/products/{id}/images", productId).file(validPng()));

        mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sortOrder").value(1))
                .andExpect(jsonPath("$[1].sortOrder").value(2));
    }

    @Test
    void 存在しない商品への画像登録は404を返すこと() throws Exception {
        mockMvc.perform(multipart("/api/products/{id}/images", 9999L).file(validPng()))
                .andExpect(status().isNotFound());
    }

    @Test
    void PNG以外のファイルは400を返すこと() throws Exception {
        MockMultipartFile jpeg = new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/products/{id}/images", productId).file(jpeg))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ファイルサイズが200KBを超える場合は400を返すこと() throws Exception {
        byte[] large = new byte[205000];
        large[0] = (byte)0x89; large[1] = 0x50; large[2] = 0x4E; large[3] = 0x47;
        MockMultipartFile bigFile = new MockMultipartFile("image", "big.png", "image/png", large);
        mockMvc.perform(multipart("/api/products/{id}/images", productId).file(bigFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 画像のsort_orderが変更できること() throws Exception {
        String created = mockMvc.perform(multipart("/api/products/{id}/images", productId).file(validPng()))
                .andReturn().getResponse().getContentAsString();
        long imageId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(put("/api/product-images/{id}/sort", imageId)
                .contentType("application/json")
                .content("{\"sortOrder\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").value(5));
    }

    @Test
    void 画像を削除できること() throws Exception {
        String created = mockMvc.perform(multipart("/api/products/{id}/images", productId).file(validPng()))
                .andReturn().getResponse().getContentAsString();
        long imageId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(delete("/api/product-images/{id}", imageId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 存在しない画像IDを削除しようとすると404を返すこと() throws Exception {
        mockMvc.perform(delete("/api/product-images/{id}", 9999L))
                .andExpect(status().isNotFound());
    }
}
