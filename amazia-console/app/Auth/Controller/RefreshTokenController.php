<?php

namespace App\Auth\Controller;

use App\Auth\Service\RefreshTokenService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class RefreshTokenController extends Controller
{
    private RefreshTokenService $service;

    public function __construct(RefreshTokenService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $refreshToken = $request->cookie('refresh_token', '');
        $response     = $this->service->refresh($refreshToken);

        $httpResponse = response()->json($response->json(), $response->status());

        // Spring が返した Set-Cookie ヘッダをそのまま透過する（031 経緯）。
        foreach ($response->getHeaders()['Set-Cookie'] ?? [] as $rawCookie) {
            $httpResponse->headers->set('Set-Cookie', $rawCookie, false);
        }

        return $httpResponse;
    }
}
