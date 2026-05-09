<?php

use App\Inquiry\Controller\GetInquiryController;
use App\Inquiry\Controller\GetUnreadInquiryCountController;
use App\Inquiry\Controller\ListInquiryController;
use App\Inquiry\Controller\ReplyInquiryController;
use App\Inquiry\Controller\UpdateInquiryStatusController;
use Illuminate\Support\Facades\Route;

/**
 * フェーズ18: 問い合わせ管理 API（Console Pass-through）。
 * 設計書: docs/design/phase11_20/phase18_inquiry_management.md（r3）
 */

Route::get('/console/inquiries/unread-count', GetUnreadInquiryCountController::class);
Route::get('/console/inquiries', ListInquiryController::class);
Route::get('/console/inquiries/{id}', GetInquiryController::class);
Route::post('/console/inquiries/{id}/messages', ReplyInquiryController::class);
Route::patch('/console/inquiries/{id}/status', UpdateInquiryStatusController::class);
