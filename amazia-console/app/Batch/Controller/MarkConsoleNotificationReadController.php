<?php

namespace App\Batch\Controller;

use App\Batch\Service\MarkConsoleNotificationReadService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * フェーズ17 Step 6-2: 通知既読 Pass-through Controller。
 * PUT /api/console/batch/notifications/{id}/read
 */
class MarkConsoleNotificationReadController extends Controller
{
    private MarkConsoleNotificationReadService $service;

    public function __construct(MarkConsoleNotificationReadService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->markRead($userId, $id);
        return response()->json($response->json(), $response->status());
    }
}
