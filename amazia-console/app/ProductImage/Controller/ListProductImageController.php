<?php

namespace App\ProductImage\Controller;

use App\ProductImage\Service\ListProductImageService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class ListProductImageController extends Controller
{
    public function __construct(private ListProductImageService $listProductImageService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->listProductImageService->list($id);
        return response()->json($response->json(), $response->status());
    }
}
