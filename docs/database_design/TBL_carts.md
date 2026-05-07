# テーブル定義書：carts

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | carts |
| 論理名 | カート |
| 所属システム | Core |
| 説明 | Market 顧客の買い物カゴ。1顧客1カート（UNIQUE customer_id）で、明細は cart_items に保持。 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | カートID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | customer_id | 顧客ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: market_customers.id（CASCADE DELETE）|
| 3 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |
| 4 | updated_at | 更新日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 行更新時に書き換え |

## インデックス・制約

| 名前 | 種別 | カラム | 備考 |
|------|------|--------|------|
| PRIMARY | PRIMARY KEY | id | |
| uk_carts_customer | UNIQUE | customer_id | 1顧客1カート |
| fk_carts_customer | FOREIGN KEY | customer_id → market_customers(id) | ON DELETE CASCADE |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| market_customers | N:1 | カートの所有顧客 |
| cart_items | 1:N | カート明細（同一 SKU・同一 is_preorder は1行に集約） |

## マイグレーション対応

- `amazia-core/src/main/resources/schema.sql` に `CREATE TABLE IF NOT EXISTS carts` で追記（フェーズ16.5 §Step 5）
- 本番 Core は Flyway 未使用。schema.sql を `spring.sql.init.mode=always` で起動時実行
