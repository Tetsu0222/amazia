<?php

namespace App\Inquiry\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ18: 問い合わせ返信投稿 Pass-through Service。
 *
 * Core 側 ReplyInquiryService が operation_logs を書き込む（既存 phase14 / 15 / 17 と同方針）。
 */
class ReplyInquiryService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function reply(?string $userId, int $inquiryId, array $payload): Response
    {
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->post("{$this->baseUrl}/console/inquiries/{$inquiryId}/messages", $payload);
    }
}
