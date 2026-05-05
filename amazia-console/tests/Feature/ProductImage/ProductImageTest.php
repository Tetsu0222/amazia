<?php

namespace Tests\Feature\ProductImage;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;

class ProductImageTest extends TestCase
{
    private string $coreBaseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withHeaders($this->authHeaders());
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    private function validPng(): UploadedFile
    {
        return UploadedFile::fake()->create('test.png', 10, 'image/png');
    }

    public function test_画像一覧をCoreから取得できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/1/images" => Http::response([
                ['id' => 1, 'productId' => 1, 'imagePath' => '1/uuid.png', 'sortOrder' => 1],
            ], 200),
        ]);

        $response = $this->getJson('/api/products/1/images');

        $response->assertStatus(200)
                 ->assertJsonFragment(['sortOrder' => 1]);

        Http::assertSent(fn($req) =>
            $req->url() === "{$this->coreBaseUrl}/products/1/images"
        );
    }

    public function test_PNG画像をCoreにアップロードできること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/1/images" => Http::response([
                'id' => 1, 'productId' => 1, 'imagePath' => '1/uuid.png', 'sortOrder' => 1,
            ], 201),
        ]);

        $response = $this->call('POST', '/api/products/1/images', [], [], ['image' => $this->validPng()]);

        $this->assertEquals(201, $response->status());
        $this->assertArrayHasKey('productId', json_decode($response->getContent(), true));
    }

    public function test_PNG以外のファイルは400を返すこと(): void
    {
        $jpeg = UploadedFile::fake()->create('test.jpg', 10, 'image/jpeg');

        $response = $this->postJson('/api/products/1/images', [
            'image' => $jpeg,
        ]);

        $response->assertStatus(400);
    }

    public function test_200KBを超えるファイルは400を返すこと(): void
    {
        $large = UploadedFile::fake()->create('big.png', 210, 'image/png');

        $response = $this->postJson('/api/products/1/images', [
            'image' => $large,
        ]);

        $response->assertStatus(400);
    }

    public function test_ファイルなしの場合は400を返すこと(): void
    {
        $response = $this->postJson('/api/products/1/images', []);
        $response->assertStatus(400);
    }

    public function test_画像のsort_orderを変更できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/product-images/1/sort" => Http::response([
                'id' => 1, 'productId' => 1, 'imagePath' => '1/uuid.png', 'sortOrder' => 5,
            ], 200),
        ]);

        $response = $this->putJson('/api/product-images/1/sort', ['sortOrder' => 5]);

        $response->assertStatus(200)
                 ->assertJsonFragment(['sortOrder' => 5]);
    }

    public function test_画像を削除できること(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/product-images/1" => Http::response(null, 204),
        ]);

        $response = $this->deleteJson('/api/product-images/1');

        $response->assertStatus(204);
    }

    public function test_Coreがエラーのときはそのステータスをそのまま返すこと(): void
    {
        Http::fake([
            "{$this->coreBaseUrl}/products/999/images" => Http::response(['message' => 'Not Found'], 404),
        ]);

        $response = $this->getJson('/api/products/999/images');
        $response->assertStatus(404);
    }
}
