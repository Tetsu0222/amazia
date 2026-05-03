<?php

namespace App\Product\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class GetProductStatusesService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function getStatuses(): Response
    {
        return Http::get("{$this->coreBaseUrl}/product-statuses");
    }
}
