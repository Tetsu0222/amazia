<?php

namespace App\Sales\Controller;

use App\Http\Controllers\Controller;
use App\Sales\Service\GetSalesService;

/**
 * 売上一覧取得 Controller（GET /api/sales）。
 *
 * Core の GET /api/sales を中継し、Console フロントへ JSON を返す。
 * 認証は routes/api.php 側のミドルウェアで担保。
 */
class GetSalesController extends Controller
{
    private GetSalesService $service;

    public function __construct(GetSalesService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->list();
        return response()->json($response->json(), $response->status());
    }
}
