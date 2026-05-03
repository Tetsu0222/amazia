<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class BulkUpdateStockService
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function bulkUpdateStock(array $data): Response
    {
        return Http::patch("{$this->coreApiUrl}/bulk-stock", $data);
    }
}
