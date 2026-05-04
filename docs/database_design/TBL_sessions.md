# テーブル定義書：sessions

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | sessions |
| 論理名 | セッション |
| 所属システム | Console |
| 説明 | ユーザーのセッション情報を管理する（Laravelセッションドライバー用） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | セッションID | VARCHAR | 255 | NOT NULL | - | PK |
| 2 | user_id | ユーザーID | BIGINT UNSIGNED | - | NULL | NULL | FK: users.id（未制約・INDEX） |
| 3 | ip_address | IPアドレス | VARCHAR | 45 | NULL | NULL | IPv6対応 |
| 4 | user_agent | ユーザーエージェント | TEXT | - | NULL | NULL | |
| 5 | payload | ペイロード | LONGTEXT | - | NOT NULL | - | セッションデータ（Base64） |
| 6 | last_activity | 最終アクティビティ | INT | - | NOT NULL | - | UNIXタイムスタンプ・INDEX |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| sessions_user_id_index | INDEX | user_id |
| sessions_last_activity_index | INDEX | last_activity |

## マイグレーションファイル

`database/migrations/0001_01_01_000000_create_users_table.php`
