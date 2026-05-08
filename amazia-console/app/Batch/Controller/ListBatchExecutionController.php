<?php

namespace App\Batch\Controller;

use App\Batch\Service\ListBatchExecutionService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * フェーズ17 Step 6-1: バッチ実行履歴一覧 Pass-through Controller。
 * GET /api/console/batch/executions
 */
class ListBatchExecutionController extends Controller
{
    private ListBatchExecutionService $service;

    public function __construct(ListBatchExecutionService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $userId   = $request->input('auth_user_id');
        $jobName  = $request->query('jobName');
        $status   = $request->query('status');
        $offset   = (int) $request->query('offset', 0);
        $size     = (int) $request->query('size', 20);

        $response = $this->service->list($userId, $jobName, $status, $offset, $size);
        return response()->json($response->json(), $response->status());
    }
}
