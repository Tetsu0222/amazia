<?php

namespace App\Workflow\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class CreateWorkflowService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function create(array $payload, int $userId): Response
    {
        return Http::withHeaders([
            'X-User-Id' => (string) $userId,
        ])->post("{$this->baseUrl}/workflows", $payload);
    }
}
