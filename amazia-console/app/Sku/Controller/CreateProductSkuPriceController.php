<?php

namespace App\Sku\Controller;

use App\Sku\Service\CreateProductSkuPriceService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class CreateProductSkuPriceController extends Controller
{
    public function __construct(private CreateProductSkuPriceService $createProductSkuPriceService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->createProductSkuPriceService->create($id, $request->all());
        return response()->json($response->json(), $response->status());
    }
}
