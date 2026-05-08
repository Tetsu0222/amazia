<?php

namespace App\ScheduledPrice\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ17 Step 5.5-2（設計書 §13.5.2）：予約価格 取得 Pass-through。
 */
class GetScheduledSkuPriceService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function get(int $skuId): Response
    {
        return Http::timeout(10)->get("{$this->coreBaseUrl}/skus/{$skuId}/scheduled-price");
    }
}
