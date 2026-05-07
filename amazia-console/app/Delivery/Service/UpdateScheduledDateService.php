<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の PATCH /api/deliveries/{id}/scheduled-date を呼び出す Service。
 *
 * [manual] プレフィックスは Core 側 Service が自動付与するため、
 * Console 側では reason を素で渡す。
 */
class UpdateScheduledDateService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function update(int $deliveryId, string $scheduledDate, ?string $reason, int $userId): Response
    {
        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->patch("{$this->baseUrl}/deliveries/{$deliveryId}/scheduled-date", [
            'scheduledDate' => $scheduledDate,
            'reason'        => $reason,
        ]);
    }
}
