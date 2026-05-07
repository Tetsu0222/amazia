<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\UpdateScheduledDateService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * 配送予定日更新 Controller（PATCH /api/deliveries/{id}/scheduled-date）。
 *
 * [manual] プレフィックスは Core 側 Service が自動付与する（Console 側で reason を素で渡すだけ）。
 */
class UpdateScheduledDateController extends Controller
{
    private UpdateScheduledDateService $service;

    public function __construct(UpdateScheduledDateService $service)
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
            'scheduledDate' => 'required|date',
            'reason'        => 'nullable|string',
        ]);

        $response = $this->service->update(
            $id,
            $validated['scheduledDate'],
            $validated['reason'] ?? null,
            $userId,
        );
        return response()->json($response->json(), $response->status());
    }
}
