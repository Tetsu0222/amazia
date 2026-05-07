<?php

namespace Tests\Feature\Inbound;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * フェーズ15 Step C-2: 入荷管理 Console 中継のテスト。
 */
class InboundProxyTest extends TestCase
{
    private string $baseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function test_入荷一覧を取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/inbounds*" => Http::response([
                ['id' => 1, 'productId' => 100, 'quantity' => 5],
                ['id' => 2, 'productId' => 100, 'quantity' => 3],
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/inbounds')
             ->assertStatus(200)
             ->assertJsonCount(2);
    }

    public function test_入荷一覧フィルタがCoreにクエリで渡る(): void
    {
        Http::fake([
            "{$this->baseUrl}/inbounds*" => Http::response([], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/inbounds?productId=100')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            return str_contains($request->url(), 'productId=100');
        });
    }

    public function test_userロールは入荷登録で403が返る(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->postJson('/api/inbounds', [
                 'productId'   => 100,
                 'skuId'       => 200,
                 'quantity'    => 5,
                 'inboundedAt' => '2026-05-07',
             ])
             ->assertStatus(403);
    }

    public function test_supervisorは入荷登録できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/inbounds" => Http::response([
                'id' => 1, 'productId' => 100, 'quantity' => 5, 'warehouseId' => 1,
            ], 201),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->postJson('/api/inbounds', [
                 'productId'   => 100,
                 'skuId'       => 200,
                 'quantity'    => 5,
                 'inboundedAt' => '2026-05-07',
             ])
             ->assertStatus(201)
             ->assertJsonFragment(['warehouseId' => 1]);
    }

    public function test_warehouse_idをリクエストに含めてもCoreに渡されない(): void
    {
        // Console は warehouseId を剥がして Core に転送する（RRRR-5）
        Http::fake([
            "{$this->baseUrl}/inbounds" => Http::response([
                'id' => 1, 'productId' => 100, 'quantity' => 5, 'warehouseId' => 1,
            ], 201),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/inbounds', [
                 'productId'   => 100,
                 'skuId'       => 200,
                 'quantity'    => 5,
                 'inboundedAt' => '2026-05-07',
                 'warehouseId' => 99, // 不正値を意図的に注入
             ])
             ->assertStatus(201);

        Http::assertSent(function ($request) {
            $body = $request->data();
            return !array_key_exists('warehouseId', $body)
                && !array_key_exists('warehouse_id', $body);
        });
    }

    public function test_quantityが0以下で422が返る(): void
    {
        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/inbounds', [
                 'productId'   => 100,
                 'skuId'       => 200,
                 'quantity'    => 0,
                 'inboundedAt' => '2026-05-07',
             ])
             ->assertStatus(422);
    }

    public function test_X_User_IdヘッダがCoreに渡る(): void
    {
        Http::fake([
            "{$this->baseUrl}/inbounds" => Http::response([
                'id' => 1, 'productId' => 100, 'quantity' => 5,
            ], 201),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/inbounds', [
                 'productId'   => 100,
                 'skuId'       => 200,
                 'quantity'    => 5,
                 'inboundedAt' => '2026-05-07',
             ])
             ->assertStatus(201);

        Http::assertSent(function ($request) {
            return $request->hasHeader('X-User-Id', '1');
        });
    }
}
