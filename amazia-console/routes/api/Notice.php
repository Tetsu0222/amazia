<?php

use App\Notice\Controller\CreateNoticeController;
use App\Notice\Controller\DeleteNoticeController;
use App\Notice\Controller\GetNoticeCategoriesController;
use App\Notice\Controller\GetNoticeController;
use App\Notice\Controller\ListNoticeController;
use App\Notice\Controller\UpdateNoticeController;
use Illuminate\Support\Facades\Route;

/**
 * フェーズ19: お知らせ管理 API（Console Pass-through）。
 * 設計書: docs/design/phase11_20/phase19_notice_management.md（r2）
 *
 * Core 側 /api/notices にラップして /api/admin/notices で公開する。
 * actor の users.id は auth.jwt ミドルウェアが auth_user_id 入力に注入するため、
 * Service 側で X-User-Id ヘッダに乗せ替えて Core を呼び出す。
 */

Route::get('/admin/notices',         ListNoticeController::class);
Route::get('/admin/notices/{id}',    GetNoticeController::class);
Route::post('/admin/notices',        CreateNoticeController::class);
Route::put('/admin/notices/{id}',    UpdateNoticeController::class);
Route::delete('/admin/notices/{id}', DeleteNoticeController::class);

// 分類マスタ（Core 認証不要・Console 経由は auth.jwt 配下で許可）
Route::get('/notice-categories', GetNoticeCategoriesController::class);
