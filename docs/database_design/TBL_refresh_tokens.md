# テーブル定義書：refresh_tokens

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | refresh_tokens |
| 論理名 | リフレッシュトークン |
| 所属システム | Core |
| 説明 | JWT認証のリフレッシュトークンを管理する。フェーズ11で追加。 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | ユーザーID | BIGINT UNSIGNED | - | NOT NULL | - | FK → users.id |
| 3 | token_hash | トークンハッシュ | VARCHAR | 255 | NOT NULL | - | UNIQUE・SHA-256ハッシュ |
| 4 | expires_at | 有効期限 | DATETIME | - | NOT NULL | - | 発行日時 + 14日 |
| 5 | revoked | 失効フラグ | BOOLEAN | - | NOT NULL | FALSE | true の場合無効 |
| 6 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| refresh_tokens_token_hash_unique | UNIQUE | token_hash |
| refresh_tokens_user_id_fk | INDEX | user_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| users | N:1 | トークンの発行対象ユーザー |

## 備考

- リフレッシュトークンの実体は HttpOnly Cookie に保存。DBにはハッシュ値のみ格納
- トークンローテーション採用：再発行時に旧トークンの `revoked = true` を設定
- `revoked = true` または `expires_at` が過去のトークンは無効扱い
- リプレイ攻撃対策：失効済みトークンを再利用した場合は 401 を返す

## マイグレーションファイル

`src/main/resources/db/migration/V1__create_auth_tables.sql`（amazia-core）
