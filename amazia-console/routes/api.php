<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

Route::get('/user', function (Request $request) {
    return $request->user();
})->middleware('auth:sanctum');

Route::get('/sales', [\App\Http\Controllers\SalesController::class, 'index']);
Route::get('/sales/inventory', [\App\Http\Controllers\SalesController::class, 'checkInventory']);

Route::get('/products', [\App\Http\Controllers\ProductController::class, 'index']);
Route::get('/products/{id}', [\App\Http\Controllers\ProductController::class, 'show']);
Route::post('/products', [\App\Http\Controllers\ProductController::class, 'store']);
Route::put('/products/{id}', [\App\Http\Controllers\ProductController::class, 'update']);
Route::delete('/products/{id}', [\App\Http\Controllers\ProductController::class, 'destroy']);

Route::post('/products/import', [\App\Http\Controllers\ImportController::class, 'importProducts']);
