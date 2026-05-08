package com.example.market.postal.controller;

import com.example.market.postal.entity.PostalAddress;
import com.example.market.postal.repository.PostalAddressRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SearchPostalAddressControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired PostalAddressRepository repository;

    private void save(String postalCode, String prefecture, String city, String town) {
        PostalAddress a = new PostalAddress();
        a.setPostalCode(postalCode);
        a.setPrefecture(prefecture);
        a.setCity(city);
        a.setTown(town);
        repository.save(a);
    }

    @Test
    void 郵便番号7桁で住所が取得できること() throws Exception {
        save("1000001", "東京都", "千代田区", "千代田");

        mockMvc.perform(get("/api/customer/postal-addresses").param("postal_code", "1000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].postalCode").value("1000001"))
                .andExpect(jsonPath("$[0].prefecture").value("東京都"))
                .andExpect(jsonPath("$[0].city").value("千代田区"))
                .andExpect(jsonPath("$[0].town").value("千代田"));
    }

    @Test
    void ハイフン付き郵便番号でも住所が取得できること() throws Exception {
        save("1000001", "東京都", "千代田区", "千代田");

        mockMvc.perform(get("/api/customer/postal-addresses").param("postal_code", "100-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].postalCode").value("1000001"));
    }

    @Test
    void 同一郵便番号の複数町域がすべて返ること() throws Exception {
        save("1000001", "東京都", "千代田区", "千代田");
        save("1000001", "東京都", "千代田区", "丸の内");

        mockMvc.perform(get("/api/customer/postal-addresses").param("postal_code", "1000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void 該当なしの郵便番号は空配列を返すこと() throws Exception {
        mockMvc.perform(get("/api/customer/postal-addresses").param("postal_code", "9999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void 桁数不正な郵便番号は空配列を返すこと() throws Exception {
        save("1000001", "東京都", "千代田区", "千代田");

        mockMvc.perform(get("/api/customer/postal-addresses").param("postal_code", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void 数字以外を含む郵便番号は空配列を返すこと() throws Exception {
        save("1000001", "東京都", "千代田区", "千代田");

        mockMvc.perform(get("/api/customer/postal-addresses").param("postal_code", "abcdefg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void postal_codeパラメータ欠落は400を返すこと() throws Exception {
        mockMvc.perform(get("/api/customer/postal-addresses"))
                .andExpect(status().isBadRequest());
    }
}
