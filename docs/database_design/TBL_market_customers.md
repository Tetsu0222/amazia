# テーブル定義書：market_customers

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | market_customers |
| 論理名 | Market 顧客マスタ |
| 所属システム | Core |
| 説明 | Amazia Market（フロント）の会員アカウント。Console 社員アカウント `users` とは別系統で同居する |
| 追加フェーズ | フェーズ13 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 顧客ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | name_last | 姓 | VARCHAR | 100 | NOT NULL | - | |
| 3 | name_first | 名 | VARCHAR | 100 | NOT NULL | - | |
| 4 | postal_code | 郵便番号 | VARCHAR | 8 | NOT NULL | - | プロフィール住所の郵便番号 |
| 5 | address | 住所 | VARCHAR | 255 | NOT NULL | - | プロフィール住所（番地以下も含む単一カラム） |
| 6 | birthday | 生年月日 | DATE | - | NOT NULL | - | |
| 7 | email | メールアドレス | VARCHAR | 255 | NOT NULL | - | UNIQUE。ログインID |
| 8 | password_hash | パスワードハッシュ | VARCHAR | 255 | NOT NULL | - | bcrypt |
| 9 | payment_method | 希望決済方法 | VARCHAR | 20 | NOT NULL | - | プロフィール上の希望決済（注文時の確定値は `sales.payment_method_id`） |
| 10 | card_token | カードトークン | VARCHAR | 255 | NULL | NULL | 決済代行のトークン参照 |
| 11 | active_flag | 有効フラグ | BOOLEAN | - | NOT NULL | TRUE | 退会・無効化用 |
| 12 | failed_attempts | 連続ログイン失敗回数 | INT | - | NOT NULL | 0 | ロックアウト判定用 |
| 13 | locked_until | ロックアウト解除時刻 | DATETIME | - | NULL | NULL | NULL ならロック解除済 |
| 14 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |
| 15 | updated_at | 更新日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| email | UNIQUE | email |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| market_sessions | 1:N | ログインセッション |
| market_customer_password_histories | 1:N | パスワード履歴（再利用検証用） |
| market_customers_password_reset_tokens | 1:N | パスワード再発行トークン |
| address | 1:N | 注文時の配送先住所スナップショット |
| sales | 1:N | 購入レコード（`sales.user_id` の参照先） |

## 設計上の注意

- Console 社員 `users` とは完全に別系統のテーブル。Market API は `market_customers.id` を主体として動作する。
- `address`（VARCHAR 単一カラム）は会員プロフィール上の住所。配送先は `address` テーブル（注文時スナップショット）で別管理する。
- `payment_method` はプロフィール上の希望決済（クレカ/d払い/代引きの文字列）。注文確定時は `sales.payment_method_id`（FK→`payment_methods.id`）に確定値を保存する。
- ID 型が BIGINT UNSIGNED であるため、Core オリジナルの BIGINT テーブル（`sales` など）から FK を張る場合は型を合わせる必要がある（029 / 037 起因）。
- ロックアウト機構は `failed_attempts` をインクリメントし、閾値到達時に `locked_until` を設定する設計（Console 側 `users` と同方式）。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ13 / V5 相当）
- `amazia-core/src/main/resources/db/migration/V5__phase13_market_auth_tables.sql`（名残ファイル：本番では実行されない）
