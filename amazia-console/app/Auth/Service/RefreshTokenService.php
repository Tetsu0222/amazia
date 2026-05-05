<?php

namespace App\Auth\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class RefreshTokenService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function refresh(string $refreshToken): Response
    {
        return Http::withCookies(['refresh_token' => $refreshToken], parse_url($this->baseUrl, PHP_URL_HOST))
                   ->post("{$this->baseUrl}/auth/refresh");
    }
}
