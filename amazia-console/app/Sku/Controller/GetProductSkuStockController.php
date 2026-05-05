<?php

namespace App\Sku\Controller;

use App\Sku\Service\GetProductSkuStockService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class GetProductSkuStockController extends Controller
{
    public function __construct(private GetProductSkuStockService $getProductSkuStockService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->getProductSkuStockService->getCurrent($id);
        if ($response->status() === 404) {
            return response()->json(null, 200);
        }
        return response()->json($response->json(), $response->status());
    }
}
