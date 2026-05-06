<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class UpdateProductService
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function update(int $id, array $data): Response
    {
        return Http::put("{$this->coreApiUrl}/{$id}", $this->buildPayload($data));
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
        ];
    }
}
