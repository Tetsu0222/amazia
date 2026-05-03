<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Maatwebsite\Excel\Facades\Excel;
use Maatwebsite\Excel\Concerns\ToCollection;
use Maatwebsite\Excel\Concerns\WithHeadingRow;
use Illuminate\Support\Collection;

class ProductImportSheet implements ToCollection, WithHeadingRow
{
    public Collection $rows;

    public function collection(Collection $rows)
    {
        $this->rows = $rows;
    }
}

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
            'file' => 'required|file|mimes:xlsx,xls',
        ]);

        $sheet = new ProductImportSheet();
        Excel::import($sheet, $request->file('file'));

        $succeeded = 0;
        $failed = [];

        foreach ($sheet->rows as $row) {
            $name  = trim($row['name']  ?? '');
            $price = $row['price'] ?? null;
            $stock = $row['stock'] ?? null;

            if ($name === '' || is_null($price) || is_null($stock)) {
                $failed[] = ['row' => $row->toArray(), 'reason' => '必須項目(name/price/stock)が不足'];
                continue;
            }

            $response = Http::post($this->coreApiUrl, [
                'name'        => $name,
                'description' => trim($row['description'] ?? ''),
                'price'       => (int) $price,
                'stock'       => (int) $stock,
            ]);

            if ($response->successful()) {
                $succeeded++;
            } else {
                $failed[] = ['row' => $row->toArray(), 'reason' => $response->body()];
            }
        }

        return response()->json([
            'succeeded' => $succeeded,
            'failed'    => $failed,
        ]);
    }
}
