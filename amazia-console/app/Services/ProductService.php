<?php

namespace App\Services;

use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

class ProductService
{
    private string $coreApiUrl;
    private string $coreBaseUrl;

    public function __construct()
    {
        $this->coreApiUrl  = config('services.amazia_core.url');
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function getPublished(): Response
    {
        return Http::get($this->coreApiUrl);
    }

    public function getAll(): Response
    {
        return Http::get("{$this->coreBaseUrl}/admin/products");
    }

    public function getById(int $id): Response
    {
        return Http::get("{$this->coreApiUrl}/{$id}");
    }

    public function create(array $data): Response
    {
        $this->validateRequired($data);
        return Http::post($this->coreApiUrl, $this->buildProductPayload($data));
    }

    public function update(int $id, array $data): Response
    {
        return Http::put("{$this->coreApiUrl}/{$id}", $this->buildProductPayload($data));
    }

    public function delete(int $id): Response
    {
        return Http::delete("{$this->coreApiUrl}/{$id}");
    }

    public function bulkDelete(string $ids): Response
    {
        return Http::withOptions(['query' => ['ids' => $ids]])->delete($this->coreApiUrl);
    }

    public function bulkUpdateStock(array $data): Response
    {
        return Http::patch("{$this->coreApiUrl}/bulk-stock", $data);
    }

    public function getStatuses(): Response
    {
        return Http::get("{$this->coreBaseUrl}/product-statuses");
    }

    private function validateRequired(array $data): void
    {
        if (empty($data['name']) || is_null($data['price'] ?? null) || is_null($data['stock'] ?? null)) {
            abort(400, '必須項目が不足しています');
        }
    }

    private function buildProductPayload(array $data): array
    {
        return [
            'name'         => $data['name']         ?? null,
            'description'  => $data['description']  ?? null,
            'price'        => $data['price']         ?? null,
            'stock'        => $data['stock']         ?? null,
            'statusCode'   => $data['statusCode']    ?? null,
            'publishStart' => $data['publishStart']  ?? null,
            'publishEnd'   => $data['publishEnd']    ?? null,
        ];
    }
}
