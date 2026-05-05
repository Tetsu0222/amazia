<?php

namespace App\Workflow\Controller;

use App\Http\Controllers\Controller;
use App\Workflow\Request\CreateWorkflowFormRequest;
use App\Workflow\Service\CreateWorkflowService;

class CreateWorkflowController extends Controller
{
    private CreateWorkflowService $service;

    public function __construct(CreateWorkflowService $service)
    {
        $this->service = $service;
    }

    public function __invoke(CreateWorkflowFormRequest $request)
    {
        $userId = (int) $request->get('auth_user_id');
        if ($userId <= 0) {
            return response()->json(['message' => 'Unauthorized'], 401);
        }

        $payload = $request->only([
            'targetType', 'targetId', 'fields', 'meta', 'destinationUserIds',
        ]);

        $response = $this->service->create($payload, $userId);
        return response()->json($response->json(), $response->status());
    }
}
