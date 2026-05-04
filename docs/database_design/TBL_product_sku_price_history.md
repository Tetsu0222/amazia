# テーブル定義書：product_sku_price_history

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_sku_price_history |
| 論理名 | SKU価格履歴 |
| 所属システム | Core |
| 説明 | SKUごとの過去・未来の価格を履歴として保持する。status により past / future / applied を区別する |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 履歴ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id |
| 3 | price | 価格 | INT | - | NOT NULL | - | |
| 4 | start_date | 適用開始日 | DATE | - | NULL | NULL | |
| 5 | end_date | 適用終了日 | DATE | - | NULL | NULL | |
| 6 | status | ステータス | VARCHAR | 20 | NOT NULL | future | past / future / applied |
| 7 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 8 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

## ステータス定義

| 値 | 意味 |
|----|------|
| future | 未来の予約価格（まだ適用前） |
| applied | 現行として適用済み |
| past | 過去の価格（適用終了） |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_sku_price_history_sku_id | INDEX | sku_id |

## マイグレーションファイル

JPA `@Entity` により自動生成
