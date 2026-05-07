<?php

namespace App\Preorder\Controller;

use App\Http\Controllers\Controller;
use App\Preorder\Service\ListPreorderService;

/**
 * 予約管理一覧取得 Controller（GET /api/preorders）。
 *
 * Core の GET /api/products/preorders を中継し、Console フロントへ JSON を返す。
 * 認証は routes/api.php 側のミドルウェアで担保。
 */
class ListPreorderController extends Controller
{
    private ListPreorderService $service;

    public function __construct(ListPreorderService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->list();
        return response()->json($response->json(), $response->status());
    }
}
