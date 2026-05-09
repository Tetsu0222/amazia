<?php

namespace App\Notice\Controller;

use App\Http\Controllers\Controller;
use App\Notice\Request\StoreNoticeRequest;
use App\Notice\Service\CreateNoticeService;

/**
 * フェーズ19: お知らせ新規作成 Controller（Console Pass-through）。
 * POST /api/admin/notices
 */
class CreateNoticeController extends Controller
{
    private CreateNoticeService $service;

    public function __construct(CreateNoticeService $service)
    {
        $this->service = $service;
    }

    public function __invoke(StoreNoticeRequest $request)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->create($userId, $request->validated());
        return response()->json($response->json(), $response->status());
    }
}
