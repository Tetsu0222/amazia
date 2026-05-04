<?php

namespace App\ProductImage\Controller;

use App\ProductImage\Service\UpdateProductImageSortService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class UpdateProductImageSortController extends Controller
{
    public function __construct(private UpdateProductImageSortService $updateProductImageSortService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->updateProductImageSortService->updateSort($id, (int) $request->input('sortOrder'));
        return response()->json($response->json(), $response->status());
    }
}
