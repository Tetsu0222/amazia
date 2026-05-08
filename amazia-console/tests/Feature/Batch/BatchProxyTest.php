<?php

namespace Tests\Feature\Batch;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * フェーズ17 Step 6-1 / 6-2 / 6-3:
 * Console Batch Pass-through Controller の Feature テスト。
 *
 * Core の実エンドポイントは Http::fake() で差し替え、中継挙動とクエリ透過、
 * 認可（手動起動 admin 限定）、X-User-Id ヘッダ付与を検証する。
 */
class BatchProxyTest extends TestCase
{
    private string $baseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    // -----------------------------------------------------------------
    // 6-1: バッチ実行履歴
    // -----------------------------------------------------------------

    public function test_履歴一覧が取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/executions*" => Http::response([
                'items' => [['id' => 1, 'jobName' => 'JobA', 'status' => 'SUCCESS']],
                'total' => 1, 'offset' => 0, 'size' => 20,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/batch/executions')
             ->assertStatus(200)
             ->assertJsonFragment(['jobName' => 'JobA']);
    }

    public function test_履歴一覧の検索クエリがCoreに透過される(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/executions*" => Http::response(
                ['items' => [], 'total' => 0, 'offset' => 0, 'size' => 20], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/batch/executions?jobName=JobA&status=FAILED&offset=10&size=5')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            $url = $request->url();
            return str_contains($url, 'jobName=JobA')
                && str_contains($url, 'status=FAILED')
                && str_contains($url, 'offset=10')
                && str_contains($url, 'size=5');
        });
    }

    public function test_履歴詳細が取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/executions/42" => Http::response([
                'id' => 42, 'jobName' => 'JobX', 'errorSummary' => 'NPE',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/batch/executions/42')
             ->assertStatus(200)
             ->assertJsonFragment(['errorSummary' => 'NPE']);
    }

    public function test_履歴詳細404を透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/executions/999" => Http::response(
                ['message' => 'not found'], 404),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/batch/executions/999')
             ->assertStatus(404);
    }

    // -----------------------------------------------------------------
    // 6-2: 通知センター
    // -----------------------------------------------------------------

    public function test_通知一覧が取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/notifications*" => Http::response([
                'items' => [['id' => 1, 'level' => 'WARN', 'title' => 't']],
                'total' => 1, 'offset' => 0, 'size' => 20,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/batch/notifications')
             ->assertStatus(200)
             ->assertJsonFragment(['level' => 'WARN']);
    }

    public function test_通知一覧でX_User_IdヘッダがCoreに透過される(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/notifications*" => Http::response(
                ['items' => [], 'total' => 0, 'offset' => 0, 'size' => 20], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/batch/notifications')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            return $request->hasHeader('X-User-Id');
        });
    }

    public function test_通知既読化を中継する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/notifications/77/read" => Http::response([
                'id' => 77, 'status' => 'read',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->putJson('/api/console/batch/notifications/77/read')
             ->assertStatus(200)
             ->assertJsonFragment(['status' => 'read']);
    }

    public function test_他ユーザ宛通知の既読化_403を透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/notifications/77/read" => Http::response([
                'message' => 'not allowed',
            ], 403),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->putJson('/api/console/batch/notifications/77/read')
             ->assertStatus(403);
    }

    // -----------------------------------------------------------------
    // 6-3: 手動起動（admin 限定）
    // -----------------------------------------------------------------

    public function test_admin_は手動起動できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/RebuildInventoriesJob/run" => Http::response([
                'message' => 'triggered', 'jobName' => 'RebuildInventoriesJob',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/console/batch/RebuildInventoriesJob/run')
             ->assertStatus(200)
             ->assertJsonFragment(['message' => 'triggered']);
    }

    public function test_user_は手動起動を拒否される(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/console/batch/RebuildInventoriesJob/run')
             ->assertStatus(403);
    }

    public function test_supervisor_は手動起動を拒否される(): void
    {
        $this->withHeaders($this->authHeaders('supervisor'))
             ->postJson('/api/console/batch/RebuildInventoriesJob/run')
             ->assertStatus(403);
    }

    public function test_eternal_advisor_は手動起動できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/RebuildInventoriesJob/run" => Http::response([
                'message' => 'triggered',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('eternal_advisor'))
             ->postJson('/api/console/batch/RebuildInventoriesJob/run')
             ->assertStatus(200);
    }

    public function test_手動起動_503を透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/RebuildInventoriesJob/run" => Http::response([
                'message' => 'manual trigger is disabled',
            ], 503),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/console/batch/RebuildInventoriesJob/run')
             ->assertStatus(503);
    }

    public function test_手動起動_本番のTriggerFaultInjectionJobは404を透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/batch/TriggerFaultInjectionJob/run" => Http::response([
                'message' => 'job not found',
            ], 404),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/console/batch/TriggerFaultInjectionJob/run')
             ->assertStatus(404);
    }
}
