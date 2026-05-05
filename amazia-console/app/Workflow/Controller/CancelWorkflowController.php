<?php

namespace App\Workflow\Controller;

use App\Http\Controllers\Controller;
use App\Workflow\Service\CancelWorkflowService;
use Illuminate\Http\Request;

class CancelWorkflowController extends Controller
{
    private CancelWorkflowService $service;

    public function __construct(CancelWorkflowService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = (int) $request->get('auth_user_id');
        $role   = (string) $request->get('auth_role');

        $response = $this->service->cancel($id, $userId, $role);
        return response()->json($response->json(), $response->status());
    }
}
