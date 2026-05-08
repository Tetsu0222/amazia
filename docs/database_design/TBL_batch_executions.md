# テーブル定義書：batch_executions

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | batch_executions |
| 論理名 | バッチ実行履歴 |
| 所属システム | Core |
| 説明 | フェーズ17 バッチ処理基盤の実行履歴。`AbstractBatchJob` の開始・終了で REQUIRES_NEW トランザクションで INSERT/UPDATE される。Console UI の「バッチ履歴」画面が参照する正本 |
| 追加フェーズ | フェーズ17（r8） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 実行ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | job_name | ジョブ名 | VARCHAR | 100 | NOT NULL | - | 例：`InventoryConsistencyCheckJob` |
| 3 | status | ステータス | VARCHAR | 20 | NOT NULL | - | RUNNING / SUCCESS / FAILED / PARTIAL |
| 4 | started_at | 開始日時 | DATETIME | - | NOT NULL | - | |
| 5 | finished_at | 終了日時 | DATETIME | - | NULL | NULL | RUNNING 中は NULL |
| 6 | target_count | 対象件数 | INT | - | NULL | NULL | 集計対象（任意） |
| 7 | success_count | 成功件数 | INT | - | NULL | NULL | |
| 8 | failure_count | 失敗件数 | INT | - | NULL | NULL | |
| 9 | error_summary | エラー概要 | TEXT | - | NULL | NULL | 例外メッセージ等 |
| 10 | triggered_by | 起動元 | VARCHAR | 50 | NOT NULL | - | `scheduler` または `manual:user_id=N` |
| 11 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_batch_executions_job_started | INDEX | (job_name, started_at) |
| idx_batch_executions_status | INDEX | status |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| console_notifications | 1:N | `source_batch_execution_id` で参照される |

## 設計上の注意

- `BatchExecutionRecorder`（`@Transactional(propagation = REQUIRES_NEW)`）で書き込む。本体ロールバック時もステータス更新は残す（設計書 §3 共通制御 1）。
- 起動時クリーンアップ（`OrphanedRunningSweeper`）で `status = RUNNING` かつ `started_at < NOW() - 24h` の行を `FAILED, error_summary='[recovery] orphaned by JVM restart'` に強制遷移する（設計書 §3 共通制御 3）。
- 多重起動防止は JVM 内 `BatchJobLockRegistry`（`ConcurrentHashMap<jobName, AtomicBoolean>`）で行うため、本テーブルに PK 以外の UNIQUE 制約は持たせない。

## Entity

`com.example.batch.entity.BatchExecution`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-1）
