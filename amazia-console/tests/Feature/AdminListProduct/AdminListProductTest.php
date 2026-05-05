<?php

namespace Tests\Feature\AdminListProduct;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class AdminListProductTest extends TestCase
{
    private string $coreBaseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withHeaders($this->authHeaders());
        $this->coreBaseUrl = config('services.amazia_core.base_url');
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

    public function test_CoreAPIが500を返すとき管理画面向け全件取得も500を返すこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/admin/products" => Http::response([], 500),
        ]);

        $this->getJson('/api/admin/products')->assertStatus(500);
    }
}
