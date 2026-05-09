<?php

namespace App\Inquiry\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ18: 問い合わせ一覧 Pass-through Service。
 * Core の GET /api/console/inquiries を呼び出す。
 */
class ListInquiryService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(?string $userId, array $filters): Response
    {
        $query = array_filter($filters, fn ($v) => $v !== null && $v !== '');
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->get("{$this->baseUrl}/console/inquiries", $query);
    }
}
