<?php

namespace App\Http\Controllers;

use Illuminate\Support\Facades\Http;

class SalesController extends Controller
{
    public function index()
    {
        $sales = [
            ['id' => 1, 'product' => '商品A', 'amount' => 5000, 'quantity' => 3],
            ['id' => 2, 'product' => '商品B', 'amount' => 3000, 'quantity' => 1],
            ['id' => 3, 'product' => '商品C', 'amount' => 8000, 'quantity' => 2],
        ];

        return response()->json($sales);
    }

    public function checkInventory()
    {
        $response = Http::get('http://localhost:8080/api/inventory');

        return response()->json([
            'message' => '在庫情報を取得しました',
            'inventory' => $response->json(),
        ], 200, [], JSON_UNESCAPED_UNICODE);
    }
}
