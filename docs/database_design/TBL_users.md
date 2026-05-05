# テーブル定義書：users

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | users |
| 論理名 | ユーザー |
| 所属システム | Core |
| 説明 | Consoleシステムの社員アカウント情報を管理する。フェーズ11でJWT認証・ロール・ロックアウト対応に刷新。 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | employee_id | 社員ID | VARCHAR | 50 | NOT NULL | - | UNIQUE・内部管理用 |
| 3 | email | メールアドレス | VARCHAR | 255 | NOT NULL | - | UNIQUE・ログインID |
| 4 | name | 氏名 | VARCHAR | 50 | NOT NULL | - | |
| 5 | password_hash | パスワードハッシュ | VARCHAR | 255 | NOT NULL | - | BCryptハッシュ |
| 6 | role_id | ロールID | BIGINT UNSIGNED | - | NOT NULL | - | FK → roles.id |
| 7 | active_flag | 有効フラグ | BOOLEAN | - | NOT NULL | TRUE | false の場合ログイン不可 |
| 8 | failed_attempts | 連続失敗回数 | INT | - | NOT NULL | 0 | 5回でロックアウト |
| 9 | locked_until | ロック解除日時 | DATETIME | - | NULL | NULL | ロックアウト解除時刻 |
| 10 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |
| 11 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| users_employee_id_unique | UNIQUE | employee_id |
| users_email_unique | UNIQUE | email |
| users_role_id_fk | INDEX | role_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| roles | N:1 | ユーザーのロール |
| refresh_tokens | 1:N | 発行済みリフレッシュトークン |
| password_reset_tokens | 1:N | パスワード再設定トークン |

## 備考

- `failed_attempts` が 5 に達すると `locked_until = NOW() + 15分` を設定しロックアウト
- `locked_until` が過去に変わると自動解除（バッチ不要）
- `active_flag = false` の場合、ロック状態に関わらずログイン不可

## マイグレーションファイル

`src/main/resources/db/migration/V1__create_auth_tables.sql`（amazia-core）
