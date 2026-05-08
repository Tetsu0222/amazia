<?php

namespace App\ScheduledPrice\Controller;

use App\ScheduledPrice\Service\DeleteScheduledSkuPriceService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Routing\Controller;

class DeleteScheduledSkuPriceController extends Controller
{
    public function __construct(private DeleteScheduledSkuPriceService $service) {}

    public function __invoke(Request $request, int $id): JsonResponse|Response
    {
        $response = $this->service->delete($id);
        if ($response->status() === 204) {
            return response()->noContent();
        }
        return response()->json($response->json(), $response->status());
    }
}
