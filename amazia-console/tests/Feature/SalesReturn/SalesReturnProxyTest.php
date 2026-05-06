<?php

namespace Tests\Feature\SalesReturn;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * フェーズ14 Step B-5-5: 返品ワークフロー Console 中継 4 本の Feature テスト。
 *
 * Core の /api/sales-returns 各エンドポイントを中継するだけのため、
 * Http::fake() で Core レスポンスを差し替えて中継挙動と権限ガードを検証する。
 */
class SalesReturnProxyTest extends TestCase
{
    private string $baseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function test_返品申請一覧が取得できること(): void
    {
        $payload = [
            [
                'id' => 1,
                'status' => 'REQUESTED',
                'quantity' => 1,
                'reason' => 'サイズが合わなかった',
                'createdAt' => '2026-05-06T10:00:00',
                'approvedAt' => null,
                'approverId' => null,
                'salesId' => 100,
                'salesDate' => '2026-05-01',
                'customerId' => 11,
                'customerName' => '田中 太郎',
                'skuId' => 101,
                'productName' => '商品A',
                'color' => '赤',
                'size' => 'M',
            ],
        ];

        Http::fake([
            "{$this->baseUrl}/sales-returns" => Http::response($payload, 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/sales-returns')
             ->assertStatus(200)
             ->assertJsonCount(1)
             ->assertJsonFragment(['customerName' => '田中 太郎'])
             ->assertJsonFragment(['productName' => '商品A']);
    }

    public function test_Core応答ステータスを透過すること(): void
    {
        Http::fake([
            "{$this->baseUrl}/sales-returns" => Http::response(['error' => 'core down'], 503),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/sales-returns')
             ->assertStatus(503);
    }

    public function test_userロールは承認で403が返ること(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/sales-returns/1/approve')
             ->assertStatus(403);
    }

    public function test_supervisorは承認できること(): void
    {
        Http::fake([
            "{$this->baseUrl}/sales-returns/1/approve" => Http::response([
                'id' => 1, 'status' => 'APPROVED', 'salesId' => 100,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->postJson('/api/sales-returns/1/approve')
             ->assertStatus(200)
             ->assertJsonFragment(['status' => 'APPROVED']);
    }

    public function test_userロールは却下で403が返ること(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/sales-returns/1/reject')
             ->assertStatus(403);
    }

    public function test_adminは却下できること(): void
    {
        Http::fake([
            "{$this->baseUrl}/sales-returns/1/reject" => Http::response([
                'id' => 1, 'status' => 'REJECTED', 'salesId' => 100,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/sales-returns/1/reject')
             ->assertStatus(200)
             ->assertJsonFragment(['status' => 'REJECTED']);
    }

    public function test_userロールは返金完了で403が返ること(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/sales-returns/1/refund')
             ->assertStatus(403);
    }

    public function test_senior_adminは返金完了できること(): void
    {
        Http::fake([
            "{$this->baseUrl}/sales-returns/1/refund" => Http::response([
                'id' => 1, 'status' => 'REFUNDED', 'salesId' => 100,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('senior_admin'))
             ->postJson('/api/sales-returns/1/refund')
             ->assertStatus(200)
             ->assertJsonFragment(['status' => 'REFUNDED']);
    }

    public function test_承認のCore_409を透過すること(): void
    {
        Http::fake([
            "{$this->baseUrl}/sales-returns/1/approve" => Http::response([
                'message' => 'sales_return cannot be approved from status APPROVED',
            ], 409),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/sales-returns/1/approve')
             ->assertStatus(409);
    }

    public function test_X_User_IdヘッダがCoreに渡ること(): void
    {
        // TestCase::makeToken() は JWT の sub を '1' 固定で発行する
        Http::fake([
            "{$this->baseUrl}/sales-returns/1/approve" => Http::response([
                'id' => 1, 'status' => 'APPROVED',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/sales-returns/1/approve')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            return $request->hasHeader('X-User-Id', '1');
        });
    }
}
