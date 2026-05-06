# テーブル定義書：market_customers_password_reset_tokens

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | market_customers_password_reset_tokens |
| 論理名 | Market 顧客パスワードリセットトークン |
| 所属システム | Core |
| 説明 | Market 顧客の「パスワードをお忘れですか」フロー用の一時トークンを保持する。トークン実体ではなくハッシュ値のみ格納する |
| 追加フェーズ | フェーズ13 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | トークンID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | customer_id | 顧客ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: market_customers.id |
| 3 | token_hash | トークンハッシュ | VARCHAR | 255 | NOT NULL | - | UNIQUE。生トークンは保存しない |
| 4 | expires_at | 有効期限 | DATETIME | - | NOT NULL | - | この時刻を過ぎたトークンは無効 |
| 5 | used | 使用済フラグ | BOOLEAN | - | NOT NULL | FALSE | 使い捨て（1回の再設定で TRUE 化） |
| 6 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| token_hash | UNIQUE | token_hash |
| idx_mcprt_customer | INDEX | customer_id |

## 外部キー

| FK名 | カラム | 参照先 | ON DELETE |
|------|--------|--------|-----------|
| fk_mcprt_customer | customer_id | market_customers(id) | CASCADE |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| market_customers | N:1 | トークンの所有者 |

## 設計上の注意

- 生トークンは DB に保存しない（メールに記載するトークンを SHA256 等でハッシュ化して保存）。Core 側 `password_reset_tokens` と同じ方針。
- 1回限り（`used=TRUE` に遷移後は再利用不可）。期限切れトークンは `expires_at < NOW()` で検出する。
- 顧客削除時は ON DELETE CASCADE でトークンも一括削除される。
- Console 社員向けの `password_reset_tokens` とは独立したテーブル（系統が違う）。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ13 / V5 相当）
- `amazia-core/src/main/resources/db/migration/V5__phase13_market_auth_tables.sql`（名残ファイル：本番では実行されない）
