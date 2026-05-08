package com.example.sales;

import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step A: 規約 4-1（テスト内で URL や設定値をハードコードせず、@Value 経由で取得する）の検証。
 * application-test.properties の amazia.sales.* が正しく読み込まれることを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class SalesConfigTest {

    @Value("${amazia.sales.payment-methods.credit-card-id}")
    private long creditCardId;

    @Value("${amazia.sales.payment-methods.d-payment-id}")
    private long dPaymentId;

    @Value("${amazia.sales.payment-methods.cash-on-delivery-id}")
    private long cashOnDeliveryId;

    @Value("${amazia.sales.shipping-statuses.pending-id}")
    private long pendingId;

    @Value("${amazia.sales.shipping-statuses.canceled-id}")
    private long canceledId;

    @Value("${amazia.sales.shipping-statuses.allowed-codes}")
    private List<String> allowedCodes;

    @Value("${amazia.sales.sku-stock-tx-types.sale}")
    private String txTypeSale;

    @Value("${amazia.sales.sku-stock-tx-types.return}")
    private String txTypeReturn;

    @Test
    void payment_methods_のマスタIDが_config_経由で取得できる() {
        assertEquals(1L, creditCardId);
        assertEquals(2L, dPaymentId);
        assertEquals(3L, cashOnDeliveryId);
    }

    @Test
    void shipping_statuses_のマスタIDが_config_経由で取得できる() {
        assertEquals(1L, pendingId);
        assertEquals(6L, canceledId);
    }

    @Test
    void 許容ステータスは_PENDING_SHIPPED_DELIVERED_RETURN_REQUESTED_RETURNED_の5件() {
        assertEquals(5, allowedCodes.size());
        assertTrue(allowedCodes.contains("PENDING"));
        assertTrue(allowedCodes.contains("SHIPPED"));
        assertTrue(allowedCodes.contains("DELIVERED"));
        assertTrue(allowedCodes.contains("RETURN_REQUESTED"));
        assertTrue(allowedCodes.contains("RETURNED"));
        assertFalse(allowedCodes.contains("CANCELED"));
        assertFalse(allowedCodes.contains("DELIVERY_FAILED"));
        assertFalse(allowedCodes.contains("RESCHEDULED"));
    }

    @Test
    void sku_stock_tx_types_に_sale_と_return_が定義されている() {
        assertEquals("sale", txTypeSale);
        assertEquals("return", txTypeReturn);
    }
}
