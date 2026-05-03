<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\DeleteProductService;

class DeleteProductController extends Controller
{
    private DeleteProductService $service;

    public function __construct(DeleteProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke(int $id)
    {
        $response = $this->service->delete($id);

        if ($response->status() === 204) {
            return response()->noContent();
        }

        return response()->json($response->json(), $response->status());
    }
}
