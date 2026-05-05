<?php

namespace Tests\Feature\User;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class UserTest extends TestCase
{
    private string $baseUrl;
    private string $jwtSecret;

    protected function setUp(): void
    {
        parent::setUp();
        $this->baseUrl   = config('services.amazia_core.base_url');
        $this->jwtSecret = config('app.auth.jwt_secret');
    }

    private function makeToken(string $role = 'admin'): string
    {
        $header  = rtrim(strtr(base64_encode(json_encode(['alg' => 'HS256', 'typ' => 'JWT'])), '+/', '-_'), '=');
        $payload = rtrim(strtr(base64_encode(json_encode([
            'sub'  => '1',
            'role' => $role,
            'iat'  => time(),
            'exp'  => time() + 900,
        ])), '+/', '-_'), '=');
        $sig = rtrim(strtr(base64_encode(hash_hmac('sha256', "{$header}.{$payload}", $this->jwtSecret, true)), '+/', '-_'), '=');
        return "{$header}.{$payload}.{$sig}";
    }

    private function authHeaders(string $role = 'admin'): array
    {
        return ['Authorization' => 'Bearer ' . $this->makeToken($role)];
    }

    // --- GET /api/users ---

    public function test_社員一覧が取得できること(): void
    {
        Http::fake(["{$this->baseUrl}/users" => Http::response([
            ['id' => 1, 'email' => 'a@example.com', 'name' => 'A', 'role' => 'admin'],
        ], 200)]);

        $this->withHeaders($this->authHeaders('admin'))
             ->getJson('/api/users')
             ->assertStatus(200)
             ->assertJsonFragment(['email' => 'a@example.com']);
    }

    public function test_認証なしで社員一覧は401(): void
    {
        $this->getJson('/api/users')->assertStatus(401);
    }

    public function test_userロールで社員一覧は403(): void
    {
        $this->withHeaders($this->authHeaders('user'))
             ->getJson('/api/users')
             ->assertStatus(403);
    }

    // --- POST /api/users ---

    public function test_社員登録が成功すると201(): void
    {
        Http::fake(["{$this->baseUrl}/users" => Http::response([
            'id' => 2, 'email' => 'new@example.com',
        ], 201)]);

        $this->withHeaders($this->authHeaders('admin'))
             ->postJson('/api/users', [
                 'employeeId' => 'E001',
                 'email'      => 'new@example.com',
                 'name'       => '新規',
                 'password'   => 'Pass@1234',
                 'role'       => 'user',
             ])
             ->assertStatus(201)
             ->assertJsonFragment(['email' => 'new@example.com']);
    }

    // --- PUT /api/users/{id} ---

    public function test_社員情報が更新できること(): void
    {
        Http::fake(["{$this->baseUrl}/users/1" => Http::response([
            'id' => 1, 'email' => 'upd@example.com',
        ], 200)]);

        $this->withHeaders($this->authHeaders('admin'))
             ->putJson('/api/users/1', [
                 'email'      => 'upd@example.com',
                 'name'       => '更新',
                 'role'       => 'user',
                 'activeFlag' => true,
             ])
             ->assertStatus(200)
             ->assertJsonFragment(['email' => 'upd@example.com']);
    }

    public function test_存在しないIDの更新はCoreの404をそのまま返すこと(): void
    {
        Http::fake(["{$this->baseUrl}/users/9999" => Http::response([], 404)]);

        $this->withHeaders($this->authHeaders('admin'))
             ->putJson('/api/users/9999', [
                 'email'      => 'x@example.com',
                 'name'       => 'X',
                 'role'       => 'user',
                 'activeFlag' => true,
             ])
             ->assertStatus(404);
    }

    public function test_CORE_BASE_URLはconfigから取得されておりハードコードされていないこと(): void
    {
        $this->assertNotEmpty($this->baseUrl, 'CORE_BASE_URL must not be empty');
        $this->assertStringStartsWith('http', $this->baseUrl);
    }
}
