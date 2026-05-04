<?php

namespace App\Sku\Controller;

use App\Sku\Service\CreateProductSkuImageService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class CreateProductSkuImageController extends Controller
{
    public function __construct(private CreateProductSkuImageService $createProductSkuImageService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->createProductSkuImageService->create($id, $request->file('image'));
        return response()->json($response->json(), $response->status());
    }
}
