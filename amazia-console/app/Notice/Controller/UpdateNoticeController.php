<?php

namespace App\Notice\Controller;

use App\Http\Controllers\Controller;
use App\Notice\Request\UpdateNoticeRequest;
use App\Notice\Service\UpdateNoticeService;

/**
 * フェーズ19: お知らせ編集 Controller（Console Pass-through）。
 * PUT /api/admin/notices/{id}
 */
class UpdateNoticeController extends Controller
{
    private UpdateNoticeService $service;

    public function __construct(UpdateNoticeService $service)
    {
        $this->service = $service;
    }

    public function __invoke(UpdateNoticeRequest $request, int $id)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->update($userId, $id, $request->validated());
        return response()->json($response->json(), $response->status());
    }
}
