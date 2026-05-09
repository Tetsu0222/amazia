<?php

namespace App\Inquiry\Controller;

use App\Http\Controllers\Controller;
use App\Inquiry\Service\GetUnreadInquiryCountService;
use Illuminate\Http\Request;

/**
 * フェーズ18: ベルマーク用未対応件数 Controller。
 * GET /api/console/inquiries/unread-count
 */
class GetUnreadInquiryCountController extends Controller
{
    private GetUnreadInquiryCountService $service;

    public function __construct(GetUnreadInquiryCountService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->get($userId);
        return response()->json($response->json(), $response->status());
    }
}
