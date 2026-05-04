# API定義書：Console

## 概要

| 項目 | 内容 |
|------|------|
| システム | Console（amazia-console） |
| ベースURL | `/api` |
| 認証方式 | Laravel Sanctum（Bearer Token） |
| レスポンス形式 | JSON |

---

## 商品 API

### 商品一覧取得（一般）

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products` |
| 認証 | 不要 |
| コントローラー | `App\Product\Controller\ListProductController` |

**レスポンス例**
```json
{
  "data": [
    {
      "id": 1,
      "name": "商品名",
      "status": "active"
    }
  ]
}
```

---

### 商品一覧取得（管理者）

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/admin/products` |
| 認証 | 要（管理者権限） |
| コントローラー | `App\Product\Controller\AdminListProductController` |

---

### 商品ステータス一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/product-statuses` |
| 認証 | 不要 |
| コントローラー | `App\Product\Controller\GetProductStatusesController` |

**レスポンス例**
```json
{
  "data": [
    { "value": "active", "label": "販売中" },
    { "value": "inactive", "label": "非公開" }
  ]
}
```

---

### 商品詳細取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/{id}` |
| 認証 | 不要 |
| コントローラー | `App\Product\Controller\GetProductController` |

**パスパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| id | integer | ○ | 商品ID |

---

### 商品登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/products` |
| 認証 | 要 |
| コントローラー | `App\Product\Controller\CreateProductController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| name | string | ○ | 商品名 |
| status | string | ○ | ステータス |

---

### 商品更新

| 項目 | 内容 |
|------|------|
| メソッド | PUT |
| パス | `/api/products/{id}` |
| 認証 | 要 |
| コントローラー | `App\Product\Controller\UpdateProductController` |

**パスパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| id | integer | ○ | 商品ID |

---

### 商品削除（単件）

| 項目 | 内容 |
|------|------|
| メソッド | DELETE |
| パス | `/api/products/{id}` |
| 認証 | 要 |
| コントローラー | `App\Product\Controller\DeleteProductController` |

---

### 商品一括削除

| 項目 | 内容 |
|------|------|
| メソッド | DELETE |
| パス | `/api/products` |
| 認証 | 要 |
| コントローラー | `App\Product\Controller\BulkDeleteProductController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| ids | integer[] | ○ | 削除対象の商品IDリスト |

---

### 在庫一括更新

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/products/bulk-stock` |
| 認証 | 要 |
| コントローラー | `App\Product\Controller\BulkUpdateStockController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| items | object[] | ○ | 在庫更新対象リスト |
| items[].id | integer | ○ | 商品ID |
| items[].stock | integer | ○ | 在庫数 |

---

### 商品Excelインポート

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/products/import` |
| 認証 | 要 |
| コントローラー | `App\Product\Controller\ImportProductController` |
| Content-Type | multipart/form-data |

**リクエスト**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| file | file | ○ | Excelファイル（.xlsx） |

---

## 売上・在庫 API

### 売上一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/sales` |
| 認証 | 要 |
| コントローラー | `App\Sales\Controller\GetSalesController` |

---

### 在庫状況取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/sales/inventory` |
| 認証 | 要 |
| コントローラー | `App\Sales\Controller\GetInventoryController` |

---

## 認証 API

### 認証済みユーザー取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/user` |
| 認証 | 要（auth:sanctum） |
| 説明 | Sanctumトークンで認証済みのユーザー情報を返す |

---

## 商品画像 API

> Console → Core へのリクエストプロキシ。バリデーションは Console 側で実施。

### 商品画像一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/{id}/images` |
| 認証 | 不要 |
| コントローラー | `App\ProductImage\Controller\ListProductImageController` |

**パスパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| id | integer | ○ | 商品ID |

**レスポンス例**
```json
[
  { "id": 1, "productId": 1, "imagePath": "1/uuid.png", "sortOrder": 1 }
]
```

---

### 商品画像登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/products/{id}/images` |
| 認証 | 不要 |
| コントローラー | `App\ProductImage\Controller\CreateProductImageController` |
| Content-Type | multipart/form-data |

**バリデーション**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| image | file | ○ | PNG のみ・200KB以下 |

---

### 商品画像 sort_order 更新

| 項目 | 内容 |
|------|------|
| メソッド | PUT |
| パス | `/api/product-images/{id}/sort` |
| 認証 | 不要 |
| コントローラー | `App\ProductImage\Controller\UpdateProductImageSortController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| sort_order | integer | ○ | 新しい表示順 |

---

### 商品画像削除

| 項目 | 内容 |
|------|------|
| メソッド | DELETE |
| パス | `/api/product-images/{id}` |
| 認証 | 不要 |
| コントローラー | `App\ProductImage\Controller\DeleteProductImageController` |

---

## SKU API

> Console → Core へのリクエストプロキシ。バリデーションは Console 側で実施。

### SKU一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/{id}/skus` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\ListProductSkuController` |

**レスポンス例**
```json
[
  { "id": 1, "productId": 1, "skuCode": "P1-001", "color": "Red", "size": "M", "status": "active" }
]
```

---

### SKU登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/products/{id}/skus` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\CreateProductSkuController` |

**バリデーション**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| color | string | ○ | 色（例：Red） |
| size | string | ○ | サイズ（例：M） |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 422 | color・sizeが未指定 |
| 409 | 同一商品内で color + size が重複 |

---

### SKU現行価格取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/prices` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\GetProductSkuPriceController` |

**レスポンス例**
```json
{ "id": 1, "skuId": 1, "price": 1000, "startDate": "2026-01-01", "endDate": null }
```

---

### SKU価格登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/skus/{id}/prices` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\CreateProductSkuPriceController` |

**バリデーション**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| price | integer | ○ | 価格（円） |
| start_date | date | × | 適用開始日 |

---

### SKU現在在庫取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/stocks` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\GetProductSkuStockController` |

**レスポンス例**
```json
{ "skuId": 1, "quantity": 50 }
```

---

### SKU入荷登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/skus/{id}/stocks/receive` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\ReceiveProductSkuStockController` |

**バリデーション**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| quantity | integer | ○ | 入荷数（1以上） |

---

### SKU在庫履歴取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/stocks/history` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\GetProductSkuStockHistoryController` |

**レスポンス例**
```json
[
  { "id": 1, "skuId": 1, "type": "receive", "quantity": 50, "createdAt": "2026-01-01T00:00:00" }
]
```
