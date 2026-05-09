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
- フェーズ16 Step1: 商品マスタの `is_active = FALSE`（Console 商品マスタの「Market 公開」スイッチ OFF）の商品は Core 側で `NOT_PUBLIC` 扱いとなり一覧から除外される
- `mainImage` が null の場合はフロントで NOIMAGE 表示
- `preorderStatus` に応じて `ProductList.jsx` がラベル（通常販売 / 予約受付中 / 完売 等）と補足表示（在庫数 / 発売日 / 予約開始日）を切り替える
- フェーズ16.5 Step3: キーワード検索はクライアントサイドフィルタ（`features/products/searchUtils.js`）で実装。商品数 100 を超えた段階でサーバーサイド検索（`?keyword=` クエリ）への移行を予定（後続フェーズ）

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

フェーズ16 Step1: 商品マスタの `is_active = FALSE` の商品は本エンドポイントでも 404 を返す（URL 直叩き対策）。

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

## カート API（フェーズ16.5実装済 / Core 呼び出し）

会員ログイン済セッションでカートを操作する。Core 側 `/api/customer/carts/*` を直接呼び出し（Console Pass-through なし。設計書 [phase16_5 §5-4](../design/phase11_20/phase16_5_market_ui_improvement.md) 参照）。

| 共通仕様 | 内容 |
|---------|------|
| 実装ファイル | `src/features/cart/api/cart.js`（`withCredentials: true` / X-CSRF-Token を `customer.js` から流用） |
| baseURL | `/api/customer/carts` |

| メソッド | パス | 関数 | 概要 |
|----------|------|------|------|
| GET | `/api/customer/carts/me` | `getMyCart()` | 自分のカート取得（未ログイン 401） |
| POST | `/api/customer/carts/me/items` | `addToCart(skuId, quantity, preorder)` | カート追加（既存なら数量加算） |
| PUT | `/api/customer/carts/me/items/{itemId}` | `updateQuantity(itemId, quantity)` | 数量変更 |
| DELETE | `/api/customer/carts/me/items/{itemId}` | `removeFromCart(itemId)` | アイテム削除 |
| DELETE | `/api/customer/carts/me` | `clearCart()` | カート全削除（Checkout 完了時に呼ぶ） |

**仕様**：
- 1顧客1カート（`carts.customer_id` UNIQUE）。初回 POST で遅延作成
- 同一 SKU・同一 `is_preorder` フラグは1行に集約（数量加算）
- 状態管理は `features/cart/context/CartContext.jsx`（`useCart()` hook）。AppHeader バッジ・ProductCard / ProductDetail のカート追加・CartPage / Checkout `?from=cart` で参照
- レスポンス DTO は Core の `CartResponse`（`docs/api_design/Core_API.md` §Market カート API 参照）。`mainImage` は別経路（`getMarketProduct(productId)` のメイン画像）を流用するため Cart レスポンスには含まれない

**Checkout 連携**：
- `/checkout?from=cart` モードでは `useCart().items` を逐次 `POST /api/customer/orders/confirm` し、全件成功後に `clearCart()` を呼ぶ（`features/checkout/pages/Checkout.jsx`）
- 単品 Checkout（`?product_id=&sku_id=&quantity=`）は引き続き動作（後方互換）

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
| `/checkout?from=cart` | Checkout（カートモード） | カート明細を逐次 POST `/api/customer/orders/confirm`、完了後 DELETE `/api/customer/carts/me`（フェーズ16.5 追加） |
| `/cart` | CartPage | GET / PUT / DELETE `/api/customer/carts/me/*`（フェーズ16.5 追加） |
| `/customer/orders` | PurchaseHistory | GET `/api/customer/orders` |
| `/mypage/inquiries` | MyPageInquiryList | GET `/api/customer/inquiries`（フェーズ18 追加） |
| `/mypage/inquiries/new` | MyPageInquiryNew | POST `/api/customer/inquiries`（フェーズ18 追加） |
| `/mypage/inquiries/:id` | MyPageInquiryDetail | GET `/api/customer/inquiries/:id`、POST `/api/customer/inquiries/:id/messages`（フェーズ18 追加） |
| `/notices` | NoticeListPage | GET `/api/notices`、`/api/notice-categories`、`/api/customer/notices/{id}/read`（フェーズ19 追加） |
| 全ページ共通ヘッダー | HeaderNotice | GET `/api/customer/notices/unread`、`/api/customer/notices/unread-count`（フェーズ19 追加） |

