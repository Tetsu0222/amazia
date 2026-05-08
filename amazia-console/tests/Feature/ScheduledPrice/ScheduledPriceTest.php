<?php

namespace Tests\Feature\ScheduledPrice;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ17 Step 5.5-2（設計書 §13.5.2）：
 * 予約価格 / 価格履歴の Console Pass-through テスト。
 */
class ScheduledPriceTest extends TestCase
{
    private string $coreBaseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withHeaders($this->authHeaders());
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    // ─── 予約価格 取得 ───────────────────────────────────

    public function test_予約価格をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/scheduled-price" => Http::response([
                'id' => 1, 'skuId' => 1, 'scheduledPrice' => 1500,
                'applyDate' => '2026-06-01', 'isPending' => true,
            ], 200),
        ]);

        $response = $this->getJson('/api/skus/1/scheduled-price');

        $response->assertStatus(200)->assertJsonFragment(['scheduledPrice' => 1500]);
    }

    public function test_予約価格が無いときは204でそのまま返ること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/scheduled-price" => Http::response(null, 204),
        ]);

        $response = $this->getJson('/api/skus/1/scheduled-price');
        $response->assertStatus(204);
    }

    // ─── 予約価格 登録（UPSERT） ─────────────────────────

    public function test_予約価格をCoreにUPSERTできること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/scheduled-price" => Http::response([
                'id' => 1, 'skuId' => 1, 'scheduledPrice' => 2000,
                'applyDate' => '2026-06-01', 'isPending' => true,
            ], 200),
        ]);

        $response = $this->putJson('/api/skus/1/scheduled-price', [
            'scheduledPrice' => 2000,
            'applyDate'      => '2026-06-01',
        ]);

        $response->assertStatus(200)->assertJsonFragment(['scheduledPrice' => 2000]);
        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreBaseUrl}/skus/1/scheduled-price"
            && $req->method() === 'PUT'
            && $req['scheduledPrice'] === 2000
            && $req['applyDate'] === '2026-06-01'
        );
    }

    public function test_予約価格のUPSERTでscheduledPriceは必須(): void
    {
        $response = $this->putJson('/api/skus/1/scheduled-price', [
            'applyDate' => '2026-06-01',
        ]);
        $response->assertStatus(400);
    }

    public function test_予約価格のUPSERTでapplyDateは必須(): void
    {
        $response = $this->putJson('/api/skus/1/scheduled-price', [
            'scheduledPrice' => 1500,
        ]);
        $response->assertStatus(400);
    }

    public function test_予約価格のapplyDateが過去日のときCore422をそのまま返すこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/scheduled-price" => Http::response([
                'message' => 'applyDate は今日以降で指定してください',
            ], 422),
        ]);

        $response = $this->putJson('/api/skus/1/scheduled-price', [
            'scheduledPrice' => 1500,
            'applyDate'      => '2020-01-01',
        ]);
        $response->assertStatus(422);
    }

    // ─── 予約価格 削除 ───────────────────────────────────

    public function test_予約価格を削除できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/scheduled-price" => Http::response(null, 204),
        ]);

        $response = $this->deleteJson('/api/skus/1/scheduled-price');
        $response->assertStatus(204);
        Http::assertSent(fn($req) =>
            $req->method() === 'DELETE'
            && $req->url() === "{$this->coreBaseUrl}/skus/1/scheduled-price"
        );
    }

    // ─── 価格履歴 ────────────────────────────────────────

    public function test_価格履歴をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/prices/history" => Http::response([
                ['id' => 2, 'skuId' => 1, 'price' => 1500, 'startDate' => '2026-04-01', 'isActive' => true],
                ['id' => 1, 'skuId' => 1, 'price' => 800,  'startDate' => '2026-03-01',
                 'endDate' => '2026-03-31', 'isActive' => false],
            ], 200),
        ]);

        $response = $this->getJson('/api/skus/1/prices/history');
        $response->assertStatus(200)
                 ->assertJsonFragment(['price' => 1500])
                 ->assertJsonFragment(['price' => 800]);
    }
}
