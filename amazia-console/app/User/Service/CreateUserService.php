<?php

namespace App\User\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class CreateUserService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function create(array $data): Response
    {
        return Http::post("{$this->baseUrl}/users", $data);
    }
}
