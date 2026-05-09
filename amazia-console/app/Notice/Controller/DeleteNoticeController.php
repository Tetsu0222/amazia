<?php

namespace App\Notice\Controller;

use App\Http\Controllers\Controller;
use App\Notice\Service\DeleteNoticeService;
use Illuminate\Http\Request;

/**
 * フェーズ19: お知らせ論理削除 Controller（Console Pass-through）。
 * DELETE /api/admin/notices/{id}
 */
class DeleteNoticeController extends Controller
{
    private DeleteNoticeService $service;

    public function __construct(DeleteNoticeService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->delete($userId, $id);

        // Core が 204 No Content を返すケースでは body が空。json() が null を返すので
        // そのまま透過する（response()->json(null, 204) は空ボディを返す）。
        return response()->json($response->json(), $response->status());
    }
}
