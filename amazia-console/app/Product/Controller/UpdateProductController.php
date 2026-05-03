<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\UpdateProductService;
use Illuminate\Http\Request;

class UpdateProductController extends Controller
{
    private UpdateProductService $service;

    public function __construct(UpdateProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $response = $this->service->update($id, $request->all());
        return response()->json($response->json(), $response->status());
    }
}
