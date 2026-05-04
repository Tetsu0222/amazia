# テーブル定義書：users

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | users |
| 論理名 | ユーザー |
| 所属システム | Console |
| 説明 | Consoleシステムの管理者ユーザー情報を管理する |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | name | ユーザー名 | VARCHAR | 255 | NOT NULL | - | |
| 3 | email | メールアドレス | VARCHAR | 255 | NOT NULL | - | UNIQUE |
| 4 | email_verified_at | メール確認日時 | TIMESTAMP | - | NULL | NULL | |
| 5 | password | パスワード | VARCHAR | 255 | NOT NULL | - | ハッシュ化済み |
| 6 | remember_token | リメンバートークン | VARCHAR | 100 | NULL | NULL | |
| 7 | created_at | 作成日時 | TIMESTAMP | - | NULL | NULL | |
| 8 | updated_at | 更新日時 | TIMESTAMP | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| users_email_unique | UNIQUE | email |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| sessions | 1:N | ユーザーのセッション情報 |
| personal_access_tokens | 1:N | Sanctum APIトークン |

## マイグレーションファイル

`database/migrations/0001_01_01_000000_create_users_table.php`
