<?php

namespace App\ScheduledPrice\Controller;

use App\ScheduledPrice\Service\RegisterScheduledSkuPriceService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class RegisterScheduledSkuPriceController extends Controller
{
    public function __construct(private RegisterScheduledSkuPriceService $service) {}

    public function __invoke(Request $request, int $id): JsonResponse
    {
        $response = $this->service->upsert($id, $request->all());
        return response()->json($response->json(), $response->status());
    }
}
