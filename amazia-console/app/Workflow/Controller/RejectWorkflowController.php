<?php

namespace App\Workflow\Controller;

use App\Http\Controllers\Controller;
use App\Workflow\Service\RejectWorkflowService;
use Illuminate\Http\Request;

class RejectWorkflowController extends Controller
{
    private RejectWorkflowService $service;

    public function __construct(RejectWorkflowService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id, int $stepNumber)
    {
        $userId = (int) $request->get('auth_user_id');
        $role   = (string) $request->get('auth_role');

        if (!in_array($role, config('app.auth.approver_roles'), true)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $response = $this->service->reject($id, $stepNumber, $userId, $role);
        return response()->json($response->json(), $response->status());
    }
}
