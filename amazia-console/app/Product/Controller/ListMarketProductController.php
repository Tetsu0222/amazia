<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\ListMarketProductService;

class ListMarketProductController extends Controller
{
    private ListMarketProductService $service;

    public function __construct(ListMarketProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->listMarket();
        return response()->json($response->json(), $response->status());
    }
}
