<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/deliveries/{id} を呼び出して配送詳細を取得する Service。
 */
class GetDeliveryService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function get(int $id): Response
    {
        return Http::get("{$this->baseUrl}/deliveries/{$id}");
    }
}
