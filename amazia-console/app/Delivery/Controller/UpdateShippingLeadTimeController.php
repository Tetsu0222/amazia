<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\UpdateShippingLeadTimeService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * 都道府県別リードタイム更新 Controller（PATCH /api/shipping-lead-times/{id}）。
 *
 * 設計書 §機能詳細：approver_roles（supervisor 以上）のみ更新可。
 * lead_time_days = 0 は無効化運用として許容（設計書 §設計上の注意）。
 */
class UpdateShippingLeadTimeController extends Controller
{
    private UpdateShippingLeadTimeService $service;

    public function __construct(UpdateShippingLeadTimeService $service)
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
            'leadTimeDays' => 'required|integer|min:0',
        ]);

        $response = $this->service->update($id, (int) $validated['leadTimeDays'], $userId);
        return response()->json($response->json(), $response->status());
    }
}
