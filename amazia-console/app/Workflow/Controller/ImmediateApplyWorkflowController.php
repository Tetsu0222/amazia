<?php

namespace App\Workflow\Controller;

use App\Http\Controllers\Controller;
use App\Workflow\Request\CreateWorkflowFormRequest;
use App\Workflow\Service\ImmediateApplyWorkflowService;

class ImmediateApplyWorkflowController extends Controller
{
    private ImmediateApplyWorkflowService $service;

    public function __construct(ImmediateApplyWorkflowService $service)
    {
        $this->service = $service;
    }

    public function __invoke(CreateWorkflowFormRequest $request)
    {
        $role = (string) $request->get('auth_role');
        if (!in_array($role, config('app.auth.approver_roles'), true)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $payload = $request->only([
            'targetType', 'targetId', 'fields', 'meta',
        ]);

        $response = $this->service->apply($payload, $role);
        return response()->json($response->json(), $response->status());
    }
}
