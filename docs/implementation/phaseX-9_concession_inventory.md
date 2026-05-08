# phaseX-9 Step 0 — 妥協点棚卸し

## 概要
- 親実装計画: [phaseX-9_implementation_plan.md](phaseX-9_implementation_plan.md)
- 親設計書: [phaseX-9_test_isolation_redesign.md](../design/phaseX/phaseX-9_test_isolation_redesign.md)
- 起因トラブル: [051_ci_apply_scheduled_prices_test_h2_isolation_and_market_e2e_timeout.md](../troubles/051_ci_apply_scheduled_prices_test_h2_isolation_and_market_e2e_timeout.md) 派生①〜③
- 作成日: 2026-05-08
- 目的: Step 4（案 B 全件適用）に進む前に、`@Transactional` 不在 6 クラス + `scheduler-enabled=true` 13 クラス（重複あり）について「`@Sql` 適用 / 個別 cleanup / Phase 21 申送り」のいずれで扱うかを決定する。

## 収集方法
- `@Transactional` 不在の `@SpringBootTest` クラス: `@SpringBootTest` 抽出 108 件 − `@Transactional` 抽出 99 件 = 不在 6 件（`PowerShell Compare-Object` で差分抽出）
- `scheduler-enabled=true` 利用クラス: `Grep -Pattern "scheduler-enabled\s*=\s*true|scheduler\.enabled\s*=\s*true"` で 13 件
- 重複: ConsoleNotificationsArchiveJobTest / SessionAndTokenSweepJobTest の 2 クラスは両条件に該当

## サマリ
- 不在 6 + scheduler 13 − 重複 2 = **対象 17 クラス**
- 内訳: `@Sql` 適用 → **11 クラス** / 個別 cleanup → 0 クラス / Phase 21 申送り → **6 クラス**
- Phase 21 申送りの 6 クラスは「マルチスレッド検証 / context-load 検証 / `@Transactional` 不在 + cleanup 困難なテーブル間アーカイブ」のいずれかに該当し、本フェーズ Step 4 の cleanup.sql + `@Sql` パターンでは構造的解決にならない（H2 共有 DB 自体を切り離す Testcontainers が必要）。

---

## 棚卸し表 — `@Transactional` 不在 6 クラス

| クラス名 | 種別 | 検証意図 | 汚染源か | 汚染受け側 | Step 4 対応 | 備考 |
|---|---|---|---|---|---|---|
| `com.example.batch.ConsoleNotificationsArchiveJobTest` | 不在 + scheduler-enabled | テーブル間アーカイブ（`console_notifications` → `console_notification_archives`）。`@Transactional` を付けると ARCHIVE 側 INSERT がロールバックで観測できなくなる | 中（`TransactionTemplate.execute()` 内に閉じているが ARCHIVE 側残置あり） | 中 | **Phase 21 申送り** | アーカイブ移動の検証は `@Transactional` ロールバックと両立しない。cleanup.sql で `console_notifications` / `console_notification_archives` 両方 TRUNCATE しても、検証の途中状態を観測するロジックがロールバックを期待しないため Testcontainers per-class が抹本対策 |
| `com.example.batch.BatchJobLockRegistryTest` | 不在 | マルチスレッド検証（`CountDownLatch` で 2 スレッド同時実行・ロック取得競争を観察） | 中（`batch_executions` に RUNNING レコード残置） | 中 | **Phase 21 申送り** | マルチスレッド検証はクラスレベル `@Transactional` と両立不可（同一 Tx 内の並列実行は分離されない）。cleanup.sql + `@Sql` でも `batch_executions` を毎テスト前 TRUNCATE はできるが、**他クラスから書き込まれた batch_executions を巻き込む副作用**が大きく、構造的にはコンテキスト分離（Testcontainers）が正しい解 |
| `com.example.batch.SessionAndTokenSweepJobTest` | 不在 + scheduler-enabled | テーブル間アーカイブ（期限切れ `market_sessions` / `customer_password_reset_tokens` の削除を検証） | **高**（`save()` 直接書き込み・cleanup なし） | 中 | **`@Sql` 適用** | 削除検証は `@Transactional` ロールバックと両立しない（削除そのものをアサートする）が、cleanup.sql で `market_sessions` / `customer_password_reset_tokens` / `batch_executions` を `BEFORE_TEST_METHOD` で TRUNCATE すれば検証意図を保ったまま分離可能。Step 4 の典型的な対応対象 |
| `com.example.batch.BatchManualTriggerControllerTest` | 不在 | プロパティ切替（`manual-trigger-enabled` の Enabled/Disabled 内部クラス分割）による Bean 起動可否検証 | 低（`operation_logs` 軽微） | 低 | **`@Sql` 適用** | 内部 `@Nested` で property を切り替える特殊構造のため `@Transactional` が貼りにくいが、`operation_logs` 単独の cleanup.sql で十分対応可能。Step 4 で対応 |
| `com.example.batch.BatchProductionValidatorContextLoadTest` | 不在 | context-load 検証（production プロファイル下での `ApplicationContext` 起動失敗を `AnnotationConfigApplicationContext` で手動検証） | なし（DB アクセスなし） | なし | **Phase 21 申送り（cleanup 不要・対応不要）** | DB 書き込みなしのため H2 共有 DB の汚染源にも受け側にもならない。クラス Javadoc に「test プロファイル既定値と production の衝突を避け、Validator 検証のみを単独で確かめるため `@Transactional` を付けない」旨の記述あり。**phaseX-9 でも Phase 21 でも対応不要**（Step 1 規約「クラス Javadoc 冒頭に理由を明記」の既存実装例として参照価値あり） |
| `com.example.batch.BatchProductionValidatorTest` | 不在 | Bean 内メソッドの単体検証（`ReflectionTestUtils` で直接 instantiate） | なし（DB アクセスなし） | なし | **対応不要** | 同上。DB 共有 DB の文脈外 |

