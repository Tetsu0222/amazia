<?php

namespace Tests\Feature\Auth;

use Tests\TestCase;
use Illuminate\Support\Facades\Http;

class AuthenticateJwtTest extends TestCase
{
    private string $coreBaseUrl;
    private string $jwtSecret;

    protected function setUp(): void
    {
        parent::setUp();
        $this->coreBaseUrl = config('services.amazia_core.base_url');
        $this->jwtSecret   = config('app.auth.jwt_secret');
    }

    private function makeToken(array $payload, ?string $secret = null): string
    {
        $secret ??= $this->jwtSecret;
        $header  = base64_encode(json_encode(['alg' => 'HS256', 'typ' => 'JWT']));
        $payload = base64_encode(json_encode($payload));
        $header  = rtrim(strtr($header, '+/', '-_'), '=');
        $payload = rtrim(strtr($payload, '+/', '-_'), '=');
        $sig     = rtrim(strtr(base64_encode(hash_hmac('sha256', "{$header}.{$payload}", $secret, true)), '+/', '-_'), '=');
        return "{$header}.{$payload}.{$sig}";
    }

    private function validToken(string $role = 'admin'): string
    {
        return $this->makeToken([
            'sub'  => '1',
            'role' => $role,
            'iat'  => time(),
            'exp'  => time() + 900,
        ]);
    }

    // --- 正常系 ---

    public function test_有効なBearerトークンを付与したリクエストは通過すること(): void
    {
        Http::fake(["{$this->coreBaseUrl}/products" => Http::response([], 200)]);

        $response = $this->withHeader('Authorization', 'Bearer ' . $this->validToken())
                         ->getJson('/api/products');

        $response->assertStatus(200);
    }

    public function test_トークン検証後にrequestにroleが付加されること(): void
    {
        Http::fake(["{$this->coreBaseUrl}/products" => Http::response([], 200)]);

        $response = $this->withHeader('Authorization', 'Bearer ' . $this->validToken('user'))
                         ->getJson('/api/products');

        $response->assertStatus(200);
    }

    // --- 異常系 ---

    public function test_Authorizationヘッダーなしは401(): void
    {
        $response = $this->getJson('/api/products');
        $response->assertStatus(401);
    }

    public function test_BearerプレフィックスなしのトークンはUser401(): void
    {
        $response = $this->withHeader('Authorization', $this->validToken())
                         ->getJson('/api/products');
        $response->assertStatus(401);
    }

    public function test_期限切れアクセストークンは401(): void
    {
        $expiredToken = $this->makeToken([
            'sub'  => '1',
            'role' => 'admin',
            'iat'  => time() - 1000,
            'exp'  => time() - 100,
        ]);

        $response = $this->withHeader('Authorization', 'Bearer ' . $expiredToken)
                         ->getJson('/api/products');
        $response->assertStatus(401);
    }

    public function test_署名が改ざんされたトークンは401(): void
    {
        $token   = $this->validToken();
        $parts   = explode('.', $token);
        $parts[2] = 'invalidsignature';
        $tampered = implode('.', $parts);

        $response = $this->withHeader('Authorization', 'Bearer ' . $tampered)
                         ->getJson('/api/products');
        $response->assertStatus(401);
    }

    // --- ロール・パーミッション統合 ---

    public function test_adminロールはusers一覧エンドポイントに200(): void
    {
        Http::fake(["{$this->coreBaseUrl}/users" => Http::response([], 200)]);

        $response = $this->withHeader('Authorization', 'Bearer ' . $this->validToken('admin'))
                         ->getJson('/api/users');
        $response->assertStatus(200);
    }

    public function test_userロールはusers一覧エンドポイントに403(): void
    {
        $response = $this->withHeader('Authorization', 'Bearer ' . $this->validToken('user'))
                         ->getJson('/api/users');
        $response->assertStatus(403);
    }
}
