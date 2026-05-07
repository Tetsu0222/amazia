<?php

/**
 * フェーズ15: 配送管理機能の設定（規約 3-1 / 4-1）。
 *
 * 設計書: docs/design/phase11_20/phase15_delivery_management.md（r5）
 *
 * Service / Controller / テストからは `config('delivery.shipping_methods.home_delivery_id')` のように参照する。
 * マスタ ID は Core 側 `schema.sql`（spring.sql.init で起動時実行）の INSERT IGNORE と整合させる。
 */

return [

    // 配送方法マスタ ID（Core schema.sql の INSERT IGNORE で投入）
    'shipping_methods' => [
        'home_delivery_id'  => 1,
        'konbini_pickup_id' => 2,
        'dropoff_id'        => 3,
    ],

    // 並行運用のダミー倉庫（RRRR-5 / warehouses が1行のみの間は固定）
    'default_warehouse_id' => 1,

    // update_scheduled_date の reason プレフィックス（RRR-5）
    // 集計可能化のため Service 層で先頭に固定値を付与する。
    'scheduled_date_reasons' => [
        'manual'         => '[manual]',
        'inbound_recalc' => '[inbound_recalc]',
        'shipping_delay' => '[shipping_delay]',
    ],

    // 配送方法別リードタイム（日数）
    // 都道府県別リードタイムは phaseX-5（マスタ化）で切り出し。
    'lead_time_days' => [
        'home_delivery'  => 3,
        'konbini_pickup' => 4,
        'dropoff'        => 2,
    ],

];
