<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class GetProductService
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function getById(int $id): Response
    {
        return Http::get("{$this->coreApiUrl}/{$id}");
    }
}
