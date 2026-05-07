<?php

namespace App\Inbound\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Core の GET /api/inbounds[?productId=N] を呼び出して入荷一覧を取得する Service。
 */
class ListInboundService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function list(?int $productId = null): Response
    {
        $query = [];
        if ($productId !== null) {
            $query['productId'] = $productId;
        }
        return Http::get("{$this->baseUrl}/inbounds", $query);
    }
}
