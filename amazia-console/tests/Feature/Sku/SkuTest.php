<?php

namespace Tests\Feature\Sku;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class SkuTest extends TestCase
{
    private string $coreBaseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withHeaders($this->authHeaders());
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    // ─── SKU ─────────────────────────────────────────────

    public function test_SKU一覧をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/1/skus" => Http::response([
                ['id' => 1, 'productId' => 1, 'color' => 'Red', 'size' => 'M', 'skuCode' => 'ABC123'],
            ], 200),
        ]);

        $response = $this->getJson('/api/products/1/skus');

        $response->assertStatus(200)->assertJsonFragment(['color' => 'Red']);
    }

    public function test_SKUをCoreに登録できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/1/skus" => Http::response([
                'id' => 1, 'productId' => 1, 'color' => 'Red', 'size' => 'M', 'skuCode' => 'ABC123',
            ], 201),
        ]);

        $response = $this->postJson('/api/products/1/skus', [
            'color' => 'Red', 'size' => 'M',
        ]);

        $response->assertStatus(201)->assertJsonFragment(['color' => 'Red', 'size' => 'M']);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreBaseUrl}/products/1/skus"
            && $req['color'] === 'Red'
        );
    }

    public function test_SKU登録時に色とサイズは必須であること(): void
    {
        $response = $this->postJson('/api/products/1/skus', ['color' => 'Red']);
        $response->assertStatus(400);
    }

    public function test_Coreがエラーのときはそのステータスをそのまま返すこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/999/skus" => Http::response(['message' => 'Not Found'], 404),
        ]);

        $response = $this->postJson('/api/products/999/skus', ['color' => 'Red', 'size' => 'M']);
        $response->assertStatus(404);
    }

    // ─── 価格 ─────────────────────────────────────────────

    public function test_SKU価格をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/prices" => Http::response([
                'id' => 1, 'skuId' => 1, 'price' => 2000,
            ], 200),
        ]);

        $response = $this->getJson('/api/skus/1/prices');
        $response->assertStatus(200)->assertJsonFragment(['price' => 2000]);
    }

    public function test_SKU価格をCoreに登録できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/prices" => Http::response([
                'id' => 1, 'skuId' => 1, 'price' => 3000,
            ], 201),
        ]);

        $response = $this->postJson('/api/skus/1/prices', [
            'price' => 3000, 'startDate' => '2026-01-01',
        ]);

        $response->assertStatus(201)->assertJsonFragment(['price' => 3000]);
    }

    public function test_SKU価格登録時に価格は必須であること(): void
    {
        $response = $this->postJson('/api/skus/1/prices', ['startDate' => '2026-01-01']);
        $response->assertStatus(400);
    }

    // ─── 在庫 ─────────────────────────────────────────────

    public function test_SKU在庫をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/stocks" => Http::response([
                'id' => 1, 'skuId' => 1, 'quantity' => 100,
            ], 200),
        ]);

        $response = $this->getJson('/api/skus/1/stocks');
        $response->assertStatus(200)->assertJsonFragment(['quantity' => 100]);
    }

    public function test_SKU在庫入荷をCoreに登録できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/stocks/receive" => Http::response([
                'id' => 1, 'skuId' => 1, 'quantity' => 50,
            ], 201),
        ]);

        $response = $this->postJson('/api/skus/1/stocks/receive', ['quantity' => 50]);
        $response->assertStatus(201)->assertJsonFragment(['quantity' => 50]);
    }

    public function test_SKU在庫入荷時に数量は必須であること(): void
    {
        $response = $this->postJson('/api/skus/1/stocks/receive', []);
        $response->assertStatus(400);
    }

    public function test_SKU在庫履歴をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/stocks/history" => Http::response([
                ['id' => 1, 'skuId' => 1, 'type' => 'receive', 'quantity' => 50],
            ], 200),
        ]);

        $response = $this->getJson('/api/skus/1/stocks/history');
        $response->assertStatus(200)->assertJsonFragment(['type' => 'receive']);
    }
}
