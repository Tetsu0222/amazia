<?php

/**
 * フェーズ14: 売上・購入機能の設定（規約 3-1 / 4-1）。
 *
 * 設計書: docs/design/phase11_20/phase14_shipping.md（r4）
 *
 * Service / Controller / テストからは `config('sales.payment_methods.credit_card_id')` のように参照する。
 * マスタ ID は Core 側 Flyway V6 / V9 の INSERT と整合させる。
 */

return [

    // 決済方法マスタ ID（V6 で INSERT 済み）
    'payment_methods' => [
        'credit_card_id'      => 1,
        'd_payment_id'        => 2,
        'cash_on_delivery_id' => 3,
    ],

    // 配送ステータスマスタ ID（V6 / V9 で INSERT 済み）
    'shipping_statuses' => [
        'pending_id'           => 1,
        'shipped_id'           => 2,
        'delivered_id'         => 3,
        'return_requested_id'  => 4,
        'returned_id'          => 5,
        'canceled_id'          => 6,
        'delivery_failed_id'   => 7,
        'rescheduled_id'       => 8,

        // Service 層で許容する遷移先（マスタ存在 ≠ 入力許容 / Q14-4）。
        // CANCELED / DELIVERY_FAILED / RESCHEDULED はマスタには存在するが
        // 本フェーズでは入力拒否する（将来 phase21 で機能対応）。
        'allowed_codes' => [
            'PENDING',
            'SHIPPED',
            'DELIVERED',
            'RETURN_REQUESTED',
            'RETURNED',
        ],
    ],

    // SKU 在庫増減ログの type 値（既存 receive / adjust に r4 で sale / return / cancel を追加）
    'sku_stock_tx_types' => [
        'receive' => 'receive',
        'adjust'  => 'adjust',
        'sale'    => 'sale',
        'return'  => 'return',
        'cancel'  => 'cancel',
    ],

];
