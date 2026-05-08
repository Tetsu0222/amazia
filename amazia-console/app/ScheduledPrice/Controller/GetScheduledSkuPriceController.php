<?php

namespace App\ScheduledPrice\Controller;

use App\ScheduledPrice\Service\GetScheduledSkuPriceService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Routing\Controller;

class GetScheduledSkuPriceController extends Controller
{
    public function __construct(private GetScheduledSkuPriceService $service) {}

    public function __invoke(Request $request, int $id): JsonResponse|Response
    {
        $response = $this->service->get($id);
        if ($response->status() === 204) {
            return response()->noContent();
        }
        return response()->json($response->json(), $response->status());
    }
}
