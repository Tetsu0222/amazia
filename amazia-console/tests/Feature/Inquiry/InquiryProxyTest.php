<?php

namespace Tests\Feature\Inquiry;

use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * フェーズ18: Console Inquiry Pass-through Controller の Feature テスト。
 * 設計書: docs/design/phase11_20/phase18_inquiry_management.md（r3）
 *
 * Core 側の operation_logs 記録は Core で完結（既存 phase14 / 15 / 17 と同方針）。
 * Pass-through 層は Core API への中継・X-User-Id ヘッダ付与・クエリ透過のみ検証する。
 */
class InquiryProxyTest extends TestCase
{
    private string $baseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function test_unread_count_を中継する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/inquiries/unread-count" => Http::response(
                ['count' => 7], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/inquiries/unread-count')
             ->assertStatus(200)
             ->assertJsonFragment(['count' => 7]);
    }

    public function test_一覧の検索クエリがCoreに透過される(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/inquiries*" => Http::response(
                ['content' => [], 'totalElements' => 0], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/inquiries?status=NEW&targetType=delivery&userName=山田&page=0&size=20')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            $url = $request->url();
            return str_contains($url, 'status=NEW')
                && str_contains($url, 'targetType=delivery')
                && (str_contains($url, '%E5%B1%B1%E7%94%B0') || str_contains($url, '山田'))
                && str_contains($url, 'size=20');
        });
    }

    public function test_詳細を中継する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/inquiries/42" => Http::response([
                'id' => 42, 'subject' => 'お問い合わせ',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/inquiries/42')
             ->assertStatus(200)
             ->assertJsonFragment(['subject' => 'お問い合わせ']);
    }

    public function test_詳細404を透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/inquiries/999" => Http::response(
                ['message' => 'Not Found'], 404),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/console/inquiries/999')
             ->assertStatus(404);
    }

    public function test_返信投稿でX_User_IdヘッダがCoreに透過される(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/inquiries/10/messages" => Http::response(
                ['id' => 100], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/console/inquiries/10/messages', [
                 'message' => 'ご回答します',
                 'isInternalNote' => false,
             ])
             ->assertStatus(200)
             ->assertJsonFragment(['id' => 100]);

        Http::assertSent(fn ($req) => $req->hasHeader('X-User-Id'));
    }

    public function test_内部メモ投稿のpayloadが透過される(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/inquiries/10/messages" => Http::response(
                ['id' => 101], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/console/inquiries/10/messages', [
                 'message' => '内部メモ',
                 'isInternalNote' => true,
             ])
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            $body = $request->data();
            return ($body['isInternalNote'] ?? null) === true
                && ($body['message'] ?? null) === '内部メモ';
        });
    }

    public function test_ステータス変更を中継する(): void
    {
        Http::fake([
            "{$this->baseUrl}/console/inquiries/10/status" => Http::response(
                ['id' => 10, 'status' => 'IN_PROGRESS'], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->patchJson('/api/console/inquiries/10/status', [
                 'newStatus' => 'IN_PROGRESS',
                 'reason' => '対応中',
             ])
             ->assertStatus(200)
             ->assertJsonFragment(['status' => 'IN_PROGRESS']);
    }

    public function test_ステータス変更_未知のステータスは422で弾く(): void
    {
        $this->withHeaders($this->authHeaders('admin'))
             ->patchJson('/api/console/inquiries/10/status', [
                 'newStatus' => 'UNKNOWN_VALUE',
             ])
             ->assertStatus(422);
    }

    public function test_文字数上限超過の返信は422で弾く(): void
    {
        $over = str_repeat('あ', config('app.inquiry.message_max_length') + 1);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/console/inquiries/10/messages', [
                 'message' => $over,
                 'isInternalNote' => false,
             ])
             ->assertStatus(422);
    }
}
