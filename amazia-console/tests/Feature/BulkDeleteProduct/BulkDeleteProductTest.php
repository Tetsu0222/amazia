<?php

namespace Tests\Feature\BulkDeleteProduct;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class BulkDeleteProductTest extends TestCase
{
    private string $coreApiUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function test_一括削除リクエストがCoreのAPIに飛ぶこと(): void
    {
        Http::fake(["{$this->coreApiUrl}*" => Http::response(null, 204)]);

        $this->deleteJson('/api/products', ['ids' => '1,2,3'])->assertStatus(204);

        Http::assertSent(fn($req) =>
            str_contains($req->url(), $this->coreApiUrl) && $req->method() === 'DELETE'
        );
    }
}
