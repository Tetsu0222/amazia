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
| 9 | version | 楽観ロックバージョン | BIGINT | - | NOT NULL | 0 | フェーズ12追加。JPA `@Version` 用 |
| 10 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 11 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |
| 12 | release_date | 発売日 | DATE | - | NULL | NULL | フェーズ14.5追加。NULL のとき「公開即発売 = `ON_SALE` 起点」 |
| 13 | preorder_start_date | 予約開始日 | DATE | - | NULL | NULL | フェーズ14.5追加。NULL のとき「公開と同時に予約可」 |
| 14 | accept_preorder | 予約購入受付フラグ | BOOLEAN | - | NOT NULL | FALSE | フェーズ14.5追加。発売日前の予約購入を受け付けるか |
| 15 | accept_backorder | 在庫切れ予約継続フラグ | BOOLEAN | - | NOT NULL | FALSE | フェーズ14.5追加。発売日後・在庫切れ時に予約を受け付けるか |
| 16 | is_active | Market 露出フラグ | BOOLEAN | - | NOT NULL | TRUE | フェーズ16 Step1追加。FALSE のとき公開期間に関わらず Market 非表示 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_images | 1:N | 商品画像（フェーズ9） |
| product_skus | 1:N | SKU（フェーズ10） |

## 変更履歴

| フェーズ | 内容 |
|---------|------|
| フェーズ12 | `version`（BIGINT NOT NULL DEFAULT 0）を追加。JPA `@Version` で楽観ロックを実現する |
| フェーズ14.5 | 予約ステータス判定用の 4 カラム（`release_date` / `preorder_start_date` / `accept_preorder` / `accept_backorder`）を追加。設計書 [phase14_5_preorder_status.md](../design/phase11_20/phase14_5_preorder_status.md) §2-3 |
| フェーズ14.5 P2 | 旧カラム `price` / `stock` を NOT NULL → NULL 許容に修正。フェーズ10で SKU 側に移行済みだったが本番 MySQL の旧 NOT NULL 制約が残っており、Console UI 経由の商品登録が 500 になる不具合を解消（[038_products_price_stock_not_null_drift.md](../troubles/038_products_price_stock_not_null_drift.md)） |
| フェーズ16 Step1 | Market 露出 ON/OFF を手動で切り替えるための `is_active`（BOOLEAN NOT NULL DEFAULT TRUE）を追加。`status_code`（販売段階）とは直交した軸として扱い、`PreorderStatusService.isPublished()` の判定に AND 条件で組み込む。設計書 [phase16_ui_ux_improvement.md](../design/phase11_20/phase16_ui_ux_improvement.md) §Step 1 |

## マイグレーションファイル

- JPA `@Entity` により自動生成（`spring.jpa.hibernate.ddl-auto`）
- `version` カラムは `amazia-core/src/main/resources/schema.sql` の ALTER TABLE で追加（既存環境向けフォールバック）
- フェーズ14.5 の 4 カラムも `schema.sql` の ALTER TABLE で追加（同上）
- フェーズ14.5 P2: `schema.sql` 末尾で `ALTER TABLE products MODIFY COLUMN price/stock INT NULL` を冪等に実行
- フェーズ16 Step1: `schema.sql` 末尾で `ALTER TABLE products ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE` を冪等に実行（continue-on-error で重複は無視）
