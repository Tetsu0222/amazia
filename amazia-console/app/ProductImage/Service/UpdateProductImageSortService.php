<?php

namespace App\ProductImage\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class UpdateProductImageSortService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function updateSort(int $imageId, int $sortOrder): Response
    {
        return Http::put("{$this->coreBaseUrl}/product-images/{$imageId}/sort", [
            'sortOrder' => $sortOrder,
        ]);
    }
}
