<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\AdminListProductService;

class AdminListProductController extends Controller
{
    private AdminListProductService $service;

    public function __construct(AdminListProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->getAll();
        return response()->json($response->json(), $response->status());
    }
}
