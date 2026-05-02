<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;

class ProductController extends Controller
{
    private string $coreApiUrl = 'http://localhost:8080/api/products';

    public function index()
    {
        $response = Http::get($this->coreApiUrl);
        return response()->json($response->json(), $response->status());
    }

    public function store(Request $request)
    {
        if (empty($request->input('name')) || is_null($request->input('price')) || is_null($request->input('stock'))) {
            return response()->json(['message' => '必須項目が不足しています'], 400);
        }

        $response = Http::post($this->coreApiUrl, [
            'name'        => $request->input('name'),
            'description' => $request->input('description'),
            'price'       => $request->input('price'),
            'stock'       => $request->input('stock'),
        ]);

        return response()->json($response->json(), $response->status());
    }
}