---

## フェーズ18: 問い合わせ管理

設計書: [phase18_inquiry_management.md](../design/phase11_20/phase18_inquiry_management.md)（r3）

Market 顧客は MarketSession Cookie + CSRF（POST のみ）で認証。Console 経由の Pass-through ではなく **React 直接 Core**（CloudFront `/api/customer/*` Behavior は phase13 §2.2 で構築済み）。`is_internal_note` は API DTO（`MarketCreateInquiryRequest` / `MarketReplyInquiryRequest`）から構造的に除外（RV-9 / Mass Assignment 防御）。

### GET `/api/customer/inquiries`

**説明**: 自分の問い合わせ一覧を取得（IDOR 対策で Service 層が `user_id = sessionCustomerId` を強制）。

**クエリパラメータ**:
- `page` (number, default=0)
- `size` (number, default=20)

**レスポンス**: Spring Page 形式（`{ content: [...], totalElements: N, ... }`）。各要素は `{ id, userId, userName, subject, status, targetType, targetId, targetLabel, createdAt, updatedAt }`。

### GET `/api/customer/inquiries/:id`

**説明**: 自分の問い合わせ詳細を取得。**内部メモ（`is_internal_note=true`）は除外**して `messages` 配列を返す。

**レスポンス**: `{ id, ..., messages: [{ id, senderType, senderId, senderName, message, isInternalNote, createdAt }] }`。

**異常系**: 他人の問い合わせ ID を指定すると 403、存在しない ID で 404。

### POST `/api/customer/inquiries`

**説明**: 新規問い合わせ作成。

**リクエスト**: `{ subject (NotBlank, ≤100), message (NotBlank, ≤4000), targetType ('delivery'|'product'|'sales'|null), targetId (number|null) }`。`is_internal_note` フィールドは **DTO 自体に存在しない**（RV-9）。

**異常系**: 文字数上限超過 422、`target_type='delivery'` で他顧客の delivery を指定 403、商品が `is_active=false` で 400。

### POST `/api/customer/inquiries/:id/messages`

**説明**: 自分の問い合わせへの返信を投稿。

**リクエスト**: `{ message (NotBlank, ≤4000) }`。`is_internal_note` フィールドは **DTO 自体に存在しない**（Service 層は常に `false` で投入）。

**通知連携**: 投稿時 phase17 `BatchAlertNotifier.dispatch('INFO', 'inquiry_alerts', ...)` が呼ばれ、Console の `console_notifications` に INSERT される（60 分以内同一 inquiry への連投は `payload_hash` 一致で suppressed=TRUE）。

---

## フェーズ19: お知らせ機能

設計書: [phase19_notice_management.md](../design/phase11_20/phase19_notice_management.md)（r2）

Market では以下の 6 エンドポイントを利用する。会員セッションがあれば `is_read` キーが付与され、未認証時は `is_read` キーが JSON から省略される（R19-9 / `Optional<Boolean>` + `@JsonInclude(NON_ABSENT)`）。`author` フィールドは Market 用 DTO に**コンパイル時から存在しない**（R19-11）。

### GET `/api/notices`

**説明**: 公開期間内 + 未削除のお知らせ一覧をページングで取得。並び順は Core 側で `category_id ASC, publish_start DESC, id DESC` 固定。

**クエリパラメータ**:
- `page` (number, 1始まり, default=1)
- `per_page` (number, max=100, default=20)
- `category_id` (number, optional) — 重要(=1) / 普通(=2)

