<?php

namespace App\Sku\Controller;

use App\Sku\Service\ListProductSkuService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class ListProductSkuController extends Controller
{
    public function __construct(private ListProductSkuService $listProductSkuService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->listProductSkuService->list($id);
        return response()->json($response->json(), $response->status());
    }
}
