<?php

namespace App\Sku\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ReceiveProductSkuStockService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function receive(int $skuId, array $data): Response
    {
        if (!isset($data['quantity'])) {
            abort(400, '数量は必須です');
        }

        return Http::post("{$this->coreBaseUrl}/skus/{$skuId}/stocks/receive", [
            'quantity' => $data['quantity'],
        ]);
    }
}
