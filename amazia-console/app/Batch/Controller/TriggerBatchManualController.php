<?php

namespace App\Batch\Controller;

use App\Batch\Service\TriggerBatchManualService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * フェーズ17 Step 6-3: バッチ手動起動 Pass-through Controller。
 * POST /api/console/batch/{jobName}/run
 *
 * <p>認可は routes/api/Batch.php 側で check.permission ミドルウェアで管理者相当のみ許可する。
 */
class TriggerBatchManualController extends Controller
{
    private TriggerBatchManualService $service;

    public function __construct(TriggerBatchManualService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, string $jobName)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->trigger($userId, $jobName);
        return response()->json($response->json(), $response->status());
    }
}
