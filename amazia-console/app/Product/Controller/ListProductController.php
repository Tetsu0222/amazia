<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\ListProductService;

class ListProductController extends Controller
{
    private ListProductService $service;

    public function __construct(ListProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->getPublished();
        return response()->json($response->json(), $response->status());
    }
}
