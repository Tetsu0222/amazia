<?php

namespace App\Notice\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ19: お知らせ詳細 Pass-through Service。
 * Core の GET /api/notices/{id} を呼び出す。Console 視点で
 * include_unpublished / include_deleted を透過する。
 */
class GetNoticeService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function get(?string $userId, int $id, array $query = []): Response
    {
        $filtered = array_filter($query, fn ($v) => $v !== null && $v !== '');
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->get("{$this->baseUrl}/notices/{$id}", $filtered);
    }
}
