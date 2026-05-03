<?php

namespace App\Http\Controllers;

use App\Services\SalesService;

class SalesController extends Controller
{
    private SalesService $salesService;

    public function __construct(SalesService $salesService)
    {
        $this->salesService = $salesService;
    }

    public function index()
    {
        return response()->json($this->salesService->getSales());
    }

    public function checkInventory()
    {
        return response()->json([
            'message'   => '在庫情報を取得しました',
            'inventory' => $this->salesService->getInventory(),
        ], 200, [], JSON_UNESCAPED_UNICODE);
    }
}
