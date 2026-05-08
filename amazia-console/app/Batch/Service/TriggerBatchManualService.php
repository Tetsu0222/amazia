<?php

namespace App\Batch\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ17 Step 6-3: Core の POST /api/console/batch/{job_name}/run を呼び出す Pass-through Service。
 * 設計書 §3.4 / §13.7.3 / 既存 BatchManualTriggerController（Core）。
 *
 * <ul>
 *   <li>Core 側で {@code amazia.batch.manual-trigger-enabled=false} のとき 503</li>
 *   <li>未登録ジョブ名は 404（本番では TriggerFaultInjectionJob が自動 404）</li>
 * </ul>
 */
class TriggerBatchManualService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function trigger(?string $userId, string $jobName): Response
    {
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->post("{$this->baseUrl}/console/batch/{$jobName}/run");
    }
}
