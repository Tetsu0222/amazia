<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\RegisterTrackingCodeService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * 追跡番号登録 Controller（PATCH /api/deliveries/{id}/tracking-code）。
 */
class RegisterTrackingCodeController extends Controller
{
    private RegisterTrackingCodeService $service;

    public function __construct(RegisterTrackingCodeService $service)
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
            'trackingCode' => 'required|string|max:100',
        ]);

        $response = $this->service->register($id, $validated['trackingCode'], $userId);
        return response()->json($response->json(), $response->status());
    }
}
