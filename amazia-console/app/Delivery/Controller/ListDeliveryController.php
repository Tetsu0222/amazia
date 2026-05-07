<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\ListDeliveryService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * 配送一覧 Controller（GET /api/deliveries[?shippingStatusId=N]）。
 */
class ListDeliveryController extends Controller
{
    private ListDeliveryService $service;

    public function __construct(ListDeliveryService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $statusId = $request->query('shippingStatusId');
        $response = $this->service->list($statusId !== null ? (int) $statusId : null);
        return response()->json($response->json(), $response->status());
    }
}
