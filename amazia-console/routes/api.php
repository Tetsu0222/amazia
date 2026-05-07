<?php

use Illuminate\Support\Facades\Route;

require __DIR__.'/api/Auth.php';

// マーケット向け公開エンドポイント（認証不要）
Route::get('/products/market',      \App\Product\Controller\ListMarketProductController::class);
Route::get('/products/{id}/market', \App\Product\Controller\GetMarketProductController::class);

// 画像配信は <img src> から呼ばれるため Authorization ヘッダを付けられず、
// auth.jwt ミドルウェアの中に置くと必ず 401 になる。Market の /api/skus/*/image-file/*
// が公開なのと同じ思想で、Console 経由の image-file 取得も認証不要として扱う。
Route::get('/skus/{id}/image-file/{path}',
    \App\Sku\Controller\ProxySkuImageController::class
)->where('path', '.+');

Route::middleware('auth.jwt')->group(function () {
    require __DIR__.'/api/Product.php';
    require __DIR__.'/api/ProductImage.php';
    require __DIR__.'/api/Sku.php';
    require __DIR__.'/api/Sales.php';
    require __DIR__.'/api/Preorder.php';
    require __DIR__.'/api/User.php';
    require __DIR__.'/api/Workflow.php';
    require __DIR__.'/api/OperationLog.php';
    require __DIR__.'/api/Delivery.php';
    require __DIR__.'/api/Inbound.php';
});
