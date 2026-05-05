<?php

namespace Tests\Feature\UpdateProduct;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class UpdateProductTest extends TestCase
{
    private string $coreApiUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withHeaders($this->authHeaders());
        $this->coreApiUrl = config('services.amazia_core.url');
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
}
