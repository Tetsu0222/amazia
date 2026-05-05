<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\GetMarketProductService;

class GetMarketProductController extends Controller
{
    private GetMarketProductService $service;

    public function __construct(GetMarketProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke(int $id)
    {
        $response = $this->service->getMarketDetail($id);
        return response()->json($response->json(), $response->status());
    }
}
