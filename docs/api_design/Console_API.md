# API定義書：Console

## 概要

| 項目 | 内容 |
|------|------|
| システム | Console（amazia-console） |
| ベースURL | `/api` |
| 認証方式 | JWT（Bearer Token）※フェーズ11でSanctumから刷新 |
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
| description | string | × | 説明 |
| statusCode | string | × | ステータスコード |
| publishStart | datetime | × | 公開開始日時（ISO 8601） |
| publishEnd | datetime | × | 公開終了日時（ISO 8601） |
| releaseDate | date | × | 発売日（YYYY-MM-DD）。フェーズ14.5 追加 |
| preorderStartDate | date | × | 予約開始日（YYYY-MM-DD）。フェーズ14.5 追加 |
| acceptPreorder | boolean | × | 予約購入受付フラグ。既定 false。フェーズ14.5 追加 |
| acceptBackorder | boolean | × | 在庫切れ予約継続フラグ。既定 false。フェーズ14.5 追加 |
| isActive | boolean | × | Market 露出フラグ。既定 true。FALSE で Market 非表示。フェーズ16 Step1 追加 |

そのまま Core `POST /api/products` に中継する。

**注記**: `price` / `stock` は **意図的にリクエストパラメータに含めない**。フェーズ10 で SKU 側（`product_sku_prices` / `product_sku_stocks`）に移行済みのため、商品登録 UI（[ProductForm.vue](../../amazia-console/resources/vue/src/features/products/pages/ProductForm.vue)）からは入力させず、Console Service の `buildPayload` でも明示的に除外している。SKU 価格・在庫の登録は SKU 管理画面（`/skus`）で個別に行う。詳細は [トラブル #038](../troubles/038_products_price_stock_not_null_drift.md) を参照。

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

**リクエストボディ**

商品登録と同じパラメータ（フェーズ14.5 追加 4 カラム + フェーズ16 Step1 追加 `isActive` 含む）を Core `PUT /api/products/{id}` に中継する。

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

## 予約管理 API（フェーズ16 Step2追加）

設計書: [phase16_ui_ux_improvement.md](../design/phase11_20/phase16_ui_ux_improvement.md) §2-4-5

### 予約商品一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/preorders` |
| 認証 | 要 |
| コントローラー | `App\Preorder\Controller\ListPreorderController` |
| サービス | `App\Preorder\Service\ListPreorderService` |

**仕様**
- Core の `GET /api/products/preorders` を中継する Pass-through
- レスポンスは Core 側スキーマと同一（Core_API.md「予約商品一覧 API」を参照）
- Core が 5xx を返した場合はそのままステータスコードを透過

---

## 認証 API（フェーズ11追加）

> Console は認証リクエストを amazia-core にプロキシする。JWT の検証は Console 側の `AuthenticateJwt` ミドルウェアで実施。

### ログイン

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/auth/login` |
| 認証 | 不要 |
| コントローラー | `App\Auth\Controller\LoginController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| email | string | ○ | メールアドレス（ログインID） |
| password | string | ○ | パスワード |

**レスポンス例（200）**
```json
{ "access_token": "<JWT>" }
```

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 401 | メールアドレスまたはパスワードが不正 |
| 403 | アカウントが無効（active_flag = false） |
| 423 | アカウントがロックアウト中 |

---

### トークン再発行

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/auth/refresh` |
| 認証 | 不要（HttpOnly CookieのリフレッシュトークンをCore側で検証） |
| コントローラー | `App\Auth\Controller\RefreshTokenController` |

**レスポンス例（200）**
```json
{ "access_token": "<新しいJWT>" }
```

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 401 | リフレッシュトークンが無効・期限切れ・失効済み |

---

### パスワード再発行メール送信

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/auth/password/reset/request` |
| 認証 | 不要 |
| コントローラー | `App\Auth\Controller\PasswordResetController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| email | string | ○ | メールアドレス |

**レスポンス**
- 200：常に成功レスポンスを返す（列挙攻撃対策。未登録メールでもエラーにしない）

---

### パスワード再設定

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/auth/password/reset/confirm` |
| 認証 | 不要 |
| コントローラー | `App\Auth\Controller\PasswordResetController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| token | string | ○ | 再発行URLに含まれるトークン |
| password | string | ○ | 新パスワード |
| password_confirmation | string | ○ | 新パスワード（確認） |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 400 | トークンが無効・期限切れ・使用済み |
| 422 | パスワードポリシー違反（8文字未満・大文字なし・小文字なし・数字なし） |
| 422 | 過去3回分のパスワードと同一 |

---

## ユーザー管理 API（フェーズ11追加）

> 全エンドポイントに JWT 認証（adminロール）が必要。

### 社員一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/users` |
| 認証 | 要（admin ロール） |
| コントローラー | `App\User\Controller\ListUserController` |

**レスポンス例**
```json
[
  {
    "id": 1,
    "employeeId": "EMP001",
    "email": "user@amazia.example.com",
    "name": "山田 太郎",
    "role": "admin",
    "activeFlag": true
  }
]
```

---

### 社員登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/users` |
| 認証 | 要（admin ロール） |
| コントローラー | `App\User\Controller\CreateUserController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| employee_id | string | ○ | 社員ID（ユニーク） |
| email | string | ○ | メールアドレス（ユニーク・ログインID） |
| name | string | ○ | 氏名（50文字以内） |
| password | string | ○ | パスワード（ポリシー準拠） |
| role | string | ○ | ロール（admin / user） |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 422 | バリデーションエラー（必須項目不足・ポリシー違反） |
| 422 | employee_id または email の重複 |

---

### 社員編集

| 項目 | 内容 |
|------|------|
| メソッド | PUT |
| パス | `/api/users/{id}` |
| 認証 | 要（admin ロール） |
| コントローラー | `App\User\Controller\UpdateUserController` |

**パスパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| id | integer | ○ | ユーザーID |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| email | string | × | メールアドレス |
| name | string | × | 氏名 |
| role | string | × | ロール（admin / user） |
| active_flag | boolean | × | 有効フラグ（false でログイン不可） |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 404 | 指定IDのユーザーが存在しない |
| 422 | バリデーションエラー |

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

---

### SKU在庫Excelインポート（フェーズ12追加）

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/skus/stocks/import` |
| 認証 | 要 |
| コントローラー | `App\Sku\Controller\ImportProductSkuStockController` |
| Content-Type | multipart/form-data |

**リクエスト**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| file | file | ○ | Excelファイル（.xlsx）。`sku_code` / `quantity` 列が必須、`tracking_code` 列は任意（フェーズ16 Step 6-6 で追加） |

**仕様**
- ファイル全行を解析後、Core の `POST /api/inbounds`（フェーズ16 Step 6-6 で `/skus/{id}/stocks/receive` から切替）を順次呼び出して在荷ヘッダ（`inbounds`）を作成し在庫を加算する
- 行ごとに `inbounds.id` が発行され、入荷管理画面（`/inbound`）の一覧・追跡番号検索の対象になる
- バリデーションエラー（SKUコード不一致・数量不正・追跡番号 255 字超）は行単位でエラーリストを返す

---

## SKU画像 API（フェーズ12追加）

> Console → Core へのリクエストプロキシ。

### SKU画像一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/images` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\ListProductSkuImageController` |

**レスポンス例**
```json
[
  { "id": 1, "skuId": 1, "imagePath": "1/uuid.png", "sortOrder": 1 }
]
```

---

### SKU画像登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/skus/{id}/images` |
| 認証 | 不要 |
| コントローラー | `App\Sku\Controller\CreateProductSkuImageController` |
| Content-Type | multipart/form-data |

**バリデーション**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| image | file | ○ | PNG のみ・200KB以下 |

---

### SKU画像ファイル配信（プロキシ）

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/image-file/{path}` |
| 認証 | 不要（`<img src>` から直接呼ばれるため） |
| コントローラー | `App\Sku\Controller\ProxySkuImageController` |

**仕様**
- Core の SKU画像配信 API（`/api/skus/{id}/image-file/{filename:.+}`）にプロキシ
- 認証ミドルウェア（auth.jwt）の外側で定義する。Market 側からも参照されるため

---

## ワークフロー API（フェーズ12追加）

> Console → Core へのリクエストプロキシ。承認・却下フローの UI 起点。
> 詳細仕様は Core_API.md の「ワークフロー API」セクションを参照。

### ワークフロー一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/workflows` |
| 認証 | 要 |
| コントローラー | `App\Workflow\Controller\ListWorkflowController` |

---

### ワークフロー詳細

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/workflows/{id}` |
| 認証 | 要 |
| コントローラー | `App\Workflow\Controller\GetWorkflowController` |

---

### ワークフロー作成

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows` |
| 認証 | 要 |
| コントローラー | `App\Workflow\Controller\CreateWorkflowController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| targetType | string | ○ | product / price / stock 等 |
| targetId | integer | ○ | 対象レコードの PK |
| payload | string | ○ | 適用予定の変更内容（JSON 文字列） |
| steps | array | ○ | 承認段階の配列 |

---

### ワークフロー即座適用

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows/immediate-apply` |
| 認証 | 要 |
| コントローラー | `App\Workflow\Controller\ImmediateApplyWorkflowController` |

---

### ワークフロー中止

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows/{id}/cancel` |
| 認証 | 要（申請者本人のみ） |
| コントローラー | `App\Workflow\Controller\CancelWorkflowController` |

---

### ワークフローステップ承認

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows/{id}/steps/{stepNumber}/approve` |
| 認証 | 要（当該ステップ承認権限） |
| コントローラー | `App\Workflow\Controller\ApproveWorkflowController` |

---

### ワークフローステップ却下

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows/{id}/steps/{stepNumber}/reject` |
| 認証 | 要（当該ステップ承認権限） |
| コントローラー | `App\Workflow\Controller\RejectWorkflowController` |

---

## 配送管理 API（フェーズ15追加）

Core の `/api/deliveries` 系を中継する Console API。すべて `auth.jwt` ミドルウェア配下。
PATCH 系は `config('app.auth.approver_roles')`（supervisor / admin / senior_admin / eternal_advisor）のみ許容。
中継時に Console は JWT の `sub` を `X-User-Id` ヘッダで Core に転送。

### 配送一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/deliveries[?shippingStatusId=N]` |
| 認証 | 要（auth.jwt） |
| コントローラー | `App\Delivery\Controller\ListDeliveryController` |
| 中継先 | Core `GET /api/deliveries` |

---

### 配送詳細

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/deliveries/{id}` |
| 認証 | 要 |
| コントローラー | `App\Delivery\Controller\GetDeliveryController` |
| 中継先 | Core `GET /api/deliveries/{id}` |

---

### 配送ステータス更新

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/deliveries/{id}/status` |
| 認証 | 要（approver_roles） |
| コントローラー | `App\Delivery\Controller\UpdateShippingStatusController` |
| 中継先 | Core `PATCH /api/deliveries/{id}/status` |

リクエストボディ：`{ shippingStatusId: long, reason: string? }`。Core が在庫不足で 409 を返した場合は透過。

---

### 配送先住所変更

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/deliveries/{id}/address` |
| 認証 | 要（approver_roles） |
| コントローラー | `App\Delivery\Controller\UpdateShippingAddressController` |
| 中継先 | Core `PATCH /api/deliveries/{id}/address` |

リクエストボディ：`{ shippingAddressId: long, reason: string? }`。Core がオーナー外住所で 403 を返した場合は透過。

---

### 配送予定日変更

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/deliveries/{id}/scheduled-date` |
| 認証 | 要（approver_roles） |
| コントローラー | `App\Delivery\Controller\UpdateScheduledDateController` |
| 中継先 | Core `PATCH /api/deliveries/{id}/scheduled-date` |

リクエストボディ：`{ scheduledDate: YYYY-MM-DD, reason: string? }`。`[manual]` プレフィックスは Core 側 Service が自動付与。

---

### 追跡番号登録

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/deliveries/{id}/tracking-code` |
| 認証 | 要（approver_roles） |
| コントローラー | `App\Delivery\Controller\RegisterTrackingCodeController` |
| 中継先 | Core `PATCH /api/deliveries/{id}/tracking-code` |

リクエストボディ：`{ trackingCode: string }`（最大100文字、空文字は 422）。

---

### 配送方法マスタ一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/shipping-methods` |
| 認証 | 要 |
| コントローラー | `App\Delivery\Controller\ListShippingMethodController` |
| 中継先 | Core `GET /api/shipping-methods` |

---

## 入荷管理 API（フェーズ15追加）

### 入荷一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/inbounds[?productId=N]` |
| 認証 | 要 |
| コントローラー | `App\Inbound\Controller\ListInboundController` |
| 中継先 | Core `GET /api/inbounds` |

---

### 入荷登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/inbounds` |
| 認証 | 要（approver_roles） |
| コントローラー | `App\Inbound\Controller\RegisterInboundController` |
| 中継先 | Core `POST /api/inbounds` |

リクエストボディ：`{ productId, skuId, quantity, inboundedAt?, supplierId? }`。phase16 Step3.1 以降は `inboundedAt` を任意項目化（未指定時は Core 側で本日付を強制セット）。

**RRRR-5**: `warehouseId` がリクエストに含まれていても Console Service が **明示的に剥がす**（`unset($payload['warehouseId'], $payload['warehouse_id'])`）。Core 側でデフォルト倉庫（id=1）を自動セット。

---

## フェーズ17（バッチ実行履歴・通知センター・手動起動）

すべて Core への Pass-through。`auth_user_id`（JWT 検証ミドルウェアが request に積む値）を Core に `X-User-Id` ヘッダで転送する。ルート定義は `routes/api/Batch.php` に集約。

| メソッド | パス | コントローラー | 中継先 | 備考 |
|----|----|----|----|----|
| GET  | `/api/console/batch/executions`              | `App\Batch\Controller\ListBatchExecutionController`         | Core `GET /api/console/batch/executions`              | クエリ透過（`jobName` / `status` / `offset` / `size`） |
| GET  | `/api/console/batch/executions/{id}`         | `App\Batch\Controller\GetBatchExecutionController`          | Core `GET /api/console/batch/executions/{id}`         | |
| GET  | `/api/console/batch/notifications`           | `App\Batch\Controller\ListConsoleNotificationController`    | Core `GET /api/console/batch/notifications`           | `X-User-Id` 必須 |
| PUT  | `/api/console/batch/notifications/{id}/read` | `App\Batch\Controller\MarkConsoleNotificationReadController`| Core `PUT /api/console/batch/notifications/{id}/read` | `X-User-Id` 必須 |
| POST | `/api/console/batch/{jobName}/run`           | `App\Batch\Controller\TriggerBatchManualController`         | Core `POST /api/console/batch/{jobName}/run`          | `check.permission:batch.manual` ミドルウェア / `X-User-Id` 必須 |

**レスポンス形式**：一覧 API は `{"items":[...], "total":N, "offset":M, "size":S}`（offset 採用。Core_API.md と整合）。

**手動起動の異常系**：Core から返却される 400 / 404 / 503 をそのまま透過。Console 経由では JWT ミドルウェアが認証を担うため `X-User-Id` 欠落起因の 400 は実運用では発生せず、認証失敗は Console が 401 を返す。

---

## フェーズ17（価格スケジュール Pass-through）

設計書 §13.5.2 に従い、Core の価格スケジュール API を Console から 1:1 で透過する。ルート定義は `routes/api/Sku.php` に集約。既存の現行価格 / 履歴系（`/api/skus/{id}/prices` 系）と同じネームスペースに収める。

| メソッド | パス | コントローラー | 中継先 |
|----|----|----|----|
| GET    | `/api/skus/{id}/scheduled-price` | `App\ScheduledPrice\Controller\GetScheduledSkuPriceController`      | Core `GET /api/skus/{id}/scheduled-price`（`is_pending=TRUE` の 1 件 / 無ければ 204） |
| PUT    | `/api/skus/{id}/scheduled-price` | `App\ScheduledPrice\Controller\RegisterScheduledSkuPriceController` | Core `PUT /api/skus/{id}/scheduled-price`（UPSERT。`apply_date < today` で 422） |
| DELETE | `/api/skus/{id}/scheduled-price` | `App\ScheduledPrice\Controller\DeleteScheduledSkuPriceController`   | Core `DELETE /api/skus/{id}/scheduled-price`（`is_pending=TRUE` を物理削除） |
| GET    | `/api/skus/{id}/prices/history`  | `App\Sku\Controller\ListSkuPriceHistoryController`                  | Core `GET /api/skus/{id}/prices/history`（`start_date DESC`） |

**SKU UI との対応**：Console SKU 詳細モーダルの「価格管理」タブ 3 ブロック化（実装計画書 §6.5）が、上記 4 エンドポイントと既存 `/api/skus/{id}/prices`（GET/POST）を組み合わせて使う。

---

## フェーズ18（問い合わせ管理 Pass-through）

設計書: [phase18_inquiry_management.md](../design/phase11_20/phase18_inquiry_management.md)（r3）

すべて Core への Pass-through。`auth_user_id`（JWT 検証ミドルウェアが request に積む値）を Core に `X-User-Id` ヘッダで転送する。`operation_logs` の記録は Core 側 `ReplyInquiryService` / `UpdateInquiryStatusService` で完結（既存 phase14/15/17 と同方針）。ルート定義は `routes/api/Inquiry.php` に集約。

| メソッド | パス | コントローラー | 中継先 | 備考 |
|----|----|----|----|----|
| GET   | `/api/console/inquiries/unread-count` | `App\Inquiry\Controller\GetUnreadInquiryCountController` | Core `GET /api/console/inquiries/unread-count` | ベルマーク用。`{ count: N }` |
| GET   | `/api/console/inquiries`              | `App\Inquiry\Controller\ListInquiryController`           | Core `GET /api/console/inquiries`              | クエリ透過（`status`/`targetType`/`dateFrom`/`dateTo`/`userName`/`page`/`size`） |
| GET   | `/api/console/inquiries/{id}`         | `App\Inquiry\Controller\GetInquiryController`            | Core `GET /api/console/inquiries/{id}`         | 内部メモを含む全件メッセージ |
| POST  | `/api/console/inquiries/{id}/messages`| `App\Inquiry\Controller\ReplyInquiryController`          | Core `POST /api/console/inquiries/{id}/messages` | `message`（必須） / `isInternalNote`（任意 boolean）。文字数上限 422 |
| PATCH | `/api/console/inquiries/{id}/status`  | `App\Inquiry\Controller\UpdateInquiryStatusController`   | Core `PATCH /api/console/inquiries/{id}/status` | `newStatus`（必須・enum） / `reason`（任意）。許容外ステータス 422 |

**Vue SPA**：
- サイドバーに `InquiryBellBadge`（30 秒ポーリング、タブ非表示時は停止 / `useVisibilityPolling` Composable）を組み込み、未対応件数バッジを表示。
- 一覧画面 `/inquiries`（`InquiryList.vue`）と詳細画面 `/inquiries/{id}`（`InquiryDetail.vue`）。詳細画面ではステータス遷移ドロップダウンと返信フォーム（内部メモチェックボックス付き）を表示。
- ポーリング間隔は `import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS`（既定 30000ms）で上書き可能。
