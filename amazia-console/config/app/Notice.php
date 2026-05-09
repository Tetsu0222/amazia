<?php

/**
 * フェーズ19: お知らせ機能（規約 3-1 / 4-1）。
 *
 * 設計書: docs/design/phase11_20/phase19_notice_management.md（r2）
 *
 * Console Pass-through 層の FormRequest / Service / Controller は本 config を経由して値を取得する。
 * Core 側 application.properties の `amazia.notice.*` と同期させる。
 */

return [

    // notice_categories マスタ ID（Core schema.sql の INSERT IGNORE と同期）
    'categories' => [
        'important_id' => (int) env('NOTICE_CATEGORY_IMPORTANT_ID', 1),
        'normal_id'    => (int) env('NOTICE_CATEGORY_NORMAL_ID', 2),
    ],

    // 件名・本文の長さ上限（FormRequest バリデーション / DB CHECK と同値）
    'subject_max_length' => (int) env('NOTICE_SUBJECT_MAX_LENGTH', 255),
    'body_max_length'    => (int) env('NOTICE_BODY_MAX_LENGTH', 10000),

];
