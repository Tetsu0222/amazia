<?php

namespace App\ProductImage\Controller;

use App\ProductImage\Service\DeleteProductImageService;
use Illuminate\Http\Response;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class DeleteProductImageController extends Controller
{
    public function __construct(private DeleteProductImageService $deleteProductImageService) {}

    public function __invoke(Request $request, int $id): Response
    {
        $coreResponse = $this->deleteProductImageService->delete($id);
        return response(null, $coreResponse->status());
    }
}
