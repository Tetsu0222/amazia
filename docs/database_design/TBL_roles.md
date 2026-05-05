# テーブル定義書：roles

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | roles |
| 論理名 | ロール |
| 所属システム | Core |
| 説明 | Consoleユーザーのロール（admin / user）を管理する。フェーズ11で追加。 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | code | ロールコード | VARCHAR | 50 | NOT NULL | - | UNIQUE（例：admin / user） |
| 3 | name | ロール名 | VARCHAR | 100 | NOT NULL | - | 表示名（例：管理者 / 一般） |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| roles_code_unique | UNIQUE | code |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| users | 1:N | このロールを持つユーザー |
| role_permissions | 1:N | このロールに付与されたパーミッション |

## 初期データ

| code | name |
|------|------|
| admin | 管理者 |
| user | 一般 |

## マイグレーションファイル

`src/main/resources/db/migration/V1__create_auth_tables.sql`（amazia-core）
`src/main/resources/db/migration/V2__insert_initial_data.sql`（初期データ）
