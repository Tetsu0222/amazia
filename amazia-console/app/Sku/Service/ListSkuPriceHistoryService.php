<?php

namespace App\Sku\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ17 Step 5.5-2（設計書 §13.5.2）：SKU 価格履歴 取得 Pass-through。
 */
class ListSkuPriceHistoryService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function list(int $skuId): Response
    {
        return Http::timeout(10)->get("{$this->coreBaseUrl}/skus/{$skuId}/prices/history");
    }
}
