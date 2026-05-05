<?php

namespace Tests\Feature\Workflow;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class WorkflowProxyTest extends TestCase
{
    private string $baseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function test_一覧をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->baseUrl}/workflows" => Http::response([
                ['id' => 1, 'targetType' => 'product', 'status' => 'pending'],
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/workflows')
             ->assertStatus(200)
             ->assertJsonFragment(['id' => 1]);
    }

    public function test_重複申請でCoreが409を返したらクライアントにも409を返すこと(): void
    {
        Http::fake([
            "{$this->baseUrl}/workflows" => Http::response(['message' => 'duplicate'], 409),
        ]);

        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/workflows', [
                 'targetType' => 'product',
                 'targetId'   => 1,
                 'fields'     => [['field' => 'statusCode', 'before' => null, 'after' => 'ON_SALE']],
             ])
             ->assertStatus(409);
    }

    public function test_Core側500のときも500を返すこと(): void
    {
        Http::fake([
            "{$this->baseUrl}/workflows/1" => Http::response([], 500),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/workflows/1')
             ->assertStatus(500);
    }

    public function test_userロールは承認エンドポイントで403が返ること(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/workflows/1/steps/1/approve')
             ->assertStatus(403);
    }

    public function test_supervisorは承認エンドポイントを呼べること(): void
    {
        Http::fake([
            "{$this->baseUrl}/workflows/1/steps/1/approve" => Http::response([
                'id' => 1, 'status' => 'pending'
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->postJson('/api/workflows/1/steps/1/approve')
             ->assertStatus(200);
    }

    public function test_userロールは即時反映で403が返ること(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/workflows/immediate-apply', [
                 'targetType' => 'stock',
                 'targetId'   => 1,
                 'fields'     => [['field' => 'quantity', 'before' => 1, 'after' => 2]],
             ])
             ->assertStatus(403);
    }

    public function test_validationエラーは422を返すこと(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/workflows', [
                 'targetType' => 'unknown_type',
                 'targetId'   => 1,
                 'fields'     => [],
             ])
             ->assertStatus(422);
    }
}
