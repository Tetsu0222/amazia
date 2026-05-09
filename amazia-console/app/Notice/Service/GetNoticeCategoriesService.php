<?php

namespace App\Notice\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * フェーズ19: お知らせ分類マスタ Pass-through Service。
 * Core の GET /api/notice-categories を呼び出す（認証不要）。
 */
class GetNoticeCategoriesService
{
    private string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.amazia_core.base_url');
    }

    public function findAll(): Response
    {
        return Http::get("{$this->baseUrl}/notice-categories");
    }
}
