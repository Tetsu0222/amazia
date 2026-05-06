package com.example.payment;

import com.example.payment.service.PaymentService;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step A: PaymentService（UUID v7 採番）の検証。
 *
 * 設計書 r4 / S14-11 の要請：
 *  - License は MIT/Apache-2.0 推奨（採用ライブラリ: f4b6a3/uuid-creator MIT）
 *  - 模擬決済期は UUID v7（時系列ソート性）
 *  - VARCHAR(100) に収まる文字列形式
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Test
    void payment_id_は_UUID_v7_形式の文字列として生成される() {
        String paymentId = paymentService.generatePaymentId();

        assertNotNull(paymentId);
        // UUID 文字列は 36 文字（ハイフン込み）
        assertEquals(36, paymentId.length());
        // VARCHAR(100) に収まる
        assertTrue(paymentId.length() <= 100);

        UUID parsed = UUID.fromString(paymentId);
        // UUID v7 はバージョン番号 7
        assertEquals(7, parsed.version());
    }

    @Test
    void 連続生成された_payment_id_は時系列順に並ぶ() {
        // UUID v7 は time-ordered なので、辞書順ソート＝生成順となる
        String first = paymentService.generatePaymentId();
        String second = paymentService.generatePaymentId();
        String third = paymentService.generatePaymentId();

        assertTrue(first.compareTo(second) < 0,
            "first < second であるべき: " + first + " vs " + second);
        assertTrue(second.compareTo(third) < 0,
            "second < third であるべき: " + second + " vs " + third);
    }

    @Test
    void 大量生成しても重複しない() {
        Set<String> ids = new HashSet<>();
        int n = 1000;
        for (int i = 0; i < n; i++) {
            ids.add(paymentService.generatePaymentId());
        }
        assertEquals(n, ids.size(), "1000 件すべて一意であるべき");
    }
}
