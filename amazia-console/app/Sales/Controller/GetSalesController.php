<?php

namespace App\Sales\Controller;

use App\Http\Controllers\Controller;
use App\Sales\Service\GetSalesService;

class GetSalesController extends Controller
{
    private GetSalesService $service;

    public function __construct(GetSalesService $service)
    {
        $this->service = $service;
    }

    public function __invoke()
    {
        return response()->json($this->service->getSales());
    }
}
