<?php

namespace App\Sku\Service;

use Illuminate\Http\Client\Response;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Http;

class CreateProductSkuImageService
{
    private string $coreBaseUrl;
    private const MAX_KB = 200;

    public function __construct()
    {
        $this->coreBaseUrl = config('services.amazia_core.base_url');
    }

    public function create(int $skuId, ?UploadedFile $image): Response
    {
        $this->validate($image);

        $multipart = [
            [
                'name'     => 'image',
                'contents' => fopen($image->getRealPath(), 'r'),
                'filename' => $image->getClientOriginalName(),
            ],
        ];

        return Http::asMultipart()
            ->withOptions(['multipart' => $multipart])
            ->post("{$this->coreBaseUrl}/skus/{$skuId}/images");
    }

    private function validate(?UploadedFile $image): void
    {
        if ($image === null) {
            abort(400, 'ファイルが選択されていません');
        }
        if ($image->getClientMimeType() !== 'image/png'
            || strtolower($image->getClientOriginalExtension()) !== 'png') {
            abort(400, 'PNG形式のファイルのみ登録できます');
        }
        if ($image->getSize() > self::MAX_KB * 1024) {
            abort(400, 'ファイルサイズは200KB以下にしてください');
        }
    }
}
