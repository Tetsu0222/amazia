<?php

namespace App\Sales\Controller;

use App\Http\Controllers\Controller;
use App\Sales\Service\GetInventoryService;

class GetInventoryController extends Controller
{
    private GetInventoryService $service;

    public function __construct(GetInventoryService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        return response()->json([
            'message'   => '在庫情報を取得しました',
            'inventory' => $this->service->getInventory(),
        ], 200, [], JSON_UNESCAPED_UNICODE);
    }
}
