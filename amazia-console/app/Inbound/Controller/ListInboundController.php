<?php

namespace App\Inbound\Controller;

use App\Http\Controllers\Controller;
use App\Inbound\Service\ListInboundService;
use Illuminate\Http\Request;

/**
 * 入荷一覧 Controller（GET /api/inbounds[?productId=N]）。
 */
class ListInboundController extends Controller
{
    private ListInboundService $service;

    public function __construct(ListInboundService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $productId = $request->query('productId');
        $response = $this->service->list($productId !== null ? (int) $productId : null);
        return response()->json($response->json(), $response->status());
    }
}
