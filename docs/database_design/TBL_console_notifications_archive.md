# テーブル定義書：console_notifications_archive

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | console_notifications_archive |
| 論理名 | 通知センター アーカイブ |
| 所属システム | Core |
| 説明 | `ConsoleNotificationsArchiveJob`（フェーズ17 Step 4-5 / J-2）が `console_notifications` から 1 年超のレコードを移送する保管先。元 `id` を保持し、INSERT → DELETE を 1 トランザクションで実施する |
| 追加フェーズ | フェーズ17 Step 4-5 |

## アーカイブ条件（`ConsoleNotificationsArchiveJob` 内で適用）

以下のいずれかを満たすレコードを対象：

1. `read_at IS NOT NULL` かつ `read_at < NOW() - 1 YEAR`（既読から 1 年経過）
2. `suppressed = TRUE` かつ `digest_sent_at IS NOT NULL` かつ `digest_sent_at < NOW() - 1 YEAR`
3. `created_at < NOW() - 1 YEAR`（無条件 1 年経過。抑制中で `digest_sent_at IS NULL` もここで救済）

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 通知ID | BIGINT | - | NOT NULL | - | PK。元 `console_notifications.id` を引き継ぐ（IDENTITY ではない） |
| 2 | level | 重要度 | VARCHAR | 10 | NOT NULL | - | INFO / WARN / ERROR |
| 3 | target_subscription_tag | 購読タグ | VARCHAR | 50 | NOT NULL | - | inventory_alerts / sales_alerts 等 |
| 4 | target_user_id | 個別宛 | BIGINT | - | NULL | NULL | |
| 5 | title | 件名 | VARCHAR | 200 | NOT NULL | - | |
| 6 | body | 本文 | TEXT | - | NOT NULL | - | |
| 7 | payload_hash | 重複抑制キー | VARCHAR | 64 | NOT NULL | - | SHA-256 |
| 8 | suppressed | 抑制フラグ | BOOLEAN | - | NOT NULL | - | |
| 9 | digest_sent_at | ダイジェスト送出時刻 | DATETIME | - | NULL | NULL | |
| 10 | read_by_user_id | 既読ユーザ | BIGINT | - | NULL | NULL | |
| 11 | read_at | 既読日時 | DATETIME | - | NULL | NULL | |
| 12 | source_job | 発信元ジョブ | VARCHAR | 100 | NULL | NULL | |
| 13 | source_batch_execution_id | 紐付け batch_executions.id | BIGINT | - | NULL | NULL | |
| 14 | created_at | 元作成日時 | DATETIME | - | NOT NULL | - | |
| 15 | archived_at | アーカイブ実行日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_cna_tag_created | INDEX | (target_subscription_tag, created_at) |

## 外部キー

なし（運用上の取り回しを優先し、FK は張らない）。

## 設計上の注意

- t3.micro のディスク圧迫を抑えるため、インデックスは PK ＋ `(target_subscription_tag, created_at)` の 1 本のみ。
- 件数のみ INFO ログで残す運用（SES 通知はしない／設計書 §3.3 ③）。
- 本テーブルへの書き込みはバッチ専用。アプリケーションコードからの直接 INSERT は想定しない。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-8）
