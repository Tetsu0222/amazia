<?php

namespace App\Notice\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ19: お知らせ編集 Pass-through Service。
 * Core の PUT /api/notices/{id} を呼び出す。時分秒補完は CreateNoticeService と同方針。
 */
class UpdateNoticeService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function update(?string $userId, int $id, array $payload): Response
    {
        $payload['publishStart'] = $this->normalizeStart($payload['publishStart'] ?? null);
        $payload['publishEnd']   = $this->normalizeEnd($payload['publishEnd'] ?? null);

        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->put("{$this->baseUrl}/notices/{$id}", $payload);
    }

    private function normalizeStart(?string $value): ?string
    {
        if ($value === null) {
            return null;
        }
        return preg_match('/^\d{4}-\d{2}-\d{2}$/', $value)
            ? $value.'T00:00:00'
            : $value;
    }

    private function normalizeEnd(?string $value): ?string
    {
        if ($value === null) {
            return null;
        }
        return preg_match('/^\d{4}-\d{2}-\d{2}$/', $value)
            ? $value.'T23:59:59'
            : $value;
    }
}
