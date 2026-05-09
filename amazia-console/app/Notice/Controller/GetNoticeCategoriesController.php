<?php

namespace App\Notice\Controller;

use App\Http\Controllers\Controller;
use App\Notice\Service\GetNoticeCategoriesService;

/**
 * フェーズ19: お知らせ分類マスタ Controller（Console Pass-through）。
 * GET /api/notice-categories
 */
class GetNoticeCategoriesController extends Controller
{
    private GetNoticeCategoriesService $service;

    public function __construct(GetNoticeCategoriesService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->findAll();
        return response()->json($response->json(), $response->status());
    }
}
