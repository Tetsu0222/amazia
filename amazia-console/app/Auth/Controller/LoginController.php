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

        foreach ($response->cookies() as $cookie) {
            $httpResponse->cookie(
                $cookie->getName(),
                $cookie->getValue(),
                $cookie->getMaxAge() / 60,
                $cookie->getPath(),
                null,                   // domain=amazia-core（コンテナ名）をブラウザに渡さない
                $cookie->getSecure(),
                $cookie->getHttpOnly()
            );
        }

        return $httpResponse;
    }
}
