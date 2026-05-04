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
