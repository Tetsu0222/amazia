<?php

namespace App\User\Controller;

use App\Http\Controllers\Controller;
use App\User\Service\CreateUserService;
use Illuminate\Http\Request;

class CreateUserController extends Controller
{
    private CreateUserService $service;

    public function __construct(CreateUserService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $response = $this->service->create($request->all());
        return response()->json($response->json(), $response->status());
    }
}
