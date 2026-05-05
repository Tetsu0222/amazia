<?php

namespace App\User\Controller;

use App\Http\Controllers\Controller;
use App\User\Service\ListUserService;
use Illuminate\Http\Request;

class ListUserController extends Controller
{
    private ListUserService $service;

    public function __construct(ListUserService $service)
    {
        $this->service = $service;
    }

    public function __invoke(Request $request)
    {
        $response = $this->service->list();
        return response()->json($response->json(), $response->status());
    }
}
