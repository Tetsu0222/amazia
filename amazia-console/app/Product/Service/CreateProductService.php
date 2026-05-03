<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class CreateProductService
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function create(array $data): Response
    {
        if (empty($data['name']) || is_null($data['price'] ?? null) || is_null($data['stock'] ?? null)) {
            abort(400, '必須項目が不足しています');
        }

        return Http::post($this->coreApiUrl, $this->buildPayload($data));
    }

    private function buildPayload(array $data): array
    {
        return [
            'name'         => $data['name']        ?? null,
            'description'  => $data['description'] ?? null,
            'price'        => $data['price']        ?? null,
            'stock'        => $data['stock']        ?? null,
            'statusCode'   => $data['statusCode']   ?? null,
            'publishStart' => $data['publishStart'] ?? null,
            'publishEnd'   => $data['publishEnd']   ?? null,
        ];
    }
}
