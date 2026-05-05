<?php

namespace App\Sku\Controller;

use Illuminate\Http\Response;
use Illuminate\Routing\Controller;
use Illuminate\Support\Facades\Http;

class ProxySkuImageController extends Controller
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function __invoke(int $skuId, string $path): Response
    {
        $response = Http::get("{$this->coreBaseUrl}/skus/{$skuId}/image-file/{$path}");

        if (!$response->successful()) {
            abort(404);
        }

        return response($response->body(), 200, [
            'Content-Type'  => 'image/png',
            'Cache-Control' => 'public, max-age=86400',
        ]);
    }
}
