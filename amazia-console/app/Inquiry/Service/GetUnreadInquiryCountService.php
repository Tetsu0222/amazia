<?php

namespace App\Inquiry\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ18: ベルマーク用未対応件数の Pass-through Service。
 * Core の GET /api/console/inquiries/unread-count を呼び出す。
 */
class GetUnreadInquiryCountService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function get(?string $userId): Response
    {
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->get("{$this->baseUrl}/console/inquiries/unread-count");
    }
}
