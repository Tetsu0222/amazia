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

        foreach ($response->cookies() as $cookie) {
            $httpResponse->cookie(
                $cookie->getName(),
                $cookie->getValue(),
                $cookie->getMaxAge() / 60,
                $cookie->getPath(),
                null,
                $cookie->getSecure(),
                $cookie->getHttpOnly()
            );
        }

        return $httpResponse;
    }
}
