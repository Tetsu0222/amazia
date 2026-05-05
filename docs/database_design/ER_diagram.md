# ER図

## システム：Core（フェーズ11以降）

認証・認可テーブルを含む Core システム全体の ER 図。

```mermaid
erDiagram
    roles {
        BIGINT_UNSIGNED id PK
        VARCHAR code UK
        VARCHAR name
    }

    permissions {
        BIGINT_UNSIGNED id PK
        VARCHAR screen_id UK
        VARCHAR name
    }

    role_permissions {
        BIGINT_UNSIGNED role_id FK
        BIGINT_UNSIGNED permission_id FK
    }

    users {
        BIGINT_UNSIGNED id PK
        VARCHAR employee_id UK
        VARCHAR email UK
        VARCHAR name
        VARCHAR password_hash
        BIGINT_UNSIGNED role_id FK
        BOOLEAN active_flag
        INT failed_attempts
        DATETIME locked_until
        DATETIME created_at
        DATETIME updated_at
    }

    refresh_tokens {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED user_id FK
        VARCHAR token_hash UK
        DATETIME expires_at
        BOOLEAN revoked
        DATETIME created_at
    }

    password_reset_tokens {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED user_id FK
        VARCHAR token_hash UK
        DATETIME expires_at
        BOOLEAN used
        DATETIME created_at
    }

    products {
        BIGINT id PK
        VARCHAR name
        TEXT description
        VARCHAR category
        DATE publish_start
        DATE publish_end
        VARCHAR status
        DATETIME created_at
        DATETIME updated_at
    }

    product_images {
        BIGINT id PK
        BIGINT product_id FK
        VARCHAR image_path
        INT sort_order
        DATETIME created_at
        DATETIME updated_at
    }

    product_skus {
        BIGINT id PK
        BIGINT product_id FK
        VARCHAR sku_code UK
        VARCHAR color
        VARCHAR size
        VARCHAR status
        DATETIME created_at
        DATETIME updated_at
    }

    product_sku_prices {
        BIGINT id PK
        BIGINT sku_id FK
        INT price
        DATE start_date
        DATE end_date
        DATETIME created_at
        DATETIME updated_at
    }

    product_sku_price_history {
        BIGINT id PK
        BIGINT sku_id FK
        INT price
        DATE start_date
        DATE end_date
        VARCHAR status
        DATETIME created_at
        DATETIME updated_at
    }

    product_sku_stocks {
        BIGINT id PK
        BIGINT sku_id FK_UK
        INT quantity
        DATETIME created_at
        DATETIME updated_at
    }

    product_sku_stock_transactions {
        BIGINT id PK
        BIGINT sku_id FK
        VARCHAR type
        INT quantity
        DATETIME created_at
    }

    product_sku_images {
        BIGINT id PK
        BIGINT sku_id FK
        VARCHAR image_path
        INT sort_order
        DATETIME created_at
        DATETIME updated_at
    }

    roles ||--o{ users : "1:N"
    roles ||--o{ role_permissions : "1:N"
    permissions ||--o{ role_permissions : "1:N"
    users ||--o{ refresh_tokens : "1:N"
    users ||--o{ password_reset_tokens : "1:N"
    products ||--o{ product_images : "1:N"
    products ||--o{ product_skus : "1:N"
    product_skus ||--o{ product_sku_prices : "1:N"
    product_skus ||--o{ product_sku_price_history : "1:N"
    product_skus ||--o| product_sku_stocks : "1:0..1"
    product_skus ||--o{ product_sku_stock_transactions : "1:N"
    product_skus ||--o{ product_sku_images : "1:N"
```

## テーブル一覧

### Core システム（認証・認可）

| テーブル名 | 論理名 | 用途 | 追加フェーズ |
|------------|--------|------|------------|
| roles | ロール | admin / user のロール定義 | フェーズ11 |
| permissions | パーミッション | 画面単位のアクセス権限定義 | フェーズ11 |
| role_permissions | ロール・パーミッション中間 | ロールと権限の多対多関係 | フェーズ11 |
| users | ユーザー | Console社員アカウント（JWT認証・ロール・ロックアウト対応） | フェーズ11で刷新 |
| refresh_tokens | リフレッシュトークン | JWT認証のリフレッシュトークン管理 | フェーズ11 |
| password_reset_tokens | パスワードリセットトークン | パスワード再発行フロー用一時トークン | フェーズ11で刷新 |

### Core システム（商品管理）

| テーブル名 | 論理名 | 用途 | 追加フェーズ |
|------------|--------|------|------------|
| products | 商品 | 商品マスタ（価格・在庫を持たない） | フェーズ8 |
| product_images | 商品画像 | 商品単位の画像管理（sort_order=1がメイン） | フェーズ9 |
| product_skus | SKU | 色×サイズの組み合わせ単位の管理 | フェーズ10 |
| product_sku_prices | SKU現行価格 | SKUごとの現在有効な価格（1レコード） | フェーズ10 |
| product_sku_price_history | SKU価格履歴 | past / future / applied の価格履歴 | フェーズ10 |
| product_sku_stocks | SKU現在在庫 | SKUごとの現在在庫数（1レコード） | フェーズ10 |
| product_sku_stock_transactions | SKU在庫履歴 | 入荷・調整の変動履歴 | フェーズ10 |
| product_sku_images | SKU画像 | SKUごとの複数画像（sort_order=1がメイン） | フェーズ10 |

## 備考

- `product_skus` は (product_id, color, size) の複合UNIQUEを持つ
- `product_sku_stocks` は sku_id に UNIQUEを持つ（SKUにつき在庫レコードは1つ）
- `users` テーブルはフェーズ11でLaravel Sanctum管理からJWT認証管理に全面刷新
- `refresh_tokens` と `password_reset_tokens` はトークン実体をDBに保存せず、ハッシュ値のみ格納
