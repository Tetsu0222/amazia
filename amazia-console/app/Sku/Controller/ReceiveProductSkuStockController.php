<?php

namespace App\Sku\Controller;

use App\Sku\Service\ReceiveProductSkuStockService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class ReceiveProductSkuStockController extends Controller
{
    public function __construct(private ReceiveProductSkuStockService $receiveProductSkuStockService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->receiveProductSkuStockService->receive($id, $request->all());
        return response()->json($response->json(), $response->status());
    }
}
