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
| 6 | version | 楽観ロックバージョン | BIGINT | - | NOT NULL | 0 | フェーズ12追加。JPA `@Version` 用 |
| 7 | is_active | 有効フラグ | BOOLEAN | - | NOT NULL | TRUE | フェーズ17追加。価格スケジュール反映時の旧価格無効化等に利用 |
| 8 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |
| 9 | updated_at | 更新日時 | DATETIME | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_sku_prices_sku_id | INDEX | sku_id |
| idx_product_sku_prices_active | INDEX | (sku_id, is_active) |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_skus | N:1 | 紐づくSKU |
| product_sku_price_history | - | 過去・未来の価格履歴 |
| product_sku_scheduled_prices | - | 予約変更（apply_date 到来時に ApplyScheduledPricesJob が反映） |

## 設計上の注意

- **履歴は物理削除しない**（フェーズ17 r7）。価格スケジュール反映時、既存 `is_active = TRUE` 行は `is_active = FALSE` ＋ `end_date = 適用日 - 1日` に降格させ、新行を `is_active = TRUE` で INSERT する。過去の値段で売れた注文の参照整合を担保するため、たとえ短期間しか有効でなかった行でも DELETE しない。
- 同 `sku_id` で `is_active = TRUE` の行は常に 1 件（`ApplyScheduledPriceService` のトランザクションで担保）。部分 UNIQUE が MySQL でサポートされないため、DB 制約ではなくサービス層契約で担保する。

## 変更履歴

| フェーズ | 内容 |
|---------|------|
| フェーズ12 | `version`（BIGINT NOT NULL DEFAULT 0）を追加。価格更新ワークフローの競合検知に利用 |
| フェーズ17 | `is_active`（BOOLEAN NOT NULL DEFAULT TRUE）を追加。`(sku_id, is_active)` の複合インデックスを追加 |

## マイグレーションファイル

- JPA `@Entity` により自動生成
- `version` カラムは `amazia-core/src/main/resources/schema.sql` の ALTER TABLE で追加（既存環境向けフォールバック）
- `is_active` カラムは `amazia-core/src/main/resources/schema.sql` の ALTER TABLE で追加（フェーズ17 Step 1-6）
