<?php

namespace App\Sku\Controller;

use App\Http\Controllers\Controller;
use App\Sku\Service\ImportProductSkuStockService;
use Illuminate\Http\Request;

class ImportProductSkuStockController extends Controller
{
    public function __construct(private ImportProductSkuStockService $service) {}

    public function __invoke(Request $request)
    {
        $request->validate([
            'file' => 'required|file|mimetypes:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,application/octet-stream',
        ]);

        $result = $this->service->importFromFile(
            $request->file('file')->getPathname()
        );

        return response()->json($result);
    }
}
