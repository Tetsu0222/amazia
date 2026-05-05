<?php

namespace App\Workflow\Controller;

use App\Http\Controllers\Controller;
use App\Workflow\Service\GetWorkflowService;

class GetWorkflowController extends Controller
{
    private GetWorkflowService $service;

    public function __construct(GetWorkflowService $service)
    {
        $this->service = $service;
    }

    public function __invoke(int $id)
    {
        $response = $this->service->get($id);
        return response()->json($response->json(), $response->status());
    }
}
