<?php

namespace App\Http\Controllers;

use App\Services\ProductService;
use Illuminate\Http\Request;

class ProductController extends Controller
{
    private ProductService $productService;

    public function __construct(ProductService $productService)
    {
        $this->productService = $productService;
    }

    public function index()
    {
        $response = $this->productService->getPublished();
        return response()->json($response->json(), $response->status());
    }

    public function adminIndex()
    {
        $response = $this->productService->getAll();
        return response()->json($response->json(), $response->status());
    }

    public function show(int $id)
    {
        $response = $this->productService->getById($id);
        return response()->json($response->json(), $response->status());
    }

    public function store(Request $request)
    {
        $response = $this->productService->create($request->all());
        return response()->json($response->json(), $response->status());
    }

    public function update(Request $request, int $id)
    {
        $response = $this->productService->update($id, $request->all());
        return response()->json($response->json(), $response->status());
    }

    public function destroy(int $id)
    {
        $response = $this->productService->delete($id);

        if ($response->status() === 204) {
            return response()->noContent();
        }

        return response()->json($response->json(), $response->status());
    }

    public function bulkDestroy(Request $request)
    {
        $response = $this->productService->bulkDelete($request->input('ids'));

        if ($response->status() === 204) {
            return response()->noContent();
        }

        return response()->json($response->json(), $response->status());
    }

    public function bulkUpdateStock(Request $request)
    {
        $response = $this->productService->bulkUpdateStock($request->all());
        return response()->json($response->json(), $response->status());
    }

    public function statuses()
    {
        $response = $this->productService->getStatuses();
        return response()->json($response->json(), $response->status());
    }
}
