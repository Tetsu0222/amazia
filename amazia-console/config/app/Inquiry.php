<?php

/**
 * フェーズ18: 問い合わせ管理（規約 3-1 / 4-1）。
 *
 * 設計書: docs/design/phase11_20/phase18_inquiry_management.md（r3）
 *
 * Console Pass-through 層の FormRequest / Service / Controller は本 config を経由して値を取得する。
 * Core 側 application.properties の `amazia.inquiry.*` と同期させる。
 */

return [

    // ステータス（Core と同期）
    'statuses' => ['NEW', 'IN_PROGRESS', 'DONE'],

    // 許容ステータス遷移（双方向許容 / 設計書 r2）
    // FormRequest の in:rule 等から参照する。
    'allowed_status_transitions' => [
        'NEW'         => ['IN_PROGRESS', 'DONE'],
        'IN_PROGRESS' => ['NEW', 'DONE'],
        'DONE'        => ['NEW', 'IN_PROGRESS'],
    ],

    // 対象種別（FormRequest in:rule で利用）
    'target_types' => ['delivery', 'product', 'sales'],

    // 件名 / 本文の長さ上限（FormRequest バリデーション）
    'subject_max_length' => (int) env('INQUIRY_SUBJECT_MAX_LENGTH', 100),
    'message_max_length' => (int) env('INQUIRY_MESSAGE_MAX_LENGTH', 4000),

    // ページサイズ（Console 側）
    'page_size_console' => (int) env('INQUIRY_LIST_PAGE_SIZE_CONSOLE', 50),

    // operation_logs.comment プレフィックス（phase15 RRR-5 / phase17 と同思想）
    'operation_log_prefixes' => [
        'admin_reply'    => '[admin_reply]',
        'customer_reply' => '[customer_reply]',
        'status_change'  => '[status_change]',
        'internal_note'  => '[internal_note]',
    ],

    // ベルマークのポーリング間隔（ミリ秒）
    // Vue 側は import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS を参照するが、
    // テストや Console 側 PHP からも参照可能なよう config にも置く。
    'bell_polling_interval_ms' => (int) env('INQUIRY_BELL_POLLING_INTERVAL_MS', 30000),

];
