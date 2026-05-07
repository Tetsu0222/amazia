<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\ListShippingMethodService;
use App\Http\Controllers\Controller;

/**
 * 配送方法マスタ一覧 Controller（GET /api/shipping-methods）。
 * マスタ参照のため認可は読み取り権限のみで十分。
 */
class ListShippingMethodController extends Controller
{
    private ListShippingMethodService $service;

    public function __construct(ListShippingMethodService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        $response = $this->service->list();
        return response()->json($response->json(), $response->status());
    }
}
