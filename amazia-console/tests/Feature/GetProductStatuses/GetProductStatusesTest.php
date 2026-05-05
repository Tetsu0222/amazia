<?php

namespace Tests\Feature\GetProductStatuses;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class GetProductStatusesTest extends TestCase
{
    private string $coreBaseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withHeaders($this->authHeaders());
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function test_ステータスマスタ一覧が取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/product-statuses" => Http::response([
                ['code' => 'WAITING',     'name' => '入荷待',    'sortOrder' => 1],
                ['code' => 'RESERVATION', 'name' => '予約受付中', 'sortOrder' => 2],
                ['code' => 'ON_SALE',     'name' => '販売中',    'sortOrder' => 3],
            ], 200),
        ]);

        $this->getJson('/api/product-statuses')->assertStatus(200)->assertJsonCount(3);
    }

    public function test_CoreAPIが500を返すときステータスマスタ取得も500を返すこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/product-statuses" => Http::response([], 500),
        ]);

        $this->getJson('/api/product-statuses')->assertStatus(500);
    }
}
