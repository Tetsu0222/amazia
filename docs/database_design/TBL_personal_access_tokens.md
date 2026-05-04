# テーブル定義書：personal_access_tokens

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | personal_access_tokens |
| 論理名 | パーソナルアクセストークン |
| 所属システム | Console |
| 説明 | Laravel Sanctum によるAPIトークン認証情報を管理する |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | tokenable_type | トークン対象タイプ | VARCHAR | 255 | NOT NULL | - | Polymorphic: モデルクラス名 |
| 3 | tokenable_id | トークン対象ID | BIGINT UNSIGNED | - | NOT NULL | - | Polymorphic: モデルID |
| 4 | name | トークン名 | TEXT | - | NOT NULL | - | |
| 5 | token | トークン | VARCHAR | 64 | NOT NULL | - | SHA-256ハッシュ・UNIQUE |
| 6 | abilities | 権限 | TEXT | - | NULL | NULL | JSON形式 |
| 7 | last_used_at | 最終使用日時 | TIMESTAMP | - | NULL | NULL | |
| 8 | expires_at | 有効期限 | TIMESTAMP | - | NULL | NULL | INDEX |
| 9 | created_at | 作成日時 | TIMESTAMP | - | NULL | NULL | |
| 10 | updated_at | 更新日時 | TIMESTAMP | - | NULL | NULL | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| personal_access_tokens_token_unique | UNIQUE | token |
| personal_access_tokens_tokenable_type_tokenable_id_index | INDEX | tokenable_type, tokenable_id |
| personal_access_tokens_expires_at_index | INDEX | expires_at |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| users | N:1 | tokenable（Polymorphic）経由でユーザーに紐づく |

## マイグレーションファイル

`database/migrations/2026_05_02_073147_create_personal_access_tokens_table.php`
