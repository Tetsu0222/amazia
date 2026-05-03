<?php

namespace Tests\Feature\ListProduct;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class ListProductTest extends TestCase
{
    private string $coreApiUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->coreApiUrl = config('services.amazia_core.url');
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

    public function test_CoreAPIが500を返すとき商品一覧取得も500を返すこと(): void
    {
        Http::fake([
            "{$this->coreApiUrl}" => Http::response([], 500),
        ]);

        $this->getJson('/api/products')->assertStatus(500);
    }
}
