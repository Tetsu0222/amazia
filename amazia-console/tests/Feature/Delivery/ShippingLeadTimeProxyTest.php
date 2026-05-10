<?php

namespace Tests\Feature\Delivery;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * フェーズX-5：都道府県別リードタイムマスタ Console 中継のテスト。
 *
 * - GET / PATCH 共に approver_roles（supervisor 以上）のみ許可
 * - lead_time_days = 0 は許容（無効化運用）／負数は 422
 * - operation_logs 記録は Core 側 Service が担当（Console 側は中継のみ）
 */
class ShippingLeadTimeProxyTest extends TestCase
{
    private string $baseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function test_supervisorはマスタ一覧を取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/shipping-lead-times*" => Http::response([
                ['id' => 1, 'shippingMethodId' => 1, 'prefecture' => '北海道', 'leadTimeDays' => 5],
                ['id' => 2, 'shippingMethodId' => 1, 'prefecture' => '青森県', 'leadTimeDays' => 3],
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->getJson('/api/shipping-lead-times')
             ->assertStatus(200)
             ->assertJsonCount(2);
    }

    public function test_userロールはマスタ一覧で403が返る(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->getJson('/api/shipping-lead-times')
             ->assertStatus(403);
    }

    public function test_shippingMethodIdクエリがCoreにそのまま渡る(): void
    {
        Http::fake([
            "{$this->baseUrl}/shipping-lead-times*" => Http::response([], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->getJson('/api/shipping-lead-times?shippingMethodId=1')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            return str_contains($request->url(), 'shippingMethodId=1');
        });
    }

    public function test_supervisorは個別取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/shipping-lead-times/10" => Http::response([
                'id' => 10, 'shippingMethodId' => 1, 'prefecture' => '東京都', 'leadTimeDays' => 3,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->getJson('/api/shipping-lead-times/10')
             ->assertStatus(200)
             ->assertJsonFragment(['prefecture' => '東京都']);
    }

    public function test_userロールは個別取得で403が返る(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->getJson('/api/shipping-lead-times/10')
             ->assertStatus(403);
    }

    public function test_supervisorはPATCHで更新を中継できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/shipping-lead-times/10" => Http::response([
                'id' => 10, 'shippingMethodId' => 1, 'prefecture' => '東京都', 'leadTimeDays' => 4,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->patchJson('/api/shipping-lead-times/10', ['leadTimeDays' => 4])
             ->assertStatus(200)
             ->assertJsonFragment(['leadTimeDays' => 4]);
    }

    public function test_userロールはPATCHで403が返る(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->patchJson('/api/shipping-lead-times/10', ['leadTimeDays' => 4])
             ->assertStatus(403);
    }

    public function test_lead_time_daysが負数のPATCHは422を返す(): void
    {
        $this->withHeaders($this->authHeaders('supervisor'))
             ->patchJson('/api/shipping-lead-times/10', ['leadTimeDays' => -1])
             ->assertStatus(422);
    }

    public function test_lead_time_daysが0のPATCHは許容される_無効化運用(): void
    {
        Http::fake([
            "{$this->baseUrl}/shipping-lead-times/10" => Http::response([
                'id' => 10, 'shippingMethodId' => 1, 'prefecture' => '東京都', 'leadTimeDays' => 0,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->patchJson('/api/shipping-lead-times/10', ['leadTimeDays' => 0])
             ->assertStatus(200)
             ->assertJsonFragment(['leadTimeDays' => 0]);
    }

    public function test_lead_time_daysが文字列のPATCHは422を返す(): void
    {
        $this->withHeaders($this->authHeaders('supervisor'))
             ->patchJson('/api/shipping-lead-times/10', ['leadTimeDays' => 'abc'])
             ->assertStatus(422);
    }

    public function test_X_User_IdヘッダがCoreに渡る(): void
    {
        Http::fake([
            "{$this->baseUrl}/shipping-lead-times/10" => Http::response([
                'id' => 10, 'shippingMethodId' => 1, 'prefecture' => '東京都', 'leadTimeDays' => 4,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->patchJson('/api/shipping-lead-times/10', ['leadTimeDays' => 4])
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            return $request->hasHeader('X-User-Id');
        });
    }
}
