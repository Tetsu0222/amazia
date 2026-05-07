<?php

namespace Tests\Feature\Sku;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class SkuTest extends TestCase
{
    private string $coreBaseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withHeaders($this->authHeaders());
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    // ─── SKU ─────────────────────────────────────────────

    public function test_SKU一覧をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/1/skus" => Http::response([
                ['id' => 1, 'productId' => 1, 'color' => 'Red', 'size' => 'M', 'skuCode' => 'ABC123'],
            ], 200),
        ]);

        $response = $this->getJson('/api/products/1/skus');

        $response->assertStatus(200)->assertJsonFragment(['color' => 'Red']);
    }

    public function test_SKUをCoreに登録できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/1/skus" => Http::response([
                'id' => 1, 'productId' => 1, 'color' => 'Red', 'size' => 'M', 'skuCode' => 'ABC123',
            ], 201),
        ]);

        $response = $this->postJson('/api/products/1/skus', [
            'color' => 'Red', 'size' => 'M',
        ]);

        $response->assertStatus(201)->assertJsonFragment(['color' => 'Red', 'size' => 'M']);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreBaseUrl}/products/1/skus"
            && $req['color'] === 'Red'
        );
    }

    public function test_SKU登録時に色とサイズは必須であること(): void
    {
        $response = $this->postJson('/api/products/1/skus', ['color' => 'Red']);
        $response->assertStatus(400);
    }

    public function test_Coreがエラーのときはそのステータスをそのまま返すこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/999/skus" => Http::response(['message' => 'Not Found'], 404),
        ]);

        $response = $this->postJson('/api/products/999/skus', ['color' => 'Red', 'size' => 'M']);
        $response->assertStatus(404);
    }

    // ─── 価格 ─────────────────────────────────────────────

    public function test_SKU価格をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/prices" => Http::response([
                'id' => 1, 'skuId' => 1, 'price' => 2000,
            ], 200),
        ]);

        $response = $this->getJson('/api/skus/1/prices');
        $response->assertStatus(200)->assertJsonFragment(['price' => 2000]);
    }

    public function test_SKU価格をCoreに登録できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/prices" => Http::response([
                'id' => 1, 'skuId' => 1, 'price' => 3000,
            ], 201),
        ]);

        $response = $this->postJson('/api/skus/1/prices', [
            'price' => 3000, 'startDate' => '2026-01-01',
        ]);

        $response->assertStatus(201)->assertJsonFragment(['price' => 3000]);
    }

    public function test_SKU価格登録時に価格は必須であること(): void
    {
        $response = $this->postJson('/api/skus/1/prices', ['startDate' => '2026-01-01']);
        $response->assertStatus(400);
    }

    // ─── 在庫 ─────────────────────────────────────────────

    public function test_SKU在庫をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/stocks" => Http::response([
                'id' => 1, 'skuId' => 1, 'quantity' => 100,
            ], 200),
        ]);

        $response = $this->getJson('/api/skus/1/stocks');
        $response->assertStatus(200)->assertJsonFragment(['quantity' => 100]);
    }

    public function test_SKU在庫入荷をCoreに登録できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/stocks/receive" => Http::response([
                'id' => 1, 'skuId' => 1, 'quantity' => 50,
            ], 201),
        ]);

        $response = $this->postJson('/api/skus/1/stocks/receive', ['quantity' => 50]);
        $response->assertStatus(201)->assertJsonFragment(['quantity' => 50]);
    }

    public function test_SKU在庫入荷時に数量は必須であること(): void
    {
        $response = $this->postJson('/api/skus/1/stocks/receive', []);
        $response->assertStatus(400);
    }

    public function test_SKU在庫履歴をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/1/stocks/history" => Http::response([
                ['id' => 1, 'skuId' => 1, 'type' => 'receive', 'quantity' => 50],
            ], 200),
        ]);

        $response = $this->getJson('/api/skus/1/stocks/history');
        $response->assertStatus(200)->assertJsonFragment(['type' => 'receive']);
    }

    // ─── Excel入荷 ───────────────────────────────────────
    // ImportProductSkuStockService の単体検証。
    // ※ Controller の MIME バリデーションは商品Excel取込と同方式を踏襲しており、
    //    実ファイル拡張子による経路ロジックは Service 層に閉じているためここで検証する。

    public function test_Excelアップロードで複数SKUの入荷が登録できること(): void
    {
        // フェーズ16 Step6-6 で /skus/{id}/stocks/receive 直叩きから /inbounds 経由に切替。
        Http::fake([
            "{$this->coreBaseUrl}/skus/by-code/SKU-A" => Http::response(
                ['id' => 11, 'productId' => 101, 'skuCode' => 'SKU-A'], 200),
            "{$this->coreBaseUrl}/skus/by-code/SKU-B" => Http::response(
                ['id' => 22, 'productId' => 202, 'skuCode' => 'SKU-B'], 200),
            "{$this->coreBaseUrl}/inbounds" => Http::response(
                ['id' => 1, 'productId' => 101, 'skuId' => 11, 'quantity' => 30], 201),
        ]);

        $path = $this->makeStockCsv([
            ['sku_code', 'quantity'],
            ['SKU-A',    30],
            ['SKU-B',    50],
        ]);

        $service = app(\App\Sku\Service\ImportProductSkuStockService::class);
        $result  = $service->importFromFile($path);

        $this->assertSame(2, $result['succeeded']);
        $this->assertSame([], $result['failed']);
    }

    public function test_Excel入荷でtracking_code列が_inbounds_に渡ること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/by-code/SKU-A" => Http::response(
                ['id' => 11, 'productId' => 101, 'skuCode' => 'SKU-A'], 200),
            "{$this->coreBaseUrl}/inbounds" => Http::response(
                ['id' => 1, 'productId' => 101, 'skuId' => 11, 'quantity' => 5,
                 'trackingCode' => 'TRK-12345'], 201),
        ]);

        $path = $this->makeStockCsv([
            ['sku_code', 'quantity', 'tracking_code'],
            ['SKU-A',    5,          'TRK-12345'],
        ]);

        $service = app(\App\Sku\Service\ImportProductSkuStockService::class);
        $result  = $service->importFromFile($path);

        $this->assertSame(1, $result['succeeded']);
        Http::assertSent(function ($request) {
            return str_ends_with($request->url(), '/inbounds')
                && $request['trackingCode'] === 'TRK-12345'
                && $request['productId']    === 101
                && $request['skuId']        === 11;
        });
    }

    public function test_tracking_code列がない既存Excelでも互換動作すること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/by-code/SKU-A" => Http::response(
                ['id' => 11, 'productId' => 101, 'skuCode' => 'SKU-A'], 200),
            "{$this->coreBaseUrl}/inbounds" => Http::response(
                ['id' => 1, 'productId' => 101, 'skuId' => 11, 'quantity' => 7], 201),
        ]);

        $path = $this->makeStockCsv([
            ['sku_code', 'quantity'],
            ['SKU-A',    7],
        ]);

        $service = app(\App\Sku\Service\ImportProductSkuStockService::class);
        $result  = $service->importFromFile($path);

        $this->assertSame(1, $result['succeeded']);
        Http::assertSent(function ($request) {
            return str_ends_with($request->url(), '/inbounds')
                && !isset($request['trackingCode']);
        });
    }

    public function test_存在しないSKUコードはエラー行として返ること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/skus/by-code/SKU-A" => Http::response(
                ['id' => 11, 'productId' => 101, 'skuCode' => 'SKU-A'], 200),
            "{$this->coreBaseUrl}/skus/by-code/UNKNOWN" => Http::response(
                ['message' => 'Not Found'], 404),
            "{$this->coreBaseUrl}/inbounds" => Http::response(
                ['id' => 1, 'productId' => 101, 'skuId' => 11, 'quantity' => 10], 201),
        ]);

        $path = $this->makeStockCsv([
            ['sku_code', 'quantity'],
            ['SKU-A',    10],
            ['UNKNOWN',  5],
        ]);

        $service = app(\App\Sku\Service\ImportProductSkuStockService::class);
        $result  = $service->importFromFile($path);

        $this->assertSame(1, $result['succeeded']);
        $this->assertCount(1, $result['failed']);
        $this->assertSame('UNKNOWN', $result['failed'][0]['row']['sku_code']);
        $this->assertStringContainsString("'UNKNOWN'", $result['failed'][0]['reason']);
    }

    public function test_quantityが0以下の行はエラーとなり減算は不可であること(): void
    {
        Http::fake();

        $path = $this->makeStockCsv([
            ['sku_code', 'quantity'],
            ['SKU-A',    -5],
            ['SKU-B',    0],
        ]);

        $service = app(\App\Sku\Service\ImportProductSkuStockService::class);
        $result  = $service->importFromFile($path);

        $this->assertSame(0, $result['succeeded']);
        $this->assertCount(2, $result['failed']);
        $this->assertSame('quantityは1以上の整数である必要があります（減算は不可）', $result['failed'][0]['reason']);
        $this->assertSame('quantityは1以上の整数である必要があります（減算は不可）', $result['failed'][1]['reason']);

        Http::assertNothingSent();
    }

    public function test_必須項目が欠けている行はエラーとなること(): void
    {
        Http::fake();

        $path = $this->makeStockCsv([
            ['sku_code', 'quantity'],
            ['',         10],
            ['SKU-B',    ''],
        ]);

        $service = app(\App\Sku\Service\ImportProductSkuStockService::class);
        $result  = $service->importFromFile($path);

        $this->assertSame(0, $result['succeeded']);
        $this->assertSame('必須項目(sku_code)が不足',         $result['failed'][0]['reason']);
        $this->assertSame('必須項目(quantity)が不足または不正', $result['failed'][1]['reason']);

        Http::assertNothingSent();
    }

    public function test_Excel入荷エンドポイントが認証済みでアクセス可能であること(): void
    {
        // Controller-routing スモークテスト：
        // 認証ミドルウェアを通過し、ファイル必須バリデーションが効くこと。
        $response = $this->postJson('/api/skus/stocks/import', []);
        $response->assertStatus(422);
    }

    private function makeStockCsv(array $rows): string
    {
        $path = tempnam(sys_get_temp_dir(), 'sku_import_') . '.csv';
        $fp   = fopen($path, 'w');
        foreach ($rows as $row) {
            fputcsv($fp, array_map(fn($v) => $v ?? '', $row));
        }
        fclose($fp);
        return $path;
    }
}
