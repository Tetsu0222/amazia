<?php

namespace App\Workflow\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ImmediateApplyWorkflowService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function apply(array $payload, string $role): Response
    {
        return Http::withHeaders([
            'X-User-Role' => $role,
        ])->post("{$this->baseUrl}/workflows/immediate-apply", $payload);
    }
}
