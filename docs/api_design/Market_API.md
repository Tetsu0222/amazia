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
- SKU なしの商品は除外済み（Core 側）
- フェーズ14.5: `preorderStatus = NOT_PUBLIC` の商品は除外。在庫 0 商品も `SOLD_OUT`/`BACK_ORDER`/`PRE_ORDER` 等として一覧に含まれる
- `mainImage` が null の場合はフロントで NOIMAGE 表示
- `preorderStatus` に応じて `ProductList.jsx` がラベル（通常販売 / 予約受付中 / 完売 等）と補足表示（在庫数 / 発売日 / 予約開始日）を切り替える

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

`preorderStatus` 値: `PRE_ORDER_NOT_STARTED` / `PRE_ORDER` / `ON_SALE` / `BACK_ORDER` / `SOLD_OUT`（`NOT_PUBLIC` は Core 側で除外済）。

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

`ProductDetail.jsx` は `preorderStatus` でステータスラベル（Chip）と購入ボタン文言（購入する / 予約する / 非表示）を切り替える。`PRE_ORDER` / `BACK_ORDER` のときは checkout に `&preorder=1` を付加して遷移する。

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
| 画面 | `/checkout` / `/checkout?preorder=1` |
| 実装ファイル | `src/features/checkout/api/checkout.js` の `confirmOrder()` / `src/features/checkout/pages/Checkout.jsx` |

**リクエストボディ（JSON）**

| パラメータ | 型 | 必須 | 説明 |
|------------|-----|------|------|
| skuId | integer | ○ | 購入対象 SKU |
| quantity | integer | ○ | 数量（予約モードでは在庫数の上限制約なし） |
| paymentMethodId | integer | ○ | 決済方法（クレジットカード / d払い / 代引き） |
| shippingMethodId | integer | ○ | 配送方法（宅配 / コンビニ受取 / 置き配） |
| preorder | boolean | × | 予約フラグ（既定 `false`）。フェーズ14.5 追加。`true` のとき Core 側で在庫減算チェックをスキップし、`is_preorder=true` の sales を作成 |

**予約フロー**: `ProductDetail.jsx` でステータスが `PRE_ORDER` / `BACK_ORDER` のとき「予約する」ボタンが `?preorder=1` クエリ付きで `/checkout` へ遷移する。`Checkout.jsx` は `preorder=1` を読み取って画面を予約モードに切替（タイトル「ご予約内容の確認」、数量上限なし、ボタン「予約を確定する」）し、`confirmOrder({ ..., preorder: true })` で送信する。詳細は [phase14_5_preorder_status.md](../design/phase11_20/phase14_5_preorder_status.md) §4-2 / [トラブル #039](../troubles/039_market_checkout_preorder_mode_missing.md)。

**価格未定 SKU の表示**: `Checkout.jsx` の合計欄は `selectedSku.price == null` のとき「価格未定」と表示し、`¥0` 表記を出さない（UI 防御）。Core 側の `OrderConfirmationService` は SKU 価格未登録の確定を 400 で弾くため、価格未定の予約は実質的にサーバ層でブロックされる（[トラブル #040](../troubles/040_market_lists_products_without_sku_price.md)）。

---

### 購入履歴取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/orders` |
| 呼び出し先 | amazia-core `GET /api/customer/orders` |
| 画面 | `/customer/orders` |

**レスポンス**: `PurchaseHistoryItem` の配列。表示項目：

| フィールド | 型 | 説明 |
|------------|-----|------|
| salesId | long | 売上 ID |
| salesDate | date | 購入日 |
| shippingDate | date \| null | 旧フィールド（フェーズ14互換）。フェーズ15以降は `delivery.shippedDate` を参照 |
| skuId | long | 購入 SKU ID |
| productName / color / size | string | 商品名 / 色 / サイズ |
| quantity / amount | int | 数量 / 金額 |
| shippingStatusCode | string | `PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED` |
| shippingMethodId | long | 配送方法 ID（1: 宅配 / 2: コンビニ受取 / 3: 置き配） |
| paymentMethodId | long | 決済方法 ID |
| preorder | boolean | 予約購入区分 |
| **delivery** | object \| null | フェーズ15 r5 で追加。フェーズ15以前の旧 sales には null |

`delivery` ネストの内訳（フェーズ15 r5 / Step D）:

| フィールド | 型 | 説明 |
|------------|-----|------|
| scheduledDate | date \| null | 配送予定日。null のときは「入荷待ち」と表示（RR-4） |
| shippedDate | date \| null | 発送日（SHIPPED 遷移時に Service 層が自動充填） |
| deliveredDate | date \| null | 配達完了日（DELIVERED 遷移時に同上） |
| trackingCode | string \| null | 配送業者発行の追跡番号 |
| shippingStatusId | long | `deliveries.shipping_status_id`（`sales.shipping_status_id` と通常同期） |
| shippingMethodId | long | `deliveries.shipping_method_id` |

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
| `/checkout` | Checkout | POST `/api/customer/orders/confirm`（通常購入） |
| `/checkout?preorder=1` | Checkout（予約モード） | POST `/api/customer/orders/confirm`（`preorder: true` 送信。フェーズ14.5 追加） |
| `/customer/orders` | PurchaseHistory | GET `/api/customer/orders` |
