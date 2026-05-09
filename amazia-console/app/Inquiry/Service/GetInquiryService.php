<?php

namespace App\Inquiry\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ18: 問い合わせ詳細 Pass-through Service。
 */
class GetInquiryService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function get(?string $userId, int $id): Response
    {
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->get("{$this->baseUrl}/console/inquiries/{$id}");
    }
}
