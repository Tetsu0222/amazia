# テーブル定義書：product_sku_stock_transactions

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_sku_stock_transactions |
| 論理名 | SKU在庫履歴 |
| 所属システム | Core |
| 説明 | SKUの入荷・調整履歴を保持する。quantity は増減値（入荷は正、調整は正負どちらもあり得る） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 履歴ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id |
| 3 | type | 取引種別 | VARCHAR | 20 | NOT NULL | - | receive / adjust |
| 4 | quantity | 増減数 | INT | - | NOT NULL | - | 入荷は正値のみ |
| 5 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |

## 取引種別定義

| 値 | 意味 |
|----|------|
| receive | 入荷（加算のみ） |
| adjust | 在庫調整（加減算） |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_sku_stock_transactions_sku_id | INDEX | sku_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_skus | N:1 | 紐づくSKU |

## マイグレーションファイル

JPA `@Entity` により自動生成
