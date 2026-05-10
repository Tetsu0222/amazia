# テーブル定義書：shipping_methods

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | shipping_methods |
| 論理名 | 配送方法マスタ |
| 所属システム | Core |
| 説明 | 注文時に選択可能な配送方法を保持するマスタ。schema.sql で初期データを INSERT IGNORE 投入する |
| 追加フェーズ | フェーズ15（r5 / P5-1） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 配送方法ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | name | コード名 | VARCHAR | 50 | NOT NULL | - | UNIQUE。`home_delivery` / `konbini_pickup` / `dropoff` |
| 3 | description | 表示名 | VARCHAR | 255 | NULL | NULL | UI 表示用の日本語名 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| name | UNIQUE | name |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| sales | 1:N | 注文の配送方法として参照される（FK は phase15 r5 で有効化） |
| deliveries | 1:N | 配送実体の配送方法として参照される |

## 初期データ

schema.sql で `INSERT IGNORE` により以下を投入する。

| id | name | description |
|----|------|-------------|
| 1 | home_delivery | 宅配 |
| 2 | konbini_pickup | コンビニ受取 |
| 3 | dropoff | 置き配 |

## 設計上の注意

- レコードは Console 画面からの登録対象ではなく、schema.sql 起動時のシード投入のみで管理する。
- ID 値は `application.properties > amazia.delivery.shipping-methods.*-id` および Console `config('delivery.shipping_methods.*_id')` と整合させる（規約 4-1 / RRRR-8）。
- `sales.shipping_method_id` への FK 制約は phase14 r1 時点で保留され、phase15 r5 のマイグレーションで有効化される（schema.sql 末尾 `ALTER TABLE sales ADD CONSTRAINT fk_sales_shipping_method ...`）。
- 配送方法別のリードタイム（日数）は `application.properties > amazia.delivery.lead-time-days.*` で管理する（フォールバック値）。都道府県別リードタイムは phaseX-5 で `shipping_lead_times` マスタとして実装済（[TBL_shipping_lead_times.md](TBL_shipping_lead_times.md)）。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ15 / r5）
