# テーブル定義書：password_histories

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | password_histories |
| 論理名 | パスワード履歴（Console 社員） |
| 所属システム | Core |
| 説明 | Console 社員 `users` のパスワード変更履歴を保持する。直近 N 件と新規パスワードを照合し、過去パスワードの再利用を防止する |
| 追加フェーズ | フェーズ11 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 履歴ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | ユーザーID | BIGINT UNSIGNED | - | NOT NULL | - | FK: users.id（044・045 同型の signed/unsigned ドリフト回避のため UNSIGNED） |
| 3 | password_hash | パスワードハッシュ | VARCHAR | 255 | NOT NULL | - | bcrypt（変更前のハッシュを保持） |
| 4 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | パスワード変更時刻 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_password_histories_user | INDEX | user_id |

## 外部キー

| FK名 | カラム | 参照先 |
|------|--------|--------|
| fk_password_histories_user | user_id | users(id) |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| users | N:1 | パスワード履歴の所有者（Console 社員） |

## 設計上の注意

- パスワード変更時は変更前のハッシュをこのテーブルに INSERT し、`users.password_hash` を新しい値で UPDATE する。
- 再利用検証では、新しい平文を直近 N 件のハッシュと bcrypt 比較する（N の値はアプリ側設定）。
- Market 顧客側 `market_customer_password_histories` とは独立したテーブル（系統が違う）。
- 本テーブルは長らく schema.sql に未記載で、本番ではテーブルが存在しないまま運用されていた（呼ばれた瞬間 1146）。phaseX-6 の主要テーブル存在確認ヘルスチェックで初めて検知され、2026-05-07 に schema.sql へ追記して解消（[048 派生節](../troubles/048_cd_healthcheck_sql_quote_break_inside_sh_c.md) 経由で初到達したヘルスチェックの実成果）。
- FK 列 `user_id` は `users.id` の `BIGINT UNSIGNED` に合わせる（044・045 同型のドリフト回避）。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql` — `password_histories` の CREATE TABLE IF NOT EXISTS（2026-05-07 追記）
- `amazia-core/src/main/resources/db/migration/V1__create_auth_tables.sql`（フェーズ11 当時 / Flyway 適用済み環境にのみ存在。本番は Flyway 未使用 — 037 参照）
