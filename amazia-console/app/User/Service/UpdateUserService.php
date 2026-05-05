<?php

namespace App\User\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class UpdateUserService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function update(int $id, array $data): Response
    {
        return Http::put("{$this->baseUrl}/users/{$id}", $data);
    }
}
