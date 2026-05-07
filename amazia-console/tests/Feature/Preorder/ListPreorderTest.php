<?php

namespace Tests\Feature\Preorder;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * 予約一覧取得 API（GET /api/preorders）の Feature テスト。
 *
 * 設計書: docs/design/phase11_20/phase16_ui_ux_improvement.md §2-9
 * Core の GET /api/products/preorders を中継するだけのため、
 * Http::fake() で Core レスポンスを差し替えて中継挙動を検証する。
 */
class ListPreorderTest extends TestCase
{
    public function test_認証なしで401が返ること(): void
    {
        $response = $this->getJson('/api/preorders');

        $response->assertStatus(401);
    }

    public function test_予約商品一覧が取得できること(): void
    {
        $payload = [
            [
                'productId' => 12,
                'productName' => 'Tシャツ夏モデル',
                'preorderStartDate' => '2026-04-01',
                'releaseDate' => '2026-08-01',
                'daysUntilRelease' => 86,
                'acceptPreorder' => true,
                'isActive' => true,
                'preorderQuantity' => 47,
                'preorderAmount' => 235000,
            ],
        ];

        $base = config('services.amazia_core.base_url');
        Http::fake([
            "{$base}/products/preorders" => Http::response($payload, 200),
        ]);

        $response = $this->withHeaders($this->authHeaders())->getJson('/api/preorders');

        $response->assertStatus(200)
                 ->assertJsonCount(1)
                 ->assertJsonFragment(['productName' => 'Tシャツ夏モデル'])
                 ->assertJsonFragment(['preorderQuantity' => 47]);
    }

    public function test_Core応答ステータスを透過すること(): void
    {
        $base = config('services.amazia_core.base_url');
        Http::fake([
            "{$base}/products/preorders" => Http::response(['error' => 'core down'], 503),
        ]);

        $response = $this->withHeaders($this->authHeaders())->getJson('/api/preorders');

        $response->assertStatus(503);
    }
}
