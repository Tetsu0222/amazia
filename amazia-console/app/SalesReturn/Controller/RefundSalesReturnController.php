<?php

namespace App\SalesReturn\Controller;

use App\Http\Controllers\Controller;
use App\SalesReturn\Service\RefundSalesReturnService;
use Illuminate\Http\Request;

/**
 * 返金完了 Controller（POST /api/sales-returns/{id}/refund）。
 *
 * 承認可ロール: config('app.auth.approver_roles')（既存ワークフローと同じ運用）。
 * Core への中継時に X-User-Id ヘッダを付与する。
 */
class RefundSalesReturnController extends Controller
{
    private RefundSalesReturnService $service;

    public function __construct(RefundSalesReturnService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = (int) $request->get('auth_user_id');
        $role   = (string) $request->get('auth_role');

        if (!in_array($role, config('app.auth.approver_roles'), true)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $response = $this->service->refund($id, $userId);
        return response()->json($response->json(), $response->status());
    }
}
