<?php

namespace App\Notice\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ19: お知らせ新規作成 Pass-through Service。
 * Core の POST /api/notices を呼び出す。
 *
 * R19-2 / R19-5：publish_start / publish_end の時分秒補完は本 Service の責務。
 * - 'YYYY-MM-DD' 形式 → publish_start は 'YYYY-MM-DDT00:00:00'、publish_end は 'YYYY-MM-DDT23:59:59'
 * - すでに時分秒を含むなら値をそのまま透過する
 */
class CreateNoticeService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function create(?string $userId, array $payload): Response
    {
        $payload['publishStart'] = $this->normalizeStart($payload['publishStart'] ?? null);
        $payload['publishEnd']   = $this->normalizeEnd($payload['publishEnd'] ?? null);

        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->post("{$this->baseUrl}/notices", $payload);
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
