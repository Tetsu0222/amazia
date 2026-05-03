<?php

namespace App\Sales\Service;

use Illuminate\Support\Facades\Http;

class GetInventoryService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function getInventory(): array
    {
        $response = Http::get("{$this->coreBaseUrl}/inventory");
        return $response->json();
    }
}
