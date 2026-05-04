# テーブル定義書：product_sku_images

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_sku_images |
| 論理名 | SKU画像 |
| 所属システム | Core |
| 説明 | SKUに紐づく画像を複数保持する。sort_order=1 がメイン画像として商品一覧・詳細で使用される |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 画像ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id |
| 3 | image_path | 画像パス | VARCHAR | 255 | NOT NULL | - | S3またはローカルストレージのパス |
| 4 | sort_order | 表示順 | INT | - | NOT NULL | 1 | 1がメイン画像 |
| 5 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 6 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_sku_images_sku_id | INDEX | sku_id |

## 仕様

- PNG固定
- 200KB以下
- 800px以内
- 複数枚対応
- sort_order 変更可能（変更後のメイン画像は sort_order=1 のもの）

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_skus | N:1 | 紐づくSKU |

## マイグレーションファイル

JPA `@Entity` により自動生成
