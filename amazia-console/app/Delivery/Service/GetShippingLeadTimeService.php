<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/shipping-lead-times/{id} を呼び出す Service（フェーズX-5）。
 */
class GetShippingLeadTimeService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function get(int $id): Response
    {
        return Http::get("{$this->baseUrl}/shipping-lead-times/{$id}");
    }
}
