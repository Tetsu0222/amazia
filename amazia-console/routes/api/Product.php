<?php

use Illuminate\Support\Facades\Route;

Route::post('/products/import', \App\Product\Controller\ImportProductController::class);

Route::get('/products',              \App\Product\Controller\ListProductController::class);
Route::get('/admin/products',        \App\Product\Controller\AdminListProductController::class);
Route::get('/product-statuses',      \App\Product\Controller\GetProductStatusesController::class);
Route::get('/products/{id}',         \App\Product\Controller\GetProductController::class);
Route::post('/products',             \App\Product\Controller\CreateProductController::class);
Route::put('/products/{id}',         \App\Product\Controller\UpdateProductController::class);
Route::delete('/products/{id}',      \App\Product\Controller\DeleteProductController::class);
Route::delete('/products',           \App\Product\Controller\BulkDeleteProductController::class);
Route::patch('/products/bulk-stock', \App\Product\Controller\BulkUpdateStockController::class);
