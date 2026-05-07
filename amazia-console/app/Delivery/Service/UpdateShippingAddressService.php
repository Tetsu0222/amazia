<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の PATCH /api/deliveries/{id}/address を呼び出す Service。
 */
class UpdateShippingAddressService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function update(int $deliveryId, int $shippingAddressId, ?string $reason, int $userId): Response
    {
        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->patch("{$this->baseUrl}/deliveries/{$deliveryId}/address", [
            'shippingAddressId' => $shippingAddressId,
            'reason'            => $reason,
        ]);
    }
}
