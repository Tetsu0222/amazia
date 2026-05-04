# テーブル定義書：product_skus

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_skus |
| 論理名 | SKU |
| 所属システム | Core |
| 説明 | 商品の色×サイズ組み合わせ（SKU）を管理する。SKUは削除不可（履歴保持のため停止ステータスで管理） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | SKU ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | product_id | 商品ID | BIGINT | - | NOT NULL | - | FK: products.id |
| 3 | sku_code | SKUコード | VARCHAR | 50 | NOT NULL | - | UNIQUE、自動採番 |
| 4 | color | 色 | VARCHAR | 100 | NOT NULL | - | 例: Red / Blue |
| 5 | size | サイズ | VARCHAR | 50 | NOT NULL | - | 例: S / M / L |
| 6 | status | ステータス | VARCHAR | 20 | NOT NULL | ACTIVE | ACTIVE / INACTIVE |
| 7 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 8 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

## インデックス・制約

| 名前 | 種別 | カラム | 備考 |
|------|------|--------|------|
| PRIMARY | PRIMARY KEY | id | |
| uk_sku_code | UNIQUE | sku_code | |
| uk_product_color_size | UNIQUE | product_id, color, size | 同一商品内で色×サイズは重複不可 |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| products | N:1 | 紐づく商品 |
| product_sku_prices | 1:1 | 現行価格 |
| product_sku_price_history | 1:N | 価格履歴 |
| product_sku_stocks | 1:1 | 現在在庫 |
| product_sku_stock_transactions | 1:N | 在庫取引履歴 |
| product_sku_images | 1:N | SKU画像 |

## マイグレーションファイル

JPA `@Entity` により自動生成
