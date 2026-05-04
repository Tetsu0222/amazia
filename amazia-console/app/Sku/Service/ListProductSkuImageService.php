<?php

namespace App\Sku\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ListProductSkuImageService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function list(int $skuId): Response
    {
        return Http::get("{$this->coreBaseUrl}/skus/{$skuId}/images");
    }
}
