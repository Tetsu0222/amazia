<?php

use Illuminate\Support\Facades\Route;

// フェーズ15 配送管理（Console 中継）
Route::get(  '/deliveries',                      \App\Delivery\Controller\ListDeliveryController::class);
Route::get(  '/deliveries/{id}',                 \App\Delivery\Controller\GetDeliveryController::class);
Route::patch('/deliveries/{id}/status',          \App\Delivery\Controller\UpdateShippingStatusController::class);
Route::patch('/deliveries/{id}/address',         \App\Delivery\Controller\UpdateShippingAddressController::class);
Route::patch('/deliveries/{id}/scheduled-date',  \App\Delivery\Controller\UpdateScheduledDateController::class);
Route::patch('/deliveries/{id}/tracking-code',   \App\Delivery\Controller\RegisterTrackingCodeController::class);

// 配送方法マスタ
Route::get('/shipping-methods', \App\Delivery\Controller\ListShippingMethodController::class);
