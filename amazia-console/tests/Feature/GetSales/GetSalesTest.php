<?php

namespace Tests\Feature\GetSales;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * 売上一覧取得 API（GET /api/sales）の Feature テスト。
 *
 * Core の GET /api/sales を中継するだけのため、
 * Http::fake() で Core レスポンスを差し替えて中継挙動を検証する。
 */
class GetSalesTest extends TestCase
{
    public function test_売上一覧が取得できること(): void
    {
        $payload = [
            [
                'salesId' => 1,
                'salesDate' => '2026-05-06',
                'shippingDate' => null,
                'customerId' => 11,
                'customerName' => '田中 太郎',
                'skuId' => 101,
                'productName' => '商品A',
                'color' => '赤',
                'size' => 'M',
                'quantity' => 2,
                'amount' => 6000,
                'shippingStatusCode' => 'PENDING',
                'shippingMethodId' => 1,
                'paymentMethodId' => 1,
                'paymentMethodName' => 'credit_card',
                'preorder' => false,
            ],
        ];

        $base = config('services.amazia_core.base_url');
        Http::fake([
            "{$base}/sales" => Http::response($payload, 200),
        ]);

        $response = $this->withHeaders($this->authHeaders())->getJson('/api/sales');

        $response->assertStatus(200)
                 ->assertJsonCount(1)
                 ->assertJsonFragment(['productName' => '商品A'])
                 ->assertJsonFragment(['customerName' => '田中 太郎'])
                 ->assertJsonFragment(['paymentMethodName' => 'credit_card']);
    }

    public function test_Core応答ステータスを透過すること(): void
    {
        $base = config('services.amazia_core.base_url');
        Http::fake([
            "{$base}/sales" => Http::response(['error' => 'core down'], 503),
        ]);

        $response = $this->withHeaders($this->authHeaders())->getJson('/api/sales');

        $response->assertStatus(503);
    }
}
