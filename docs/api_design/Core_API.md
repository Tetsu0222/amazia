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
