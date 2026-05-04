<?php

namespace App\Sku\Controller;

use App\Sku\Service\ListProductSkuImageService;
use Illuminate\Http\JsonResponse;
use Illuminate\Routing\Controller;

class ListProductSkuImageController extends Controller
{
    public function __construct(private ListProductSkuImageService $listProductSkuImageService) {}

    public function __invoke(int $id): JsonResponse
    {
        $response = $this->listProductSkuImageService->list($id);
        return response()->json($response->json(), $response->status());
    }
}
