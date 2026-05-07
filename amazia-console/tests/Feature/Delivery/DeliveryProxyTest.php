<?php

namespace Tests\Feature\Delivery;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * フェーズ15 Step C-1: 配送管理 Console 中継のテスト。
 *
 * Core の /api/deliveries 各エンドポイントを中継するため、
 * Http::fake() で Core レスポンスを差し替えて中継挙動と権限ガードを検証する。
 */
class DeliveryProxyTest extends TestCase
{
    private string $baseUrl;
    private int $homeDeliveryId;
    private int $pendingId;
    private int $shippedId;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
        // config/app.php 経由で require されるため、ルート名前空間は 'app'
        $this->homeDeliveryId = (int) config('app.delivery.shipping_methods.home_delivery_id');
        $this->pendingId = (int) config('app.sales.shipping_statuses.pending_id');
        $this->shippedId = (int) config('app.sales.shipping_statuses.shipped_id');
    }

    public function test_配送詳細を取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries/1" => Http::response([
                'id' => 1, 'salesId' => 100, 'shippingStatusId' => $this->pendingId,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/deliveries/1')
             ->assertStatus(200)
             ->assertJsonFragment(['id' => 1])
             ->assertJsonFragment(['shippingStatusId' => $this->pendingId]);
    }

    public function test_配送一覧を取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries*" => Http::response([
                ['id' => 1, 'shippingStatusId' => $this->pendingId],
                ['id' => 2, 'shippingStatusId' => $this->shippedId],
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/deliveries')
             ->assertStatus(200)
             ->assertJsonCount(2);
    }

    public function test_配送一覧フィルタがCoreにクエリで渡る(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries*" => Http::response([], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/deliveries?shippingStatusId=' . $this->shippedId)
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            return str_contains($request->url(), 'shippingStatusId=' . $this->shippedId);
        });
    }

    public function test_userロールはステータス更新で403が返る(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->patchJson('/api/deliveries/1/status', ['shippingStatusId' => $this->shippedId])
             ->assertStatus(403);
    }

    public function test_supervisorはステータス更新できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries/1/status" => Http::response([
                'id' => 1, 'shippingStatusId' => $this->shippedId,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('supervisor'))
             ->patchJson('/api/deliveries/1/status', [
                 'shippingStatusId' => $this->shippedId,
                 'reason'           => '出荷完了',
             ])
             ->assertStatus(200)
             ->assertJsonFragment(['shippingStatusId' => $this->shippedId]);
    }

    public function test_ステータス更新の在庫不足_409を透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries/1/status" => Http::response([
                'message' => 'preorder shipment blocked: insufficient stock',
            ], 409),
        ]);

        $shippedId = (int) config('app.sales.shipping_statuses.shipped_id');
        $this->withHeaders($this->authHeaders('admin'))
             ->patchJson('/api/deliveries/1/status', ['shippingStatusId' => $shippedId])
             ->assertStatus(409);
    }

    public function test_配送先住所変更ができる(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries/1/address" => Http::response([
                'id' => 1, 'shippingAddressId' => 99,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->patchJson('/api/deliveries/1/address', [
                 'shippingAddressId' => 99,
                 'reason'            => 'ユーザ希望',
             ])
             ->assertStatus(200);
    }

    public function test_配送先住所変更_オーナー外403を透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries/1/address" => Http::response([
                'message' => 'address does not belong to sales owner',
            ], 403),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->patchJson('/api/deliveries/1/address', ['shippingAddressId' => 99])
             ->assertStatus(403);
    }

    public function test_配送予定日変更ができる(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries/1/scheduled-date" => Http::response([
                'id' => 1, 'scheduledDate' => '2026-05-20',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->patchJson('/api/deliveries/1/scheduled-date', [
                 'scheduledDate' => '2026-05-20',
                 'reason'        => '出荷遅延',
             ])
             ->assertStatus(200)
             ->assertJsonFragment(['scheduledDate' => '2026-05-20']);
    }

    public function test_追跡番号登録ができる(): void
    {
        Http::fake([
            "{$this->baseUrl}/deliveries/1/tracking-code" => Http::response([
                'id' => 1, 'trackingCode' => 'YMT-1234',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->patchJson('/api/deliveries/1/tracking-code', ['trackingCode' => 'YMT-1234'])
             ->assertStatus(200)
             ->assertJsonFragment(['trackingCode' => 'YMT-1234']);
    }

    public function test_X_User_IdヘッダがCoreに渡る(): void
    {
        $shippedId = (int) config('app.sales.shipping_statuses.shipped_id');
        Http::fake([
            "{$this->baseUrl}/deliveries/1/status" => Http::response([
                'id' => 1, 'shippingStatusId' => $shippedId,
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->patchJson('/api/deliveries/1/status', ['shippingStatusId' => $shippedId])
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            return $request->hasHeader('X-User-Id', '1');
        });
    }

    public function test_配送方法マスタを取得できる(): void
    {
        Http::fake([
            "{$this->baseUrl}/shipping-methods" => Http::response([
                ['id' => 1, 'name' => 'home_delivery', 'description' => '宅配'],
                ['id' => 2, 'name' => 'konbini_pickup', 'description' => 'コンビニ受取'],
                ['id' => 3, 'name' => 'dropoff', 'description' => '置き配'],
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/shipping-methods')
             ->assertStatus(200)
             ->assertJsonCount(3);
    }
}
