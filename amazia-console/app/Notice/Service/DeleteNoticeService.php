<?php

namespace App\Notice\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ19: お知らせ論理削除 Pass-through Service。
 * Core の DELETE /api/notices/{id} を呼び出す。
 */
class DeleteNoticeService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function delete(?string $userId, int $id): Response
    {
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->delete("{$this->baseUrl}/notices/{$id}");
    }
}
