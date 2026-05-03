<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;

class ProductController extends Controller
{
    private string $coreApiUrl;
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreApiUrl  = config('services.amazia_core.url');
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    /** Market向け：公開期間内のみ */
    public function index()
    {
        $response = Http::get($this->coreApiUrl);
        return response()->json($response->json(), $response->status());
    }

    /** Console向け：全件（公開期間外も含む） */
    public function adminIndex()
    {
        $response = Http::get("{$this->coreBaseUrl}/admin/products");
        return response()->json($response->json(), $response->status());
    }

    public function show(int $id)
    {
        $response = Http::get("{$this->coreApiUrl}/{$id}");
        return response()->json($response->json(), $response->status());
    }

    public function store(Request $request)
    {
        if (empty($request->input('name')) || is_null($request->input('price')) || is_null($request->input('stock'))) {
            return response()->json(['message' => '必須項目が不足しています'], 400);
        }

        $response = Http::post($this->coreApiUrl, [
            'name'         => $request->input('name'),
            'description'  => $request->input('description'),
            'price'        => $request->input('price'),
            'stock'        => $request->input('stock'),
            'statusCode'   => $request->input('statusCode'),
            'publishStart' => $request->input('publishStart'),
            'publishEnd'   => $request->input('publishEnd'),
        ]);

        return response()->json($response->json(), $response->status());
    }

    public function update(Request $request, int $id)
    {
        $response = Http::put("{$this->coreApiUrl}/{$id}", [
            'name'         => $request->input('name'),
            'description'  => $request->input('description'),
            'price'        => $request->input('price'),
            'stock'        => $request->input('stock'),
            'statusCode'   => $request->input('statusCode'),
            'publishStart' => $request->input('publishStart'),
            'publishEnd'   => $request->input('publishEnd'),
        ]);

        return response()->json($response->json(), $response->status());
    }

    public function destroy(int $id)
    {
        $response = Http::delete("{$this->coreApiUrl}/{$id}");

        if ($response->status() === 204) {
            return response()->noContent();
        }

        return response()->json($response->json(), $response->status());
    }

    public function bulkDestroy(Request $request)
    {
        $ids = $request->input('ids');
        $response = Http::withOptions(['query' => ['ids' => $ids]])->delete($this->coreApiUrl);

        if ($response->status() === 204) {
            return response()->noContent();
        }

        return response()->json($response->json(), $response->status());
    }

    public function bulkUpdateStock(Request $request)
    {
        $response = Http::patch("{$this->coreApiUrl}/bulk-stock", $request->all());
        return response()->json($response->json(), $response->status());
    }

    public function statuses()
    {
        $response = Http::get("{$this->coreBaseUrl}/product-statuses");
        return response()->json($response->json(), $response->status());
    }
}
