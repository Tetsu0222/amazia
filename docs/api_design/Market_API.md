# API定義書：Market

## 概要

| 項目 | 内容 |
|------|------|
| システム | Market（amazia-market） |
| 説明 | ユーザー向けECフロントエンド（React + Vite）。APIリクエストは amazia-core に直接送信する。 |
| Core ベースURL | `http://core:8080/api`（Vite プロキシ経由） |

---

## 商品 API（Core 呼び出し）

### 商品一覧取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/market` |
| 呼び出し先 | amazia-core `GET /api/products/market` |
| 実装ファイル | `src/features/products/api/products.js` の `getMarketProducts()` |

**仕様**
- SKUなし・在庫0の商品は除外済み（Core側）
- mainImage が null の場合はフロントでNOIMAGE表示

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

### 商品詳細取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/{id}/market` |
| 呼び出し先 | amazia-core `GET /api/products/{id}/market` |
| 実装ファイル | `src/features/products/api/products.js` の `getMarketProduct(id)` |

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

## 顧客 API（フェーズ13実装済 / Core 呼び出し）

会員登録・ログイン・プロフィールの API 群。Cookie ベースのセッション認証で、状態変更系リクエストには CSRF トークンを `X-CSRF-Token` ヘッダで付与する。

| 共通仕様 | 内容 |
|---------|------|
| 認証方式 | `MARKET_SESSION_ID` Cookie + `X-CSRF-Token` ヘッダ |
| 実装ファイル | `src/features/customer/api/customer.js`（`withCredentials: true`） |
| baseURL | `/api/customer` |

### CSRF トークン取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/csrf-token` |
| 呼び出し先 | amazia-core `GET /api/customer/csrf-token` |

---

### メールアドレス利用可能チェック

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/email-availability` |
| 呼び出し先 | amazia-core `GET /api/customer/email-availability` |

会員登録フォームでの即時バリデーション用。

---

### 会員ログイン

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/login` |
| 呼び出し先 | amazia-core `POST /api/customer/login` |
| 画面 | `/customer/login` |

---

### 会員ログアウト

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/logout` |
| 呼び出し先 | amazia-core `POST /api/customer/logout` |

---

### 会員新規登録

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/register` |
| 呼び出し先 | amazia-core `POST /api/customer/register` |
| 画面 | `/customer/register` |

---

### ログイン会員情報取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/me` |
| 呼び出し先 | amazia-core `GET /api/customer/me` |
| 画面 | `/customer/mypage` |

---

### 会員パスワード再発行申請

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/password/reset` |
| 呼び出し先 | amazia-core `POST /api/customer/password/reset` |
| 画面 | `/customer/password-reset` |

---

### 会員パスワード再設定

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/password/reset/confirm` |
| 呼び出し先 | amazia-core `POST /api/customer/password/reset/confirm` |
| 画面 | `/customer/password-reset/:token` |

---

### 郵便番号→住所検索

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/postal-addresses` |
| 呼び出し先 | amazia-core `GET /api/customer/postal-addresses` |

会員登録・配送先入力フォームでの住所自動入力。

---

## 注文 API（フェーズ14実装済 / Core 呼び出し）

会員ログイン済セッションで注文確定・購入履歴を扱う。

| 共通仕様 | 内容 |
|---------|------|
| 実装ファイル | `src/features/checkout/api/checkout.js`（`withCredentials: true`） |
| baseURL | `/api/customer` |

### 注文確定

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/orders/confirm` |
| 呼び出し先 | amazia-core `POST /api/customer/orders/confirm` |
| 画面 | `/checkout` |

---

### 購入履歴取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/orders` |
| 呼び出し先 | amazia-core `GET /api/customer/orders` |
| 画面 | `/customer/orders` |

---

## ルーティング対応表

Market 画面ルートと API の対応関係。

| 画面ルート | コンポーネント | 主な API |
|-----------|--------------|---------|
| `/products` | ProductList | GET `/api/products/market` |
| `/products/:id` | ProductDetail | GET `/api/products/:id/market` |
| `/customer/register` | Register | POST `/api/customer/register`、GET `/api/customer/email-availability`、GET `/api/customer/postal-addresses` |
| `/customer/login` | Login | POST `/api/customer/login`、GET `/api/customer/csrf-token` |
| `/customer/mypage` | MyPage | GET `/api/customer/me` |
| `/customer/password-reset` | PasswordResetRequest | POST `/api/customer/password/reset` |
| `/customer/password-reset/:token` | PasswordResetConfirm | POST `/api/customer/password/reset/confirm` |
| `/checkout` | Checkout | POST `/api/customer/orders/confirm` |
| `/customer/orders` | PurchaseHistory | GET `/api/customer/orders` |
