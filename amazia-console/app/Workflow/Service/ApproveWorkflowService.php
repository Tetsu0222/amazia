<?php

namespace App\Workflow\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ApproveWorkflowService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function approve(int $workflowId, int $stepNumber, int $userId, string $role): Response
    {
        return Http::withHeaders([
            'X-User-Id'   => (string) $userId,
            'X-User-Role' => $role,
        ])->post("{$this->baseUrl}/workflows/{$workflowId}/steps/{$stepNumber}/approve");
    }
}
