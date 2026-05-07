<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/shipping-methods を呼び出して配送方法マスタを取得する Service。
 */
class ListShippingMethodService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(): Response
    {
        return Http::get("{$this->baseUrl}/shipping-methods");
    }
}
