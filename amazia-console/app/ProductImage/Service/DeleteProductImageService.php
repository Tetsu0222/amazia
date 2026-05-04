<?php

namespace App\ProductImage\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class DeleteProductImageService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function delete(int $imageId): Response
    {
        return Http::delete("{$this->coreBaseUrl}/product-images/{$imageId}");
    }
}
