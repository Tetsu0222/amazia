<?php

namespace App\Batch\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ17 Step 6-0 / 6-2: Core の PUT /api/console/batch/notifications/{id}/read を呼び出す Pass-through Service。
 * 設計書 §13.7.1。
 */
class MarkConsoleNotificationReadService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function markRead(?string $userId, int $id): Response
    {
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->put("{$this->baseUrl}/console/batch/notifications/{$id}/read");
    }
}
