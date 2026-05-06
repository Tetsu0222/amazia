<?php

namespace App\SalesReturn\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の POST /api/sales-returns/{id}/approve を呼び出し、返品申請を承認する Service。
 *
 * 設計書: docs/design/phase11_20/phase14_shipping.md（r4） §Console 返品管理
 * Core 側エンドポイント: ApproveSalesReturnController（com.example.salesreturn.controller）
 *
 * Core 側で sales.shipping_status_id を RETURN_REQUESTED に更新する。
 */
class ApproveSalesReturnService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function approve(int $salesReturnId, int $userId): Response
    {
        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->post("{$this->baseUrl}/sales-returns/{$salesReturnId}/approve");
    }
}
