<?php

namespace App\Delivery\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の PATCH /api/shipping-lead-times/{id} を呼び出す Service（フェーズX-5）。
 *
 * operation_logs 記録は Core 側 Service が担当（Console 側は中継のみ）。
 */
class UpdateShippingLeadTimeService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function update(int $id, int $leadTimeDays, int $userId): Response
    {
        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->patch("{$this->baseUrl}/shipping-lead-times/{$id}", [
            'leadTimeDays' => $leadTimeDays,
        ]);
    }
}
