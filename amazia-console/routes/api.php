<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

require __DIR__.'/api/Auth.php';

// マーケット向け公開エンドポイント（認証不要）
Route::get('/products/market',      \App\Product\Controller\ListMarketProductController::class);
Route::get('/products/{id}/market', \App\Product\Controller\GetMarketProductController::class);

Route::middleware('auth.jwt')->group(function () {
    require __DIR__.'/api/Product.php';
    require __DIR__.'/api/ProductImage.php';
    require __DIR__.'/api/Sku.php';
    require __DIR__.'/api/Sales.php';
    require __DIR__.'/api/User.php';
});
