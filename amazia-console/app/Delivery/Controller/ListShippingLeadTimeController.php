<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\ListShippingLeadTimeService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * 都道府県別リードタイム一覧 Controller（GET /api/shipping-lead-times[?shippingMethodId=N]）。
 *
 * 設計書 §機能詳細：approver_roles（supervisor 以上）のみ閲覧可。
 */
class ListShippingLeadTimeController extends Controller
{
    private ListShippingLeadTimeService $service;

    public function __construct(ListShippingLeadTimeService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $role = (string) $request->get('auth_role');
        if (!in_array($role, config('app.auth.approver_roles'), true)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $shippingMethodId = $request->query('shippingMethodId');
        $response = $this->service->list($shippingMethodId === null ? null : (int) $shippingMethodId);
        return response()->json($response->json(), $response->status());
    }
}
