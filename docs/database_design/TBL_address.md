# テーブル定義書：address

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | address |
| 論理名 | 配送先住所スナップショット |
| 所属システム | Core |
| 説明 | 注文時点での配送先住所を非正規化保持する。Market 顧客のプロフィール住所が後から変わっても、過去注文の配送先は不変であることを保証する |
| 追加フェーズ | フェーズ14 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 住所ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | 顧客ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: market_customers.id |
| 3 | postal_code | 郵便番号 | VARCHAR | 20 | NULL | NULL | ハイフン有無は呼び出し側に委ねる |
| 4 | prefecture | 都道府県 | VARCHAR | 50 | NULL | NULL | |
| 5 | city | 市区町村 | VARCHAR | 100 | NULL | NULL | |
| 6 | address_line | 番地以下 | VARCHAR | 255 | NOT NULL | - | 必須カラム |
| 7 | building | 建物名・部屋番号 | VARCHAR | 255 | NULL | NULL | |
| 8 | is_active | 有効フラグ | BOOLEAN | - | NOT NULL | TRUE | 論理削除用。FALSE は新規注文では選択不可 |
| 9 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_address_user_id | INDEX | user_id |
| idx_address_user_active | INDEX | (user_id, is_active) |

## 外部キー

| FK名 | カラム | 参照先 |
|------|--------|--------|
| fk_address_user | user_id | market_customers(id) |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| market_customers | N:1 | 住所所有者（Market 顧客） |
| sales | 1:N | 配送先として参照される売上レコード |
| deliveries | 1:N | 配送実体（phase15 r5：`deliveries.shipping_address_id` から参照） |
| shipping_lead_times | 参照のみ（厳密一致） | phaseX-5：`address.prefecture` を `shipping_lead_times.prefecture` と厳密一致でリードタイム引当（不一致時は config フォールバック） |

## 設計上の注意

- 注文ごとに新規レコードを作成し、過去レコードは `is_active=FALSE` にしても物理削除はしない（過去注文の配送先参照を維持するため）。
- `postal_code` / `prefecture` / `city` が NULL を許容するのは、海外住所など将来拡張を見据えた仕様。`address_line` のみ必須。
- `user_id` は `market_customers.id` を参照する点に注意（Console `users.id` ではない）。FK 名は `fk_address_user` だが「user」は Market 顧客を意味する。
- `postal_addresses`（郵便番号→住所マスタ）とは別物。`postal_addresses` は KEN_ALL の参照マスタで、`address` は注文時のスナップショット。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ14 / V6 相当）
- `amazia-core/src/main/resources/db/migration/V6_*.sql`（名残ファイル：本番では実行されない）
