<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\BulkUpdateStockService;
use Illuminate\Http\Request;

class BulkUpdateStockController extends Controller
{
    private BulkUpdateStockService $service;

    public function __construct(BulkUpdateStockService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $response = $this->service->bulkUpdateStock($request->all());
        return response()->json($response->json(), $response->status());
    }
}
