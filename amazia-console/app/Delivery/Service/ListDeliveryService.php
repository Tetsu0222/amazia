<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/deliveries[?shippingStatusId=N] を呼び出して配送一覧を取得する Service。
 */
class ListDeliveryService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(?int $shippingStatusId = null): Response
    {
        $query = [];
        if ($shippingStatusId !== null) {
            $query['shippingStatusId'] = $shippingStatusId;
        }
        return Http::get("{$this->baseUrl}/deliveries", $query);
    }
}
