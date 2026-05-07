<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\UpdateShippingStatusService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * 配送ステータス更新 Controller（PATCH /api/deliveries/{id}/status）。
 *
 * 承認可ロール: config('app.auth.approver_roles')。
 */
class UpdateShippingStatusController extends Controller
{
    private UpdateShippingStatusService $service;

    public function __construct(UpdateShippingStatusService $service)
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

        $validated = $request->validate([
            'shippingStatusId' => 'required|integer|min:1',
            'reason'           => 'nullable|string',
        ]);

        $response = $this->service->update(
            $id,
            (int) $validated['shippingStatusId'],
            $validated['reason'] ?? null,
            $userId,
        );
        return response()->json($response->json(), $response->status());
    }
}
