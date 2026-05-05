<?php

use App\Auth\Controller\LoginController;
use App\Auth\Controller\RefreshTokenController;
use App\Auth\Controller\PasswordResetController;
use Illuminate\Support\Facades\Route;

Route::post('/auth/login',                    LoginController::class);
Route::post('/auth/refresh',                  RefreshTokenController::class);
Route::post('/auth/password/reset/request',   [PasswordResetController::class, 'request']);
Route::post('/auth/password/reset/confirm',   [PasswordResetController::class, 'confirm']);
