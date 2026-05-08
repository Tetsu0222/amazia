<?php

namespace App\Batch\Controller;

use App\Batch\Service\ListConsoleNotificationService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

/**
 * フェーズ17 Step 6-2: 通知センター一覧 Pass-through Controller。
 * GET /api/console/batch/notifications
 */
class ListConsoleNotificationController extends Controller
{
    private ListConsoleNotificationService $service;

    public function __construct(ListConsoleNotificationService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $userId      = $request->input('auth_user_id');
        $level       = $request->query('level');
        $tag         = $request->query('tag');
        $includeRead = filter_var($request->query('include_read', 'false'), FILTER_VALIDATE_BOOLEAN);
        $offset      = (int) $request->query('offset', 0);
        $size        = (int) $request->query('size', 20);

        $response = $this->service->list($userId, $level, $tag, $includeRead, $offset, $size);
        return response()->json($response->json(), $response->status());
    }
}
