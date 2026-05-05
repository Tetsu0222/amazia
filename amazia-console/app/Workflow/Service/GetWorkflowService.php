<?php

namespace App\Workflow\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class GetWorkflowService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function get(int $id): Response
    {
        return Http::get("{$this->baseUrl}/workflows/{$id}");
    }
}
