<?php

namespace App\User\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ListUserService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(): Response
    {
        return Http::get("{$this->baseUrl}/users");
    }
}
