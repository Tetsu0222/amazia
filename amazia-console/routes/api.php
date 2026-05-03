<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

Route::get('/user', function (Request $request) {
    return $request->user();
})->middleware('auth:sanctum');

Route::get('/sales', [\App\Http\Controllers\SalesController::class, 'index']);
Route::get('/sales/inventory', [\App\Http\Controllers\SalesController::class, 'checkInventory']);

Route::post('/products/import', [\App\Http\Controllers\ImportController::class, 'importProducts']);

Route::get('/products', [\App\Http\Controllers\ProductController::class, 'index']);
Route::get('/admin/products', [\App\Http\Controllers\ProductController::class, 'adminIndex']);
Route::get('/product-statuses', [\App\Http\Controllers\ProductController::class, 'statuses']);
Route::get('/products/{id}', [\App\Http\Controllers\ProductController::class, 'show']);
Route::post('/products', [\App\Http\Controllers\ProductController::class, 'store']);
Route::put('/products/{id}', [\App\Http\Controllers\ProductController::class, 'update']);
Route::delete('/products/{id}', [\App\Http\Controllers\ProductController::class, 'destroy']);
Route::delete('/products', [\App\Http\Controllers\ProductController::class, 'bulkDestroy']);
Route::patch('/products/bulk-stock', [\App\Http\Controllers\ProductController::class, 'bulkUpdateStock']);
