<?php

use Illuminate\Support\Facades\Route;

Route::get('/operation-logs', \App\OperationLog\Controller\ListOperationLogController::class);
