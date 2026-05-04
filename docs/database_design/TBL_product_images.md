# テーブル定義書：product_images

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_images |
| 論理名 | 商品画像 |
| 所属システム | Core |
| 説明 | 商品に紐づく画像を複数管理する。sort_order=1 がメイン画像 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 画像ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | product_id | 商品ID | BIGINT | - | NOT NULL | - | FK: products.id |
| 3 | image_path | 画像パス | VARCHAR | 300 | NOT NULL | - | `{productId}/{uuid}.png` 形式 |
| 4 | sort_order | 表示順 | INT | - | NOT NULL | - | 1がメイン画像、昇順で表示 |
| 5 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 6 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_product_images_product_id_sort | INDEX | product_id, sort_order |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| products | N:1 | 紐づく商品 |

## 画像仕様

| 項目 | 内容 |
|------|------|
| フォーマット | PNG固定 |
| 最大サイズ | 200KB |
| 最大解像度 | 800×800px（超過時はサーバーリサイズ） |
| 最大枚数 | 10枚/商品 |
| 保存先 | ローカル: `storage/Product/images/{productId}/` / 本番: S3 |

## マイグレーションファイル

JPA `@Entity` により自動生成
