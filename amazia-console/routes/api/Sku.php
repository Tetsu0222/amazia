<?php

use Illuminate\Support\Facades\Route;

Route::get('/products/{id}/skus',         \App\Sku\Controller\ListProductSkuController::class);
Route::post('/products/{id}/skus',        \App\Sku\Controller\CreateProductSkuController::class);

Route::get('/skus/{id}/prices',           \App\Sku\Controller\GetProductSkuPriceController::class);
Route::post('/skus/{id}/prices',          \App\Sku\Controller\CreateProductSkuPriceController::class);

Route::get('/skus/{id}/stocks',           \App\Sku\Controller\GetProductSkuStockController::class);
Route::post('/skus/{id}/stocks/receive',  \App\Sku\Controller\ReceiveProductSkuStockController::class);
Route::get('/skus/{id}/stocks/history',   \App\Sku\Controller\GetProductSkuStockHistoryController::class);

Route::get('/skus/{id}/images',                      \App\Sku\Controller\ListProductSkuImageController::class);
Route::post('/skus/{id}/images',                     \App\Sku\Controller\CreateProductSkuImageController::class);
Route::get('/skus/{id}/image-file/{path}',           \App\Sku\Controller\ProxySkuImageController::class)->where('path', '.+');
