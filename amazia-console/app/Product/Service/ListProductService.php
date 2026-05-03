<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ListProductService
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function getPublished(): Response
    {
        return Http::get($this->coreApiUrl);
    }
}
