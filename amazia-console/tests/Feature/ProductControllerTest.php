<?php

namespace Tests\Feature;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class ProductControllerTest extends TestCase
{
    public function test_商品登録フォームを送信するとCoreのAPIにリクエストが飛ぶこと(): void
    {
        Http::fake([
            'http://localhost:8080/api/products' => Http::response([
                'id' => 1,
                'name' => '商品A',
                'description' => '説明A',
                'price' => 1000,
                'stock' => 100,
            ], 201),
        ]);

        $response = $this->postJson('/api/products', [
            'name' => '商品A',
            'description' => '説明A',
            'price' => 1000,
            'stock' => 100,
        ]);

        $response->assertStatus(201)
                 ->assertJsonFragment(['name' => '商品A']);

        Http::assertSent(function ($request) {
            return $request->url() === 'http://localhost:8080/api/products'
                && $request['name'] === '商品A';
        });
    }

    public function test_必須項目が欠けているとき400が返ること(): void
    {
        $response = $this->postJson('/api/products', [
            'description' => '説明のみ',
        ]);

        $response->assertStatus(400);
    }

    public function test_商品一覧をCoreのAPIから取得できること(): void
    {
        Http::fake([
            'http://localhost:8080/api/products' => Http::response([
                ['id' => 1, 'name' => '商品A', 'price' => 1000, 'stock' => 100],
                ['id' => 2, 'name' => '商品B', 'price' => 2000, 'stock' => 50],
            ], 200),
        ]);

        $response = $this->getJson('/api/products');

        $response->assertStatus(200)
                 ->assertJsonFragment(['name' => '商品A']);
    }
}
