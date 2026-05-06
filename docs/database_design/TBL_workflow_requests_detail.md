# テーブル定義書：workflow_requests_detail

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | workflow_requests_detail |
| 論理名 | ワークフロー申請詳細（段階別承認） |
| 所属システム | Core |
| 説明 | `workflow_requests` 1件に対する段階ごとの承認情報を保持する。1申請に対し複数ステップ（step_number 順）で承認・却下フローを進める |
| 追加フェーズ | フェーズ12 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 詳細ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | workflow_requests_id | 申請ID | BIGINT | - | NOT NULL | - | workflow_requests.id（明示的な FK 制約は未付与） |
| 3 | step_number | ステップ番号 | INT | - | NOT NULL | - | 1 から開始。承認順序を表す |
| 4 | target_role | 対象ロール | VARCHAR | 30 | NOT NULL | - | 当該ステップを承認すべきロールコード |
| 5 | destination_user_id | 通知先ユーザーID | BIGINT | - | NULL | NULL | users.id（特定ユーザー指定時。FK 未付与） |
| 6 | destination_name | 通知先名称 | VARCHAR | 100 | NULL | NULL | UI 表示用の冗長カラム |
| 7 | approver_user_id | 承認者ID | BIGINT | - | NULL | NULL | users.id（承認・却下確定時にセット。FK 未付与） |
| 8 | approver_name | 承認者名 | VARCHAR | 100 | NULL | NULL | UI 表示用の冗長カラム |
| 9 | status | ステップステータス | VARCHAR | 20 | NOT NULL | - | waiting / pending / approved / rejected / canceled |
| 10 | updated_at | 更新日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_wfd_parent_step | INDEX | (workflow_requests_id, step_number) |
| idx_wfd_destination | INDEX | destination_user_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| workflow_requests | N:1 | 親となる申請 |
| users | N:1 | 通知先（`destination_user_id`）／承認者（`approver_user_id`）。FK 未付与 |

## ステータス遷移

```
waiting ──→ pending ──→ approved
                    └─→ rejected
                    └─→ canceled
```

- 申請作成時、step_number=1 のレコードは `pending`、それ以降は `waiting`。
- 直前ステップが `approved` になると、次ステップが `waiting` → `pending` に遷移する。
- 1ステップでも `rejected` / `canceled` になると、後続ステップは状態確定（`canceled` 等）し、親 `workflow_requests.status` も終端に確定する。

## 設計上の注意

- `destination_*` と `approver_*` を冗長に名前カラム保持しているのは、承認後にユーザーが改名・退職してもログ表示が崩れないため（履歴性の担保）。
- `target_role` と `destination_user_id` のどちらか／両方を使うかは申請定義側の運用に依存する（ロール承認 vs 個人承認）。
- スキーマ上の FK 制約は付与されていない（`workflow_requests` / `users` への参照はアプリ側で担保）。
- ステップ承認 API は `/api/workflows/{id}/steps/{stepNumber}/approve` / `.../reject` で受け付ける。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ12 / V4 相当：IF NOT EXISTS 版）
- `amazia-core/src/main/resources/db/migration/V4__create_workflow_tables.sql`（名残ファイル：本番では実行されない）
