<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class BulkDeleteProductService
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function bulkDelete(string $ids): Response
    {
        return Http::withOptions(['query' => ['ids' => $ids]])->delete($this->coreApiUrl);
    }
}
