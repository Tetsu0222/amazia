<?php

use Illuminate\Support\Facades\Route;

// フェーズ15 入荷管理（Console 中継）
Route::get( '/inbounds', \App\Inbound\Controller\ListInboundController::class);
Route::post('/inbounds', \App\Inbound\Controller\RegisterInboundController::class);
