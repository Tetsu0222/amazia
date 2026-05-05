<?php

namespace App\Auth\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class PasswordResetService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function request(array $data): Response
    {
        return Http::post("{$this->baseUrl}/auth/password/reset/request", $data);
    }

    public function confirm(array $data): Response
    {
        return Http::post("{$this->baseUrl}/auth/password/reset/confirm", $data);
    }
}
