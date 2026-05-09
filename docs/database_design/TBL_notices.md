# テーブル定義書：notices

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | notices |
| 論理名 | お知らせ本体 |
| 所属システム | Core |
| 説明 | フェーズ19 お知らせ機能の本体。Console 社員が作成し、公開期間内のものを Market 会員に閲覧させる。`deleted_at` での論理削除方式。投稿者は Console のみ表示し Market には漏らさない（R19-11） |
| 追加フェーズ | フェーズ19（r2） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | お知らせID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | subject | 件名 | VARCHAR | 255 | NOT NULL | - | 上限は `amazia.notice.subject.max-length` で config 化 |
| 3 | category_id | 分類ID | BIGINT | - | NOT NULL | - | FK: notice_categories.id |
| 4 | body | 本文 | TEXT | - | NOT NULL | - | プレーンテキスト保存。Market 表示時 React の自動エスケープに依存（XSS 対策） |
| 5 | author_id | 投稿者ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: users.id（045 / 044 同型対策：users.id が BIGINT UNSIGNED のため一致） |
| 6 | publish_start | 公開開始日時 | DATETIME | - | NOT NULL | - | JST 想定。Console FormRequest が `00:00:00` を補完（R19-2 / R19-5） |
| 7 | publish_end | 公開終了日時 | DATETIME | - | NOT NULL | - | JST 想定。Console FormRequest が `23:59:59` を補完 |
| 8 | deleted_at | 削除日時 | DATETIME | - | NULL | NULL | NOT NULL なら論理削除済（YAGNI / `deleted_flag` 廃止） |
| 9 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | `@PrePersist` で設定 |
| 10 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | `@PreUpdate` で更新（H2 互換のため `ON UPDATE CURRENT_TIMESTAMP` は不採用） |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_notices_publish_period | INDEX | (publish_start, publish_end) |
| idx_notices_category_id | INDEX | (category_id) |
| idx_notices_deleted_at | INDEX | (deleted_at) |
| idx_notices_author_id | INDEX | (author_id) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| fk_notices_category | FK | `category_id` → `notice_categories.id` |
| fk_notices_author | FK | `author_id` → `users.id` |
| chk_notices_publish_period | CHECK | `publish_start <= publish_end` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| notice_categories | N:1 | 分類マスタ（重要 / 普通） |
| users | N:1 | 投稿者（Console 社員） |
| notice_reads | 1:N | Market 会員の既読履歴 |

## 設計上の注意

- **公開期間判定**：Service 層で `now() BETWEEN publish_start AND publish_end` を JPQL で評価する。サーバ・DB のタイムゾーン `Asia/Tokyo` 前提（phase14 r3 と同方針／海外展開・サマータイムはスコープ外）。
- **CHECK の二重防御**：本番 MySQL は `chk_notices_publish_period` で物理担保。H2 テストは `ddl-auto=create-drop` で Entity から DDL 自動生成のため CHECK が無く、Service 層 `NoticePeriodValidator` で 422 を返すことで同等の検証を行う（test_insights カテゴリ7-2）。
- **Market 投稿者非表示**：DTO クラスを `NoticeMarketDto`（`author` フィールドなし）と `NoticeConsoleDto`（`author: { id, name }` あり）で分離してコンパイル時保証（R19-11）。
- **論理削除の挙動**：削除時は `deleted_at = NOW()` のみ更新。関連 `notice_reads` は CASCADE DELETE せず参照履歴として残す（既読バッジの履歴維持）。

## Entity

`com.example.notice.entity.Notice`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ19 Step A-2）
