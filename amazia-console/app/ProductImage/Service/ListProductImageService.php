<?php

namespace App\ProductImage\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ListProductImageService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function list(int $productId): Response
    {
        return Http::get("{$this->coreBaseUrl}/products/{$productId}/images");
    }
}
