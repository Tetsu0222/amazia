# テーブル定義書：product_sku_prices

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_sku_prices |
| 論理名 | SKU現行価格 |
| 所属システム | Core |
| 説明 | SKUごとの現在有効な販売価格を1レコードで管理する。価格変更時は上書き更新し、履歴はproduct_sku_price_historyへ移動 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 価格ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id |
| 3 | price | 価格 | INT | - | NOT NULL | - | 円（税抜） |
| 4 | start_date | 適用開始日 | DATE | - | NULL | NULL | |
| 5 | end_date | 適用終了日 | DATE | - | NULL | NULL | NULL = 無期限 |
| 6 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 7 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_sku_prices_sku_id | INDEX | sku_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_skus | N:1 | 紐づくSKU |
| product_sku_price_history | - | 過去・未来の価格履歴 |

## マイグレーションファイル

JPA `@Entity` により自動生成
