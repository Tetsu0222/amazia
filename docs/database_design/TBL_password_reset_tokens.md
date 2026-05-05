# テーブル定義書：password_reset_tokens

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | password_reset_tokens |
| 論理名 | パスワードリセットトークン |
| 所属システム | Core |
| 説明 | パスワード再発行フロー用の一時トークンを管理する。フェーズ11でCoreに移管・仕様刷新。 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | ユーザーID | BIGINT UNSIGNED | - | NOT NULL | - | FK → users.id |
| 3 | token_hash | トークンハッシュ | VARCHAR | 255 | NOT NULL | - | UNIQUE・SHA-256ハッシュ（64文字のランダムトークンをハッシュ化） |
| 4 | expires_at | 有効期限 | DATETIME | - | NOT NULL | - | 発行日時 + 30分 |
| 5 | used | 使用済みフラグ | BOOLEAN | - | NOT NULL | FALSE | 1回限り有効。使用後 true に更新 |
| 6 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| password_reset_tokens_token_hash_unique | UNIQUE | token_hash |
| password_reset_tokens_user_id_fk | INDEX | user_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| users | N:1 | パスワード再設定対象ユーザー |

## 備考

- トークンの実体は **DBに保存しない**。URLに含まれるランダム64文字をSHA-256でハッシュ化してDBに格納
- 未登録メールアドレスへのリクエストにも 200 を返す（列挙攻撃対策）。その場合はDBにレコードを作成しない
- `used = true` または `expires_at` が過去のトークンは無効扱い → 400 を返す
- 過去3世代のパスワードは再利用不可（`users` テーブルの履歴管理は別途検討）

## マイグレーションファイル

`src/main/resources/db/migration/V1__create_auth_tables.sql`（amazia-core）
