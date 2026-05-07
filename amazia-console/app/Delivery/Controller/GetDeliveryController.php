<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\GetDeliveryService;
use App\Http\Controllers\Controller;

/**
 * 配送詳細 Controller（GET /api/deliveries/{id}）。
 */
class GetDeliveryController extends Controller
{
    private GetDeliveryService $service;

    public function __construct(GetDeliveryService $service)
    {
        $this->service = $service;
    }

    public function __invoke(int $id)
    {
        $response = $this->service->get($id);
        return response()->json($response->json(), $response->status());
    }
}
