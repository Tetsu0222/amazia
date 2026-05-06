# テーブル定義書：market_customer_password_histories

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | market_customer_password_histories |
| 論理名 | Market 顧客パスワード履歴 |
| 所属システム | Core |
| 説明 | Market 顧客のパスワード変更履歴を保持する。直近 N 件と新規パスワードを照合し、過去パスワード再利用を防止する |
| 追加フェーズ | フェーズ13 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 履歴ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | customer_id | 顧客ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: market_customers.id |
| 3 | password_hash | パスワードハッシュ | VARCHAR | 255 | NOT NULL | - | bcrypt（変更前のハッシュを保持） |
| 4 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | パスワード変更時刻 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_mcph_customer | INDEX | customer_id |

## 外部キー

| FK名 | カラム | 参照先 | ON DELETE |
|------|--------|--------|-----------|
| fk_mcph_customer | customer_id | market_customers(id) | CASCADE |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| market_customers | N:1 | パスワード履歴の所有者 |

## 設計上の注意

- パスワード変更時は変更前のハッシュをこのテーブルに INSERT し、`market_customers.password_hash` を新しい値で UPDATE する。
- 再利用検証では、新しい平文を直近 N 件のハッシュと bcrypt 比較する（N の値はアプリ側設定）。
- 顧客削除時は ON DELETE CASCADE で履歴も一括削除される。
- Console 側 `users` の `password_histories` とは独立したテーブル（系統が違う）。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ13 / V5 相当）
- `amazia-core/src/main/resources/db/migration/V5__phase13_market_auth_tables.sql`（名残ファイル：本番では実行されない）
