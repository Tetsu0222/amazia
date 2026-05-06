<?php

namespace App\OperationLog\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/operation-logs を呼び出し、操作履歴一覧を取得する Service。
 *
 * 設計書: docs/design/phase11_20/phase14_shipping.md（r4） §Console 操作履歴
 * Core 側エンドポイント: ListOperationLogController（com.example.operationlog.controller）
 *
 * 検索パラメータ（任意）：
 *   - screenName : 部分一致（例: "console.sales_return"）
 *   - apiName    : 部分一致（例: "/api/sales-returns"）
 *   - action     : 完全一致（例: "approve_sales_return"）
 */
class ListOperationLogService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(?string $screenName, ?string $apiName, ?string $action): Response
    {
        $query = array_filter([
            'screenName' => $screenName,
            'apiName'    => $apiName,
            'action'     => $action,
        ], fn ($v) => $v !== null && $v !== '');
        return Http::get("{$this->baseUrl}/operation-logs", $query);
    }
}
