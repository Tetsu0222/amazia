<?php

namespace App\Auth\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class LoginService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function login(array $credentials): Response
    {
        return Http::post("{$this->baseUrl}/auth/login", $credentials);
    }
}
