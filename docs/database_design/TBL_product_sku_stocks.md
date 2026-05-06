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
| 4 | version | 楽観ロックバージョン | BIGINT | - | NOT NULL | 0 | フェーズ12追加。JPA `@Version` 用 |
| 5 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 6 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

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

## 変更履歴

| フェーズ | 内容 |
|---------|------|
| フェーズ12 | `version`（BIGINT NOT NULL DEFAULT 0）を追加。在庫増減・予約注文との同時更新競合を検知する |

## マイグレーションファイル

- JPA `@Entity` により自動生成
- `version` カラムは `amazia-core/src/main/resources/schema.sql` の ALTER TABLE で追加（既存環境向けフォールバック）
