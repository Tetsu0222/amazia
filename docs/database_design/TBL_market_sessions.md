# テーブル定義書：market_sessions

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | market_sessions |
| 論理名 | Market セッション |
| 所属システム | Core |
| 説明 | Market 顧客のログインセッションを DB で直管理する。Cookie ベース（`MARKET_SESSION_ID`）で認証し、CSRF トークンも同レコードで保持する |
| 追加フェーズ | フェーズ13 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | session_id | セッションID | VARCHAR | 64 | NOT NULL | - | PK。乱数生成（Cookie 値） |
| 2 | customer_id | 顧客ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: market_customers.id |
| 3 | csrf_token | CSRFトークン | VARCHAR | 64 | NOT NULL | - | レスポンスで返却し、状態変更系リクエストで検証する |
| 4 | expires_at | 有効期限 | DATETIME | - | NOT NULL | - | この時刻を過ぎたセッションは無効 |
| 5 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |
| 6 | last_accessed_at | 最終アクセス時刻 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | アクセスのたびに更新（slidingセッション用） |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | session_id |
| idx_ms_customer | INDEX | customer_id |
| idx_ms_expires | INDEX | expires_at |

## 外部キー

| FK名 | カラム | 参照先 | ON DELETE |
|------|--------|--------|-----------|
| fk_ms_customer | customer_id | market_customers(id) | CASCADE |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| market_customers | N:1 | セッション保有者（顧客） |

## 設計上の注意

- セッション ID は `MarketSessionCookieFactory` が乱数生成し、Cookie として返却する（HttpOnly / Secure / SameSite=Lax 想定）。
- CSRF トークンは `/api/customer/csrf-token` で取得し、状態変更系リクエストではリクエストヘッダで検証する（`MarketSessionAuthFilter`）。
- 期限切れセッションは `idx_ms_expires` を使ったバッチ削除を想定。`expires_at < NOW()` で物理削除する（フェーズ17 バッチ処理で対応予定）。
- 顧客削除時は ON DELETE CASCADE でセッションも一括削除される。
- Console 側のセッションは Laravel の `sessions` テーブルで管理される別系統。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ13 / V5 相当）
- `amazia-core/src/main/resources/db/migration/V5__phase13_market_auth_tables.sql`（名残ファイル：本番では実行されない）
