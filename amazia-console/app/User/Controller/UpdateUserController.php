<?php

namespace App\User\Controller;

use App\Http\Controllers\Controller;
use App\User\Service\UpdateUserService;
use Illuminate\Http\Request;

class UpdateUserController extends Controller
{
    private UpdateUserService $service;

    public function __construct(UpdateUserService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request, int $id)
    {
        $response = $this->service->update($id, $request->all());
        return response()->json($response->json(), $response->status());
    }
}
