<?php

namespace App\Shared\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class CheckPermission
{
    public function handle(Request $request, Closure $next, string $screenId): Response
    {
        $role = $request->get('auth_role');
        if (!$role) {
            return response()->json(['message' => 'Unauthorized'], 401);
        }

        $permissions = config('app.auth.role_permissions');
        $allowed     = $permissions[$role] ?? [];

        $hasPermission = in_array('*', $allowed, true)
            || in_array($screenId, $allowed, true)
            || $this->matchesWildcard($screenId, $allowed);

        if (!$hasPermission) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        return $next($request);
    }

    private function matchesWildcard(string $screenId, array $allowed): bool
    {
        foreach ($allowed as $pattern) {
            if (str_ends_with($pattern, '.*')) {
                $prefix = rtrim($pattern, '*');
                if (str_starts_with($screenId, $prefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
