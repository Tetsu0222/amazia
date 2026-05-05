<?php

namespace App\Shared\Middleware;

use App\Shared\Service\JwtVerifyService;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class AuthenticateJwt
{
    private JwtVerifyService $jwtVerifyService;

    public function __construct(JwtVerifyService $jwtVerifyService)
    {
        $this->jwtVerifyService = $jwtVerifyService;
    }

    public function handle(Request $request, Closure $next): Response
    {
        $authHeader = $request->header('Authorization');

        if (!$authHeader || !str_starts_with($authHeader, 'Bearer ')) {
            return response()->json(['message' => 'Unauthorized'], 401);
        }

        $token = substr($authHeader, 7);

        try {
            $payload = $this->jwtVerifyService->verify($token);
        } catch (\RuntimeException $e) {
            return response()->json(['message' => 'Unauthorized'], 401);
        }

        $request->merge([
            'auth_user_id' => $payload['sub'] ?? null,
            'auth_role'    => $payload['role'] ?? null,
        ]);

        return $next($request);
    }
}
