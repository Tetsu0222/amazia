<?php

namespace App\Inquiry\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ18: 問い合わせステータス変更 Pass-through Service。
 */
class UpdateInquiryStatusService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function update(?string $userId, int $inquiryId, array $payload): Response
    {
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->patch("{$this->baseUrl}/console/inquiries/{$inquiryId}/status", $payload);
    }
}
