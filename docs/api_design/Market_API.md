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

## 予定エンドポイント（フェーズ13以降）

| メソッド | パス | 説明 |
|----------|------|------|
| POST | `/api/auth/login` | Marketユーザーログイン |
| POST | `/api/auth/logout` | Marketユーザーログアウト |
| POST | `/api/auth/register` | Marketユーザー新規登録 |
| GET | `/api/orders` | 注文履歴取得 |
| POST | `/api/orders` | 注文作成 |
