<?php

use Illuminate\Support\Facades\Route;

Route::get('/products/{id}/images',        \App\ProductImage\Controller\ListProductImageController::class);
Route::post('/products/{id}/images',       \App\ProductImage\Controller\CreateProductImageController::class);
Route::put('/product-images/{id}/sort',    \App\ProductImage\Controller\UpdateProductImageSortController::class);
Route::delete('/product-images/{id}',      \App\ProductImage\Controller\DeleteProductImageController::class);
