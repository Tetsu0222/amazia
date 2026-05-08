<?php

namespace App\Batch\Controller;

use App\Batch\Service\GetBatchExecutionService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * フェーズ17 Step 6-1: バッチ実行履歴詳細 Pass-through Controller。
 * GET /api/console/batch/executions/{id}
 */
class GetBatchExecutionController extends Controller
{
    private GetBatchExecutionService $service;

    public function __construct(GetBatchExecutionService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->get($userId, $id);
        return response()->json($response->json(), $response->status());
    }
}
