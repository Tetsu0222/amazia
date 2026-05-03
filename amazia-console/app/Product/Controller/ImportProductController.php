<?php

namespace App\Product\Controller;

use App\Http\Controllers\Controller;
use App\Product\Service\ImportProductService;
use Illuminate\Http\Request;

class ImportProductController extends Controller
{
    private ImportProductService $service;

    public function __construct(ImportProductService $service)
    {
        $this->service = $service;
    }

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
