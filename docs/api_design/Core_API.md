# API定義書：Core

## 概要

| 項目 | 内容 |
|------|------|
| システム | Core（amazia-core） |
| ベースURL | `/api` |
| 認証方式 | なし（内部サービス間通信） |
| レスポンス形式 | JSON |

---

## 認証 API（フェーズ11追加）

### ログイン

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/auth/login` |
| コントローラー | `LoginController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| email | string | ○ | メールアドレス |
| password | string | ○ | パスワード |

**レスポンス例（200）**
```json
{ "access_token": "<JWT（HS256・有効期限15分）>" }
```

**備考**
- リフレッシュトークン（有効期限14日）は HttpOnly Cookie にセットして返す
- 成功時に `failed_attempts` を 0 にリセット

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 401 | メールアドレスまたはパスワードが不正（列挙攻撃防止のため同一メッセージ） |
| 403 | アカウントが無効（active_flag = false） |
| 423 | アカウントがロックアウト中（5回連続失敗・15分後に自動解除） |

---

### トークン再発行

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/auth/refresh` |
| コントローラー | `RefreshTokenController` |

**備考**
- リクエスト Cookie の `refresh_token` を検証し、新アクセストークンを返す
- トークンローテーション：再発行後に旧リフレッシュトークンを失効（revoked = true）
- 失効済みトークンの再利用は 401（リプレイ攻撃対策）

**レスポンス例（200）**
```json
{ "access_token": "<新しいJWT>" }
```

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 401 | Cookie なし・存在しないトークン・期限切れ・失効済み |

---

### パスワード再発行メール送信

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/auth/password/reset/request` |
| コントローラー | `PasswordResetRequestController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| email | string | ○ | メールアドレス |

**備考**
- 常に 200 を返す（列挙攻撃対策）
- 登録済みメールの場合のみ AWS SES でメールを送信し、DBにトークンを保存
- トークンは 64 文字ランダム文字列を SHA-256 でハッシュ化してDBに格納（有効期限30分・1回限り）

---

### パスワード再設定

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/auth/password/reset/confirm` |
| コントローラー | `PasswordResetConfirmController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| token | string | ○ | 再発行URLに含まれるトークン |
| password | string | ○ | 新パスワード |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 400 | トークンが無効・期限切れ・使用済み |
| 422 | パスワードポリシー違反（8文字未満・大文字なし・小文字なし・数字なし） |
| 422 | 過去3回分のパスワードと同一 |

---

## ユーザー管理 API（フェーズ11追加）

### 社員一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/users` |
| コントローラー | `ListUserController` |

**レスポンス例**
```json
[
  {
    "id": 1,
    "employeeId": "EMP001",
    "email": "user@amazia.example.com",
    "name": "山田 太郎",
    "roleCode": "admin",
    "activeFlag": true
  }
]
```

---

### 社員登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/users` |
| コントローラー | `CreateUserController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| employeeId | string | ○ | 社員ID（ユニーク） |
| email | string | ○ | メールアドレス（ユニーク） |
| name | string | ○ | 氏名（50文字以内） |
| password | string | ○ | パスワード |
| roleCode | string | ○ | ロール（admin / user） |

**パスワードポリシー**：8文字以上・英大文字・英小文字・数字を含む

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 422 | バリデーションエラー |
| 422 | employeeId または email の重複 |

---

### 社員編集

| 項目 | 内容 |
|------|------|
| メソッド | PUT |
| パス | `/users/{id}` |
| コントローラー | `UpdateUserController` |

**パスパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| id | integer | ○ | ユーザーID |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| email | string | × | メールアドレス |
| name | string | × | 氏名 |
| roleCode | string | × | ロール（admin / user） |
| activeFlag | boolean | × | 有効フラグ（false でログイン不可） |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 404 | 指定IDのユーザーが存在しない |
| 422 | バリデーションエラー |

---

## 商品画像 API

### 商品画像一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/{id}/images` |
| コントローラー | `ListProductImageController` |

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
| コントローラー | `CreateProductImageController` |
| Content-Type | multipart/form-data |

**バリデーション**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| image | file | ○ | PNG のみ・200KB以下 |

**保存パス**: `storage/Product/images/{productId}/{uuid}.png`

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 400 | PNG以外・200KB超 |

---

### 商品画像 sort_order 更新

