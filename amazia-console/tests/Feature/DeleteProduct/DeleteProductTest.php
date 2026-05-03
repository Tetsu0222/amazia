<?php

namespace Tests\Feature\DeleteProduct;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class DeleteProductTest extends TestCase
{
    private string $coreApiUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->coreApiUrl = config('services.amazia_core.url');
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
}
