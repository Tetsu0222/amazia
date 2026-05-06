# テーブル定義書：workflow_requests

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | workflow_requests |
| 論理名 | ワークフロー申請 |
| 所属システム | Core |
| 説明 | 商品・価格・在庫等に対する変更申請のメタ情報を保持する。承認段階の詳細は `workflow_requests_detail` で別管理する |
| 追加フェーズ | フェーズ12 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 申請ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | target_type | 対象種別 | VARCHAR | 20 | NOT NULL | - | product / price / stock 等 |
| 3 | target_id | 対象ID | BIGINT | - | NOT NULL | - | target_type に対応する PK |
| 4 | requested_by | 申請者ID | BIGINT | - | NOT NULL | - | users.id（Console 社員）。明示的な FK 制約は付与なし |
| 5 | status | 申請ステータス | VARCHAR | 20 | NOT NULL | - | pending / approved / rejected / canceled |
| 6 | payload | 申請内容 | JSON | - | NOT NULL | - | 適用予定の変更内容を JSON で保持 |
| 7 | completed_at | 完了日時 | DATETIME | - | NULL | NULL | approved / rejected / canceled に確定した時刻 |
| 8 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |
| 9 | updated_at | 更新日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_workflow_status | INDEX | status |
| idx_workflow_target | INDEX | (target_type, target_id) |
| idx_workflow_requester | INDEX | requested_by |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| workflow_requests_detail | 1:N | 段階ごとの承認情報（複数ステップ） |
| users | N:1 | 申請者（`requested_by`）。FK 制約は未付与 |

## ステータス遷移

```
pending ──→ approved
        ├─→ rejected
        └─→ canceled
```

- 申請直後は `pending`。全段階の承認が完了すると `approved`、いずれかで却下されると `rejected`、申請者が取り下げると `canceled` に遷移する。
- ステータスが終端（approved / rejected / canceled）に到達した時点で `completed_at` を設定する。

## 設計上の注意

- `payload` カラムは MySQL 本番では JSON 型で作成済み（V4 マイグレーション）。Entity 側では H2/Hibernate の二重エスケープ問題を避けるため `@Lob String` として扱っている（`WorkflowRequest.java` の Javadoc コメント参照）。アプリ側では JSON 文字列として手動シリアライズする運用。
- `requested_by` / `target_id` には schema.sql レベルでの FK 制約は付与されていない（`target_type` で参照先が分岐するため）。アプリ側で参照整合性を担保する。
- 「即座適用」フローでは申請レコードを作らずに直接適用するルートもある（`/api/workflows/immediate-apply`）。このテーブルに到達するのは多段承認が必要なケースのみ。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ12 / V4 相当：IF NOT EXISTS 版）
- `amazia-core/src/main/resources/db/migration/V4__create_workflow_tables.sql`（名残ファイル：本番では実行されない）
