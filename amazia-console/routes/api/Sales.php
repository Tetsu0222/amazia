<?php

use Illuminate\Support\Facades\Route;

Route::get('/sales',           \App\Sales\Controller\GetSalesController::class);
Route::get('/sales/inventory', \App\Sales\Controller\GetInventoryController::class);

// フェーズ14 Step B-5: 返品ワークフロー
Route::get( '/sales-returns',              \App\SalesReturn\Controller\ListSalesReturnController::class);
Route::post('/sales-returns/{id}/approve', \App\SalesReturn\Controller\ApproveSalesReturnController::class);
Route::post('/sales-returns/{id}/reject',  \App\SalesReturn\Controller\RejectSalesReturnController::class);
Route::post('/sales-returns/{id}/refund',  \App\SalesReturn\Controller\RefundSalesReturnController::class);
