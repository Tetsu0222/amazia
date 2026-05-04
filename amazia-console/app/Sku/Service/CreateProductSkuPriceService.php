<?php

namespace App\Sku\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class CreateProductSkuPriceService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function create(int $skuId, array $data): Response
    {
        if (!array_key_exists('price', $data) || ($data['price'] === null || $data['price'] === '')) {
            abort(400, '価格は必須です');
        }

        return Http::post("{$this->coreBaseUrl}/skus/{$skuId}/prices", [
            'price'     => $data['price'],
            'startDate' => $data['startDate'] ?? null,
            'endDate'   => $data['endDate']   ?? null,
        ]);
    }
}
