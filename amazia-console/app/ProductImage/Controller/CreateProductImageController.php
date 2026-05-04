<?php

namespace App\ProductImage\Controller;

use App\ProductImage\Service\CreateProductImageService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class CreateProductImageController extends Controller
{
    public function __construct(private CreateProductImageService $createProductImageService) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->createProductImageService->create($id, $request->file('image'));
        return response()->json($response->json(), $response->status());
    }
}
