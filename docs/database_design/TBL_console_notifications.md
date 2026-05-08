# テーブル定義書：console_notifications

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | console_notifications |
| 論理名 | Console 通知センター |
| 所属システム | Core |
| 説明 | フェーズ17 通知センターの正本。バッチ失敗・在庫不整合・配送遅延等の通知をタグ単位／ユーザー単位で蓄積し、Console UI の「通知センター」画面が読み取る |
| 追加フェーズ | フェーズ17（r8） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 通知ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | level | レベル | VARCHAR | 10 | NOT NULL | - | INFO / WARN / ERROR |
| 3 | target_subscription_tag | 配信タグ | VARCHAR | 50 | NOT NULL | - | inventory_alerts / sales_alerts / delivery_alerts / postal_alerts / batch_failure |
| 4 | target_user_id | 宛先ユーザーID | BIGINT | - | NULL | NULL | NULL のときはタグ全員宛 |
| 5 | title | タイトル | VARCHAR | 200 | NOT NULL | - | |
| 6 | body | 本文 | TEXT | - | NOT NULL | - | |
| 7 | payload_hash | ペイロードハッシュ | VARCHAR | 64 | NOT NULL | - | SHA-256（subscription_tag + 主要キー）／重複抑制キー（M-9 / J-5） |
| 8 | suppressed | 抑制フラグ | BOOLEAN | - | NOT NULL | FALSE | 同一 payload_hash 短期間連発時に TRUE。digest で集約配信 |
| 9 | digest_sent_at | ダイジェスト送信日時 | DATETIME | - | NULL | NULL | digest 配信済みなら set |
| 10 | read_by_user_id | 既読ユーザーID | BIGINT | - | NULL | NULL | 既読化したユーザー |
| 11 | read_at | 既読日時 | DATETIME | - | NULL | NULL | |
| 12 | source_job | 発生元ジョブ | VARCHAR | 100 | NULL | NULL | 例：`InventoryConsistencyCheckJob` |
| 13 | source_batch_execution_id | 発生元実行ID | BIGINT | - | NULL | NULL | FK: batch_executions.id |
| 14 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_cn_tag_unread | INDEX | (target_subscription_tag, read_by_user_id, created_at) |
| idx_cn_user_unread | INDEX | (target_user_id, read_by_user_id, created_at) |
| idx_cn_payload_hash | INDEX | (payload_hash, created_at) |
| idx_cn_suppressed_digest | INDEX | (suppressed, digest_sent_at, created_at) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| fk_console_notifications_batch | FK | `source_batch_execution_id` → `batch_executions.id` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| batch_executions | N:1 | 通知の発生元バッチ実行 |
| notification_subscriptions | （tag 経由） | `target_subscription_tag` で配信先を解決 |

## 設計上の注意

- `payload_hash` は NOT NULL（M-9）。ペイロードのないジョブ通知は `SHA-256("no-payload:" + job_name)` で埋める（J-5）。
- 短期間に同一 `payload_hash` が連発した場合は `suppressed = TRUE` にして individual 送信を抑え、`DigestNotificationDispatchJob` がまとめて配信する。
- アーカイブは `ConsoleNotificationsArchiveJob`（年次）で別系統に退避する想定（フェーズ17.5 以降の課題）。

## Entity

`com.example.notification.entity.ConsoleNotification`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-2）
