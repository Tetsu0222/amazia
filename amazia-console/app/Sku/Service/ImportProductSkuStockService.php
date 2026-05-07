<?php

namespace App\Sku\Service;

use Illuminate\Support\Facades\Http;
use PhpOffice\PhpSpreadsheet\IOFactory;

/**
 * Excel 一括入荷 Service。
 *
 * フェーズ16 Step 6-6 で `/skus/{id}/stocks/receive` 直接呼び出しから
 * `/inbounds` 経由に切り替え、Excel 入荷も入荷管理画面で履歴・追跡番号を確認できる構造とした。
 * Excel ヘッダーに任意列 `tracking_code` を追加（既存ファイルとの後方互換維持）。
 */
class ImportProductSkuStockService
{
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function importFromFile(string $filePath, ?int $userId = null): array
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

            $skuCode      = trim((string) $data['sku_code']);
            $quantity     = (int) $data['quantity'];
            $trackingCode = isset($data['tracking_code'])
                ? trim((string) $data['tracking_code'])
                : '';

            $skuResp = Http::get("{$this->coreBaseUrl}/skus/by-code/{$skuCode}");
            if (!$skuResp->successful()) {
                $failed[] = ['row' => $data, 'reason' => "SKUコード '{$skuCode}' が存在しません"];
                continue;
            }

            $skuId     = $skuResp->json('id');
            $productId = $skuResp->json('productId');

            $payload = [
                'productId' => $productId,
                'skuId'     => $skuId,
                'quantity'  => $quantity,
            ];
            if ($trackingCode !== '') {
                $payload['trackingCode'] = $trackingCode;
            }

            $inboundReq = Http::asJson();
            if ($userId !== null) {
                $inboundReq = $inboundReq->withHeaders(['X-User-Id' => (string) $userId]);
            }
            $inboundResp = $inboundReq->post("{$this->coreBaseUrl}/inbounds", $payload);

            if ($inboundResp->successful()) {
                $succeeded++;
            } else {
                $failed[] = ['row' => $data, 'reason' => $inboundResp->body()];
            }
        }

        return ['succeeded' => $succeeded, 'failed' => $failed];
    }

    private function validateRow(array $data): ?string
    {
        $skuCode      = trim((string) ($data['sku_code'] ?? ''));
        $quantity     = $data['quantity'] ?? null;
        $trackingCode = isset($data['tracking_code'])
            ? trim((string) $data['tracking_code'])
            : '';

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
        if (mb_strlen($trackingCode) > 255) {
            return 'tracking_codeは255文字以内で指定してください';
        }

        return null;
    }
}
