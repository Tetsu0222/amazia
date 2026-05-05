<?php

namespace App\Sku\Service;

use Illuminate\Support\Facades\Http;
use PhpOffice\PhpSpreadsheet\IOFactory;

class ImportProductSkuStockService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
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

            $skuCode  = trim((string) $data['sku_code']);
            $quantity = (int) $data['quantity'];

            $skuResp = Http::get("{$this->coreBaseUrl}/skus/by-code/{$skuCode}");
            if (!$skuResp->successful()) {
                $failed[] = ['row' => $data, 'reason' => "SKUコード '{$skuCode}' が存在しません"];
                continue;
            }

            $skuId = $skuResp->json('id');
            $receiveResp = Http::post("{$this->coreBaseUrl}/skus/{$skuId}/stocks/receive", [
                'quantity' => $quantity,
            ]);

            if ($receiveResp->successful()) {
                $succeeded++;
            } else {
                $failed[] = ['row' => $data, 'reason' => $receiveResp->body()];
            }
        }

        return ['succeeded' => $succeeded, 'failed' => $failed];
    }

    private function validateRow(array $data): ?string
    {
        $skuCode  = trim((string) ($data['sku_code'] ?? ''));
        $quantity = $data['quantity'] ?? null;

        if ($skuCode === '') {
            return '必須項目(sku_code)が不足';
        }
        if (is_null($quantity) || !is_numeric($quantity)) {
            return '必須項目(quantity)が不足または不正';
        }
        $intQty = (int) $quantity;
        if ($intQty <= 0) {
            return 'quantityは1以上の整数である必要があります（減算は不可）';
        }
        if ((float) $quantity != $intQty) {
            return 'quantityは整数である必要があります';
        }

        return null;
    }
}
