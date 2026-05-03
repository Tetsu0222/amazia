<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\BulkDeleteProductService;
use Illuminate\Http\Request;

class BulkDeleteProductController extends Controller
{
    private BulkDeleteProductService $service;

    public function __construct(BulkDeleteProductService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $response = $this->service->bulkDelete($request->input('ids'));

        if ($response->status() === 204) {
            return response()->noContent();
        }

        return response()->json($response->json(), $response->status());
    }
}
