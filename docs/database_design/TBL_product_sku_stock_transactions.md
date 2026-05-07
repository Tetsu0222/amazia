# テーブル定義書：product_sku_stock_transactions

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | product_sku_stock_transactions |
| 論理名 | SKU在庫履歴 |
| 所属システム | Core |
| 説明 | SKU の入荷・調整・出荷等の在庫変動履歴を保持する。quantity は増減値（入荷は正、調整は正負どちらもあり得る） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 履歴ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sku_id | SKU ID | BIGINT | - | NOT NULL | - | FK: product_skus.id |
| 3 | type | 取引種別 | VARCHAR | 20 | NOT NULL | - | `receive` / `adjust` / `sale` / `return` / `cancel`（phase14 r4）/ `sale_preorder_shipment`（phase15 r5：予約購入の PENDING→SHIPPED 遷移時の減算） |
| 4 | quantity | 増減数 | INT | - | NOT NULL | - | 入荷・返品は正値、販売・出荷時減算は負値、調整は正負どちらもあり得る |
| 5 | reference_type | 参照対象種別 | VARCHAR | 50 | NULL | NULL | フェーズ11追加。sales / workflow 等の参照元テーブル種別 |
| 6 | reference_id | 参照対象ID | BIGINT | - | NULL | NULL | フェーズ11追加。reference_type に対応する PK |
| 7 | created_by_user_id | 操作者ID | BIGINT | - | NULL | NULL | フェーズ11追加。users.id（誰が在庫操作したか） |
| 8 | comment | コメント | TEXT | - | NULL | NULL | フェーズ11追加。補足説明 |
| 9 | created_at | 作成日時 | DATETIME | - | NULL | NULL | |

## 取引種別定義

| 値 | 意味 |
|----|------|
| receive | 入荷（加算のみ） |
| adjust | 在庫調整（加減算） |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_sku_stock_transactions_sku_id | INDEX | sku_id |
| idx_sku_stock_tx_reference | INDEX | (reference_type, reference_id) — フェーズ11追加 |
| idx_sku_stock_tx_created_by | INDEX | created_by_user_id — フェーズ11追加 |
| idx_sku_stock_tx_created_at | INDEX | created_at — フェーズ11追加 |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| product_skus | N:1 | 紐づくSKU |
| users | N:1 | 操作者（`created_by_user_id`）。FK 制約は未付与 |

## 変更履歴

| フェーズ | 内容 |
|---------|------|
| フェーズ11 | `reference_type` / `reference_id` / `created_by_user_id` / `comment` の4カラムと、対応する3つのインデックスを追加。在庫変動の出所追跡（売上・ワークフロー等）と監査性を強化 |
| フェーズ14 r4 | `type` 値に `sale` / `return` / `cancel` を追加（注文確定時の販売減算・返品復元・キャンセル復元の記録用） |
| フェーズ15 r5 | `type` 値に `sale_preorder_shipment` を追加（予約購入の PENDING→SHIPPED 遷移時の在庫減算記録用 / P5-3） |

## 設計上の注意

- `reference_type` / `reference_id` の組み合わせで在庫変動の出所を特定する。たとえば売上による出庫は `reference_type='sales'` / `reference_id=<sales.id>` を入れる運用。
- `created_by_user_id` は Console 社員 `users.id` を想定。Market 顧客の購入経由の出庫では NULL となる場合がある。
- 既存テーブルへの追加カラムは schema.sql の ALTER TABLE で投入。重複実行は `spring.sql.init.continue-on-error=true` で許容している。

## マイグレーションファイル

- JPA `@Entity` により自動生成（基本カラム）
- フェーズ11 追加カラム/インデックスは `amazia-core/src/main/resources/schema.sql` の ALTER TABLE / CREATE INDEX で追加
