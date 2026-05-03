<?php

namespace Tests\Feature\CreateProduct;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class CreateProductTest extends TestCase
{
    private string $coreApiUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->coreApiUrl = config('services.amazia_core.url');
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
}
