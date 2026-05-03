<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\GetProductStatusesService;

class GetProductStatusesController extends Controller
{
    private GetProductStatusesService $service;

    public function __construct(GetProductStatusesService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->getStatuses();
        return response()->json($response->json(), $response->status());
    }
}
