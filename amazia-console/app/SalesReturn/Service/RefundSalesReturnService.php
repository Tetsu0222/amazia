<?php

namespace App\SalesReturn\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の POST /api/sales-returns/{id}/refund を呼び出し、返金完了処理を行う Service。
 *
 * 設計書: docs/design/phase11_20/phase14_shipping.md（r4） §Console 返品管理
 * Core 側エンドポイント: RefundSalesReturnController（com.example.salesreturn.controller）
 *
 * Core 側で在庫戻し（product_sku_stocks.quantity += quantity）と
 * product_sku_stock_transactions への return 記録、sales.shipping_status を RETURNED に
 * 同一トランザクションで実行する。
 */
class RefundSalesReturnService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function refund(int $salesReturnId, int $userId): Response
    {
        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->post("{$this->baseUrl}/sales-returns/{$salesReturnId}/refund");
    }
}
