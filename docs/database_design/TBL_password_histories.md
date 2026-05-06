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
| 1 | id | 履歴ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | ユーザーID | BIGINT | - | NOT NULL | - | FK: users.id |
| 3 | password_hash | パスワードハッシュ | VARCHAR | 255 | NOT NULL | - | bcrypt（変更前のハッシュを保持） |
| 4 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | パスワード変更時刻 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |

## 外部キー

| FK名 | カラム | 参照先 |
|------|--------|--------|
| _匿名_ | user_id | users(id) |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| users | N:1 | パスワード履歴の所有者（Console 社員） |

## 設計上の注意

- パスワード変更時は変更前のハッシュをこのテーブルに INSERT し、`users.password_hash` を新しい値で UPDATE する。
- 再利用検証では、新しい平文を直近 N 件のハッシュと bcrypt 比較する（N の値はアプリ側設定）。
- Market 顧客側 `market_customer_password_histories` とは独立したテーブル（系統が違う）。
- このテーブルは Flyway V1（`db/migration/V1__create_auth_tables.sql`）で定義されているが、本番 schema.sql には CREATE TABLE が含まれていない。Entity 定義（`com.example.auth.entity.PasswordHistory`）から JPA が自動生成する経路、または既存環境に残った V1 適用済みテーブルを利用する形となっている。新規環境構築時は schema.sql への追加検討が必要（037 起因の DB 初期化方式に注意）。

## マイグレーションファイル

- `amazia-core/src/main/resources/db/migration/V1__create_auth_tables.sql`（フェーズ11 / Flyway 適用済み環境のみ）
- 本番 schema.sql には未記載（要検討事項）
