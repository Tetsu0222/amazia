<?php

namespace App\Inbound\Controller;

use App\Http\Controllers\Controller;
use App\Inbound\Service\RegisterInboundService;
use Illuminate\Http\Request;

/**
 * 入荷登録 Controller（POST /api/inbounds）。
 *
 * RRRR-5：warehouse_id をリクエストに含めない（バックエンド DEFAULT=1 自動セット）。
 */
class RegisterInboundController extends Controller
{
    private RegisterInboundService $service;

    public function __construct(RegisterInboundService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $userId = (int) $request->get('auth_user_id');
        $role   = (string) $request->get('auth_role');

        if (!in_array($role, config('app.auth.approver_roles'), true)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        // phase16 Step3.1：inboundedAt は任意化。未指定時は Core 側で本日付を強制セットする。
        $validated = $request->validate([
            'productId'   => 'required|integer|min:1',
            'skuId'       => 'required|integer|min:1',
            'quantity'    => 'required|integer|min:1',
            'inboundedAt' => 'nullable|date',
            'supplierId'  => 'nullable|integer|min:1',
        ]);

        $response = $this->service->register($validated, $userId);
        return response()->json($response->json(), $response->status());
    }
}
