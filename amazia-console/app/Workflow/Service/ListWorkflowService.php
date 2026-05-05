<?php

namespace App\Workflow\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ListWorkflowService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(?string $status): Response
    {
        $query = $status ? ['status' => $status] : [];
        return Http::get("{$this->baseUrl}/workflows", $query);
    }
}
