<?php

namespace App\SalesReturn\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/sales-returns を呼び出し、管理画面向け返品申請一覧を取得する Service。
 *
 * 設計書: docs/design/phase11_20/phase14_shipping.md（r4） §Console 返品管理
 * Core 側エンドポイント: ListSalesReturnController（com.example.salesreturn.controller）
 */
class ListSalesReturnService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(): Response
    {
        return Http::get("{$this->baseUrl}/sales-returns");
    }
}
