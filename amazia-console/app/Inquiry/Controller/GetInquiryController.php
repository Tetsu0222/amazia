<?php

namespace App\Inquiry\Controller;

use App\Http\Controllers\Controller;
use App\Inquiry\Service\GetInquiryService;
use Illuminate\Http\Request;

/**
 * フェーズ18: 問い合わせ詳細 Controller。
 * GET /api/console/inquiries/{id}
 */
class GetInquiryController extends Controller
{
    private GetInquiryService $service;

    public function __construct(GetInquiryService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = $request->input('auth_user_id');
        $response = $this->service->get($userId, $id);
        return response()->json($response->json(), $response->status());
    }
}
