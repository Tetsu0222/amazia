# テーブル定義書：cart_items

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | cart_items |
| 論理名 | カート明細 |
| 所属システム | Core |
| 説明 | カート内の SKU と数量。同一 SKU かつ同一 is_preorder は1行に集約され数量が加算される。 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 明細ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | cart_id | カートID | BIGINT | - | NOT NULL | - | FK: carts.id（CASCADE DELETE）|
| 3 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id |
| 4 | quantity | 数量 | INT | - | NOT NULL | 1 | 1 以上 |
| 5 | is_preorder | 予約フラグ | BOOLEAN | - | NOT NULL | FALSE | TRUE: 予約購入扱い、FALSE: 通常購入扱い |
| 6 | added_at | 追加日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |

## インデックス・制約

| 名前 | 種別 | カラム | 備考 |
|------|------|--------|------|
| PRIMARY | PRIMARY KEY | id | |
| uk_cart_items_cart_sku_preorder | UNIQUE | cart_id, sku_id, is_preorder | 同一 SKU・同一フラグは1行（数量で集約）|
| chk_cart_items_quantity_positive | CHECK | quantity > 0 | |
| idx_cart_items_cart | INDEX | cart_id | カートからの一覧取得用 |
| fk_cart_items_cart | FOREIGN KEY | cart_id → carts(id) | ON DELETE CASCADE |
| fk_cart_items_sku | FOREIGN KEY | sku_id → product_skus(id) | |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| carts | N:1 | 親カート |
| product_skus | N:1 | 対象 SKU |

## マイグレーション対応

- `amazia-core/src/main/resources/schema.sql` に `CREATE TABLE IF NOT EXISTS cart_items` で追記（フェーズ16.5 §Step 5）
- 本番 Core は Flyway 未使用。schema.sql を `spring.sql.init.mode=always` で起動時実行
