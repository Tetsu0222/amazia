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

        // ヘッダ alg を読んで Core が使ったハッシュアルゴリズムに合わせる。
        // Spring の Keys.hmacShaKeyFor() は秘密鍵長で HS256 / HS384 / HS512 を
        // 自動選択するため、Console 側で固定すると不一致で必ず 401 になる。
        $header = json_decode($this->base64UrlDecode($headerB64), true);
        $alg    = $header['alg'] ?? '';
        $hashAlgo = match ($alg) {
            'HS256' => 'sha256',
            'HS384' => 'sha384',
            'HS512' => 'sha512',
            default => throw new \RuntimeException("Unsupported JWT alg: {$alg}"),
        };

        $signature = $this->base64UrlDecode($signatureB64);
        $expected  = hash_hmac($hashAlgo, "{$headerB64}.{$payloadB64}", $this->secret, true);

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
