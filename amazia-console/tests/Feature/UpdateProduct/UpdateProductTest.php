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

    public function test_予約発売4カラム更新リクエストがCoreに転送されること(): void
    {
        Http::fake(["{$this->coreApiUrl}/1" => Http::response(['id' => 1, 'name' => '予約商品'], 200)]);

        $this->putJson('/api/products/1', [
            'name'              => '予約商品',
            'releaseDate'       => '2026-09-01',
            'preorderStartDate' => '2026-08-01',
            'acceptPreorder'    => true,
            'acceptBackorder'   => true,
        ])->assertStatus(200);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreApiUrl}/1"
            && $req['releaseDate']       === '2026-09-01'
            && $req['preorderStartDate'] === '2026-08-01'
            && $req['acceptPreorder']    === true
            && $req['acceptBackorder']   === true
        );
    }

    public function test_isActive_falseがCoreに透過されること(): void
    {
        // フェーズ16 Step1: Market 露出 ON/OFF スイッチを Core に透過する。
        Http::fake(["{$this->coreApiUrl}/1" => Http::response(['id' => 1, 'name' => '非表示商品', 'isActive' => false], 200)]);

        $this->putJson('/api/products/1', [
            'name'     => '非表示商品',
            'isActive' => false,
        ])->assertStatus(200);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreApiUrl}/1"
            && $req['isActive'] === false
        );
    }

    public function test_isActive未指定時はtrueを既定値としてCoreに送ること(): void
    {
        Http::fake(["{$this->coreApiUrl}/1" => Http::response(['id' => 1], 200)]);

        $this->putJson('/api/products/1', ['name' => 'デフォルト'])
             ->assertStatus(200);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreApiUrl}/1"
            && $req['isActive'] === true
        );
    }
}
