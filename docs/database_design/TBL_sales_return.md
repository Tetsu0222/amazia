# テーブル定義書：sales_return

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | sales_return |
| 論理名 | 返品管理 |
| 所属システム | Core |
| 説明 | 売上に対する返品申請〜承認〜通知の状態を管理する。1売上に対し複数の返品レコードを許容する（部分返品想定） |
| 追加フェーズ | フェーズ14 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 返品ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sales_id | 売上ID | BIGINT | - | NOT NULL | - | FK: sales.id |
| 3 | status | 返品ステータス | VARCHAR | 50 | NOT NULL | - | requested / approved / rejected / completed 等 |
| 4 | reason | 返品理由 | TEXT | - | NULL | NULL | 顧客が入力した自由記述 |
| 5 | quantity | 返品数量 | INT | - | NOT NULL | - | CHECK: > 0。元注文の数量を超えない前提（アプリ側で検証） |
| 6 | approver_id | 承認者ID | BIGINT | - | NULL | NULL | FK: users.id（Console 社員）。承認後にセット |
| 7 | approved_at | 承認日時 | DATETIME | - | NULL | NULL | |
| 8 | notified_user | 顧客通知済フラグ | BOOLEAN | - | NOT NULL | FALSE | 返品結果メールが顧客に送信済み |
| 9 | notified_admin | 管理者通知済フラグ | BOOLEAN | - | NOT NULL | FALSE | 返品申請時の管理者通知が送信済み |
| 10 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |
| 11 | updated_at | 更新日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| chk_sales_return_quantity_positive | CHECK | quantity > 0 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_sales_return_sales_id | INDEX | sales_id |
| idx_sales_return_status | INDEX | status |

## 外部キー

| FK名 | カラム | 参照先 |
|------|--------|--------|
| fk_sales_return_sales | sales_id | sales(id) |
| fk_sales_return_approver | approver_id | users(id) |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| sales | N:1 | 返品対象の売上 |
| users | N:1 | 承認者（Console 社員） |

## 設計上の注意

- `approver_id` は Console 社員 `users.id`（管理画面で承認操作したユーザー）を参照する。`sales.user_id`（Market 顧客）とは別系統。
- `notified_user` / `notified_admin` はメール送信状態を保持する。送信失敗時のリトライや、二重送信防止に利用する。
- ステータス遷移はアプリ側で管理（`requested` → `approved`/`rejected` → `completed`）。`shipping_statuses.code = 'RETURN_REQUESTED'/'RETURNED'` と連動する場合があるが、両者は独立したライフサイクルを持つ。
- 部分返品を想定し、1売上レコードに対し複数の返品レコードが作られることを許容する。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ14 / V6 + V8 相当）
- `amazia-core/src/main/resources/db/migration/V6_*.sql` / `V8_*.sql`（名残ファイル：本番では実行されない）
