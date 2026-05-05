<?php

namespace Tests;

use Illuminate\Foundation\Testing\TestCase as BaseTestCase;

abstract class TestCase extends BaseTestCase
{
    protected function makeToken(string $role = 'admin'): string
    {
        $secret  = config('app.auth.jwt_secret');
        $header  = rtrim(strtr(base64_encode(json_encode(['alg' => 'HS256', 'typ' => 'JWT'])), '+/', '-_'), '=');
        $payload = rtrim(strtr(base64_encode(json_encode([
            'sub'  => '1',
            'role' => $role,
            'iat'  => time(),
            'exp'  => time() + 900,
        ])), '+/', '-_'), '=');
        $sig = rtrim(strtr(base64_encode(hash_hmac('sha256', "{$header}.{$payload}", $secret, true)), '+/', '-_'), '=');
        return "{$header}.{$payload}.{$sig}";
    }

    protected function authHeaders(string $role = 'admin'): array
    {
        return ['Authorization' => 'Bearer ' . $this->makeToken($role)];
    }
}
