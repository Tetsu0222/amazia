<?php

namespace App\SalesReturn\Controller;

use App\Http\Controllers\Controller;
use App\SalesReturn\Service\RejectSalesReturnService;
use Illuminate\Http\Request;

/**
 * 返品却下 Controller（POST /api/sales-returns/{id}/reject）。
 *
 * 承認可ロール: config('app.auth.approver_roles')（既存ワークフローと同じ運用）。
 * Core への中継時に X-User-Id ヘッダを付与する。
 */
class RejectSalesReturnController extends Controller
{
    private RejectSalesReturnService $service;

    public function __construct(RejectSalesReturnService $service)
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

        $response = $this->service->reject($id, $userId);
        return response()->json($response->json(), $response->status());
    }
}
