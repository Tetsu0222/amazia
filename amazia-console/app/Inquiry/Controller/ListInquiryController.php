<?php

namespace App\Inquiry\Controller;

use App\Http\Controllers\Controller;
use App\Inquiry\Service\ListInquiryService;
use Illuminate\Http\Request;

/**
 * フェーズ18: 問い合わせ一覧 Controller。
 * GET /api/console/inquiries
 */
class ListInquiryController extends Controller
{
    private ListInquiryService $service;

    public function __construct(ListInquiryService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->list($userId, [
            'status'     => $request->query('status'),
            'targetType' => $request->query('targetType'),
            'dateFrom'   => $request->query('dateFrom'),
            'dateTo'     => $request->query('dateTo'),
            'userName'   => $request->query('userName'),
            'page'       => $request->query('page', 0),
            'size'       => $request->query('size'),
        ]);
        return response()->json($response->json(), $response->status());
    }
}
