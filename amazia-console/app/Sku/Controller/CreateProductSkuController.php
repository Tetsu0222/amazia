<?php

namespace App\Sku\Controller;

use App\Sku\Service\CreateProductSkuService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class CreateProductSkuController extends Controller
{
    public function __construct(private CreateProductSkuService $createProductSkuService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->createProductSkuService->create($id, $request->all());
        return response()->json($response->json(), $response->status());
    }
}
