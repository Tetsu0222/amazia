<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\CreateProductService;
use Illuminate\Http\Request;

class CreateProductController extends Controller
{
    private CreateProductService $service;

    public function __construct(CreateProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $response = $this->service->create($request->all());
        return response()->json($response->json(), $response->status());
    }
}
