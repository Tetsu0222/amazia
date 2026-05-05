<?php

namespace App\Sku\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class GetProductSkuStockService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function getCurrent(int $skuId): Response
    {
        return Http::timeout(10)->get("{$this->coreBaseUrl}/skus/{$skuId}/stocks");
    }

    public function getHistory(int $skuId): Response
    {
        return Http::timeout(10)->get("{$this->coreBaseUrl}/skus/{$skuId}/stocks/history");
    }
}