| 項目 | 内容 |
|------|------|
| メソッド | PUT |
| パス | `/api/product-images/{id}/sort` |
| コントローラー | `UpdateProductImageSortController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| sortOrder | integer | ○ | 新しい表示順 |

---

### 商品画像削除

| 項目 | 内容 |
|------|------|
| メソッド | DELETE |
| パス | `/api/product-images/{id}` |
| コントローラー | `DeleteProductImageController` |

---

## SKU API

### SKU一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/{id}/skus` |
| コントローラー | `ListProductSkuController` |

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
| コントローラー | `CreateProductSkuController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| color | string | ○ | 色 |
| size | string | ○ | サイズ |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 409 | 同一商品内で color + size が重複 |

---

## SKU価格 API

### SKU現行価格取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/prices` |
| コントローラー | `GetProductSkuPriceController` |

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
| コントローラー | `CreateProductSkuPriceController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| price | integer | ○ | 価格（円） |
| startDate | date | × | 適用開始日 |

---

## SKU在庫 API

### SKU現在在庫取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/stocks` |
| コントローラー | `GetProductSkuStockController` |

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
| コントローラー | `ReceiveProductSkuStockController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| quantity | integer | ○ | 入荷数（1以上） |

---

### SKU在庫履歴取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/stocks/history` |
| コントローラー | `GetProductSkuStockHistoryController` |

**レスポンス例**
```json
[
  { "id": 1, "skuId": 1, "type": "receive", "quantity": 50, "createdAt": "2026-01-01T00:00:00" }
]
```

---

## Market集約 API

### 商品一覧（Market向け）

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/market` |
| コントローラー | `ListProductMarketController` |

**仕様**
- SKUが1つもない商品は除外
- 全SKUの在庫合計が0の商品は除外

**レスポンス例**
```json
[
  {
    "productId": 1,
    "productName": "商品A",
    "description": "説明",
    "minPrice": 1000,
    "totalStock": 50,
    "mainImage": "1/uuid.png"
  }
]
```

---

### 商品詳細（Market向け）

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/{id}/market` |
| コントローラー | `ListProductMarketController` |

**レスポンス例**
```json
{
  "product": { "id": 1, "name": "商品A", "description": "説明" },
  "skus": [
    {
      "skuId": 1,
      "skuCode": "P1-001",
      "color": "Red",
      "size": "M",
      "status": "active",
      "price": 1000,
      "stock": 50,
      "images": ["1/uuid.png"]
    }
  ]
}
```

---

## SKU 補助 API（フェーズ12追加）

### SKUコード検索

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/by-code/{code}` |
| コントローラー | `GetProductSkuByCodeController` |

**パスパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| code | string | ○ | SKU コード |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 404 | 指定コードの SKU が存在しない |

---

## SKU画像 API（フェーズ12追加）

### SKU画像一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/images` |
| コントローラー | `ListProductSkuImageController` |

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
| コントローラー | `CreateProductSkuImageController` |
| Content-Type | multipart/form-data |

**バリデーション**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| image | file | ○ | PNG のみ・200KB以下 |

**保存パス**: `storage/Product/sku_images/{skuId}/{uuid}.png`

---

### SKU画像ファイル配信

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/skus/{id}/image-file/{filename:.+}` |
| コントローラー | `ServeProductSkuImageController` |

**仕様**
- ローカル保存された SKU 画像ファイルをそのまま配信する
- `Content-Type: image/png` 固定。それ以外の拡張子は 404
- パスは `{filename:.+}` でドット含む完全名にマッチ

---

## ワークフロー API（フェーズ12追加）

商品・価格・在庫等への変更申請を承認フローに乗せるための API 群。テーブル `workflow_requests` / `workflow_requests_detail` を参照。

### ワークフロー一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/workflows` |
| コントローラー | `ListWorkflowRequestController` |

**仕様**
- ステータス（pending / approved / rejected / canceled）でフィルタ可能
- 結果は `workflow_requests` の作成日時降順

---

