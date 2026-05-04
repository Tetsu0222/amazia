<?php

namespace App\Sku\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class CreateProductSkuService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function create(int $productId, array $data): Response
    {
        if (empty($data['color']) || empty($data['size'])) {
            abort(400, '色とサイズは必須です');
        }

        return Http::post("{$this->coreBaseUrl}/products/{$productId}/skus", [
            'color' => $data['color'],
            'size'  => $data['size'],
        ]);
    }
}
