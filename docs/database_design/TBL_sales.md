# テーブル定義書：sales

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | sales |
| 論理名 | 売上・注文 |
| 所属システム | Core |
| 説明 | Market 顧客の購入レコード。注文確定時に1注文1SKU=1レコードで作成し、決済方法・配送先住所・配送ステータスをスナップショット参照する |
| 追加フェーズ | フェーズ14 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 売上ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | 購入者ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: market_customers.id（Console社員 users ではない） |
| 3 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id |
| 4 | quantity | 購入数量 | INT | - | NOT NULL | - | CHECK: > 0 |
| 5 | amount | 購入金額（合計） | INT | - | NOT NULL | - | 単価 × quantity の確定額（円） |
| 6 | payment_method_id | 決済方法ID | BIGINT | - | NOT NULL | - | FK: payment_methods.id |
| 7 | shipping_method_id | 配送方法ID | BIGINT | - | NOT NULL | - | FK: shipping_methods.id（フェーズ15 r5 で有効化） |
| 8 | shipping_address_id | 配送先住所ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: address.id（注文時スナップショット） |
| 9 | shipping_status_id | 配送ステータスID | BIGINT | - | NOT NULL | - | FK: shipping_statuses.id（初期値は PENDING=1） |
| 10 | payment_id | 決済ID | VARCHAR | 100 | NOT NULL | - | UNIQUE。決済代行から払い出される一意ID。冪等キーを兼ねる |
| 11 | is_preorder | 予約注文フラグ | BOOLEAN | - | NOT NULL | FALSE | TRUE の場合は予約販売 |
| 12 | sales_date | 売上日 | DATE | - | NOT NULL | - | 注文確定日 |
| 13 | shipping_date | 出荷日 | DATE | - | NULL | NULL | SHIPPED 遷移時に設定 |
| 14 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |
| 15 | updated_at | 更新日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| chk_sales_quantity_positive | CHECK | quantity > 0 |
| uk_sales_payment_id | UNIQUE | payment_id |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_sales_payment_id | UNIQUE | payment_id |
| idx_sales_user_id | INDEX | user_id |
| idx_sales_sales_date | INDEX | sales_date |
| idx_sales_sku_id | INDEX | sku_id |
| idx_sales_payment_method_id | INDEX | payment_method_id |
| idx_sales_shipping_method_id | INDEX | shipping_method_id |
| idx_sales_shipping_status_id | INDEX | shipping_status_id |

## 外部キー

| FK名 | カラム | 参照先 |
|------|--------|--------|
| fk_sales_user | user_id | market_customers(id) |
| fk_sales_sku | sku_id | product_skus(id) |
| fk_sales_payment_method | payment_method_id | payment_methods(id) |
| fk_sales_shipping_status | shipping_status_id | shipping_statuses(id) |
| fk_sales_shipping_address | shipping_address_id | address(id) |
| fk_sales_shipping_method | shipping_method_id | shipping_methods(id) ※フェーズ15 r5 で有効化 |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| market_customers | N:1 | 購入者（Market 顧客） |
| product_skus | N:1 | 購入SKU |
| payment_methods | N:1 | 決済方法 |
| shipping_methods | N:1 | 配送方法（phase15 r5 で FK 有効化） |
| shipping_statuses | N:1 | 配送ステータス |
| address | N:1 | 配送先住所スナップショット |
| sales_return | 1:N | 返品申請 |
| deliveries | 1:1 | 配送実体（phase15 r5：注文確定と同時に生成） |

## 設計上の注意

- `user_id` は `market_customers.id` を参照する。Console 社員の `users.id` ではない。
- `shipping_address_id` はスナップショットを参照するため、`market_customers` のプロフィール住所を後から変更しても過去注文の配送先は不変。
- 1注文に複数SKUを含める設計ではなく、現状は1注文1SKU=1レコード。複数SKU注文は同一 `payment_id` で複数行を作る前提（フェーズ15以降の見直し対象）。
- `payment_id` UNIQUE により注文確定APIの冪等性を担保する（同じ決済IDで二重 INSERT を防ぐ）。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ14 / V6+V7 相当：base + Step A 拡張カラムをすべて含む IF NOT EXISTS 版）
- `amazia-core/src/main/resources/db/migration/V6_*.sql` / `V7_*.sql`（名残ファイル：本番では実行されない）