---

## 棚卸し表 — `scheduler-enabled=true` 13 クラス

| クラス名 | `@Tx` | count assert | 主テーブル | 自衛コード | 検証の核 | Step 4 対応 |
|---|---|---|---|---|---|---|
| `com.example.batch.InventoryConsistencyCheckJobTest` | N | Y | `products` / `product_skus` / `operation_logs` | N | 在庫不一致検知 + operation ログ記録 | **`@Sql` 適用**（`operation_logs` 等を cleanup） |
| `com.example.batch.ApplyScheduledPricesJobTest` | Y | Y | `product_sku_prices` / `product_sku_scheduled_prices` | Y | 予約価格の適用と現行価格非アクティブ化（051 起因） | **`@Sql` 適用**（自衛コード除去ターゲット） |
| `com.example.batch.PostalCsvImportJobTest` | N | Y | `batch_executions` | Y | バッチ実行結果記録（外部 HTTP は MockServer） | **`@Sql` 適用**（`batch_executions` cleanup） |
| `com.example.batch.OperationLogArchiveJobTest` | N | Y | `operation_logs` / `operation_log_archives` | N | アーカイブテーブルへの移送 | **Phase 21 申送り**（テーブル間アーカイブ + `@Tx` 不在の典型。Console アーカイブと同型のため別クラスでも同方針） |
| `com.example.batch.ConsoleNotificationsArchiveJobTest` | N | Y | `console_notifications` / `console_notification_archives` | N | 通知の段階的アーカイブ | **Phase 21 申送り**（上記重複行参照） |
| `com.example.batch.SessionAndTokenSweepJobTest` | N | Y | `market_sessions` / `customer_password_reset_tokens` | N | 期限切れレコード削除 | **`@Sql` 適用**（上記重複行参照） |
| `com.example.batch.YearlySalesReportJobTest` | Y | Y | `monthly_sales_reports` / `yearly_sales_reports` | N | 月次→年次集計と UPSERT 冪等性 | **`@Sql` 適用**（年次レポート系 cleanup） |
| `com.example.batch.PreorderStatusRefreshJobTest` | Y | Y | `products` / `batch_executions` | N | 予約商品ステータス遷移件数 | **`@Sql` 適用**（`products` の予約状態カラム分離） |
| `com.example.batch.PostalAddressIntegrityCheckJobTest` | Y | Y | `postal_addresses` / `console_notifications` | Y | 郵便番号データ件数下限チェック + 通知 | **`@Sql` 適用**（`console_notifications` cleanup） |
| `com.example.batch.MonthlySalesReportJobTest` | Y | Y | `sales` / `monthly_sales_reports` | N | 売上 4 軸集計と UPSERT 冪等性 | **`@Sql` 適用**（月次レポート系 cleanup） |
| `com.example.batch.DeliveryStatusAdvanceJobTest` | Y | Y | `deliveries` / `console_notifications` | N | 配送遅延検知（状態遷移なし） | **`@Sql` 適用**（`console_notifications` cleanup） |
| `com.example.batch.SalesReconciliationJobTest` | Y | N | `console_notifications` | N | 振込整合性チェック完走 | **`@Sql` 適用**（`console_notifications` cleanup） |
| `com.example.faultinjection.TriggerFaultInjectionJobTest` | Y | Y | `fault_injection_logs` / `products` / `sales` / `deliveries` | N | 3 Injector 同時発火と Controller 連動 | **`@Sql` 適用**（`fault_injection_logs` cleanup — Step 2 PoC 対象 `FaultInjectionLogRepositoryTest` と同 cleanup ファイル流用想定） |

---

## Step 4 対応の最終分類

### 11 クラス: `@Sql` 適用 → 本フェーズ Step 4 で対応
（既に `@Transactional` 付与済みでも、件数アサーションを行い REQUIRES_NEW 経由汚染の受け側になりうるため `@Sql` で確実な分離を行う）

