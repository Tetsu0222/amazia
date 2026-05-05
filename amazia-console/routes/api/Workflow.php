<?php

use Illuminate\Support\Facades\Route;

Route::get(   '/workflows',                                  \App\Workflow\Controller\ListWorkflowController::class);
Route::post(  '/workflows',                                  \App\Workflow\Controller\CreateWorkflowController::class);
Route::post(  '/workflows/immediate-apply',                  \App\Workflow\Controller\ImmediateApplyWorkflowController::class);
Route::get(   '/workflows/{id}',                             \App\Workflow\Controller\GetWorkflowController::class);
Route::post(  '/workflows/{id}/cancel',                      \App\Workflow\Controller\CancelWorkflowController::class);
Route::post(  '/workflows/{id}/steps/{stepNumber}/approve',  \App\Workflow\Controller\ApproveWorkflowController::class);
Route::post(  '/workflows/{id}/steps/{stepNumber}/reject',   \App\Workflow\Controller\RejectWorkflowController::class);
