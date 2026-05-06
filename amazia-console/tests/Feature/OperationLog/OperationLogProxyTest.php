<?php

namespace Tests\Feature\OperationLog;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * フェーズ14 Step B-6: Console 操作履歴中継の Feature テスト。
 *
 * Core の GET /api/operation-logs を中継するだけのため、
 * Http::fake() で Core レスポンスを差し替えて中継挙動と検索クエリ透過を検証する。
 */
class OperationLogProxyTest extends TestCase
{
    private string $baseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function test_操作履歴一覧が取得できること(): void
    {
        $payload = [
            [
                'id' => 1,
                'userId' => 11,
                'userName' => '田中 太郎',
                'action' => 'approve_sales_return',
                'targetType' => 'sales_return',
                'targetId' => 100,
                'screenName' => 'console.sales_return.approve',
                'apiName' => 'POST /api/sales-returns/:id/approve',
                'comment' => null,
                'createdAt' => '2026-05-06T12:00:00',
            ],
        ];

        Http::fake([
            "{$this->baseUrl}/operation-logs*" => Http::response($payload, 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/operation-logs')
             ->assertStatus(200)
             ->assertJsonCount(1)
             ->assertJsonFragment(['action' => 'approve_sales_return'])
             ->assertJsonFragment(['userName' => '田中 太郎']);
    }

    public function test_検索クエリがCoreに透過されること(): void
    {
        Http::fake([
            "{$this->baseUrl}/operation-logs*" => Http::response([], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/operation-logs?screenName=console.sales_return&apiName=sales-returns&action=approve_sales_return')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            $url = $request->url();
            return str_contains($url, 'screenName=console.sales_return')
                && str_contains($url, 'apiName=sales-returns')
                && str_contains($url, 'action=approve_sales_return');
        });
    }

    public function test_空の検索クエリはCoreへ送らないこと(): void
    {
        Http::fake([
            "{$this->baseUrl}/operation-logs*" => Http::response([], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/operation-logs?screenName=&apiName=&action=')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            $url = $request->url();
            // 空クエリは透過しない（array_filter で除外）
            return !str_contains($url, 'screenName=')
                && !str_contains($url, 'apiName=')
                && !str_contains($url, 'action=');
        });
    }

    public function test_Core応答ステータスを透過すること(): void
    {
        Http::fake([
            "{$this->baseUrl}/operation-logs*" => Http::response(['error' => 'core down'], 503),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/operation-logs')
             ->assertStatus(503);
    }
}
