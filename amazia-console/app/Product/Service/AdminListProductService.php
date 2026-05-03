<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class AdminListProductService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function getAll(): Response
    {
        return Http::get("{$this->coreBaseUrl}/admin/products");
    }
}
