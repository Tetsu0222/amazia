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

        // フェーズ16 Step6-6: Excel 入荷も /inbounds 経由になり X-User-Id ヘッダが必須。
        $userId = (int) $request->get('auth_user_id');

        $result = $this->service->importFromFile(
            $request->file('file')->getPathname(),
            $userId,
        );

        return response()->json($result);
    }
}
