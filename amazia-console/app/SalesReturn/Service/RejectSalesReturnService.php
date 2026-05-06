<?php

namespace App\SalesReturn\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の POST /api/sales-returns/{id}/reject を呼び出し、返品申請を却下する Service。
 *
 * 設計書: docs/design/phase11_20/phase14_shipping.md（r4） §Console 返品管理
 * Core 側エンドポイント: RejectSalesReturnController（com.example.salesreturn.controller）
 *
 * 却下では sales.shipping_status_id は DELIVERED のまま（再申請可）。
 */
class RejectSalesReturnService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function reject(int $salesReturnId, int $userId): Response
    {
        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->post("{$this->baseUrl}/sales-returns/{$salesReturnId}/reject");
    }
}
