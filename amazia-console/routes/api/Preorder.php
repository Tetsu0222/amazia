<?php

use Illuminate\Support\Facades\Route;

// フェーズ16 Step 2: 予約管理画面向け Pass-through
Route::get('/preorders', \App\Preorder\Controller\ListPreorderController::class);
