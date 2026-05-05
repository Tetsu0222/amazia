<?php

namespace App\Sku\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class GetProductSkuPriceService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function get(int $skuId): Response
    {
        return Http::timeout(10)->get("{$this->coreBaseUrl}/skus/{$skuId}/prices");
    }
}
