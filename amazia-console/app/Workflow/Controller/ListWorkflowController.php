<?php

namespace App\Workflow\Controller;

use App\Http\Controllers\Controller;
use App\Workflow\Service\ListWorkflowService;
use Illuminate\Http\Request;

class ListWorkflowController extends Controller
{
    private ListWorkflowService $service;

    public function __construct(ListWorkflowService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $response = $this->service->list($request->query('status'));
        return response()->json($response->json(), $response->status());
    }
}
