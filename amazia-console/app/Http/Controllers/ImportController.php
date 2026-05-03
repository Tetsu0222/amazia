<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use PhpOffice\PhpSpreadsheet\IOFactory;

class ImportController extends Controller
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function importProducts(Request $request)
    {
        $request->validate([
            'file' => 'required|file|mimetypes:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,application/octet-stream',
        ]);

        $spreadsheet = IOFactory::load($request->file('file')->getPathname());
        $rows = $spreadsheet->getActiveSheet()->toArray(null, true, true, false);

        if (count($rows) < 2) {
            return response()->json(['succeeded' => 0, 'failed' => []]);
        }

        // 1行目をヘッダーとしてキーに変換
        $headers = array_map('strtolower', array_map('trim', $rows[0]));
        $dataRows = array_slice($rows, 1);

        $succeeded = 0;
        $failed = [];

        foreach ($dataRows as $row) {
            $data = array_combine($headers, $row);

            $name  = trim($data['name']  ?? '');
            $price = $data['price'] ?? null;
            $stock = $data['stock'] ?? null;

            if ($name === '' || is_null($price) || is_null($stock)) {
                $failed[] = ['row' => $data, 'reason' => '必須項目(name/price/stock)が不足'];
                continue;
            }

            $response = Http::post($this->coreApiUrl, [
                'name'        => $name,
                'description' => trim($data['description'] ?? ''),
                'price'       => (int) $price,
                'stock'       => (int) $stock,
            ]);

            if ($response->successful()) {
                $succeeded++;
            } else {
                $failed[] = ['row' => $data, 'reason' => $response->body()];
            }
        }

        return response()->json([
            'succeeded' => $succeeded,
            'failed'    => $failed,
        ]);
    }
}
