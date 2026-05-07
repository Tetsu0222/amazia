<?php

namespace App\Inbound\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の POST /api/inbounds を呼び出して入荷を登録する Service。
 *
 * RRRR-5：warehouse_id はリクエストに含めず、Core 側がデフォルト倉庫を自動セット。
 */
class RegisterInboundService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function register(array $payload, int $userId): Response
    {
        // warehouse_id 系のキーが入っていても明示的に剥がす（防御的）
        unset($payload['warehouseId'], $payload['warehouse_id']);

        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->post("{$this->baseUrl}/inbounds", $payload);
    }
}
