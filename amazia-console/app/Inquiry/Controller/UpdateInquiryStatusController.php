<?php

namespace App\Inquiry\Controller;

use App\Http\Controllers\Controller;
use App\Inquiry\Service\UpdateInquiryStatusService;
use Illuminate\Http\Request;

/**
 * フェーズ18: 問い合わせステータス変更 Controller。
 * PATCH /api/console/inquiries/{id}/status
 */
class UpdateInquiryStatusController extends Controller
{
    private UpdateInquiryStatusService $service;

    public function __construct(UpdateInquiryStatusService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $userId = $request->input('auth_user_id');

        $validated = $request->validate([
            'newStatus' => 'required|string|in:'.implode(',', config('app.inquiry.statuses')),
            'reason'    => 'nullable|string',
        ]);

        $response = $this->service->update($userId, $id, [
            'newStatus' => $validated['newStatus'],
            'reason'    => $validated['reason'] ?? null,
        ]);
        return response()->json($response->json(), $response->status());
    }
}
