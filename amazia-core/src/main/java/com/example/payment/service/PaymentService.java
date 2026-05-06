package com.example.payment.service;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;

/**
 * 決済関連の Service（フェーズ14 Step A 雛形）。
 *
 * 設計書: docs/design/phase11_20/phase14_shipping.md（r4）
 *
 * 本フェーズでは「模擬決済」を提供する。本番決済 API 接続時に
 * 外部発行値をそのまま受領する形へ拡張できるよう、ID 採番ロジックは
 * generatePaymentId() に隠蔽する。
 *
 * 採番方式（r4）:
 *  - 模擬決済期: UUID v7（時系列ソート性）
 *  - 本番決済 API 接続後: 外部システム由来の決済 ID をそのまま受領
 *
 * 採用ライブラリ: f4b6a3/uuid-creator 6.1.1（License: MIT）
 */
@Service
public class PaymentService {

    /**
     * payment_id を採番する（模擬決済期）。
     * UUID v7（time-ordered epoch）を返す。
     *
     * 例: "01967b8d-3a4b-7d8e-9c5f-a1b2c3d4e5f6"
     */
    public String generatePaymentId() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }
}
