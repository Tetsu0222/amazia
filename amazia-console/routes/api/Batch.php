<?php

use Illuminate\Support\Facades\Route;

/**
 * フェーズ17 Step 6: Console バッチ管理 API ルート（設計書 §13.7.3）。
 *
 * - 履歴一覧 / 履歴詳細 / 通知一覧 / 既読は全認証ユーザに公開（自身宛のみ取得できる仕様）
 * - 手動起動のみ管理者相当（admin / senior_admin / eternal_advisor）に制限
 */

Route::get('/console/batch/executions',
    \App\Batch\Controller\ListBatchExecutionController::class);
Route::get('/console/batch/executions/{id}',
    \App\Batch\Controller\GetBatchExecutionController::class);

Route::get('/console/batch/notifications',
    \App\Batch\Controller\ListConsoleNotificationController::class);
Route::put('/console/batch/notifications/{id}/read',
    \App\Batch\Controller\MarkConsoleNotificationReadController::class);

Route::post('/console/batch/{jobName}/run',
    \App\Batch\Controller\TriggerBatchManualController::class
)->middleware('check.permission:batch.manual');
