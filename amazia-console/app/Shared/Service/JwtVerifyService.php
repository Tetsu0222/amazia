<?php

namespace App\Shared\Service;

class JwtVerifyService
{
    private string $secret;

    public function __construct()
    {
        $this->secret = config('app.auth.jwt_secret');
    }

    public function verify(string $token): array
    {
        $parts = explode('.', $token);
        if (count($parts) !== 3) {
            throw new \RuntimeException('Invalid JWT structure');
        }

        [$headerB64, $payloadB64, $signatureB64] = $parts;

        $signature = $this->base64UrlDecode($signatureB64);
        $expected  = hash_hmac('sha256', "{$headerB64}.{$payloadB64}", $this->secret, true);

        if (!hash_equals($expected, $signature)) {
            throw new \RuntimeException('JWT signature mismatch');
        }

        $payload = json_decode($this->base64UrlDecode($payloadB64), true);

        if (!isset($payload['exp']) || $payload['exp'] < time()) {
            throw new \RuntimeException('JWT expired');
        }

        return $payload;
    }

    private function base64UrlDecode(string $input): string
    {
        $remainder = strlen($input) % 4;
        if ($remainder) {
            $input .= str_repeat('=', 4 - $remainder);
        }
        return base64_decode(strtr($input, '-_', '+/'));
    }
}
