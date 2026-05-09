# テーブル定義書：notice_reads

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | notice_reads |
| 論理名 | お知らせ既読履歴 |
| 所属システム | Core |
| 説明 | フェーズ19 お知らせ機能の既読管理。Market 会員がお知らせを開いたタイミングで 1 行 INSERT する。同一 (notice_id, market_customer_id) は UNIQUE 制約で物理担保し、重複登録は冪等扱い |
| 追加フェーズ | フェーズ19（r2） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 既読履歴ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | notice_id | お知らせID | BIGINT | - | NOT NULL | - | FK: notices.id |
| 3 | market_customer_id | 顧客ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: market_customers.id（045 / 044 同型対策：market_customers.id が BIGINT UNSIGNED のため一致） |
| 4 | read_at | 既読日時 | DATETIME | - | NOT NULL | - | `@PrePersist` で設定。冪等のため 2 回目以降は更新しない |
| 5 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | `@PrePersist` で設定 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_notice_reads_notice_customer | UNIQUE | (notice_id, market_customer_id) |
| idx_notice_reads_market_customer_id | INDEX | (market_customer_id) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| uk_notice_reads_notice_customer | UNIQUE | 同一会員が同じお知らせを既読登録するのは 1 回だけ |
| fk_notice_reads_notice | FK | `notice_id` → `notices.id` |
| fk_notice_reads_customer | FK | `market_customer_id` → `market_customers.id` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| notices | N:1 | 既読対象のお知らせ |
| market_customers | N:1 | 既読登録した会員 |

## 設計上の注意

- **冪等性の確保**：H2 / MySQL 互換のため `ON DUPLICATE KEY UPDATE` は使わず、Service 層 `MarkAsReadService` で「`exists` 確認 → false なら INSERT」方式を採用（計画書 §2-3-7）。本番 MySQL でも UNIQUE 違反は発生しない設計。
- **削除済お知らせとの関係**：お知らせを論理削除した場合も `notice_reads` は維持する（CASCADE DELETE 不採用）。これは「過去に読んだ既読履歴」を将来要件で参照可能にしておくため。
- **本テーブルへの書き込み権限**：Market 会員セッション認証 + CSRF（`MarketCsrfFilter` の `/api/customer/` 配下保護）を経た POST `/api/customer/notices/{id}/read` のみ。Console 社員の既読履歴はスコープ外（設計書 §スコープ外）。

## Entity

`com.example.notice.entity.NoticeRead`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ19 Step A-3）