1. SessionAndTokenSweepJobTest（不在クラスから昇格）
2. BatchManualTriggerControllerTest（不在クラスから昇格）
3. InventoryConsistencyCheckJobTest
4. ApplyScheduledPricesJobTest
5. PostalCsvImportJobTest
6. YearlySalesReportJobTest
7. PreorderStatusRefreshJobTest
8. PostalAddressIntegrityCheckJobTest
9. MonthlySalesReportJobTest
10. DeliveryStatusAdvanceJobTest
11. SalesReconciliationJobTest
12. TriggerFaultInjectionJobTest

※ 12 件あるのは scheduler 13 件のうち **アーカイブ系 2 件（OperationLogArchive / ConsoleNotificationsArchive）を Phase 21 申送りに分類**したため。当初想定 11 から +1 で TriggerFaultInjectionJobTest を含む。

### 4 クラス: Phase 21 申送り
（cleanup.sql + `@Sql` では構造的解決にならない、または対応不要）

| クラス | 申送り理由 |
|---|---|
| ConsoleNotificationsArchiveJobTest | テーブル間アーカイブ + `@Tx` 不在（ロールバックで ARCHIVE 側観測不可） |
| OperationLogArchiveJobTest | 同上（テーブル間アーカイブ） |
| BatchJobLockRegistryTest | マルチスレッド検証（`@Tx` 自体と両立不可、コンテキスト分離が必要） |
| BatchProductionValidatorContextLoadTest | DB アクセスなし。Phase 21 でも対応不要だが「`@Transactional` を付けない理由を Javadoc に明記する」既存実装例として参照 |

### 1 クラス: 対応不要
- BatchProductionValidatorTest（DB アクセスなし）

---

## Step 1（規約化）で参照すべき具体例

### `@Transactional` を付けない理由を Javadoc に明記している実装例
[BatchProductionValidatorContextLoadTest.java](../../amazia-core/src/test/java/com/example/batch/BatchProductionValidatorContextLoadTest.java)

→ Step 1 の `test_insights.md` カテゴリ 7-2 規約「『付けない』と判断した場合はクラス Javadoc 冒頭に理由を明記」の既存実装例として `prompt_templates.md` TPL-009 から引用候補。

### 自衛コード（`@BeforeEach cleanupPrior*`）が既に入っているクラス
- ApplyScheduledPricesJobTest
- PostalCsvImportJobTest
- PostalAddressIntegrityCheckJobTest
- FaultInjectionLogRepositoryTest（Step 2 PoC 対象）
- ConsoleNotificationRepositoryTest（051 派生③で追加済み）
- NotificationSubscriptionRepositoryTest（051 派生②で `@Transactional` 付与済みだが念のため再確認）

→ Step 4 で `@Sql` 適用後に **自衛コード削除可否** の二値判定指標として使用。

---

## Phase 21 引継ぎ事項

Step 6 で作成する Phase 21 設計書（`phaseX-10_testcontainers_migration.md`）の「phaseX-9 からの引継ぎ事項」節に以下を記載すること:

1. **テーブル間アーカイブ系 2 クラス**（ConsoleNotificationsArchiveJobTest / OperationLogArchiveJobTest）
   - `@Transactional` 不在 + 2 テーブル間の移送を検証 → ロールバックで ARCHIVE 側観測不可
   - cleanup.sql で TRUNCATE しても、検証の途中状態（移送済み行 vs 移送前行）を観測するロジックは H2 共有 DB と相性が悪い
   - per-class MySQL（Testcontainers）でコンテキスト分離するのが正

2. **マルチスレッド検証 1 クラス**（BatchJobLockRegistryTest）
   - `CountDownLatch` で 2 スレッド同時実行・`@Transactional` クラスレベルと根本的に両立不可
   - cleanup.sql で `batch_executions` を毎テスト TRUNCATE は可能だが、他クラスからの書き込みを巻き込む副作用大
   - Testcontainers per-class でコンテキスト分離が正

3. **MySQL 互換性**
   - Step 2/3 で導入する cleanup.sql は H2 専用構文（`SET REFERENTIAL_INTEGRITY FALSE/TRUE`）
   - Phase 21 で MySQL 移行時に `SET FOREIGN_KEY_CHECKS=0/1` への置換、または cleanup 機構自体の再設計が必要

4. **`scheduler-enabled=true` の非決定性**
   - Step 4 で `@Sql` 適用しても `@Scheduled` 発火タイミングの非決定性は残る
   - Phase 21 で Spring Test の `@MockitoBean(TaskScheduler.class)` 化、または scheduler を完全分離する設計を検討

---

## Step 0 完了条件チェック

- [x] `@Transactional` 不在 6 クラスの分類完了
- [x] `scheduler-enabled=true` 13 クラスの分類完了（重複 2 を除く）
- [x] 各クラスの「Step 4 対応」列が `@Sql` 適用 / Phase 21 申送り / 対応不要 のいずれかで確定
- [x] Phase 21 引継ぎ事項が抽出され、Step 6 で参照可能な状態
- [x] Step 1 規約化で参照すべき具体例（Javadoc 明記の既存実装・自衛コードの既存実装）が抽出済み