### ワークフロー詳細

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/workflows/{id}` |
| コントローラー | `GetWorkflowRequestController` |

**レスポンス例**
```json
{
  "id": 1,
  "targetType": "price",
  "targetId": 10,
  "requestedBy": 5,
  "status": "pending",
  "payload": "{\"newPrice\":1500}",
  "completedAt": null,
  "createdAt": "2026-04-01T10:00:00",
  "steps": [
    { "stepNumber": 1, "targetRole": "manager", "approverUserId": null, "status": "pending" },
    { "stepNumber": 2, "targetRole": "director", "approverUserId": null, "status": "waiting" }
  ]
}
```

---

### ワークフロー作成

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows` |
| コントローラー | `CreateWorkflowRequestController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| targetType | string | ○ | product / price / stock 等 |
| targetId | integer | ○ | 対象レコードの PK |
| payload | string | ○ | 適用予定の変更内容（JSON 文字列） |
| steps | array | ○ | 承認段階の配列（target_role / destination_user_id 等） |

**備考**
- `payload` は MySQL 本番では JSON 型カラム、Entity 側では H2 互換のため文字列扱い（`TBL_workflow_requests.md` 参照）

---

### ワークフロー即座適用

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows/immediate-apply` |
| コントローラー | `ImmediateApplyWorkflowController` |

**仕様**
- 承認フローを介さず変更を即時適用する管理者ルート
- 操作記録は `operation_logs` に積まれる

---

### ワークフロー中止

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows/{id}/cancel` |
| コントローラー | `CancelWorkflowRequestController` |

**仕様**
- 申請者本人のみが取り下げ可能。`workflow_requests.status` を `canceled` に遷移し、未終端のステップも canceled にする

---

### ワークフローステップ承認

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows/{id}/steps/{stepNumber}/approve` |
| コントローラー | `ApproveWorkflowStepController` |

**仕様**
- 承認すると当該ステップが `approved` に遷移し、次ステップが `waiting` → `pending` に進む
- 最終ステップ承認時に親 `workflow_requests.status` を `approved` に確定し、`payload` を実適用する

---

### ワークフローステップ却下

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/workflows/{id}/steps/{stepNumber}/reject` |
| コントローラー | `RejectWorkflowStepController` |

**仕様**
- いずれかのステップが却下されると親 `workflow_requests.status` も `rejected` に確定し、後続ステップは状態確定する

---

## Market 顧客 API（フェーズ13追加）

Market 会員（`market_customers`）向けの認証・プロフィール・住所検索 API 群。Console 社員（`users`）とは別系統で、セッション Cookie + CSRF トークンで認証する。

| 共通仕様 | 内容 |
|---------|------|
| 認証方式 | `MARKET_SESSION_ID` Cookie + `X-CSRF-Token` ヘッダ |
| セッションテーブル | `market_sessions`（CSRF トークン同居） |
| Cookie 属性 | HttpOnly / Secure / SameSite=Lax |

### CSRF トークン取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/csrf-token` |
| コントローラー | `CsrfTokenController` |

**仕様**
- 状態変更系リクエストの直前に呼び、レスポンスのトークンを `X-CSRF-Token` ヘッダで送る
- `MARKET_SESSION_ID` が無効な場合は新規セッションを発行する

---

### メールアドレス利用可能チェック

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/email-availability` |
| コントローラー | `EmailAvailabilityController` |

**クエリパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| email | string | ○ | チェック対象のメールアドレス |

**レスポンス例**
```json
{ "available": true }
```

---

### 会員新規登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/register` |
| コントローラー | `RegisterCustomerController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| nameLast / nameFirst | string | ○ | 姓・名 |
| postalCode | string | ○ | 郵便番号（ハイフンなし7桁） |
| address | string | ○ | 住所（番地以下含む） |
| birthday | date | ○ | 生年月日 |
| email | string | ○ | メールアドレス（UNIQUE） |
| password | string | ○ | パスワード |
| paymentMethod | string | ○ | 希望決済方法（credit_card / d_payment / cash_on_delivery） |
| cardToken | string | × | カードトークン（決済代行から取得） |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 422 | バリデーションエラー / email 重複 |

---

### 会員ログイン

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/login` |
| コントローラー | `LoginCustomerController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| email | string | ○ | メールアドレス |
| password | string | ○ | パスワード |

**仕様**
- 成功時は `MARKET_SESSION_ID` Cookie をセットし、`market_sessions` レコードを作成
- 連続失敗時は `failed_attempts` をインクリメント、閾値到達で `locked_until` 設定

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 401 | メールアドレスまたはパスワードが不正 |
| 403 | 退会済（active_flag = false） |
| 423 | アカウントロック中 |

---

### 会員ログアウト

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/logout` |
| コントローラー | `LogoutCustomerController` |

