# テーブル定義書：inquiries

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | inquiries |
| 論理名 | 問い合わせ親 |
| 所属システム | Core |
| 説明 | フェーズ18 問い合わせ管理の親テーブル。Market 顧客が登録した 1 件の問い合わせ（件名＋ステータス＋対象多態参照）を保持する。スレッド本体（メッセージ群）は `inquiry_messages` 側 |
| 追加フェーズ | フェーズ18（r3） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 問い合わせID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | 顧客ID | BIGINT UNSIGNED | - | NOT NULL | - | FK: market_customers.id（045 / 044 同型対策：market_customers.id が BIGINT UNSIGNED のため型を一致させる必要あり） |
| 3 | subject | 件名 | VARCHAR | 100 | NOT NULL | - | 上限は `amazia.inquiry.subject-max-length` で config 化 |
| 4 | status | ステータス | VARCHAR | 20 | NOT NULL | 'NEW' | NEW / IN_PROGRESS / DONE。遷移ルールは `amazia.inquiry.allowed-status-transitions` |
| 5 | target_type | 対象種別 | VARCHAR | 20 | NULL | NULL | delivery / product / sales / NULL（汎用）。多態参照のため FK は張らない |
| 6 | target_id | 対象ID | BIGINT | - | NULL | NULL | `target_type` と pair NULL（CHECK で担保） |
| 7 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | `@PrePersist` で設定 |
| 8 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | `@PreUpdate` + 返信/ステータス変更時に明示更新 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_inquiries_status_updated_at | INDEX | (status, updated_at) |
| idx_inquiries_user_id_updated_at | INDEX | (user_id, updated_at) |
| idx_inquiries_target | INDEX | (target_type, target_id) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| fk_inquiries_user | FK | `user_id` → `market_customers.id` |
| chk_inquiries_status | CHECK | `status IN ('NEW', 'IN_PROGRESS', 'DONE')` |
| chk_inquiries_target_type | CHECK | `target_type IN ('delivery', 'product', 'sales') OR target_type IS NULL` |
| chk_inquiries_target_pair | CHECK | `target_type / target_id` の pair NULL（両方 NULL or 両方 NOT NULL） |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| market_customers | N:1 | 問い合わせを起こした顧客 |
| inquiry_messages | 1:N | 問い合わせのスレッドメッセージ群（CASCADE DELETE） |
| deliveries / products / sales | 多態（FK なし） | `target_type` ごとの対象。Service 層 `InquiryTargetOwnershipValidator` が所有者検証 |

## 設計上の注意

- 多態参照：`target_type` / `target_id` には物理 FK を張らず、Service 層（`InquiryTargetOwnershipValidator`）で `target_type` ごとに整合性を検証する（設計書 r2 / RV-5）。
- 通知発火：新規作成・顧客返信・ステータス変更のタイミングで phase17 `BatchAlertNotifier.dispatch(...)` を呼び、`subscription_tag='inquiry_alerts'`、`level='INFO'` で `console_notifications` に INSERT する。
- ステータス遷移：双方向許容（`NEW → IN_PROGRESS → DONE → NEW` の巻き戻し含む）。許容組合せは `amazia.inquiry.allowed-status-transitions` で管理。
- ベルマーク件数の真実の元は `inquiries.status='NEW'` の COUNT（`console_notifications` ではない）。

## Entity

`com.example.inquiry.entity.Inquiry`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ18 Step 1-1）
