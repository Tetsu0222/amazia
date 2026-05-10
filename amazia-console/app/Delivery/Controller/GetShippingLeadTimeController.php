<?php

namespace App\Delivery\Controller;

use App\Delivery\Service\GetShippingLeadTimeService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * 都道府県別リードタイム詳細 Controller（GET /api/shipping-lead-times/{id}）。
 */
class GetShippingLeadTimeController extends Controller
{
    private GetShippingLeadTimeService $service;

    public function __construct(GetShippingLeadTimeService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $role = (string) $request->get('auth_role');
        if (!in_array($role, config('app.auth.approver_roles'), true)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $response = $this->service->get($id);
        return response()->json($response->json(), $response->status());
    }
}
