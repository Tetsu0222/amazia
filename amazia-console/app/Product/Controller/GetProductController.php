<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\GetProductService;

class GetProductController extends Controller
{
    private GetProductService $service;

    public function __construct(GetProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke(int $id)
    {
        $response = $this->service->getById($id);
        return response()->json($response->json(), $response->status());
    }
}
