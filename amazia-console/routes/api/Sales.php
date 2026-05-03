<?php

use Illuminate\Support\Facades\Route;

Route::get('/sales',           \App\Sales\Controller\GetSalesController::class);
Route::get('/sales/inventory', \App\Sales\Controller\GetInventoryController::class);
