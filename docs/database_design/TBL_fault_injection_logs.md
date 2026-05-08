# テーブル定義書：fault_injection_logs

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | fault_injection_logs |
| 論理名 | フォルトインジェクション実行履歴 |
| 所属システム | Core |
| 説明 | dev / staging 環境でのみ動作する障害注入機能の実行履歴。本番では DB CHECK 制約で物理的に INSERT が拒否される（五重防御の DB 層） |
| 追加フェーズ | フェーズ17（r8 / 5.3） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 実行ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | injector_name | インジェクタ名 | VARCHAR | 100 | NOT NULL | - | SalesMismatchInjector / InventoryMismatchInjector / DeliveryTroubleInjector |
| 3 | triggered_at | 発火日時 | DATETIME | - | NOT NULL | - | |
| 4 | triggered_by | 発火元 | VARCHAR | 50 | NOT NULL | - | scheduler / manual:user_id=N |
| 5 | environment | 環境 | VARCHAR | 20 | NOT NULL | - | dev / staging のみ許可（CHECK 制約） |
| 6 | target_summary | 対象サマリ | TEXT | - | NULL | NULL | 影響を与えた SKU / sales_id 等の概要 |
| 7 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_fil_injector_created | INDEX | (injector_name, created_at) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| chk_fault_logs_no_prod | CHECK | `environment IN ('dev', 'staging')` ／本番からの INSERT を物理拒否 |

## 関連テーブル

なし（独立ログテーブル）。フォルトインジェクションが書き込んだ補償レコードは `product_sku_stock_transactions`（`type='adjust'`, `reference_type='fault_injection'`）側に残る。

## 設計上の注意

- 五重防御の構成：機能フラグ + 起動時 Validator + `@Profile("!production")` + DB CHECK + SKU TX 補償（H-7）。
- CHECK 制約は H2 / MySQL の双方で通る `CHECK (environment IN (...))` 構文で記述（設計書 §12.1）。
- 補償（インジェクションが行った副作用の戻し）は `InventoryAdjustmentService.adjust(..., referenceType="fault_injection_compensation", ...)` 経由で SKU TX に記録する（H-7）。
- **H2 互換**：JPA `@Table` のメタデータには CHECK 制約を表現する標準アノテーションが無いため、Entity 側に `@org.hibernate.annotations.Check(constraints = "environment IN ('dev', 'staging')")` を付与し、ddl-auto=create-drop で H2 にも CHECK が再現されるようにしている（Step 1.5 / 2026-05-08）。MySQL 用 schema.sql 側の `chk_fault_logs_no_prod` と論理同等。

## Entity

`com.example.faultinjection.entity.FaultInjectionLog`（`@Profile("!production")` のクラス群が参照）

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-4）
