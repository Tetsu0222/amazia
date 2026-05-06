<?php

namespace App\OperationLog\Controller;

use App\Http\Controllers\Controller;
use App\OperationLog\Service\ListOperationLogService;
use Illuminate\Http\Request;

/**
 * 操作履歴一覧取得 Controller（GET /api/operation-logs）。
 *
 * Core の GET /api/operation-logs を中継する。
 * 検索クエリ（screenName / apiName / action）はそのまま透過。
 * 認証は routes/api.php 側の auth.jwt ミドルウェアで担保。
 */
class ListOperationLogController extends Controller
{
    private ListOperationLogService $service;

    public function __construct(ListOperationLogService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $screenName = $request->query('screenName');
        $apiName    = $request->query('apiName');
        $action     = $request->query('action');

        $response = $this->service->list($screenName, $apiName, $action);
        return response()->json($response->json(), $response->status());
    }
}
