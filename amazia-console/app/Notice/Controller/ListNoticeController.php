<?php

namespace App\Notice\Controller;

use App\Http\Controllers\Controller;
use App\Notice\Service\ListNoticeService;
use Illuminate\Http\Request;

/**
 * フェーズ19: お知らせ一覧 Controller（Console Pass-through）。
 * GET /api/admin/notices
 */
class ListNoticeController extends Controller
{
    private ListNoticeService $service;

    public function __construct(ListNoticeService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->list($userId, [
            'page'                => $request->query('page'),
            'per_page'            => $request->query('per_page'),
            'category_id'         => $request->query('category_id'),
            'include_unpublished' => $request->query('include_unpublished'),
            'include_deleted'     => $request->query('include_deleted'),
        ]);
        return response()->json($response->json(), $response->status());
    }
}
