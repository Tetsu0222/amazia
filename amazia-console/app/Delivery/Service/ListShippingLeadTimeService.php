<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/shipping-lead-times[?shippingMethodId=N] を呼び出す Service（フェーズX-5）。
 */
class ListShippingLeadTimeService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(?int $shippingMethodId = null): Response
    {
        $query = $shippingMethodId === null ? [] : ['shippingMethodId' => $shippingMethodId];
        return Http::get("{$this->baseUrl}/shipping-lead-times", $query);
    }
}
