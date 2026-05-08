<?php

namespace App\ScheduledPrice\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ17 Step 5.5-2（設計書 §13.5.2）：予約価格 UPSERT Pass-through。
 *
 * リクエスト：{ scheduledPrice, applyDate }
 * Core 側で applyDate が今日より前なら 422 を返す。
 */
class RegisterScheduledSkuPriceService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function upsert(int $skuId, array $data): Response
    {
        if (!array_key_exists('scheduledPrice', $data)
            || $data['scheduledPrice'] === null
            || $data['scheduledPrice'] === '') {
            abort(400, 'scheduledPrice は必須です');
        }
        if (!array_key_exists('applyDate', $data)
            || $data['applyDate'] === null
            || $data['applyDate'] === '') {
            abort(400, 'applyDate は必須です');
        }

        return Http::timeout(10)->put("{$this->coreBaseUrl}/skus/{$skuId}/scheduled-price", [
            'scheduledPrice' => $data['scheduledPrice'],
            'applyDate'      => $data['applyDate'],
        ]);
    }
}
