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
- フェーズ14.5: `preorderStatus = NOT_PUBLIC` の商品は除外（公開期間外）
- フェーズ14.5: 在庫合計0の商品も `SOLD_OUT` / `BACK_ORDER` / `PRE_ORDER` 等として一覧に含まれる（旧仕様の「在庫0は除外」は撤廃）
- フェーズ14.5 P2 (#040): 全 SKU で価格未登録（`minPrice == null`）の商品は除外。EC 業界標準（Amazon / 楽天 / ZOZO）の「価格未登録は出品不可」に揃え、出品ミスが Market に露出することを Service 層で防ぐ
- フェーズ14.5 P2 (#041): 公開期間判定は **JST 0:00 基準**（`PreorderStatusService.isPublished()` 経由）。旧 `Product#isPublished()` の秒単位判定は廃止

**レスポンス例**
```json
[
  {
    "productId": 1,
    "productName": "商品A",
    "description": "説明",
    "minPrice": 1000,
    "totalStock": 50,
    "mainImage": "1/uuid.png",
    "preorderStatus": "ON_SALE",
    "releaseDate": null,
    "preorderStartDate": null,
    "acceptPreorder": false,
    "acceptBackorder": false
  }
]
```

`preorderStatus` 値は [予約ステータス API](#予約ステータス取得) と同じ 6 種。

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
  "product": {
    "id": 1, "name": "商品A", "description": "説明",
    "releaseDate": "2026-08-01",
    "preorderStartDate": "2026-07-01",
    "acceptPreorder": true,
    "acceptBackorder": false
  },
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
  ],
  "preorderStatus": "PRE_ORDER"
}
```

フェーズ14.5: `preorderStatus` をトップレベルに、4 カラム（`releaseDate` / `preorderStartDate` / `acceptPreorder` / `acceptBackorder`）を `product` 配下に追加。

**エラー**

| HTTPステータス | 条件 |
|----------------|------|
| 404 | 指定 ID の商品が存在しない |
| 404 | フェーズ14.5 P2 (#040): 商品は存在するが SKU が 0 件、または全 SKU で価格未登録（販売不可商品） |

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
| preorder | boolean | × | 予約購入フラグ（既定 `false`）。フェーズ14.5 追加。`true` のとき在庫減算チェックをスキップし、`is_preorder=true` で `sales` を作成（出荷時減算は phase15 r5 で実装） |

**仕様**
- 配送先住所は会員（`market_customers`）の現住所を Service 側で `address` テーブルへ自動スナップショット INSERT してから `sales` を作成
- `payment_id` は Core 内部で UUID v7 採番し UNIQUE 制約 + 冪等処理（user_id / sku_id / quantity / amount すべて一致時のみ既存 sales を返却）
- 配送ステータスは PENDING（id=1）で開始
- フェーズ14.5 (#041): 公開判定は **JST 0:00 基準**（`PreorderStatusService.isPublished()` 経由）。旧 `Product#isPublished()` の秒単位判定は廃止
- フェーズ14.5: `preorder=true` のとき `OrderConfirmationService` は在庫予備チェックと在庫減算をスキップ（[phase14_5_preorder_status.md](../design/phase11_20/phase14_5_preorder_status.md) §3-1）

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 400 | SKU 非 ACTIVE / 商品非公開（JST 0:00 基準） / 決済方法不在 / SKU 価格未登録 / SKU 在庫レコード不在 |
| 409 | 在庫不足（`preorder=false` のとき） / `payment_id` 衝突かつ冪等条件不一致 |
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
- `shippingStatusCode` は `deliveries.shipping_status_id` を「真」として返す（Console の状態遷移は `deliveries` のみを更新するため）。`deliveries` レコードが存在しない旧 sales のみ `sales.shipping_status_id` にフォールバック（trouble 043）

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

---

## 予約ステータス API（フェーズ14.5追加）

設計書: [phase14_5_preorder_status.md](../design/phase11_20/phase14_5_preorder_status.md)

### 予約ステータス取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/{id}/preorder-status` |
| コントローラー | `GetPreorderStatusController` |
| サービス | `PreorderStatusService` |

**パスパラメータ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| id | integer | ○ | 商品ID |

**レスポンス例**
```json
{
  "productId": 1,
  "status": "PRE_ORDER",
  "releaseDate": "2026-08-01",
  "preorderStartDate": "2026-07-01",
  "acceptPreorder": true,
  "acceptBackorder": false
}
```

**ステータス値（6 種）**

| status | 意味 |
|--------|------|
| NOT_PUBLIC | 公開開始日未到来 |
| PRE_ORDER_NOT_STARTED | 予約開始日未到来 |
| PRE_ORDER | 予約受付中（発売日未到来） |
| ON_SALE | 通常販売中（在庫あり） |
| BACK_ORDER | 在庫切れ・予約継続受付中 |
| SOLD_OUT | 完売 |

**判定基準日**: JST 0:00 起点（`Asia/Tokyo`）。判定優先順位は設計書 §2-2 を参照。

**エラー**

| HTTPステータス | 説明 |
|----------------|------|
| 404 | 指定 ID の商品が存在しない |

---

## 予約商品一覧 API（フェーズ16 Step2追加）

設計書: [phase16_ui_ux_improvement.md](../design/phase11_20/phase16_ui_ux_improvement.md) §2-4-4

### 予約商品一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/preorders` |
| コントローラー | `ListPreorderProductsController` |
| サービス | `ListPreorderProductsService` |

**仕様**
- `PreorderStatusService#judge()` が `PRE_ORDER` を返す商品のみ抽出（`is_active = TRUE` / 公開期間内 / 予約開始日到来済み / 発売日未到来）
- 各商品について `sales.is_preorder = TRUE` のレコードを商品単位で集計（数量・金額）
- 発売日昇順（`null` は末尾）で返却

**レスポンス例**
```json
[
  {
    "productId": 12,
    "productName": "Tシャツ夏モデル",
    "preorderStartDate": "2026-04-01",
    "releaseDate": "2026-08-01",
    "daysUntilRelease": 86,
    "acceptPreorder": true,
    "isActive": true,
    "preorderQuantity": 47,
    "preorderAmount": 235000,
    "minPrice": 1500,
    "maxPrice": 3000
  }
]
```

**レスポンス項目**

| 項目 | 型 | 説明 |
|------|-----|------|
| productId | integer | 商品 ID |
| productName | string | 商品名 |
| preorderStartDate | date \| null | 予約開始日（NULL なら公開と同時） |
| releaseDate | date \| null | 発売日 |
| daysUntilRelease | integer \| null | 当日基準で発売日までの日数（負値は経過日数） |
| acceptPreorder | boolean | 予約受付フラグ |
| isActive | boolean | Market 露出 ON/OFF |
| preorderQuantity | long | 予約数量合計（`is_preorder = TRUE` のみ） |
| preorderAmount | long | 予約金額合計（円） |
| minPrice | integer \| null | 配下 SKU 現行価格の最小値（フェーズ16 Step 6-4 で追加。SKU/価格未登録なら null） |
| maxPrice | integer \| null | 配下 SKU 現行価格の最大値（同上） |

---

## 商品登録・更新リクエスト拡張（フェーズ14.5追記）

`POST /api/products` / `PUT /api/products/{id}` に予約・発売関連 4 カラムを追加。

| パラメータ | 型 | 必須 | 既定 | 説明 |
|------------|-----|------|-----|------|
| releaseDate | date (YYYY-MM-DD) | × | NULL | 発売日。NULL = 公開即発売 |
| preorderStartDate | date (YYYY-MM-DD) | × | NULL | 予約開始日。NULL = 公開と同時に予約可 |
| acceptPreorder | boolean | × | false | 予約購入を受け付けるか |
| acceptBackorder | boolean | × | false | 在庫切れ時に予約継続するか |

レスポンスにも同 4 カラムが含まれる（Product Entity を返す既存挙動を踏襲）。

## 商品登録・更新リクエスト拡張（フェーズ16 Step1追記）

`POST /api/products` / `PUT /api/products/{id}` に Market 露出 ON/OFF スイッチを追加。

| パラメータ | 型 | 必須 | 既定 | 説明 |
|------------|-----|------|-----|------|
| isActive | boolean | × | true | Market 露出フラグ。FALSE で `/api/products` および `/api/products/market` から除外、`/api/products/{id}/market` は 404 を返す |

レスポンスにも `isActive` が含まれる（Jackson の `@JsonProperty("isActive")` で固定）。`status_code`（販売段階）とは直交した軸として扱う。

---

## 公開商品一覧（フェーズ14.5補完記載）

phase11 以前から実装されているがドキュメント未整備だったエンドポイント。フェーズ14.5 P2 (#041) で公開判定基準を変更した時点で最低限の API 仕様を補完する。

### 公開商品一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products` |
| コントローラー | `ListProductController` |
| サービス | `ListProductService.getPublished()` |

**仕様**
- 公開期間内の商品を全件返す（管理者向けは `/api/admin/products`）
- フェーズ14.5 P2 (#041): 公開判定は **JST 0:00 基準**（`PreorderStatusService.isPublished()` 経由）。旧 `Product#isPublished()` の秒単位判定は廃止。`publish_start` / `publish_end` が NULL のときは「制限なし」扱い
- フェーズ16 Step1: `is_active = FALSE` の商品は公開期間に関わらず除外（`isPublished()` の AND 条件）

**レスポンス**: `Product` エンティティの配列（id / name / description / statusCode / publishStart / publishEnd / releaseDate / preorderStartDate / acceptPreorder / acceptBackorder / isActive / version / createdAt / updatedAt）。`price` / `stock` はフェーズ10 で SKU 側に移行済の旧カラムで、現在は常に NULL。

---

## 配送管理 API（フェーズ15追加）

Console 管理画面向けの配送実体（`deliveries`）操作 API。注文確定と同時に `OrderConfirmationService` から `DeliveryCreationService.createForSales(...)` が呼ばれて PENDING で生成される。

### 配送一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/deliveries` |
| コントローラー | `ListDeliveryController` |

**クエリパラメータ（任意）**

| パラメータ | 型 | 説明 |
|------------|-----|------|
| shippingStatusId | long | 配送ステータス ID でフィルタ |

**レスポンス**: `DeliveryResponse` の配列（id / salesId / shippingAddressId / shippingMethodId / shippingStatusId / trackingCode / scheduledDate / shippedDate / deliveredDate / createdAt / updatedAt）

---

### 配送詳細取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/deliveries/{id}` |
| コントローラー | `GetDeliveryController` |

**レスポンス**: `DeliveryResponse`（404 時は `{"message":"delivery not found"}`）

---

### 配送ステータス遷移

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/deliveries/{id}/status` |
| コントローラー | `UpdateShippingStatusController` |
| サービス | `DeliveryStatusTransitionService.transition` |
| 操作者 | `X-User-Id` ヘッダ（`users.id`） |

**リクエストボディ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| shippingStatusId | long | ○ | 遷移先ステータス ID |
| reason | string | × | 遷移理由（任意フリーテキスト） |

**遷移可否ルール**（設計書 §配送ステータス遷移ルール）：

| 現在 → 次 | 遷移可否 |
|-----------|---------|
| PENDING → SHIPPED | ✅ |
| SHIPPED → DELIVERED | ✅ |
| DELIVERED → RETURN_REQUESTED | ✅ |
| RETURN_REQUESTED → RETURNED | ✅ |
| その他（巻き戻し・飛び越し） | 400 |

**SHIPPED 遷移時の在庫処理（P5-3 / P5-4）**：
- `is_preorder=false`：在庫操作なし（注文確定時に減算済み）
- `is_preorder=true`：`product_sku_stocks.quantity -= sales.quantity`（@Version 楽観ロック）+ `inventories.quantity` 同期減算（並行運用 / RRRR-2）+ `product_sku_stock_transactions` に `type='sale_preorder_shipment'` 記録
- 在庫不足時は 409 で **PENDING のまま維持**。Controller が REQUIRES_NEW で `operation_logs.action='shipping_blocked_insufficient_stock'` を別 TX 記録

**operation_logs**: 成功時 `action='update_shipping_status' / target_type='deliveries' / screen_name='console.delivery.update_status'` を記録

---

### 配送先住所変更

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/deliveries/{id}/address` |
| コントローラー | `UpdateShippingAddressController` |
| 操作者 | `X-User-Id` ヘッダ |

**リクエストボディ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| shippingAddressId | long | ○ | 新しい配送先住所 ID |
| reason | string | × | 変更理由 |

**RRR-7 オーナー検証**：`address.user_id == sales.user_id` のみ許容（不一致は 403）。`is_active=false` の住所は 400。

**operation_logs**: `action='update_shipping_address' / screen_name='console.delivery.update_address'`

---

### 配送予定日変更

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/deliveries/{id}/scheduled-date` |
| コントローラー | `UpdateScheduledDateController` |
| 操作者 | `X-User-Id` ヘッダ |

**リクエストボディ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| scheduledDate | date | ○ | 新しい配送予定日（YYYY-MM-DD） |
| reason | string | × | 変更理由 |

**operation_logs**: `action='update_scheduled_date' / comment` 先頭に **`[manual]` プレフィックス自動付与**（RRR-5 / Service 層が固定）。バッチ起点の入荷再計算では `[inbound_recalc]` プレフィックスを付与（`screen_name='core.batch.inbound_recalc'`）。

---

### 追跡番号登録

| 項目 | 内容 |
|------|------|
| メソッド | PATCH |
| パス | `/api/deliveries/{id}/tracking-code` |
| コントローラー | `RegisterTrackingCodeController` |
| 操作者 | `X-User-Id` ヘッダ |

**リクエストボディ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| trackingCode | string | ○ | 配送業者の追跡番号（最大100文字） |

**operation_logs**: `action='register_tracking_code' / screen_name='console.delivery.register_tracking'`

---

### 配送方法マスタ一覧

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/shipping-methods` |
| コントローラー | `ListShippingMethodController` |

**レスポンス**: `ShippingMethodResponse` の配列（id=1 home_delivery / id=2 konbini_pickup / id=3 dropoff）。schema.sql の INSERT IGNORE で固定投入。

---

## 入荷管理 API（フェーズ15追加）

### 入荷一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/inbounds` |
| コントローラー | `ListInboundController` |

**クエリパラメータ（任意）**

| パラメータ | 型 | 説明 |
|------------|-----|------|
| productId | long | 商品 ID でフィルタ |

**レスポンス**: `InboundResponse` の配列（id / productId / warehouseId / supplierId / quantity / inboundedAt / trackingCode / createdAt / updatedAt）

> `trackingCode` はフェーズ16 Step 6-6 で追加（任意・null 可）。Excel 一括入荷でのみ取り込み、手動入荷では null。

---

### 入荷登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/inbounds` |
| コントローラー | `RegisterInboundController` |
| サービス | `RegisterInboundService.register` |
| 操作者 | `X-User-Id` ヘッダ |

**リクエストボディ**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| productId | long | ○ | 商品 ID |
| skuId | long | ○ | SKU ID（商品の子であること） |
| quantity | integer | ○ | 入荷数量（≥1） |
| inboundedAt | date | × | 入荷日。phase16 Step3.1 以降は任意。未指定時は Service 側で本日付を強制セット（未来日入荷は Step:X「入荷予定画面」で扱う） |
| supplierId | long | × | 仕入先 ID（マスタ未整備のため任意） |
| trackingCode | string | × | 配送追跡番号（最大255文字）。フェーズ16 Step 6-6 で追加。Excel 一括入荷経由でのみ実運用利用 |

**RRRR-5**: `warehouseId` はリクエストに含めない。バックエンドが `config('amazia.delivery.default-warehouse-id')` を自動セット。

**処理フロー**:
1. `inbounds` INSERT
2. 既存 `ReceiveProductSkuStockService.receive(skuId, quantity)` を呼び出して `product_sku_stocks` 加算 + `product_sku_stock_transactions` 記録
3. `InventorySyncService.applyDelta(productId, 1, +quantity)` で `inventories.quantity` 同期加算（RRRR-2）
4. `DeliveryRescheduleService.recalculateForProduct(productId)` で在庫切れ `deliveries` の `scheduled_date` を FIFO 再計算（RRR-4 / RRRR-4）
5. `operation_logs.action='register_inbound' / screen_name='console.inbound.register'` 記録

**異常系**:
- 404: `productId` 存在しない / `skuId` 存在しない
- 400: `skuId` が指定 `productId` の子でない
- 422: バリデーションエラー（必須欠落・数量 0 以下など）

**レスポンス**: 201 で `InboundResponse`