**レスポンス**: Spring Page 形式（`{ content: [...], totalElements: N, ... }`）。各要素は `{ id, subject, category: { id, code, label, displayOrder }, body, publishStart, publishEnd, updatedAt, isRead? }`。

### GET `/api/notices/{id}`

**説明**: お知らせ単件取得（公開期間内 + 未削除）。

**レスポンス**: 上記単体オブジェクト。

**異常系**: 公開期間外 / 論理削除済 / 存在しない id は 404。

### GET `/api/notice-categories`

**説明**: お知らせ分類マスタ一覧（認証不要）。`display_order` 昇順で `[{ id, code, label, displayOrder }, ...]` を返す。

### POST `/api/customer/notices/{id}/read`

**説明**: 既読登録。MarketSession + CSRF（`X-CSRF-Token`）必須。**冪等**：同一 (notice_id, market_customer_id) を複数回叩いても 200。

**異常系**: 未ログインで 401、公開期間外 / 論理削除済 / 存在しない id で 404。

### GET `/api/customer/notices/unread-count`

**説明**: 未読数集計（会員セッション必須）。レスポンス：

```json
{ "data": { "important": 1, "normal": 3, "total": 4 } }
```

未存在 category は 0 で埋める。`HeaderNotice` / `useUnreadCount` Hook が 60 秒 Polling（`VITE_NOTICE_UNREAD_POLL_MS`）。

**異常系**: 未ログインで 401。

### GET `/api/customer/notices/unread`

**説明**: ヘッダー表示用未読お知らせ取得。最大 `amazia.notice.header.max-items`（=10）件まで。並び順は `category_id ASC → publish_start DESC → id DESC`。

**レスポンス**: `[{ id, subject, category, body, publishStart, publishEnd, updatedAt, isRead }, ...]`（`author` 含まず）。

### React 構成

| パス / コンポーネント | 役割 |
|---|---|
| `/notices`（[NoticeListPage.jsx](../../amazia-market/src/features/notice/components/NoticeListPage.jsx)） | 一覧 + タブフィルタ（すべて / 重要 / 普通） + ページング |
| `<HeaderNotice />`（全ページ共通ヘッダー） | アコーディオン形式 + 5 秒ローテーション + 60 秒 Polling |
| `<NoticeModal />` | 件名クリックでモーダル展開、開いた瞬間に未読なら markAsRead を発火、前後遷移ボタン |
| `<UnreadBadge />` | 未読数バッジ（`count === 0` で非表示） |
| `useUnreadCount` Hook | 未読数 Polling（既定 60 秒 / `VITE_NOTICE_UNREAD_POLL_MS`） |
| `useHeaderNotices` Hook | ヘッダー用 Polling + ローテーション（既定 5 秒 / `VITE_NOTICE_HEADER_ROTATE_MS`）。Polling × ローテーション競合は「次の境界で切替」方式（R19-7）、未読 0 件への遷移のみ即時非表示化 |

**XSS 対策**: 本文はプレーンテキスト保存 / React の自動エスケープに依存。改行のみ `<br>` 変換、`dangerouslySetInnerHTML` は使用しない。

**Vitest テスト**:
- [NoticeListPage.test.jsx](../../amazia-market/src/features/notice/components/NoticeListPage.test.jsx)（一覧表示 / タブ切替の category_id クエリ / 空状態 / モーダル展開時の markAsRead 発火）
- [NoticeModal.test.jsx](../../amazia-market/src/features/notice/components/NoticeModal.test.jsx)（未読時 markAsRead / 既読時非発火 / XSS エスケープ / 前後ボタン活性制御）
- [HeaderNotice.test.jsx](../../amazia-market/src/features/notice/components/HeaderNotice.test.jsx)（0 件・401 で非表示 / 件名表示 / 5 秒切替 fake timer）
- [useUnreadCount.test.jsx](../../amazia-market/src/features/notice/hooks/useUnreadCount.test.jsx)（初回取得 / 401 authError / 60 秒 Polling 再取得）
