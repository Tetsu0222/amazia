<?php

namespace App\Sku\Controller;

use App\Sku\Service\ListSkuPriceHistoryService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class ListSkuPriceHistoryController extends Controller
{
    public function __construct(private ListSkuPriceHistoryService $service) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->service->list($id);
        return response()->json($response->json(), $response->status());
    }
}
