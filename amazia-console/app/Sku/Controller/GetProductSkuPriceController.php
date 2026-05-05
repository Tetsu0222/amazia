<?php

namespace App\Sku\Controller;

use App\Sku\Service\GetProductSkuPriceService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class GetProductSkuPriceController extends Controller
{
    public function __construct(private GetProductSkuPriceService $getProductSkuPriceService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->getProductSkuPriceService->get($id);
        if ($response->status() === 404) {
            return response()->json(null, 200);
        }
        return response()->json($response->json(), $response->status());
    }
}
