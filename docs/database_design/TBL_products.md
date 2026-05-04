# テーブル定義書：products

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | products |
| 論理名 | 商品 |
| 所属システム | Core |
| 説明 | 商品の基本情報を管理する。価格・在庫はSKU側で管理するため本テーブルは持たない |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 商品ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | name | 商品名 | VARCHAR | 255 | NOT NULL | - | |
| 3 | description | 商品説明 | TEXT | - | NULL | NULL | |
| 4 | price | 価格（旧） | INT | - | NULL | NULL | フェーズ10以降はSKU側で管理 |
| 5 | stock | 在庫（旧） | INT | - | NULL | NULL | フェーズ10以降はSKU側で管理 |
| 6 | status_code | ステータスコード | VARCHAR | 50 | NULL | NULL | ON_SALE / WAITING 等 |
| 7 | publish_start | 公開開始日時 | DATETIME | - | NULL | NULL | |
| 8 | publish_end | 公開終了日時 | DATETIME | - | NULL | NULL | |
| 9 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 10 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_images | 1:N | 商品画像（フェーズ9） |
| product_skus | 1:N | SKU（フェーズ10） |

## マイグレーションファイル

JPA `@Entity` により自動生成（`spring.jpa.hibernate.ddl-auto`）
