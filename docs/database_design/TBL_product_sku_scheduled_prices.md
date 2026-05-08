# テーブル定義書：product_sku_scheduled_prices

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_sku_scheduled_prices |
| 論理名 | SKU 価格変更予約 |
| 所属システム | Core |
| 説明 | SKU 価格の予約変更。`apply_date` が到来した行を `ApplyScheduledPricesJob`（日次）が `product_sku_prices` に反映し、`is_pending=FALSE / applied_at=NOW` に更新する |
| 追加フェーズ | フェーズ17（r8 / 3.1 ⑥ / 13.5） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 予約ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id |
| 3 | scheduled_price | 予約価格 | INT | - | NOT NULL | - | 円（税抜）／非負 |
| 4 | apply_date | 適用日 | DATE | - | NOT NULL | - | この日の `ApplyScheduledPricesJob` で反映 |
| 5 | is_pending | 未適用フラグ | BOOLEAN | - | NOT NULL | TRUE | 適用済みは FALSE |
| 6 | applied_at | 適用日時 | DATETIME | - | NULL | NULL | 反映時に NOW を set |
| 7 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |
| 8 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_pssp_apply_pending | INDEX | (apply_date, is_pending) |
| idx_pssp_sku_pending | INDEX | (sku_id, is_pending) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| fk_pssp_sku | FK | `sku_id` → `product_skus.id` |
| chk_pssp_price_nonneg | CHECK | `scheduled_price >= 0` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_skus | N:1 | 対象 SKU |
| product_sku_prices | （適用先） | `apply_date` 到来時に `ApplyScheduledPricesJob` で反映 |

## 設計上の注意

- 「1 SKU につき未適用予約は 1 件まで」の擬似ユニーク：MySQL は部分 UNIQUE（`WHERE is_pending=TRUE`）を直接サポートしないため、アプリ側 `RegisterScheduledSkuPriceService` で UPSERT として実装する（規約 1-1）。
- `ApplyScheduledPricesJob` のロジック：`WHERE apply_date <= CURRENT_DATE AND is_pending = TRUE` を読み、`product_sku_prices` を上書き → 履歴を `product_sku_price_history` に転記 → 当該行の `is_pending=FALSE / applied_at=NOW` で締める（同一トランザクション）。
- 削除は論理削除ではなく物理 DELETE（未適用のみ）。適用済み行は監査用に残す。
- **H2 互換**：JPA `@Table` のメタデータには CHECK 制約を表現する標準アノテーションが無いため、Entity 側に `@org.hibernate.annotations.Check(constraints = "scheduled_price >= 0")` を付与し、ddl-auto=create-drop で H2 にも CHECK が再現されるようにしている（Step 1.5 / 2026-05-08）。MySQL 用 schema.sql 側の `chk_pssp_price_nonneg` と論理同等。

## Entity

`com.example.scheduledprice.entity.ProductSkuScheduledPrice`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-6）
