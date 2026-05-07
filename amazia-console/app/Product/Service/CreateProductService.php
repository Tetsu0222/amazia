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
        if (empty($data['name'])) {
            abort(400, '商品名は必須です');
        }

        return Http::post($this->coreApiUrl, $this->buildPayload($data));
    }

    private function buildPayload(array $data): array
    {
        return [
            'name'              => $data['name']              ?? null,
            'description'       => $data['description']       ?? null,
            'statusCode'        => $data['statusCode']        ?? null,
            'publishStart'      => $data['publishStart']      ?? null,
            'publishEnd'        => $data['publishEnd']        ?? null,
            'releaseDate'       => $data['releaseDate']       ?? null,
            'preorderStartDate' => $data['preorderStartDate'] ?? null,
            'acceptPreorder'    => $data['acceptPreorder']    ?? false,
            'acceptBackorder'   => $data['acceptBackorder']   ?? false,
            'isActive'          => $data['isActive']          ?? true,
        ];
    }
}
