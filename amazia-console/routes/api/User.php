<?php

use App\User\Controller\ListUserController;
use App\User\Controller\CreateUserController;
use App\User\Controller\UpdateUserController;
use Illuminate\Support\Facades\Route;

Route::middleware('check.permission:users.list')->group(function () {
    Route::get('/users', ListUserController::class);
});

Route::middleware('check.permission:users.create')->group(function () {
    Route::post('/users', CreateUserController::class);
});

Route::middleware('check.permission:users.edit')->group(function () {
    Route::put('/users/{id}', UpdateUserController::class);
});
