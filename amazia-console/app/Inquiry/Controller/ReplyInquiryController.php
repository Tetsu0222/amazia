<?php

namespace App\Inquiry\Controller;

use App\Http\Controllers\Controller;
use App\Inquiry\Service\ReplyInquiryService;
use Illuminate\Http\Request;

/**
 * フェーズ18: 問い合わせ返信投稿 Controller。
 * POST /api/console/inquiries/{id}/messages
 */
class ReplyInquiryController extends Controller
{
    private ReplyInquiryService $service;

    public function __construct(ReplyInquiryService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = $request->input('auth_user_id');

        $validated = $request->validate([
            'message'        => 'required|string|max:'.config('app.inquiry.message_max_length'),
            'isInternalNote' => 'nullable|boolean',
        ]);

        $response = $this->service->reply($userId, $id, [
            'message'        => $validated['message'],
            'isInternalNote' => (bool) ($validated['isInternalNote'] ?? false),
        ]);
        return response()->json($response->json(), $response->status());
    }
}
