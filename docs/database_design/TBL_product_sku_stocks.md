# テーブル定義書：product_sku_stocks

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_sku_stocks |
| 論理名 | SKU現在在庫 |
| 所属システム | Core |
| 説明 | SKUごとの現在在庫数を1レコードで保持する。入荷・調整のたびに quantity を加算更新する |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 在庫ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id, UNIQUE |
| 3 | quantity | 在庫数 | INT | - | NOT NULL | 0 | |
| 4 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 5 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_sku_stocks_sku_id | UNIQUE | sku_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_skus | N:1 | 紐づくSKU |
| product_sku_stock_transactions | - | 在庫変動履歴 |

## マイグレーションファイル

JPA `@Entity` により自動生成
