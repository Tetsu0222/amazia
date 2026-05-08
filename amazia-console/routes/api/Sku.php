<?php

use Illuminate\Support\Facades\Route;

Route::get('/products/{id}/skus',         \App\Sku\Controller\ListProductSkuController::class);
Route::post('/products/{id}/skus',        \App\Sku\Controller\CreateProductSkuController::class);

Route::get('/skus/{id}/prices',           \App\Sku\Controller\GetProductSkuPriceController::class);
Route::post('/skus/{id}/prices',          \App\Sku\Controller\CreateProductSkuPriceController::class);
Route::get('/skus/{id}/prices/history',   \App\Sku\Controller\ListSkuPriceHistoryController::class);

// 価格スケジュール（フェーズ17 Step 5.5 / 設計書 §13.5.2）
Route::get('/skus/{id}/scheduled-price',    \App\ScheduledPrice\Controller\GetScheduledSkuPriceController::class);
Route::put('/skus/{id}/scheduled-price',    \App\ScheduledPrice\Controller\RegisterScheduledSkuPriceController::class);
Route::delete('/skus/{id}/scheduled-price', \App\ScheduledPrice\Controller\DeleteScheduledSkuPriceController::class);

Route::get('/skus/{id}/stocks',           \App\Sku\Controller\GetProductSkuStockController::class);
Route::post('/skus/{id}/stocks/receive',  \App\Sku\Controller\ReceiveProductSkuStockController::class);
Route::get('/skus/{id}/stocks/history',   \App\Sku\Controller\GetProductSkuStockHistoryController::class);
Route::post('/skus/stocks/import',        \App\Sku\Controller\ImportProductSkuStockController::class);

Route::get('/skus/{id}/images',                      \App\Sku\Controller\ListProductSkuImageController::class);
Route::post('/skus/{id}/images',                     \App\Sku\Controller\CreateProductSkuImageController::class);
// /skus/{id}/image-file/{path} は <img src> から呼ばれるため認証不要として
// routes/api.php に直接定義した（auth.jwt の外）。

