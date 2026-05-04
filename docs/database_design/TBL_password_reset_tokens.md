# テーブル定義書：password_reset_tokens

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | password_reset_tokens |
| 論理名 | パスワードリセットトークン |
| 所属システム | Console |
| 説明 | パスワードリセット用の一時トークンを管理する |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | email | メールアドレス | VARCHAR | 255 | NOT NULL | - | PK |
| 2 | token | トークン | VARCHAR | 255 | NOT NULL | - | ハッシュ化済み |
| 3 | created_at | 作成日時 | TIMESTAMP | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | email |

## マイグレーションファイル

`database/migrations/0001_01_01_000000_create_users_table.php`
