<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use PhpOffice\PhpSpreadsheet\IOFactory;

class ImportProductService
{
    private string $coreApiUrl;

    public function __construct()
    {
        $this->coreApiUrl = config('services.amazia_core.url');
    }

    public function importFromFile(string $filePath): array
    {
        $spreadsheet = IOFactory::load($filePath);
        $rows = $spreadsheet->getActiveSheet()->toArray(null, true, true, false);

        if (count($rows) < 2) {
            return ['succeeded' => 0, 'failed' => []];
        }

        $headers  = array_map('strtolower', array_map('trim', $rows[0]));
        $dataRows = array_slice($rows, 1);

        $succeeded = 0;
        $failed    = [];

        foreach ($dataRows as $row) {
            $data  = array_combine($headers, $row);
            $error = $this->validateRow($data);

            if ($error !== null) {
                $failed[] = ['row' => $data, 'reason' => $error];
                continue;
            }

            $response = Http::post($this->coreApiUrl, [
                'name'        => trim($data['name']),
                'description' => trim($data['description'] ?? ''),
                'price'       => (int) $data['price'],
                'stock'       => (int) $data['stock'],
            ]);

            if ($response->successful()) {
                $succeeded++;
            } else {
                $failed[] = ['row' => $data, 'reason' => $response->body()];
            }
        }

        return ['succeeded' => $succeeded, 'failed' => $failed];
    }

    private function validateRow(array $data): ?string
    {
        $name  = trim($data['name']  ?? '');
        $price = $data['price'] ?? null;
        $stock = $data['stock'] ?? null;

        if ($name === '' || is_null($price) || is_null($stock)) {
            return '必須項目(name/price/stock)が不足';
        }

        return null;
    }
}