**仕様**
- `market_sessions` レコードを削除し、`MARKET_SESSION_ID` Cookie を expire させる

---

### ログイン会員情報取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/me` |
| コントローラー | `MyPageController` |

**レスポンス例**
```json
{
  "id": 1,
  "nameLast": "山田",
  "nameFirst": "太郎",
  "email": "tarou@example.com",
  "postalCode": "1000001",
  "address": "東京都千代田区..."
}
```

---

### 会員パスワード再発行申請

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/password/reset` |
| コントローラー | `PasswordResetRequestCustomerController` |

**仕様**
- 列挙攻撃対策のため常に 200 を返す
- 登録済みメールの場合のみ `market_customers_password_reset_tokens` にトークン（ハッシュ）を保存し、AWS SES でメール送信

---

### 会員パスワード再設定

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/password/reset/confirm` |
| コントローラー | `PasswordResetConfirmCustomerController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| token | string | ○ | 再発行URLに含まれるトークン |
| password | string | ○ | 新パスワード |

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 400 | トークン無効・期限切れ・使用済み |
| 422 | パスワードポリシー違反 / 過去 N 件と同一（`market_customer_password_histories` 参照） |

---

### 郵便番号→住所検索

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/postal-addresses` |
| コントローラー | `SearchPostalAddressController` |

**クエリパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| postalCode | string | ○ | 郵便番号（ハイフンなし7桁） |

**仕様**
- `postal_addresses` テーブル（KEN_ALL 取込先）から prefecture / city / town を返す
- 1郵便番号に複数町域が紐づく場合は配列で返す

**レスポンス例**
```json
[
  { "prefecture": "東京都", "city": "千代田区", "town": "永田町" }
]
```

---

## Market 注文 API（フェーズ14追加）

会員ログイン済セッションで注文確定・購入履歴を扱う API。`sales` / `address` / `payment_methods` / `shipping_statuses` を参照。

### 注文確定

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/orders/confirm` |
| コントローラー | `ConfirmOrderController` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| skuId | integer | ○ | 購入 SKU |
| quantity | integer | ○ | 購入数量 |
| paymentMethodId | integer | ○ | 決済方法（`payment_methods.id`） |
| shippingMethodId | integer | ○ | 配送方法（フェーズ15 で `shipping_methods` に正規化予定） |
| address | object | ○ | 配送先住所スナップショット（postal_code / prefecture / city / address_line / building） |
| paymentId | string | ○ | 決済代行から払い出される一意ID（冪等キー） |

**仕様**
- 注文時の住所を `address` テーブルにスナップショット INSERT してから `sales` を作成する
- `payment_id` UNIQUE で同一決済の二重 INSERT を防止（同じ paymentId の再送は 200 + 既存レコード返却）
- 配送ステータスは PENDING（id=1）で開始

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 400 | 在庫不足 / SKU 非販売状態 |
| 422 | バリデーションエラー（quantity ≤ 0 など） |

---

### 購入履歴取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/orders` |
| コントローラー | `GetMyPurchaseHistoryController` |

**仕様**
- ログイン中の `market_customers.id` に紐づく `sales` を取得
- 商品名・SKU色サイズ・配送ステータスを集約

**レスポンス例**
```json
[
  {
    "salesId": 100,
    "productName": "商品A",
    "skuCode": "P1-001",
    "color": "Red",
    "size": "M",
    "quantity": 1,
    "amount": 1000,
    "salesDate": "2026-04-01",
    "shippingStatusCode": "DELIVERED"
  }
]
```

---

## 売上・在庫 API（フェーズ14追加）

Console 管理画面向けの売上・在庫一覧 API。

### 売上一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/sales` |
| コントローラー | `ListSalesController` |

**クエリパラメータ（任意）**

| パラメータ | 型 | 説明 |
|------------|-----|------|
| from | date | 売上日下限 |
| to | date | 売上日上限 |
| shippingStatus | string | 配送ステータスコードでフィルタ |

---

### 在庫状況取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/inventory` |
| コントローラー | `GetInventoryController` |

**仕様**
- SKU 単位で `quantity` / 直近の入荷履歴を集約して返す
- フェーズ14時点では Console 在庫画面の表示元として利用
