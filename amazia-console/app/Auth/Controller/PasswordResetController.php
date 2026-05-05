<?php

namespace App\Auth\Controller;

use App\Auth\Service\PasswordResetService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class PasswordResetController extends Controller
{
    private PasswordResetService $service;

    public function __construct(PasswordResetService $service)
    {
        $this->service = $service;
    }

    public function request(Request $request)
    {
        $response = $this->service->request($request->only(['email']));
        return response()->json($response->json(), $response->status());
    }

    public function confirm(Request $request)
    {
        $response = $this->service->confirm($request->only(['token', 'newPassword']));
        return response()->json($response->json(), $response->status());
    }
}
