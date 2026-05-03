<?php

namespace Tests\Feature;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class ProductControllerTest extends TestCase
{
    private string $coreApiUrl;
    private string $coreBaseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->coreApiUrl  = config('services.amazia_core.url');
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function test_商品登録フォームを送信するとCoreのAPIにリクエストが飛ぶこと(): void
    {
        Http::fake([
            "{$this->coreApiUrl}" => Http::response([
                'id' => 1, 'name' => '商品A', 'description' => '説明A', 'price' => 1000, 'stock' => 100,
            ], 201),
        ]);

        $response = $this->postJson('/api/products', [
            'name' => '商品A', 'description' => '説明A', 'price' => 1000, 'stock' => 100,
        ]);

        $response->assertStatus(201)->assertJsonFragment(['name' => '商品A']);

        Http::assertSent(fn($req) =>
            $req->url() === $this->coreApiUrl && $req['name'] === '商品A'
        );
    }

    public function test_必須項目が欠けているとき400が返ること(): void
    {
        $response = $this->postJson('/api/products', ['description' => '説明のみ']);
        $response->assertStatus(400);
    }

    public function test_商品一覧をCoreのAPIから取得できること(): void
    {
        Http::fake([
            "{$this->coreApiUrl}" => Http::response([
                ['id' => 1, 'name' => '商品A', 'price' => 1000, 'stock' => 100],
                ['id' => 2, 'name' => '商品B', 'price' => 2000, 'stock' => 50],
            ], 200),
        ]);

        $this->getJson('/api/products')->assertStatus(200)->assertJsonFragment(['name' => '商品A']);
    }

    public function test_商品1件をCoreのAPIから取得できること(): void
    {
        Http::fake([
            "{$this->coreApiUrl}/1" => Http::response([
                'id' => 1, 'name' => '商品A', 'description' => '説明A', 'price' => 1000, 'stock' => 100,
            ], 200),
        ]);

        $this->getJson('/api/products/1')->assertStatus(200)->assertJsonFragment(['name' => '商品A']);
    }

    public function test_商品更新リクエストがCoreのAPIに飛ぶこと(): void
    {
        Http::fake([
            "{$this->coreApiUrl}/1" => Http::response([
                'id' => 1, 'name' => '商品A改', 'description' => '説明A改', 'price' => 2000, 'stock' => 50,
            ], 200),
        ]);

        $response = $this->putJson('/api/products/1', [
            'name' => '商品A改', 'description' => '説明A改', 'price' => 2000, 'stock' => 50,
        ]);

        $response->assertStatus(200)->assertJsonFragment(['name' => '商品A改']);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreApiUrl}/1" && $req['name'] === '商品A改'
        );
    }

    public function test_存在しない商品を更新しようとしたとき404が返ること(): void
    {
        Http::fake(["{$this->coreApiUrl}/999" => Http::response([], 404)]);
        $this->putJson('/api/products/999', ['name' => '商品X', 'price' => 1000, 'stock' => 10])
             ->assertStatus(404);
    }

    public function test_商品削除リクエストがCoreのAPIに飛ぶこと(): void
    {
        Http::fake(["{$this->coreApiUrl}/1" => Http::response(null, 204)]);

        $this->deleteJson('/api/products/1')->assertStatus(204);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreApiUrl}/1" && $req->method() === 'DELETE'
        );
    }

    public function test_存在しない商品を削除しようとしたとき404が返ること(): void
    {
        Http::fake(["{$this->coreApiUrl}/999" => Http::response([], 404)]);
        $this->deleteJson('/api/products/999')->assertStatus(404);
    }

    public function test_一括削除リクエストがCoreのAPIに飛ぶこと(): void
    {
        Http::fake(["{$this->coreApiUrl}*" => Http::response(null, 204)]);

        $this->deleteJson('/api/products', ['ids' => '1,2,3'])->assertStatus(204);

        Http::assertSent(fn($req) =>
            str_contains($req->url(), $this->coreApiUrl) && $req->method() === 'DELETE'
        );
    }

    public function test_一括在庫更新リクエストがCoreのAPIに飛ぶこと(): void
    {
        Http::fake([
            "{$this->coreApiUrl}/bulk-stock" => Http::response([
                ['id' => 1, 'name' => '商品A', 'price' => 1000, 'stock' => 999],
            ], 200),
        ]);

        $this->patchJson('/api/products/bulk-stock', [['id' => 1, 'stock' => 999]])
             ->assertStatus(200)->assertJsonFragment(['stock' => 999]);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreApiUrl}/bulk-stock" && $req->method() === 'PATCH'
        );
    }

    public function test_管理画面向け全件取得がCoreのAPIに飛ぶこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/admin/products" => Http::response([
                ['id' => 1, 'name' => '公開中商品', 'price' => 1000, 'stock' => 10, 'statusCode' => 'ON_SALE'],
                ['id' => 2, 'name' => '終了商品',  'price' => 2000, 'stock' => 0,  'statusCode' => 'ON_SALE',
                 'publishEnd' => '2020-12-31T23:59:59'],
            ], 200),
        ]);

        $this->getJson('/api/admin/products')->assertStatus(200)->assertJsonCount(2);

        Http::assertSent(fn($req) => $req->url() === "{$this->coreBaseUrl}/admin/products");
    }

    public function test_ステータスマスタ一覧が取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/product-statuses" => Http::response([
                ['code' => 'WAITING',     'name' => '入荷待',    'sortOrder' => 1],
                ['code' => 'RESERVATION', 'name' => '予約受付中', 'sortOrder' => 2],
                ['code' => 'ON_SALE',     'name' => '販売中',    'sortOrder' => 3],
            ], 200),
        ]);

        $this->getJson('/api/product-statuses')->assertStatus(200)->assertJsonCount(3);
    }

    public function test_公開期間を含む商品登録リクエストがCoreのAPIに飛ぶこと(): void
    {
        Http::fake([
            "{$this->coreApiUrl}" => Http::response([
                'id' => 1, 'name' => '商品A', 'price' => 1000, 'stock' => 100,
                'statusCode' => 'ON_SALE', 'publishStart' => '2026-01-01T00:00:00', 'publishEnd' => null,
            ], 201),
        ]);

        $this->postJson('/api/products', [
            'name' => '商品A', 'price' => 1000, 'stock' => 100,
            'statusCode' => 'ON_SALE', 'publishStart' => '2026-01-01T00:00:00',
        ])->assertStatus(201);

        Http::assertSent(fn($req) =>
            $req->url() === $this->coreApiUrl
            && $req['statusCode'] === 'ON_SALE'
            && $req['publishStart'] === '2026-01-01T00:00:00'
        );
    }

    // --- 異常系：Core APIが500を返す場合 ---

    public function test_CoreAPIが500を返すとき管理画面向け全件取得も500を返すこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/admin/products" => Http::response([], 500),
        ]);

        $this->getJson('/api/admin/products')->assertStatus(500);
    }

    public function test_CoreAPIが500を返すときステータスマスタ取得も500を返すこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/product-statuses" => Http::response([], 500),
        ]);

        $this->getJson('/api/product-statuses')->assertStatus(500);
    }

    public function test_CoreAPIが500を返すとき商品一覧取得も500を返すこと(): void
    {
        Http::fake([
            "{$this->coreApiUrl}" => Http::response([], 500),
        ]);

        $this->getJson('/api/products')->assertStatus(500);
    }
}
