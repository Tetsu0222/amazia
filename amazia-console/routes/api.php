<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

Route::get('/user', function (Request $request) {
    return $request->user();
})->middleware('auth:sanctum');

require __DIR__.'/api/Product.php';
require __DIR__.'/api/ProductImage.php';
require __DIR__.'/api/Sku.php';
require __DIR__.'/api/Sales.php';
