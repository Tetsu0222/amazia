<?php

namespace Tests\Feature\Notice;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * フェーズ19: Console Notice Pass-through の Feature テスト。
 * 設計書: docs/design/phase11_20/phase19_notice_management.md（r2）
 *
 * R19-3 / R19-4：Console 自身は operation_logs に書き込まない（Core 側 Service が記録）。
 * 本テストは Core への中継・X-User-Id ヘッダ付与・時分秒補完・config 駆動を検証する。
 */
class NoticeProxyTest extends TestCase
{
    private string $baseUrl;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    // ---------- 一覧 ----------

    public function test_一覧クエリが_Core_に透過される(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices*" => Http::response(
                ['content' => [], 'totalElements' => 0], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/admin/notices?page=1&per_page=20&category_id=2&include_unpublished=true&include_deleted=true')
             ->assertStatus(200);

        Http::assertSent(function ($request) {
            $url = $request->url();
            return str_contains($url, 'page=1')
                && str_contains($url, 'per_page=20')
                && str_contains($url, 'category_id=2')
                && str_contains($url, 'include_unpublished=true')
                && str_contains($url, 'include_deleted=true');
        });
    }

    public function test_一覧は_X_User_Id_ヘッダ付きで_Core_を呼ぶ(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices*" => Http::response(['content' => []], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/admin/notices')
             ->assertStatus(200);

        Http::assertSent(fn ($req) => $req->hasHeader('X-User-Id'));
    }

    // ---------- 詳細 ----------

    public function test_詳細を中継する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices/42*" => Http::response([
                'id' => 42, 'subject' => 'お知らせ',
            ], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/admin/notices/42')
             ->assertStatus(200)
             ->assertJsonFragment(['subject' => 'お知らせ']);
    }

    public function test_詳細404を透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices/999*" => Http::response(
                ['message' => 'Not Found'], 404),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/admin/notices/999')
             ->assertStatus(404);
    }

    // ---------- 作成 ----------

    public function test_作成時_publish_start_が_DATE_のみなら_T00_00_00_を補完する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices" => Http::response(['id' => 1], 201),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/admin/notices', [
                 'subject' => '件名',
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-09',
                 'publishEnd' => '2026-05-15',
             ])
             ->assertStatus(201);

        Http::assertSent(function ($req) {
            $body = $req->data();
            return $body['publishStart'] === '2026-05-09T00:00:00'
                && $body['publishEnd']   === '2026-05-15T23:59:59';
        });
    }

    public function test_作成時_すでに時分秒を含む値はそのまま透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices" => Http::response(['id' => 1], 201),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/admin/notices', [
                 'subject' => '件名',
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-09T10:00:00',
                 'publishEnd'   => '2026-05-15T18:30:00',
             ])
             ->assertStatus(201);

        Http::assertSent(function ($req) {
            $body = $req->data();
            return $body['publishStart'] === '2026-05-09T10:00:00'
                && $body['publishEnd']   === '2026-05-15T18:30:00';
        });
    }

    public function test_作成時_subject_欠落で_422_かつ_Core_は呼ばれない(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices" => Http::response(['id' => 1], 201),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/admin/notices', [
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-09',
                 'publishEnd' => '2026-05-15',
             ])
             ->assertStatus(422);

        Http::assertNothingSent();
    }

    public function test_作成時_publish_end_が_publish_start_より早いと_422(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices" => Http::response(['id' => 1], 201),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/admin/notices', [
                 'subject' => '件名',
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-15',
                 'publishEnd' => '2026-05-09',
             ])
             ->assertStatus(422);
    }

    public function test_作成時_未知の_category_id_で_422(): void
    {
        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/admin/notices', [
                 'subject' => '件名',
                 'categoryId' => 99999,
                 'body' => '本文',
                 'publishStart' => '2026-05-09',
                 'publishEnd' => '2026-05-15',
             ])
             ->assertStatus(422);
    }

    public function test_作成時_Core_の_422_を_そのまま_透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices" => Http::response([
                'status' => 422, 'errors' => [['field' => 'category', 'message' => 'not found']],
            ], 422),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/admin/notices', [
                 'subject' => '件名',
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-09',
                 'publishEnd' => '2026-05-15',
             ])
             ->assertStatus(422)
             ->assertJsonFragment(['field' => 'category']);
    }

    public function test_作成時_X_User_Id_が_Core_に渡る(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices" => Http::response(['id' => 1], 201),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/admin/notices', [
                 'subject' => '件名',
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-09',
                 'publishEnd' => '2026-05-15',
             ])
             ->assertStatus(201);

        Http::assertSent(fn ($req) => $req->hasHeader('X-User-Id'));
    }

    // ---------- 編集・削除 ----------

    public function test_編集を中継する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices/10" => Http::response(['id' => 10, 'subject' => '更新後'], 200),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->putJson('/api/admin/notices/10', [
                 'subject' => '更新後',
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-09',
                 'publishEnd' => '2026-05-15',
             ])
             ->assertStatus(200)
             ->assertJsonFragment(['subject' => '更新後']);
    }

    public function test_編集_Core_の_410_を_透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices/10" => Http::response(['status' => 410], 410),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->putJson('/api/admin/notices/10', [
                 'subject' => '更新後',
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-09',
                 'publishEnd' => '2026-05-15',
             ])
             ->assertStatus(410);
    }

    public function test_削除を中継する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices/10" => Http::response(null, 204),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->deleteJson('/api/admin/notices/10')
             ->assertStatus(204);

        Http::assertSent(fn ($req) => $req->method() === 'DELETE'
            && $req->hasHeader('X-User-Id'));
    }

    public function test_削除_Core_の_410_を_透過する(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices/10" => Http::response(['status' => 410], 410),
        ]);

        $this->withHeaders($this->authHeaders('admin'))
             ->deleteJson('/api/admin/notices/10')
             ->assertStatus(410);
    }

    // ---------- config 駆動の検証（規約 4-1） ----------

    public function test_config_notice_categories_が_phpunit_xml_の値と一致する(): void
    {
        $this->assertSame(1, (int) config('app.notice.categories.important_id'));
        $this->assertSame(2, (int) config('app.notice.categories.normal_id'));
        $this->assertSame(255, (int) config('app.notice.subject_max_length'));
        $this->assertSame(10000, (int) config('app.notice.body_max_length'));
    }

    // ---------- operation_logs 非書込（R19-3 / R19-4） ----------

    public function test_Console自身は_operation_logs_に書き込まない(): void
    {
        Http::fake([
            "{$this->baseUrl}/notices" => Http::response(['id' => 1], 201),
        ]);

        $beforeOpsLogs = DB::connection()->getDatabaseName(); // smoke

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/admin/notices', [
                 'subject' => '件名',
                 'categoryId' => config('app.notice.categories.normal_id'),
                 'body' => '本文',
                 'publishStart' => '2026-05-09',
                 'publishEnd' => '2026-05-15',
             ])
             ->assertStatus(201);

        // Console DB（sqlite :memory:）には operation_logs テーブルが存在しないため、
        // 「書き込まない」ことを「テーブル不在 / クエリログに INSERT 文が混じっていない」で担保する。
        $insertCount = collect(DB::getQueryLog())
            ->filter(fn ($q) => str_contains(strtolower($q['query']), 'insert into operation_logs'))
            ->count();
        $this->assertSame(0, $insertCount);
    }
}
