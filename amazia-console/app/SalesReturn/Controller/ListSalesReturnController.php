<?php

namespace App\SalesReturn\Controller;

use App\Http\Controllers\Controller;
use App\SalesReturn\Service\ListSalesReturnService;

/**
 * 返品申請一覧取得 Controller（GET /api/sales-returns）。
 *
 * Core の GET /api/sales-returns を中継し、Console フロントへ JSON を返す。
 * 認証は routes/api.php 側のミドルウェアで担保。
 */
class ListSalesReturnController extends Controller
{
    private ListSalesReturnService $service;

    public function __construct(ListSalesReturnService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->list();
        return response()->json($response->json(), $response->status());
    }
}
