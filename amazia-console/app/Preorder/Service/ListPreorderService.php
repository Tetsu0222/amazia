<?php

namespace App\Preorder\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/products/preorders を呼び出し、予約管理画面向け一覧を取得する Service。
 *
 * 設計書: docs/design/phase11_20/phase16_ui_ux_improvement.md §2-4-5
 * Core 側エンドポイント: ListPreorderProductsController（com.example.product.controller）
 */
class ListPreorderService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(): Response
    {
        return Http::get("{$this->baseUrl}/products/preorders");
    }
}
