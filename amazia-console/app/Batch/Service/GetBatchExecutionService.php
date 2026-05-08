<?php

namespace App\Batch\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ17 Step 6-0 / 6-1: Core の GET /api/console/batch/executions/{id} を呼び出す Pass-through Service。
 * 設計書 §13.7.1。
 */
class GetBatchExecutionService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function get(?string $userId, int $id): Response
    {
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->get("{$this->baseUrl}/console/batch/executions/{$id}");
    }
}
