<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class DeleteProductService
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function delete(int $id): Response
    {
        return Http::delete("{$this->coreApiUrl}/{$id}");
    }
}
