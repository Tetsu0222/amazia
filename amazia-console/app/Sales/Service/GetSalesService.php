<?php

namespace App\Sales\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/sales を呼び出し、管理画面向け売上一覧を取得する Service。
 *
 * 設計書: docs/design/phase11_20/phase14_shipping.md（r4） §Console 売上管理
 * Core 側エンドポイント: ListSalesController（com.example.sales.controller）
 */
class GetSalesService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(): Response
    {
        return Http::get("{$this->baseUrl}/sales");
    }
}
