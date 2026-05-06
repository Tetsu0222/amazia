<?php

namespace App\Auth\Controller;

use App\Auth\Service\LoginService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class LoginController extends Controller
{
    private LoginService $service;

    public function __construct(LoginService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $response = $this->service->login($request->only(['email', 'password']));

        $httpResponse = response()->json($response->json(), $response->status());

        // Spring が返した Set-Cookie ヘッダをそのままブラウザへ透過する。
        // Laravel の cookie() / Guzzle CookieJar 経由では Domain や属性が
        // 再構築されてしまい、CloudFront 経由の本番環境で必要な属性
        // （Domain=www.amazia-portfolio.dedyn.io / Path=/console/api/auth/refresh /
        //  Secure / HttpOnly）が落ちる事象が発生したため生ヘッダで素通しする。
        // 詳細はトラブル031を参照。
        foreach ($response->getHeaders()['Set-Cookie'] ?? [] as $rawCookie) {
            $httpResponse->headers->set('Set-Cookie', $rawCookie, false);
        }

        return $httpResponse;
    }
}
