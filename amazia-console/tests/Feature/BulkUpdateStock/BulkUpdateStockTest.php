<?php

namespace Tests\Feature\BulkUpdateStock;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class BulkUpdateStockTest extends TestCase
{
    private string $coreApiUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withHeaders($this->authHeaders());
        $this->coreApiUrl = config('services.amazia_core.url');
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
}
