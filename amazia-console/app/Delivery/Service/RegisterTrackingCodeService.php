<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の PATCH /api/deliveries/{id}/tracking-code を呼び出す Service。
 */
class RegisterTrackingCodeService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function register(int $deliveryId, string $trackingCode, int $userId): Response
    {
        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->patch("{$this->baseUrl}/deliveries/{$deliveryId}/tracking-code", [
            'trackingCode' => $trackingCode,
        ]);
    }
}
