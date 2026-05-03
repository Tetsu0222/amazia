<?php

namespace App\Http\Controllers;

use App\Services\ImportProductService;
use Illuminate\Http\Request;

class ImportController extends Controller
{
    private ImportProductService $importProductService;

    public function __construct(ImportProductService $importProductService)
    {
        $this->importProductService = $importProductService;
    }

    public function importProducts(Request $request)
    {
        $request->validate([
            'file' => 'required|file|mimetypes:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,application/octet-stream',
        ]);

        $result = $this->importProductService->importFromFile(
            $request->file('file')->getPathname()
        );

        return response()->json($result);
    }
}
