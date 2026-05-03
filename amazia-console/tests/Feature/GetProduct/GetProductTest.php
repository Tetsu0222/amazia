<?php

namespace Tests\Feature\GetProduct;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class GetProductTest extends TestCase
{
    private string $coreApiUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->coreApiUrl = config('services.amazia_core.url');
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
}
