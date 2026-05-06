package com.example.sales;

import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step A: Sales Entity の永続化検証。
 *
 * Step A で追加したカラム（sku_id / quantity / amount / payment_method_id /
 * shipping_method_id / shipping_address_id / shipping_status_id / payment_id / is_preorder）
 * が JPA から正しく読み書きできることを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class SalesEntityTest {

    @Autowired
    private SalesRepository salesRepository;

    @Test
    void Sales_は拡張カラムを含めて保存と取得ができる() {
        Sales sales = new Sales();
        sales.setUserId(1L);
        sales.setSkuId(100L);
        sales.setQuantity(2);
        sales.setAmount(5000);
        sales.setPaymentMethodId(1L);
        sales.setShippingMethodId(1L);
        sales.setShippingAddressId(10L);
        sales.setShippingStatusId(1L);
        sales.setPaymentId("01967b8d-3a4b-7d8e-9c5f-a1b2c3d4e5f6");
        sales.setPreorder(false);
        sales.setSalesDate(LocalDate.of(2026, 5, 6));

        Sales saved = salesRepository.save(sales);

        assertNotNull(saved.getId());
        Sales loaded = salesRepository.findById(saved.getId()).orElseThrow();
        assertEquals(1L, loaded.getUserId());
        assertEquals(100L, loaded.getSkuId());
        assertEquals(2, loaded.getQuantity());
        assertEquals(5000, loaded.getAmount());
        assertEquals(1L, loaded.getPaymentMethodId());
        assertEquals(1L, loaded.getShippingMethodId());
        assertEquals(10L, loaded.getShippingAddressId());
        assertEquals(1L, loaded.getShippingStatusId());
        assertEquals("01967b8d-3a4b-7d8e-9c5f-a1b2c3d4e5f6", loaded.getPaymentId());
        assertFalse(loaded.isPreorder());
        assertEquals(LocalDate.of(2026, 5, 6), loaded.getSalesDate());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void payment_id_は一意制約により重複登録できない() {
        String dupPaymentId = "01967b8d-aaaa-7d8e-9c5f-deadbeef0001";

        Sales first = newSales(dupPaymentId);
        salesRepository.saveAndFlush(first);

        Sales second = newSales(dupPaymentId);

        assertThrows(Exception.class, () -> salesRepository.saveAndFlush(second),
            "payment_id が UNIQUE 制約違反となるはず");
    }

    private Sales newSales(String paymentId) {
        Sales s = new Sales();
        s.setUserId(1L);
        s.setSkuId(100L);
        s.setQuantity(1);
        s.setAmount(1000);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(1L);
        s.setShippingAddressId(10L);
        s.setShippingStatusId(1L);
        s.setPaymentId(paymentId);
        s.setPreorder(false);
        s.setSalesDate(LocalDate.of(2026, 5, 6));
        return s;
    }
}
