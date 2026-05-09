<?php

namespace App\Notice\Controller;

use App\Http\Controllers\Controller;
use App\Notice\Service\GetNoticeService;
use Illuminate\Http\Request;

/**
 * フェーズ19: お知らせ詳細 Controller（Console Pass-through）。
 * GET /api/admin/notices/{id}
 */
class GetNoticeController extends Controller
{
    private GetNoticeService $service;

    public function __construct(GetNoticeService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->get($userId, $id, [
            'include_unpublished' => $request->query('include_unpublished'),
            'include_deleted'     => $request->query('include_deleted'),
        ]);
        return response()->json($response->json(), $response->status());
    }
}
