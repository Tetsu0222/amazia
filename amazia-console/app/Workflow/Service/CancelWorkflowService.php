<?php

namespace App\Workflow\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class CancelWorkflowService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function cancel(int $workflowId, int $userId, string $role): Response
    {
        return Http::withHeaders([
            'X-User-Id'   => (string) $userId,
            'X-User-Role' => $role,
        ])->post("{$this->baseUrl}/workflows/{$workflowId}/cancel");
    }
}
