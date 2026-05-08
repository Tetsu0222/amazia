# テーブル定義書：notification_subscriptions

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | notification_subscriptions |
| 論理名 | 通知購読設定 |
| 所属システム | Core |
| 説明 | ユーザーごとの通知購読タグ管理。`NotificationDispatcher` が各タグの SES / Console 通知配信先を解決する際に参照 |
| 追加フェーズ | フェーズ17（r8 / 6.2.1） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 購読ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | ユーザーID | BIGINT UNSIGNED | - | NOT NULL | - | FK: users.id |
| 3 | subscription_tag | 配信タグ | VARCHAR | 50 | NOT NULL | - | inventory_alerts / sales_alerts / delivery_alerts / postal_alerts / batch_failure |
| 4 | email_enabled | メール配信有効 | BOOLEAN | - | NOT NULL | TRUE | |
| 5 | in_app_enabled | アプリ内通知有効 | BOOLEAN | - | NOT NULL | TRUE | |
| 6 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |
| 7 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_ns_user_tag | UNIQUE | (user_id, subscription_tag) |
| idx_ns_subscription_tag | INDEX | subscription_tag |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| uk_ns_user_tag | UNIQUE | 1 ユーザー × 1 タグは一意 |
| fk_ns_user | FK | `user_id` → `users.id` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| users | N:1 | 購読者 |
| console_notifications | （tag 経由） | `target_subscription_tag` で配信先を解決 |

## 初期データ（schema.sql）

`role_permissions` で全権限を持つロール（`admin` / `senior_admin` / `eternal_advisor`）に属するユーザー全員に、5 タグ（inventory_alerts / sales_alerts / delivery_alerts / postal_alerts / batch_failure）を `email_enabled=TRUE, in_app_enabled=TRUE` で自動購読する。再実行時は `INSERT IGNORE` で重複しない。

実運用での自動購読対象ロールは環境変数 `BATCH_NOTIFICATIONS_AUTO_SUBSCRIBE_ROLES` で外出し管理（規約 4-1）。phase11 の `CreateUserService` / `UpdateUserService` は Step 6-4 で `SyncNotificationSubscriptionsService.applyForUserRole(userId, roleCode)` を呼び出すようになっており、自動購読対象ロールなら全タグを `email_enabled=TRUE / in_app_enabled=TRUE` で UPSERT、非対象ロール（admin → user 降格を含む）なら当該ユーザの全行を `email_enabled=FALSE / in_app_enabled=FALSE` に降格させる（行は物理削除しない）。タグ一覧は `BATCH_NOTIFICATIONS_SUBSCRIPTION_TAGS` で外出し管理。

## Entity

`com.example.notification.entity.NotificationSubscription`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-3）
