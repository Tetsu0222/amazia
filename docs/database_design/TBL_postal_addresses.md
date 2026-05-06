# テーブル定義書：postal_addresses

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | postal_addresses |
| 論理名 | 郵便番号→住所マスタ |
| 所属システム | Core |
| 説明 | 日本郵便 KEN_ALL CSV を取り込んだ住所マスタ。Market 会員登録・配送先入力時の住所自動入力（郵便番号→都道府県/市区町村/町域）に利用する |
| 追加フェーズ | フェーズ13 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 住所ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | postal_code | 郵便番号 | VARCHAR | 8 | NOT NULL | - | ハイフンなし7桁を想定（UNIQUE ではない） |
| 3 | prefecture | 都道府県 | VARCHAR | 20 | NOT NULL | - | |
| 4 | city | 市区町村 | VARCHAR | 100 | NOT NULL | - | |
| 5 | town | 町域 | VARCHAR | 200 | NOT NULL | - | 同一郵便番号で複数町域があるため複数行になりうる |
| 6 | updated_at | 更新日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | KEN_ALL 取込バッチ実行時に更新される |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_pa_postal_code | INDEX | postal_code |
| idx_pa_pref_city | INDEX | (prefecture, city) |

## 関連テーブル

参照先・参照元の FK は持たない参照マスタ。`market_customers.postal_code` や `address.postal_code` とは値ベースで紐づくのみで、テーブル間 FK は張らない。

## 設計上の注意

- `postal_code` は UNIQUE ではない。1郵便番号に複数町域が紐づく（例：「東京都千代田区永田町」と「東京都千代田区永田町(複数番地)」）ケースがあるため、検索結果は配列で返す前提。
- データ取込は `KenAllImportRunner` 系（`ApplicationRunner` で起動する単発バッチ）で実施する。Spring Boot のライフサイクル制約により、起動時はコンテナがハングしないよう `docker compose run -d` でデタッチ起動する運用（`operational_insights.md` カテゴリ1 参照）。
- 取込時は全件 TRUNCATE → INSERT、または UPSERT で更新する設計。`updated_at` で最終取込時刻が分かる。
- `address`（注文時スナップショット）とは別物。`postal_addresses` は KEN_ALL の参照マスタで、`address` は注文ごとに INSERT される。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ13 / V5 相当）
- `amazia-core/src/main/resources/db/migration/V5__phase13_market_auth_tables.sql`（名残ファイル：本番では実行されない）
