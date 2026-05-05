<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class GetMarketProductService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function getMarketDetail(int $id): Response
    {
        return Http::get("{$this->baseUrl}/products/{$id}/market");
    }
}
