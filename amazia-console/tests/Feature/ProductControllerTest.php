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

    public function test_商品1件をCoreのAPIから取得できること(): void
    {
        Http::fake([
            'http://localhost:8080/api/products/1' => Http::response([
                'id' => 1, 'name' => '商品A', 'description' => '説明A', 'price' => 1000, 'stock' => 100,
            ], 200),
        ]);

        $response = $this->getJson('/api/products/1');

        $response->assertStatus(200)
                 ->assertJsonFragment(['name' => '商品A']);
    }

    public function test_商品更新リクエストがCoreのAPIに飛ぶこと(): void
    {
        Http::fake([
            'http://localhost:8080/api/products/1' => Http::response([
                'id' => 1,
                'name' => '商品A改',
                'description' => '説明A改',
                'price' => 2000,
                'stock' => 50,
            ], 200),
        ]);

        $response = $this->putJson('/api/products/1', [
            'name'        => '商品A改',
            'description' => '説明A改',
            'price'       => 2000,
            'stock'       => 50,
        ]);

        $response->assertStatus(200)
                 ->assertJsonFragment(['name' => '商品A改']);

        Http::assertSent(function ($request) {
            return $request->url() === 'http://localhost:8080/api/products/1'
                && $request['name'] === '商品A改';
        });
    }

    public function test_存在しない商品を更新しようとしたとき404が返ること(): void
    {
        Http::fake([
            'http://localhost:8080/api/products/999' => Http::response([], 404),
        ]);

        $response = $this->putJson('/api/products/999', [
            'name'  => '商品X',
            'price' => 1000,
            'stock' => 10,
        ]);

        $response->assertStatus(404);
    }

    public function test_商品削除リクエストがCoreのAPIに飛ぶこと(): void
    {
        Http::fake([
            'http://localhost:8080/api/products/1' => Http::response(null, 204),
        ]);

        $response = $this->deleteJson('/api/products/1');

        $response->assertStatus(204);

        Http::assertSent(function ($request) {
            return $request->url() === 'http://localhost:8080/api/products/1'
                && $request->method() === 'DELETE';
        });
    }

    public function test_存在しない商品を削除しようとしたとき404が返ること(): void
    {
        Http::fake([
            'http://localhost:8080/api/products/999' => Http::response([], 404),
        ]);

        $response = $this->deleteJson('/api/products/999');

        $response->assertStatus(404);
    }
}
