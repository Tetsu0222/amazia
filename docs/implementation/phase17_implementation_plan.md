# フェーズ17 実装計画（バッチ処理）

## 概要
- 対象設計書: [phase17_batch_processing.md](../design/phase11_20/phase17_batch_processing.md)（**r8 / 2026-05-08**）
- 対象範囲: Amazia Core / Amazia Console / DB 設計 / SES / CloudWatch
- 段取り: 設計書 §11 のステップ 0 〜 10 をベースに、**Step 0（前提整備＋ r8 派生事項の確定）→ 1（DB マイグレーション）→ 2（バッチ共通基盤）→ 3（日次ジョブ 6 本）→ 4（月次／年次ジョブ）→ 5（フォルトインジェクション）→ 5.5（価格スケジュール Core / Console API）→ 6（Console UI：履歴・通知・手動）→ 6.5（SKU 詳細モーダル価格管理タブ 3 ブロック化）→ 7（SES テンプレート）→ 8（E2E）→ 9（Docker 完走）→ 10（ドキュメント反映）** の 13 段階で実施
- 作成日: 2026-05-08
- 親フェーズ: [phase15_implementation_plan.md](phase15_implementation_plan.md)（phase15 r5 完了済み）／ [phase16_step5_implementation_plan.md](phase16_step5_implementation_plan.md)（phase16 Step 5 完了済み）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | Step 0 → 1 → 2 → 3 → 4 → 5 → 5.5 → 6 → 6.5 → 7 → 8 → 9 → 10 を厳守。Step を跨いだ部分実装は禁止。各 Step 末で `mvn test` / `phpunit` / `vitest` の該当層が緑であることを完了条件とする |
| 規模感 | Core 5 テーブル新設 + 1 テーブル拡張（`batch_executions` / `console_notifications` / `fault_injection_logs` / `monthly_sales_reports` / `yearly_sales_reports` / `notification_subscriptions` / `product_sku_scheduled_prices` 新設、`product_sku_prices.is_active` 追加）、業務バッチ 11 本（日次 6・月次 2・年次 3・高頻度 1・オンデマンド 4）、Service 約 20 本、Controller 約 10 本、Console 画面 3 種（バッチ履歴 / 通知センター / 手動起動）、Vue 拡張 1 種（SKU 価格管理タブ 3 ブロック化） |
| TDD | 設計書 §12 の TDD ケースを Step ごとに割り当てて実装。E2E は Step 8 に集約 |
| コーディング規約 | [coding_guidelines.md](../coding_guidelines.md) 厳守（Service にロジック寄せ・config 駆動・1 ファイル 1 ユースケース・ドメイン単位パッケージ） |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` + `application-test.properties`（Core）+ Console `phpunit.xml`（Console 値が要る場合のみ）をセット更新（規約 4-3）。設計書 §7 のチェックリストを Step 1 着手前に必ず実行 |
| テスト値 | ハードコードせず `config()` / `@Value` 経由で取得（規約 4-1） |
| メモリ事項 | Core 側 Heap 制限（`-Xmx384m`／[user memory: phaseX4_t3micro_recovery]）を意識し、整合性チェック・売上集計 SQL は `ScrollableResults` か LIMIT/OFFSET ページングで処理する。`inventories` / SKU TX の全件 List 化禁止 |
| 同時実行制御 | JVM 内 `ConcurrentHashMap<jobName, AtomicBoolean>` 軽量ロック（設計書 §3 共通制御 R-7）。`SELECT ... FOR UPDATE` は本書では使わない |
| 在庫モデル | **設計書 r8 で `inventory_movements` を取り下げ済**。`product_sku_stock_transactions`（以下 SKU TX）を在庫増減ログの正本として扱い、`InventoryConsistencyCheckJob` は SKU TX を `sku_id → product_id` でロールアップして `inventories` と突合する 2 段集計で動かす |
| マイグレーション | 業務テーブルは Core `schema.sql` に冪等構文（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE` / `ALTER TABLE ADD COLUMN`）で追記（[037](../troubles/037_flyway_misassumed_phase14_tables_missing.md) / phase14 計画 §1-6 と同方針）。Console Laravel migrations にバッチテーブルを追加しない |
| H2 互換 | schema.sql の MySQL 専用構文を持ち込まないことを最優先（test_insights カテゴリ7-2）。テストは `application-test.properties > spring.sql.init.schema-locations=` を空のまま、Entity から ddl-auto=create-drop で生成。`fault_injection_logs.environment` の CHECK 制約は H2 / MySQL 双方で通る `CHECK (environment IN (...))` で記述（設計書 §12.1） |
| 本番安全 | フォルトインジェクションは「機能フラグ + 起動時 Validator + `@Profile("!production")` + DB CHECK + SKU TX 補償」の五重防御（r8）。Step 5 着手前に本番 Validator の起動失敗を 1 度確認する（設計書 §7 末尾チェックリスト J-6） |

### 設計書からの「本フェーズのスコープ外」確認

| 項目 | 取り扱い |
|------|---------|
| `inventory_movements` テーブル新設 | **r8 で取り下げ済**。SKU TX で代替するため本計画でも一切触れない |
| SKU TX への `CHECK (quantity != 0)` 制約追加 | スコープ外（Service 層契約で代替・H-4）。将来 phase18 以降で再検討 |
| `monthly_sales_reports` のテーブル分割化（R-11） | 本フェーズでは集計軸 NULL 運用のまま実装し、UNIQUE + UPSERT（R-15）で同月二重 INSERT を防ぐ。分割化は r9 候補 |
| ShedLock / Quartz クラスタ運用 | スコープ外（単一 EC2 のため不要） |
| Lambda / EventBridge 移行 | スコープ外（無料枠方針） |
| `inventory_movements_archive` 新設 | **r8 で `product_sku_stock_transactions_archive` に名称変更**。本フェーズでは未着手（13.4 の将来課題） |

### 設計書 r8 改訂で本計画に反映する追加変更（H-1 〜 H-10）

| ID | 反映先 Step | 内容 |
|----|----------|------|
| H-1 | Step 0 〜 全 Step | `inventory_movements` の参照を全廃。SKU TX に統一 |
| H-2 | Step 3-1 | `InventoryConsistencyCheckJob` の判定 SQL を「SKU TX → SKU → 商品ロールアップ → `inventories` 倉庫合算と突合」の 2 段集計に書き換え |
| H-3 | Step 2 / Step 5 | `movement_type` 表記を SKU TX `type` 表記に統一 |
| H-4 | Step 2 | `InventoryAdjustmentService` を新設し Service 層契約（`type='adjustment'` のみ `quantity != 0` 許容、その他は `quantity > 0` 強制）を担保 |
| H-5 | Step 3-7（オンデマンド） | `RebuildInventoriesJob` のロジックを SKU TX 集計 → 商品ロールアップに書き換え |
| H-6 | Step 3-7（オンデマンド） | `BootstrapInventoryAdjustmentJob` の補填値を `product_sku_stocks.quantity` ベースに書き換え。冪等性チェックは `reference_type = 'bootstrap'` で判定 |
| H-7 | Step 5 | フォルトインジェクションの補償レコードを SKU TX `type='adjustment'` で書き込む |
| H-8 | Step 0（取り下げ） | phase14 r4 への CHECK 緩和要請を取り下げ（設計書 13.1） |
| H-9 | Step 1 | phase17 マイグレーション内で SKU TX への bootstrap INSERT（SKU 単位）を実装 |
| H-10 | Step 10（ドキュメント） | リスク表・将来課題のテーブル名表記を `product_sku_stock_transactions_archive` に統一 |

---

## 1. Step 0 — 前提整備 ✅ 完了（2026-05-08）

### 1-1. 既存実装との整合性（2026-05-08 棚卸し結果 / Step 0 で確定）

#### 1-1-1. SKU TX 関連（実装の実値で揃える／設計書 r8 G-1 反映済み）

| 既存資産 | 場所 | フェーズ17 での利用方針 |
|---------|------|----------------------|
| `product_sku_stock_transactions`（SKU TX） | [ProductSkuStockTransaction.java](../../amazia-core/src/main/java/com/example/sku/entity/ProductSkuStockTransaction.java) | **本書での在庫増減ログ正本**。`type` カラムの**実値**は `application.properties` で外出し管理（規約 4-1）。`movement_type` は持ち込まない（H-3） |
| `product_sku_stocks` | [ProductSkuStock.java](../../amazia-core/src/main/java/com/example/sku/entity/ProductSkuStock.java) | SKU 単位の現在在庫（`@Version` 楽観ロック付き）。`BootstrapInventoryAdjustmentJob` の補填値ソース（H-6） |
| `inventories` | [Inventories.java](../../amazia-core/src/main/java/com/example/inventory/entity/Inventories.java) | 商品×倉庫単位の現在在庫（`@Check(quantity >= 0)`）。`InventoryConsistencyCheckJob` の主参照（突合相手） |
| `InventorySyncService` | [InventorySyncService.java](../../amazia-core/src/main/java/com/example/inventory/service/InventorySyncService.java) | **既存シグネチャは `applyDelta(productId, warehouseId, delta)`**（product 単位）。`InventoryAdjustmentService` から内部呼び出しする（G-3） |
| `OrderConfirmationService` | `OrderConfirmationService.java` | 既存の SKU TX 投入経路（`type=${amazia.sales.sku-stock-tx-types.sale}` ＝ `sale`）。phase17 では追加修正なし |
| `RefundSalesReturnService` | `RefundSalesReturnService.java` | 既存の SKU TX 投入経路（`type=${amazia.sales.sku-stock-tx-types.return}` ＝ `return`）。phase17 では追加修正なし |
| `ReceiveProductSkuStockService` | `ReceiveProductSkuStockService.java` | 既存の SKU TX 投入経路。**実値は `type='receive'`**（設計書 r7 想定の `inbound` ではない／G-1）。phase17 では追加修正なし |
| `DeliveryStatusTransitionService` | `DeliveryStatusTransitionService.java` | 既存の SKU TX 投入経路（`type=${amazia.delivery.sku-stock-tx-types.sale-preorder-shipment}` ＝ `sale_preorder_shipment` ／予約購入の出荷時減算）。phase17 では追加修正なし |

#### 1-1-2. SKU TX `type` 実値表

[application.properties#L52-L75](../../amazia-core/src/main/resources/application.properties#L52-L75) より：

| キー | 実値 | 用途 | 投入元 |
|------|------|------|------|
| `amazia.sales.sku-stock-tx-types.receive` | `receive` | 入荷 | `ReceiveProductSkuStockService` |
| `amazia.sales.sku-stock-tx-types.adjust` | `adjust` | 棚卸補正・bootstrap・人為注入の補償 | `InventoryAdjustmentService`（**Step 2 で新設**） |
| `amazia.sales.sku-stock-tx-types.sale` | `sale` | 通常購入の注文確定 | `OrderConfirmationService` |
| `amazia.sales.sku-stock-tx-types.return` | `return` | 返品復元 | `RefundSalesReturnService` |
| `amazia.sales.sku-stock-tx-types.cancel` | `cancel` | 将来用（未使用） | （未投入） |
| `amazia.delivery.sku-stock-tx-types.sale-preorder-shipment` | `sale_preorder_shipment` | 予約購入の出荷時減算 | `DeliveryStatusTransitionService` |

#### 1-1-3. phase11 認証関連（実態との乖離・3 種ロールに修正／設計書 r8 G-2 反映済み）

| 既存資産 | 場所 | 実態 |
|---------|------|------|
| `roles` マスタ | [test-data.sql L6-10](../../amazia-core/src/test/resources/test-data.sql#L6-L10) | **5 種ロール存在**：`admin` / `user` / `supervisor` / `senior_admin` / `eternal_advisor` |
| `role_permissions` | test-data.sql L26-28 | **全権限保持＝管理者相当**は `admin` / `senior_admin` / `eternal_advisor` の 3 種 |
| `users` Entity | `User.java` | `role` フィールド（`Role` Entity 参照、`code` カラム） |

**自動購読対象ロール（設計書 13.0 / 6.2.1）：** `admin / senior_admin / eternal_advisor` の 3 種（`role_permissions` で全権限を持つロール群）。CSV 環境変数 `BATCH_NOTIFICATIONS_AUTO_SUBSCRIBE_ROLES` で外出し管理（規約 4-1）。

#### 1-1-4. その他の既存資産

| 既存資産 | 場所 | 利用方針 |
|---------|------|--------|
| `market_sessions` / `market_customers_password_reset_tokens` | phase13 | `SessionAndTokenSweepJob` の対象（設計書 3.1 ⑤） |
| `postal_addresses` | phase13 | `PostalAddressIntegrityCheckJob` の対象（設計書 3.2 ①）。取込本体は phase13 で実装済 |
| `operation_logs` | phase14 | `OperationLogArchiveJob` の対象（設計書 3.3 ②）。**アーカイブ先 `operation_logs_archive` は未存在のため Step 1 で追加する** |
| Vue SKU 詳細モーダル | [SkuList.vue](../../amazia-console/resources/vue/src/features/skus/pages/SkuList.vue) | phase16 Step 5 で導入済の「価格管理 / 在庫管理 / 画像管理」タブ。Step 6.5 で価格管理タブを 3 ブロック化 |
| `product_sku_prices` | [ProductSkuPrice.java](../../amazia-core/src/main/java/com/example/sku/entity/ProductSkuPrice.java) | Step 1-6 で `isActive` フィールドを追加 |
| `skus.js` API 関数 | [skus.js](../../amazia-console/resources/vue/src/features/skus/api/skus.js) | Step 6.5 で `getCurrentSkuPrice` / `getScheduledSkuPrice` 等の関数を追加 |
| `docker-compose.yml` の amazia-core `environment` セクション | [docker-compose.yml#L27-L55](../../docker-compose.yml#L27-L55) | Step 1 着手前に新規環境変数を追加（1-5 参照） |

### 1-2. Step 0-1: パッケージ構成の確定（Core）

新規 Java パッケージ：

```
com.example.batch
├── scheduler
│   ├── DailyBatchScheduler / MonthlyBatchScheduler / YearlyBatchScheduler / DigestBatchScheduler
│   └── （各 Scheduler は @Scheduled の薄いラッパーのみ）
├── job
│   ├── inventory   InventoryConsistencyCheckJob / RebuildInventoriesJob / BootstrapInventoryAdjustmentJob /
│   │               ApplyScheduledPricesJob
│   ├── sales       SalesReconciliationJob / MonthlySalesReportJob / YearlySalesReportJob
│   ├── delivery    DeliveryStatusAdvanceJob / RecalculateDeliveryScheduleJob
│   ├── postal      PostalAddressIntegrityCheckJob
│   ├── session     SessionAndTokenSweepJob
│   ├── preorder    PreorderStatusRefreshJob
│   ├── operationlog OperationLogArchiveJob
│   ├── notification ConsoleNotificationsArchiveJob / DigestNotificationDispatchJob
│   └── faultinjection TriggerFaultInjectionJob（@Profile("!production")）
├── support
│   ├── AbstractBatchJob（テンプレートメソッド：開始/終了の batch_executions 制御 / 多重起動防止 / リトライ）
│   ├── BatchExecutionRecorder（REQUIRES_NEW で batch_executions に書き込む Service）
│   ├── BatchJobLockRegistry（ConcurrentHashMap<jobName, AtomicBoolean> ベース）
│   ├── OrphanedRunningSweeper（ApplicationReadyEvent 起動時クリーンアップ）
│   ├── PayloadHasher（subscription_tag + キー連結 → SHA-256／K-1 / J-5 / N-11 候補）
│   ├── BatchRetryClassifier（リトライ可能例外の判定）
│   ├── RandomGeneratorAdapter（テスト時に決定論的モック差し替え／R-14 候補）
│   └── BatchManualTriggerController（手動起動 API のガード／M-1）
├── config
│   ├── BatchProperties（@ConfigurationProperties amazia.batch）
│   ├── SimulationProperties（amazia.simulation）
│   └── BatchProductionValidator（@Profile("production") / 起動時 Validator）
└── controller
    ├── ListBatchExecutionController / GetBatchExecutionDetailController
    ├── ListConsoleNotificationController / MarkNotificationReadController
    └── BatchManualTriggerController（admin のみ）

com.example.notification
├── entity        ConsoleNotification / NotificationSubscription
├── repository    ConsoleNotificationRepository / NotificationSubscriptionRepository
├── service       NotificationDispatcher / DigestNotificationDispatchService /
│                 RegisterNotificationSubscriptionService（phase11 への要請への内部委譲先・13.0）
└── dto           ConsoleNotificationResponse / NotificationDispatchRequest

com.example.faultinjection（@Profile("!production")）
├── entity        FaultInjectionLog
├── repository    FaultInjectionLogRepository
├── injector      SalesMismatchInjector / InventoryMismatchInjector / DeliveryTroubleInjector
└── client        BankTransferMockClient（mode 切替）

com.example.inventory（既存に追加）
└── service       InventoryAdjustmentService（H-4 / G-1 / G-3 で新設・Step 2 で実装）
                  - シグネチャ：adjust(skuId, delta, referenceType, referenceId, userId, comment)
                  - SKU TX `type=${amazia.sales.sku-stock-tx-types.adjust}` ＝ `adjust` の唯一の投入経路
                  - 符号契約：type='adjust' は delta != 0 強制（差し引きゼロのダミー記録は禁止）
                  - 内部処理：
                      ① validate(delta != 0)
                      ② product_sku_stocks の対象 SKU に delta 加算（楽観ロック付き）
                      ③ SKU TX に type='adjust' で INSERT
                      ④ SKU から product_id 解決 → InventorySyncService.applyDelta(productId, defaultWarehouseId, delta)
                  - 既存 InventorySyncService / OrderConfirmationService 等は一切修正しない

com.example.scheduledprice（H-9 派生・r7 由来）
├── entity        ProductSkuScheduledPrice
├── repository    ProductSkuScheduledPriceRepository
├── service       GetScheduledSkuPriceService / RegisterScheduledSkuPriceService /
│                 DeleteScheduledSkuPriceService / ListSkuPriceHistoryService
└── controller    GetScheduledSkuPriceController / RegisterScheduledSkuPriceController /
                  DeleteScheduledSkuPriceController / ListSkuPriceHistoryController

com.example.salesreport（既存に追加 or 新設）
├── entity        MonthlySalesReport / YearlySalesReport
├── repository    MonthlySalesReportRepository / YearlySalesReportRepository
└── service       BuildMonthlySalesReportService / BuildYearlySalesReportService
```

既存 `com.example.inventory.service.InventorySyncService` は `Inventories` の更新ロジックを既に持っているため、`InventoryAdjustmentService` から内部呼び出しする形で重複を避ける。

### 1-3. Step 0-2: パッケージ構成の確定（Console）

```
app/Batch/
├── Controller/  ListBatchExecutionController / GetBatchExecutionDetailController /
│                ListConsoleNotificationController / MarkNotificationReadController /
│                TriggerBatchManualController
└── Service/     ListBatchExecutionService / GetBatchExecutionDetailService /
                 ListConsoleNotificationService / MarkNotificationReadService /
                 TriggerBatchManualService

app/ScheduledPrice/（Step 5.5 / 13.5.2 Pass-through）
├── Controller/  GetScheduledSkuPriceController / RegisterScheduledSkuPriceController /
│                DeleteScheduledSkuPriceController / ListSkuPriceHistoryController
└── Service/     ScheduledSkuPriceProxyService

amazia-console/resources/vue/src/features/
├── batch/
│   ├── pages/   BatchExecutionList.vue / BatchExecutionDetail.vue / BatchManualTrigger.vue
│   └── api/     batch.js
├── notifications/
│   ├── pages/   NotificationCenter.vue（既読/未読切替・タグ別フィルタ）
│   └── api/     notifications.js
└── skus/（既存に追加）
    └── components/PriceManagementTab.vue（モーダル内・現行/予約/履歴3ブロック）
```

### 1-4. Step 0-3: r8 派生事項の確定（Step 0 確定済み回答）

着手前の論点 4 件 + 棚卸し時の追加論点 3 件（G-1 / G-2 / G-3）について、Step 0 で以下に確定：

#### 1-4-1. `InventoryAdjustmentService` の責務境界（H-4 / G-1 / G-3 確定）

- **シグネチャ：** `adjust(long skuId, int delta, String referenceType, Long referenceId, Long userId, String comment)` ＝ SKU 単位
- **type 値：** `${amazia.sales.sku-stock-tx-types.adjust}`（実値 `adjust` ／設計書 r7 想定の `adjustment` ではない／G-1）。`@Value` 経由で取得（規約 4-1）
- **責務：**
  - ① 符号契約 validate（`delta != 0`）
  - ② `product_sku_stocks` の対象 SKU を `quantity += delta`（楽観ロック）
  - ③ SKU TX に `type='adjust'` で INSERT
  - ④ SKU → `product_id` 解決 → 既存 `InventorySyncService.applyDelta(productId, defaultWarehouseId, delta)` 呼び出し
- **既存 Service の修正：** `OrderConfirmationService` / `RefundSalesReturnService` / `ReceiveProductSkuStockService` / `DeliveryStatusTransitionService` / `InventorySyncService` は**一切修正しない**

#### 1-4-2. SKU TX bootstrap 投入の形式（H-9 確定）

- **配置：** `schema.sql` 末尾の「フェーズ17 Step 1 セクション」内（schema.sql 357-361 行の `inventories` 初期複製とは独立に追加）
- **SQL：**
  ```sql
  INSERT IGNORE INTO product_sku_stock_transactions
      (sku_id, type, quantity, reference_type, reference_id, created_by_user_id, comment, created_at)
  SELECT s.sku_id, 'adjust', s.quantity, 'bootstrap', NULL, NULL,
         '[bootstrap] initial inventory', CURRENT_TIMESTAMP
    FROM product_sku_stocks s
   WHERE s.quantity > 0;
  ```
- **冪等性：** `INSERT IGNORE` ＋ `BootstrapInventoryAdjustmentJob` の `WHERE reference_type = 'bootstrap'` 重複検知の二重保証（J-7）

#### 1-4-3. `InventoryConsistencyCheckJob` の倉庫合算範囲（確定）

- 環境変数 `amazia.batch.sales-reconciliation.target-warehouse-ids` を `SalesReconciliationJob` と **共有** （複数倉庫運用時の事故防止）
- 既定 `1` で単一倉庫運用と整合
- `SalesReconciliationJob` の env 名を流用するか、別 env （`amazia.batch.inventory-check.target-warehouse-ids`）を持つかは、Step 3-1 着手時に再検討。**Step 0 時点では `sales-reconciliation` 側を共有する方針**で進める（運用上、両ジョブで対象倉庫を別ける運用は想定されないため）

#### 1-4-4. 本番 Validator の起動失敗テスト方針（J-6 確定）

- **Validator クラス：** `BatchProductionValidator`（`@Profile("production")` / `@Component`）
- **検証ロジック：** `@PostConstruct` で以下を確認し、いずれか不正なら `IllegalStateException` を投げる
  - `amazia.simulation.fault-injection.enabled = false` であること
  - `amazia.batch.bank-transfer-verification.mode IN ('disabled', 'mock-match', 'production')` であること（`mock-mismatch-rate` を本番で拒否）
- **テスト：** `@SpringBootTest(properties = {"spring.profiles.active=production", "amazia.simulation.fault-injection.enabled=true"})` で `ApplicationContext` ロード失敗を検証

#### 1-4-5. 自動購読対象ロール（G-2 確定）

- **対象ロール：** `admin / senior_admin / eternal_advisor` の 3 種（test-data.sql の `role_permissions` で全権限保持として集約されているロール）
- **CSV 環境変数：** `BATCH_NOTIFICATIONS_AUTO_SUBSCRIBE_ROLES=admin,senior_admin,eternal_advisor`（既定）
- **適用箇所：**
  - Step 1-3 マイグレーションの `INSERT INTO notification_subscriptions ...` の `WHERE r.code IN (:roles)` を CSV 駆動に
  - phase11 側のフック（Step 6-4）で `CreateUserService` / `UpdateUserService` が「管理者相当」を判定する際にも本 CSV を参照
  - 13.0 で要請する全コードパスで `@Value("${amazia.batch.notifications.auto-subscribe-roles}")` 経由（規約 4-1）

### 1-5. 環境変数追加チェックリスト（設計書 §7 / G-2 で 1 変数追加）

Step 1 着手前に以下 19 個の新規環境変数を **3 箇所セット更新**（規約 4-3 / [user memory: env_vars_and_tests]）：

| # | 環境変数 | docker-compose.yml | application.properties | application-test.properties |
|---|---------|:-:|:-:|:-:|
| 1 | `BATCH_ENABLED` | ✅ | ✅ | ✅ |
| 2 | `BATCH_SCHEDULER_ENABLED` | ✅ | ✅ | ✅ |
| 3 | `BATCH_MANUAL_TRIGGER_ENABLED` | ✅ | ✅ | ✅ |
| 4 | `BATCH_DIGEST_ENABLED` | ✅ | ✅ | ✅ |
| 5 | `SIMULATION_FAULT_INJECTION` | ✅ | ✅ | ✅ |
| 6 | `SIM_SALES_RATE` | ✅ | ✅ | ✅ |
| 7 | `SIM_INV_RATE` | ✅ | ✅ | ✅ |
| 8 | `SIM_DELIVERY_RATE` | ✅ | ✅ | ✅ |
| 9 | `BATCH_DAILY_CRON` | ✅ | ✅ | ✅ |
| 10 | `BATCH_MONTHLY_POSTAL_CHECK_CRON` | ✅ | ✅ | ✅ |
| 11 | `BATCH_YEARLY_CRON` | ✅ | ✅ | ✅ |
| 12 | `BATCH_NOTIFICATIONS_SLACK_WEBHOOK_URL` | ✅ | ✅ | ✅ |
| 13 | `BANK_TRANSFER_VERIFICATION_MODE` | ✅ | ✅ | ✅ |
| 14 | `SALES_RECONCILIATION_WAREHOUSES` | ✅ | ✅ | ✅ |
| 15 | `BATCH_RATE_LIMIT_SUPPRESSION_MINUTES` | ✅ | ✅ | ✅ |
| 16 | `BATCH_RATE_LIMIT_DIGEST_ENABLED` | ✅ | ✅ | ✅ |
| 17 | `BATCH_NOTIFICATIONS_AUTO_SUBSCRIBE_ROLES`（**G-2 で追加**） | ✅ | ✅ | ✅ |

> 2026-05-08 Step 1.5 時点で 17 個 × 3 箇所すべて反映済み（§2-6-1）。
> Console 側 `phpunit.xml` への追記は Step 6（Console UI）で必要になった環境変数のみ・同フェーズ内で対応する方針。

J-6 の本番 Validator 起動失敗テストは Step 1.5 で `BatchProductionValidatorTest`（VALID-1 / VALID-2 / 安全組合せ / mock-match 許容）として 1 度通過済み。`@SpringBootTest(properties=...)` ベースの ApplicationContext ロード失敗テストは Step 2 で AbstractBatchJob 等と同居し始める段階で追加する（§2-6-1）。

### 1-6. 完了条件（Step 0 ✅ 完了）

- [x] パッケージ構成（Core / Console）が確定（§1-2 / §1-3）
- [x] r8 派生事項 4 点 + 棚卸し時追加論点 3 点（G-1 / G-2 / G-3）が確定回答とともに記録（§1-4）
- [x] phase15 / phase16 Step 5 と既存実装の整合確認済み（§1-1）
- [x] 環境変数追加チェックリストの対象が 17 個に確定（§1-5）
- [x] 設計書 r8 が G-1 / G-2 / G-3 を取り込んで整合済み（設計書改訂履歴 r8 行参照）

---

## 2. Step 1 — DB マイグレーション

### 2-1. schema.sql 追記（本番 MySQL 向け）

`amazia-core/src/main/resources/schema.sql` 末尾に「フェーズ17: バッチ処理基盤」セクションを追加する。重複実行は `spring.sql.init.continue-on-error=true` で許容。

#### 2-1-1. `batch_executions`（設計書 §5.1）

```sql
-- ============================================================================
-- フェーズ17 Step 1-1: batch_executions（バッチ実行履歴）
-- ============================================================================
CREATE TABLE IF NOT EXISTS batch_executions (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_name        VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,           -- RUNNING / SUCCESS / FAILED / PARTIAL
    started_at      DATETIME     NOT NULL,
    finished_at     DATETIME     NULL,
    target_count    INT          NULL,
    success_count   INT          NULL,
    failure_count   INT          NULL,
    error_summary   TEXT         NULL,
    triggered_by    VARCHAR(50)  NOT NULL,           -- scheduler / manual:user_id=N
    created_at      DATETIME     NOT NULL
);
CREATE INDEX idx_batch_executions_job_started ON batch_executions (job_name, started_at);
CREATE INDEX idx_batch_executions_status      ON batch_executions (status);
```

#### 2-1-2. `console_notifications`（設計書 §5.2）

```sql
-- ============================================================================
-- フェーズ17 Step 1-2: console_notifications（通知センター）
-- ============================================================================
CREATE TABLE IF NOT EXISTS console_notifications (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    level                       VARCHAR(10)  NOT NULL,          -- INFO / WARN / ERROR
    target_subscription_tag     VARCHAR(50)  NOT NULL,
    target_user_id              BIGINT       NULL,
    title                       VARCHAR(200) NOT NULL,
    body                        TEXT         NOT NULL,
    payload_hash                VARCHAR(64)  NOT NULL,          -- M-9: NOT NULL / J-5: SHA-256('no-payload:' + job_name)
    suppressed                  BOOLEAN      NOT NULL DEFAULT FALSE,
    digest_sent_at              DATETIME     NULL,
    read_by_user_id             BIGINT       NULL,
    read_at                     DATETIME     NULL,
    source_job                  VARCHAR(100) NULL,
    source_batch_execution_id   BIGINT       NULL,
    created_at                  DATETIME     NOT NULL,
    CONSTRAINT fk_console_notifications_batch FOREIGN KEY (source_batch_execution_id) REFERENCES batch_executions(id)
);
CREATE INDEX idx_cn_tag_unread        ON console_notifications (target_subscription_tag, read_by_user_id, created_at);
CREATE INDEX idx_cn_user_unread       ON console_notifications (target_user_id, read_by_user_id, created_at);
CREATE INDEX idx_cn_payload_hash      ON console_notifications (payload_hash, created_at);
CREATE INDEX idx_cn_suppressed_digest ON console_notifications (suppressed, digest_sent_at, created_at);
```

#### 2-1-3. `notification_subscriptions`（設計書 §6.2.1）

```sql
-- ============================================================================
-- フェーズ17 Step 1-3: notification_subscriptions
-- ============================================================================
CREATE TABLE IF NOT EXISTS notification_subscriptions (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT UNSIGNED NOT NULL,
    subscription_tag  VARCHAR(50) NOT NULL,
    email_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    in_app_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        DATETIME    NOT NULL,
    updated_at        DATETIME    NOT NULL,
    CONSTRAINT uk_ns_user_tag UNIQUE (user_id, subscription_tag),
    CONSTRAINT fk_ns_user     FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_ns_subscription_tag ON notification_subscriptions (subscription_tag);

-- 初期データ：admin の users 全員に全タグ自動購読（K-3 UPSERT）
-- ※ phase11 側のフックは Step 6 で実装。本マイグレーションでは既存 admin 分のみ補填
INSERT INTO notification_subscriptions (user_id, subscription_tag, email_enabled, in_app_enabled, created_at, updated_at)
SELECT u.id, t.tag, TRUE, TRUE, NOW(), NOW()
  FROM users u
  CROSS JOIN (
       SELECT 'inventory_alerts' AS tag UNION ALL
       SELECT 'sales_alerts'           UNION ALL
       SELECT 'delivery_alerts'        UNION ALL
       SELECT 'postal_alerts'          UNION ALL
       SELECT 'batch_failure'
  ) t
 WHERE u.role_id = (SELECT id FROM roles WHERE name = 'admin')
ON DUPLICATE KEY UPDATE updated_at = NOW();
```

#### 2-1-4. `fault_injection_logs`（設計書 §5.3）

```sql
-- ============================================================================
-- フェーズ17 Step 1-4: fault_injection_logs（本番 INSERT を CHECK で物理拒否）
-- ============================================================================
CREATE TABLE IF NOT EXISTS fault_injection_logs (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    injector_name   VARCHAR(100) NOT NULL,
    triggered_at    DATETIME     NOT NULL,
    triggered_by    VARCHAR(50)  NOT NULL,
    environment     VARCHAR(20)  NOT NULL,
    target_summary  TEXT         NULL,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT chk_fault_logs_no_prod CHECK (environment IN ('dev', 'staging'))
);
CREATE INDEX idx_fil_injector_created ON fault_injection_logs (injector_name, created_at);
```

#### 2-1-5. `monthly_sales_reports` / `yearly_sales_reports`（設計書 §5.4）

```sql
-- ============================================================================
-- フェーズ17 Step 1-5: 売上レポート（月次・年次）
--   R-15 対応：UNIQUE 制約で同月二重 INSERT を防ぎ、UPSERT で再実行可能化
-- ============================================================================
CREATE TABLE IF NOT EXISTS monthly_sales_reports (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year                SMALLINT NOT NULL,
    month               TINYINT  NOT NULL,
    product_id          BIGINT   NULL,
    payment_method_id   BIGINT   NULL,
    shipping_method_id  BIGINT   NULL,
    is_preorder         BOOLEAN  NULL,
    total_amount        BIGINT   NOT NULL,
    total_quantity      INT      NOT NULL,
    created_at          DATETIME NOT NULL,
    CONSTRAINT uk_msr_axes UNIQUE (year, month, product_id, payment_method_id, shipping_method_id, is_preorder)
);

CREATE TABLE IF NOT EXISTS yearly_sales_reports (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year                SMALLINT NOT NULL,
    product_id          BIGINT   NULL,
    payment_method_id   BIGINT   NULL,
    shipping_method_id  BIGINT   NULL,
    is_preorder         BOOLEAN  NULL,
    total_amount        BIGINT   NOT NULL,
    total_quantity      INT      NOT NULL,
    created_at          DATETIME NOT NULL,
    CONSTRAINT uk_ysr_axes UNIQUE (year, product_id, payment_method_id, shipping_method_id, is_preorder)
);
```

#### 2-1-6. `product_sku_prices.is_active` 追加 + `product_sku_scheduled_prices` 新設（設計書 §3.1 ⑥ / §13.5）

```sql
-- ============================================================================
-- フェーズ17 Step 1-6: 価格スケジュール機能
-- ============================================================================
ALTER TABLE product_sku_prices ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_product_sku_prices_active ON product_sku_prices (sku_id, is_active);

CREATE TABLE IF NOT EXISTS product_sku_scheduled_prices (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku_id           BIGINT  NOT NULL,
    scheduled_price  INT     NOT NULL,
    apply_date       DATE    NOT NULL,
    is_pending       BOOLEAN NOT NULL DEFAULT TRUE,
    applied_at       DATETIME NULL,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    CONSTRAINT fk_pssp_sku FOREIGN KEY (sku_id) REFERENCES product_skus(id),
    CONSTRAINT chk_pssp_price_nonneg CHECK (scheduled_price >= 0)
);
-- 「1 SKU につき未済の予約変更は 1 件まで」の擬似ユニーク。MySQL は部分ユニークを直接サポートしないため、
-- アプリ側（RegisterScheduledSkuPriceService）で UPSERT として実装する（規約 1-1）
CREATE INDEX idx_pssp_apply_pending ON product_sku_scheduled_prices (apply_date, is_pending);
CREATE INDEX idx_pssp_sku_pending   ON product_sku_scheduled_prices (sku_id, is_pending);
```

#### 2-1-7. SKU TX への bootstrap INSERT（H-9 / 設計書 §13.2）

```sql
-- ============================================================================
-- フェーズ17 Step 1-7: SKU TX bootstrap 投入（H-9）
--   schema.sql 357-361 行（inventories 初期複製・phase15 RRRR-1）と対になる初期化。
--   既存 admin 環境では INSERT IGNORE により再実行で重複を作らない。
--   実行後、product_sku_stock_transactions WHERE reference_type = 'bootstrap'
--   は SKU 単位で 1 行となる（quantity > 0 の SKU のみ）。
-- ============================================================================
INSERT IGNORE INTO product_sku_stock_transactions
    (sku_id, type, quantity, reference_type, reference_id, created_by_user_id, comment, created_at)
SELECT s.sku_id, 'adjust', s.quantity, 'bootstrap', NULL, NULL,
       '[bootstrap] initial inventory', CURRENT_TIMESTAMP
  FROM product_sku_stocks s
 WHERE s.quantity > 0;
```

> ⚠ schema.sql の `product_sku_stock_transactions` 既存スキーマ確認：phase14 r4 で `reference_type` / `reference_id` / `created_by_user_id` / `comment` カラムは既に追加済（schema.sql 281-284 行）。本 INSERT はそれらカラムへ書き込む。`type` 実値は `application.properties amazia.sales.sku-stock-tx-types.adjust` ＝ `adjust`（G-1 / §1-1-2）に揃え、設計書 r7 想定の `adjustment` は採用しない（schema.sql 内では `@Value` を使えないためリテラル `'adjust'` を直書き）。

### 2-2. JPA Entity の追加・改修

| Entity | 追加 / 改修 | 主要カラム |
|--------|-----------|----------|
| `BatchExecution`（新規） | 追加 | id / jobName / status / startedAt / finishedAt / targetCount / successCount / failureCount / errorSummary / triggeredBy / createdAt |
| `ConsoleNotification`（新規） | 追加 | id / level / targetSubscriptionTag / targetUserId / title / body / payloadHash / suppressed / digestSentAt / readByUserId / readAt / sourceJob / sourceBatchExecutionId / createdAt |
| `NotificationSubscription`（新規） | 追加 | id / userId / subscriptionTag / emailEnabled / inAppEnabled / createdAt / updatedAt |
| `FaultInjectionLog`（新規） | 追加 | id / injectorName / triggeredAt / triggeredBy / environment / targetSummary / createdAt |
| `MonthlySalesReport` / `YearlySalesReport`（新規） | 追加 | 設計書 §5.4 通り |
| `ProductSkuPrice`（既存） | 改修 | `isActive` フィールドを追加（`@Column(name = "is_active", nullable = false)` / 既定 TRUE） |
| `ProductSkuScheduledPrice`（新規） | 追加 | id / skuId / scheduledPrice / applyDate / isPending / appliedAt / createdAt / updatedAt |

### 2-3. Repository の追加

各 Entity に対応する `JpaRepository` を `com.example.{batch,notification,faultinjection,salesreport,scheduledprice}.repository` に追加。検索メソッドは Step 3 〜 6 の必要に応じて宣言する：

```java
// 例：BatchExecutionRepository
List<BatchExecution> findByStatus(String status);
List<BatchExecution> findByStatusAndStartedAtBefore(String status, LocalDateTime threshold);  // OrphanedRunningSweeper

// 例：NotificationSubscriptionRepository
List<NotificationSubscription> findBySubscriptionTagAndEmailEnabledTrue(String tag);

// 例：ConsoleNotificationRepository
Optional<ConsoleNotification> findFirstByPayloadHashAndJobNameOrderByCreatedAtDesc(...);

// 例：ProductSkuScheduledPriceRepository
Optional<ProductSkuScheduledPrice> findFirstBySkuIdAndIsPendingTrue(Long skuId);
List<ProductSkuScheduledPrice> findByApplyDateLessThanEqualAndIsPendingTrue(LocalDate today);
```

### 2-4. テスト

| 層 | 内容 |
|----|------|
| Core JUnit | 各 Entity の `@PrePersist` / `@PreUpdate` / `@Version` 動作確認、Repository の基本クエリ |
| H2 互換 | `application-test.properties` で `schema-locations=` 空のまま、`spring.jpa.hibernate.ddl-auto=create-drop` で全 Entity がスキーマ生成可能 |
| MySQL 互換 | Docker 環境で `docker compose down -v && docker compose up --build` を 1 度実行し、schema.sql 全体（既存 + Step 1 追加分）が **continue-on-error 無し**（`spring.sql.init.continue-on-error=false`）で完走することを確認（カテゴリ9） |

### 2-5. 完了条件（Step 1）
- 上記 6 テーブルが H2 / MySQL 双方で生成され、Repository 単体テストが緑
- `product_sku_prices.is_active` 追加が冪等
- SKU TX bootstrap INSERT が H2（`product_sku_stocks` 既存テストデータあり）で 1 度のみ実行され、再実行で件数が変わらない
- 環境変数追加チェックリスト（設計書 §7）の **Step 1 着手前項目**が全件チェック済み（K-10 / J-6）
- 本番 Validator 起動失敗テストが少なくとも 1 度通過（J-6）

### 2-6. Step 1.5 — 実装後検証（着手済み・2026-05-08）

Step 1 で配布した DB / Entity / Repository の上に、Step 2 着手前に必要な検証を集約する小ステップ。

#### 2-6-1. 実装済み（AI 側）

| 項目 | 反映先 | 備考 |
|------|--------|------|
| 環境変数 17 個の 3 箇所セット更新 | `docker-compose.yml`（amazia-core 環境節）／`application.properties`（フェーズ17 セクション）／`application-test.properties`（フェーズ17 セクション） | 規約 4-3。BATCH_DIGEST_ENABLED が独立（J-1）であることが YAML フォールバック表現から再確認できる |
| Repository 単体テスト 7 本 | `src/test/java/com/example/{batch,notification,faultinjection,salesreport,scheduledprice,sku}/` | save / 既定値反映 / UNIQUE 違反 / CHECK 拒否 / Sweeper 用クエリ抽出 / pending 抽出を最低 1 本ずつ検証 |
| `BatchProductionValidator` 本体 + J-6 ユニットテスト | `com.example.batch.config.BatchProductionValidator` + `BatchProductionValidatorTest`（VALID-1 / VALID-2） | `@Profile("production")` の Bean を直接 new し `validate()` を `ReflectionTestUtils` 経由で呼ぶ。`@SpringBootTest(properties=...)` ベースの ApplicationContext ロード失敗テストは Step 2 で AbstractBatchJob 等が同居し始める段階で追加する |
| Entity 側 CHECK 制約の H2 反映 | `FaultInjectionLog` / `ProductSkuScheduledPrice` に `@org.hibernate.annotations.Check` を付与 | `inventories` の前例と同じ手当。schema.sql の CHECK は MySQL でのみ効くため H2 ではこの注釈で再現する |

#### 2-6-2. 実機検証ログ（2026-05-08 実施・AI 側で実行）

| 項目 | コマンド | 結果 |
|------|---------|------|
| Core JUnit 完走確認 | `cd amazia-core && mvn test` | **383 件すべて緑（Tests run: 383, Failures: 0, Errors: 0, Skipped: 0）**。Step 1.5 で追加した 25 件（BatchExecution / ConsoleNotification / NotificationSubscription / FaultInjectionLog / SalesReport / ProductSkuScheduledPrice / ProductSkuPriceIsActive / BatchProductionValidator）も全緑 |
| 予約語衝突修正 | `monthly_sales_reports` / `yearly_sales_reports` の `year` / `month` カラムは H2 では予約語のため、Entity 側 `@Column(name = "\`year\`")` / schema.sql 側もバッククォートに揃えた | H2 起動失敗 → 修正後緑化 |
| MySQL 実機 schema.sql 完走確認 | `docker compose -f docker-compose.local.yml down -v && docker compose -f docker-compose.local.yml up --build -d mysql amazia-core amazia-console` | **完走**。`amazia-core: Started Main in 9.196 seconds`。`amazia-console: Server running on [http://0.0.0.0:8000]`（Laravel migrations 完走で `users` 等を生成） |
| 7 テーブル生成確認 | `docker exec amazia-mysql mysql ... SHOW TABLES LIKE ...` | **6/7 生成成功**：`batch_executions` / `console_notifications` / `notification_subscriptions` / `fault_injection_logs` / `monthly_sales_reports` / `yearly_sales_reports`。`product_sku_scheduled_prices` のみ FK 先 `product_skus` がローカル DB に存在しないため未生成 |
| schema.sql 冪等性確認 | `docker restart amazia-core` | 再起動 7.891 秒で完走、`continue-on-error` で潰されたエラーログなし、6 テーブルすべて 0 件のまま（重複行なし） |
| 月次レポートのカラム検証 | `DESCRIBE monthly_sales_reports` | `year SMALLINT NOT NULL` / `month TINYINT NOT NULL` がそのまま生成（MySQL 側ではバッククォートなしでも予約語として扱われない）|

#### 2-6-3. Step 2 着手前に **本番 EC2 で**実施が必要な確認

ローカルの新規 MySQL ボリュームでは **`products` / `product_skus` / `inventories` 等の Core 業務テーブルが存在しない**（schema.sql では作らず、本番では従来からのデプロイで実体化済みのため）。そのため以下 2 点はローカルで再現できず、本番 EC2 への反映時にあわせて確認する：

| 項目 | 本番での確認手順 |
|------|----------------|
| `product_sku_scheduled_prices` の生成 | デプロイ後に `mysql -e "SHOW TABLES LIKE 'product_sku_scheduled_prices';"` が 1 行返ること |
| SKU TX bootstrap の重複検証（H-9 / J-7） | デプロイ後に `SELECT COUNT(*) FROM product_sku_stock_transactions WHERE reference_type='bootstrap';` を 2 回測り、2 回目で件数が変わらないことを確認。Core 再起動を 1 度挟んで再測定する |
| `is_active` 列の追加 | `DESCRIBE product_sku_prices;` で `is_active` 行が `tinyint(1) NOT NULL DEFAULT 1` で出ること |

これら 3 点は Step 10（ドキュメント反映 / 本番デプロイ）で消化する。

---

## 3. Step 2 — バッチ共通基盤 ✅ 完了（2026-05-08）

### 3-0. 実装ログ（2026-05-08）

| 種別 | 反映先 | 備考 |
|------|--------|------|
| 共通基盤クラス 7 種 新設 | `com.example.batch.config.{AbstractBatchJob, BatchJobLockRegistry, BatchRetryClassifier, BatchResult, OnDemandJob}` / `com.example.batch.service.{BatchExecutionRecorder, OrphanedRunningSweeper}` / `com.example.batch.controller.BatchManualTriggerController` | 設計書 §3 共通制御 1〜6 / R-6 / R-7 / M-1 / M-4 を満たす |
| `BatchRetryClassifier` のメール例外判定方式 | `MailSendException` 直接参照は `spring-context-support` 依存追加が必要なため、FQCN 文字列照合（`org.springframework.mail.MailSendException`）に変更 | Step 7（SES テンプレート）で依存追加が起きた段階で `instanceof` に戻す。動作仕様は同等 |
| `InventoryAdjustmentService` 新設（H-4） | `com.example.inventory.service.InventoryAdjustmentService` | 設計書 H-4 の符号契約。SKU TX → SKU stock → `inventories` の三者同期を 1 メソッドで担保。`InventorySyncService.applyDelta(productId, warehouseId, delta)` を内部で `default-warehouse-id` 解決して呼ぶ |
| Step 2 テスト 10 件 追加 | `AbstractBatchJobTest`(3) / `BatchJobLockRegistryTest`(2) / `OrphanedRunningSweeperTest`(1) / `BatchManualTriggerControllerTest$Enabled`(2) + `$Disabled`(1) / `InventoryAdjustmentServiceTest`(2) / `BatchProductionValidatorContextLoadTest`(2) | ABJ-1/2/3・LOCK-1/2・SWEEP-1・MANUAL-1/2・ADJ-1/2・VALID-1/2 を網羅。`Map<String,OnDemandJob>` の Bean 名解決検証も含む |
| ABJ-2 のテスト例外型変更 | `MailSendException` → `ResourceAccessException`（同じく `BatchRetryClassifier.shouldRetry=true` の I/O 例外） | 依存最小化のため。リトライ回数 (3) と `error_summary` への型名反映の検証は同等 |
| 全件回帰確認 | `mvn test` | **393 件すべて緑（Failures: 0, Errors: 0, Skipped: 0）**。Step 1.5 完了時の 383 件 + Step 2 で追加した 10 件で計 393 件 |

## 3. Step 2 — バッチ共通基盤（実装計画）

### 3-1. `AbstractBatchJob`（テンプレートメソッド）

```java
@Component
public abstract class AbstractBatchJob {

    @Autowired private BatchExecutionRecorder recorder;
    @Autowired private BatchJobLockRegistry lockRegistry;
    @Autowired private BatchRetryClassifier retryClassifier;

    public final void run(String triggeredBy) {
        if (!lockRegistry.tryAcquire(jobName())) {
            return;  // 多重起動防止：黙ってスキップ（R-7 / N-8）
        }
        BatchExecution exec = recorder.start(jobName(), triggeredBy);
        try {
            BatchResult result = runWithRetry();
            recorder.success(exec, result);
        } catch (Exception e) {
            recorder.failure(exec, e);
            // 通知は dispatchFailureNotification(e, exec) で 6.2 経由に送る
        } finally {
            lockRegistry.release(jobName());
        }
    }

    protected abstract String jobName();
    protected abstract BatchResult execute() throws Exception;

    private BatchResult runWithRetry() throws Exception {
        int attempt = 0;
        long delayMs = 1000;
        while (true) {
            try { return execute(); }
            catch (Exception e) {
                if (!retryClassifier.shouldRetry(e) || ++attempt >= 3) throw e;
                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }
    }
}
```

### 3-2. `BatchExecutionRecorder`（REQUIRES_NEW）

`@Transactional(propagation = REQUIRES_NEW)` で `batch_executions` への INSERT/UPDATE を独立トランザクション化（設計書 §3 共通制御 1）。本体ロールバック時もステータス更新は残す。

### 3-3. `BatchJobLockRegistry`（JVM 内 ConcurrentHashMap ロック）

```java
@Component
public class BatchJobLockRegistry {
    private final ConcurrentHashMap<String, AtomicBoolean> locks = new ConcurrentHashMap<>();

    public boolean tryAcquire(String jobName) {
        return locks.computeIfAbsent(jobName, k -> new AtomicBoolean(false))
                    .compareAndSet(false, true);
    }
    public void release(String jobName) {
        locks.computeIfPresent(jobName, (k, v) -> { v.set(false); return v; });
    }
}
```

### 3-4. `OrphanedRunningSweeper`（起動時クリーンアップ）

`ApplicationReadyEvent` のリスナーで、`status = RUNNING` かつ `started_at < NOW() - 24h` のレコードを `FAILED, error_summary='[recovery] orphaned by JVM restart'` に強制遷移（設計書 §3 共通制御 3）。

### 3-5. `BatchRetryClassifier`（リトライ可能例外判定）

設計書 §3 共通制御 4 のリスト：
- `MailSendException` / `SocketTimeoutException` / `ResourceAccessException`
- 5xx を含む `RestClientResponseException`
- `TransientDataAccessException` / `CannotAcquireLockException`

それ以外（`DataIntegrityViolationException` / `IllegalStateException` / 業務例外）は `shouldRetry() = false` で即 FAILED。

### 3-6. `BatchManualTriggerController`（手動起動 API ガード／M-1）

```java
@RestController
@RequestMapping("/api/console/batch")
public class BatchManualTriggerController {

    @Value("${amazia.batch.manual-trigger-enabled:true}")
    private boolean manualEnabled;

    @Autowired
    private Map<String, OnDemandJob> onDemandJobs;  // Bean 名 = jobName

    @PostMapping("/{jobName}/run")
    public ResponseEntity<?> trigger(@PathVariable String jobName, @AuthenticationPrincipal Principal user) {
        if (!manualEnabled) return ResponseEntity.status(503).body(...);
        OnDemandJob job = onDemandJobs.get(jobName);
        if (job == null) return ResponseEntity.status(404).body(...);  // 本番の TriggerFaultInjectionJob が引っかかる
        job.run("manual:user_id=" + ...);
        return ResponseEntity.ok().build();
    }
}
```

`TriggerFaultInjectionJob` は `@Profile("!production")` で本番には Bean 登録されないため、本番では `onDemandJobs.get("TriggerFaultInjectionJob") == null` で 404 を返す（M-4）。

### 3-7. `InventoryAdjustmentService`（H-4 で新設）

```java
@Service
public class InventoryAdjustmentService {

    @Autowired private ProductSkuStockTransactionRepository txRepo;
    @Autowired private ProductSkuStockRepository skuStockRepo;
    @Autowired private InventorySyncService inventorySync;

    @Value("${amazia.sales.sku-stock-tx-types.adjust}")
    private String adjustTypeValue;  // 実値 'adjust'（G-1）

    @Transactional
    public ProductSkuStockTransaction adjust(long skuId, int quantity, String referenceType,
                                             Long referenceId, Long userId, String comment) {
        validate(quantity);                        // type='adjust' は != 0 を要求

        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(skuId);
        tx.setType(adjustTypeValue);
        tx.setQuantity(quantity);
        tx.setReferenceType(referenceType);
        tx.setReferenceId(referenceId);
        tx.setCreatedByUserId(userId);
        tx.setComment(comment);
        ProductSkuStockTransaction saved = txRepo.save(tx);

        // SKU 在庫の同期更新（既存 InventorySyncService に委譲）
        inventorySync.applyDeltaForSku(skuId, quantity);
        return saved;
    }

    private void validate(int quantity) {
        if (quantity == 0) {
            throw new IllegalArgumentException("adjust quantity must not be 0");
        }
    }
}
```

### 3-8. `BatchProductionValidator`

`@Profile("production")` のクラスで、起動時に `application.yml` 値を読み、以下のいずれかなら `IllegalStateException` を投げる：
- `amazia.simulation.fault-injection.enabled = true`
- `amazia.batch.bank-transfer-verification.mode = mock-mismatch-rate`

### 3-9. テスト

| ID | 内容 |
|----|------|
| ABJ-1 | `AbstractBatchJob` の正常系：execute 成功 → `batch_executions.status = SUCCESS` |
| ABJ-2 | execute 内で `MailSendException` → 3 回リトライ後 FAILED |
| ABJ-3 | execute 内で `DataIntegrityViolationException` → 1 回で FAILED（リトライしない） |
| LOCK-1 | 2 スレッド `CountDownLatch` 同時起動 → 1 スレッドのみが新規 RUNNING を作成。先行スレッド完了後の後続起動は通常通り動作（N-8） |
| LOCK-2 | 先行スレッドが例外を投げても `finally` でロックが解放され、後続起動が成立 |
| SWEEP-1 | `started_at < NOW() - 24h` の RUNNING が `ApplicationReadyEvent` 後に FAILED に遷移 |
| MANUAL-1 | `manualEnabled = false` → 503 / `manualEnabled = true` → 200 |
| MANUAL-2 | 存在しない jobName → 404（本番 TriggerFaultInjectionJob の検証は Step 5） |
| ADJ-1 | `InventoryAdjustmentService.adjust(skuId, +5, ...)` → SKU TX に `+5` で記録、`product_sku_stocks.quantity += 5`、`inventories.quantity += 5` |
| ADJ-2 | `quantity = 0` で `IllegalArgumentException`（H-4 符号契約） |
| VALID-1 | `production` プロファイル + `SIMULATION_FAULT_INJECTION=true` で `ApplicationContext` ロード失敗（J-6） |
| VALID-2 | `production` プロファイル + `BANK_TRANSFER_VERIFICATION_MODE=mock-mismatch-rate` で `ApplicationContext` ロード失敗（J-6） |

### 3-10. 完了条件（Step 2）
- `AbstractBatchJob` を継承する空ジョブ（`NoopJob`）でテストが緑
- `BatchManualTriggerController` の 503 / 404 / 200 動作が緑
- `InventoryAdjustmentService` 単体テスト緑（符号契約含む）
- `BatchProductionValidator` 起動失敗テスト緑

---

## 4. Step 3 — 日次ジョブ 6 本 ✅ 完了（2026-05-08：日次 6 本のみ、オンデマンド 3 本は別ステップ送り）

### 4-0. 実装ログ（2026-05-08）

| 種別 | 反映先 | 備考 |
|------|--------|------|
| `@EnableScheduling` 有効化 | `Main.java` | Step 3 以降のすべての `@Scheduled` を有効化 |
| 通知スタブ | `com.example.notification.service.BatchAlertNotifier` | Console 通知（`console_notifications`）への INSERT のみ実装。`payload_hash` は J-5 / M-9 仕様で生成（`tag:identity` か `no-payload:job_name` の SHA-256）。SES 送信・購読者解決は Step 6/7 で本実装 |
| 日次ジョブ 6 本 | `com.example.batch.job.{InventoryConsistencyCheckJob, PreorderStatusRefreshJob, SalesReconciliationJob, DeliveryStatusAdvanceJob, SessionAndTokenSweepJob, ApplyScheduledPricesJob}` | 各 Job は `AbstractBatchJob` を継承し `@Scheduled(cron=…/zone=…)` + `@ConditionalOnProperty(amazia.batch.scheduler-enabled, matchIfMissing=true)` を個別付与（M-2）。`ApplyScheduledPricesJob` は `OnDemandJob` も実装し Bean 名 = jobName で `BatchManualTriggerController` 経由起動可能 |
| 振込確認モック | `com.example.batch.service.{BankTransferMockClient, RandomGeneratorAdapter}` | mode 切替（disabled / mock-match / mock-mismatch-rate）と乱数ポイント（R-14）を実装 |
| Repository 拡張 | `InventoryConsistencyCheckRepository`（新設）/ `SalesReconciliationRepository`（新設）/ `DeliveryRepository.findByShippingStatusIdAndDeliveredDateIsNullAndScheduledDateBefore`（追加）/ `CustomerPasswordResetTokenRepository.deleteExpiredOrUsed`（追加）/ `ProductSkuPriceRepository.deactivateActive`（追加） | 設計書 §3.1 の 2 段ロールアップ SQL は `WITH expected AS (...), current_inv AS (...)` の native query で実装。H2 / MySQL 両対応 |
| 価格切替 Service | `com.example.scheduledprice.service.ApplyScheduledPriceService` | 1 SKU 分の「現行非アクティブ化 → 新価格 INSERT → 予約レコード適用済化」を 1 トランザクションで実行（規約 1-1 で Service 層に集約） |
| AOP proxy 由来の落とし穴対応 | 各 Job の `execute()` から `@Transactional` を削除 | `AbstractBatchJob` の `@Autowired` フィールドが CGLib proxy 経由で null になる事故を回避。リポジトリ側で `@Transactional` を持っているメソッドを使うか、REQUIRES_NEW の Recorder/Notifier に委譲する構造で代替 |
| Step 3 テスト 18 件 追加 | `InventoryConsistencyCheckJobTest`(2) / `PreorderStatusRefreshJobTest`(2) / `SalesReconciliationJobTest`(1) / `BankTransferMockClientTest`(4) / `DeliveryStatusAdvanceJobTest`(2) / `SessionAndTokenSweepJobTest`(2) / `ApplyScheduledPricesJobTest`(3) / `BatchAlertNotifierTest`(2) | 計画書 §4-1〜§4-6 の TDD 設計に沿って実装。`bank-transfer-verification.mode=disabled` 既定の前提で Job テストを書き、mode 切替検証は `BankTransferMockClientTest` に集約 |
| 全件回帰確認 | `mvn test` | **411 件すべて緑（Failures: 0, Errors: 0, Skipped: 0）**。Step 2 完了時 393 件 + Step 3 で追加 18 件 |

### 4-0-1. スコープ外（後続フェーズ送り）

- **Step 3-7 オンデマンド 3 ジョブ**（`RebuildInventoriesJob` / `BootstrapInventoryAdjustmentJob` / `RecalculateDeliveryScheduleJob`）はユーザー判断で本ステップから切り出し。後続ステップで実装する
- **SES 実送信** / **購読者解決アルゴリズム** / **ダイジェスト送出** は Step 6 / Step 7 で本実装。本ステップでは Console 通知のみ INSERT
- **Spring Cache 連携**（`PreorderStatusRefreshJob` のキャッシュ無効化）は本フェーズ未導入のため抽出件数の保持にとどめる
- 設計書 §4-8 完了条件のうち「`cron` 表現 / `BATCH_SCHEDULER_ENABLED=false` で全 6 ジョブ Bean 非登録」の検証は test プロファイル既定値が `scheduler-enabled=false` であり Bean 非登録になることを暗黙に確認済み（テスト時は `@SpringBootTest(properties="amazia.batch.scheduler-enabled=true")` で Bean 化して検証）

## 4. Step 3 — 日次ジョブ 6 本（実装計画）

`@Scheduled(cron = "${amazia.batch.daily.cron}", zone = "${amazia.batch.timezone}")` で 03:30 JST 起動。各ジョブは `AbstractBatchJob` を継承。各クラスに `@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled", havingValue = "true", matchIfMissing = true)` を付与（M-2）。

### 4-1. Step 3-1: `InventoryConsistencyCheckJob`（設計書 §3.1 ① / H-2）

**SQL（2 段ロールアップ）：**

```java
@Query(nativeQuery = true, value = """
WITH expected AS (
  SELECT s.product_id,
         COALESCE(SUM(t.quantity), 0) AS expected_qty
    FROM product_skus s
    LEFT JOIN product_sku_stock_transactions t ON t.sku_id = s.id
   GROUP BY s.product_id
), current_inv AS (
  SELECT i.product_id,
         COALESCE(SUM(i.quantity), 0) AS current_qty
    FROM inventories i
   WHERE i.warehouse_id IN (:warehouseIds)
   GROUP BY i.product_id
)
SELECT e.product_id, COALESCE(c.current_qty, 0) AS current_qty, e.expected_qty
  FROM expected e
  LEFT JOIN current_inv c ON c.product_id = e.product_id
 WHERE COALESCE(c.current_qty, 0) <> e.expected_qty
""")
List<InventoryInconsistencyRow> findInconsistencies(@Param("warehouseIds") List<Long> warehouseIds);
```

**通知：**
- `inventory_alerts` 購読者へ SES + Console 通知（`payload_hash = SHA-256("inventory_alerts:product_id=N")`）
- `operation_logs` に `action='inventory_inconsistency_detected', screen_name='BatchScheduler', comment='[inventory_check] product_id=N expected=X actual=Y'` を 1 件ずつ記録

**TDD（K-8 / M-8 を含む）：**
- 整合データで不整合 0 件
- 1 商品のみ `inventories.quantity` を + 1 ずらす → 1 件検知
- 商品ロールアップ後の `inventories.quantity = 0` かつ SKU TX なし → 検知できない（構造的限界として正常動作）

### 4-2. Step 3-2: `PreorderStatusRefreshJob`（設計書 §3.1 ②）

**処理：** `public_date` / `release_date` / `preorder_start_date` が `CURRENT_DATE` と一致する `products.id` を抽出し、Spring Cache の `@CacheEvict` を発火。マスタ更新は行わない（YAGNI）。

**通知：** 失敗時のみ `batch_failure` 購読者。

### 4-3. Step 3-3: `SalesReconciliationJob`（設計書 §3.1 ③ / R-3）

**SQL（倉庫合算）：** 設計書 §3.1 ③ の SQL をそのまま採用。`@Value("${amazia.batch.sales-reconciliation.target-warehouse-ids}")` で CSV 読込。

**振込確認：** `BankTransferMockClient.verify(mode)` を呼び、mode が `disabled` ならスキップ。`mock-mismatch-rate` は `RandomGeneratorAdapter`（R-14 候補）で確率発火。

**通知：**
- 在庫照合不整合 → `inventory_alerts`
- 振込不整合 → `sales_alerts`

**TDD：** N-6 マトリクス全 6 通り。`mode=mock-mismatch-rate ＋ SIMULATION_FAULT_INJECTION=false` は起動時 Validator 失敗（Step 2 で確認済）。

### 4-4. Step 3-4: `DeliveryStatusAdvanceJob`（設計書 §3.1 ④）

- `SHIPPED` かつ `delivered_date IS NULL` かつ `scheduled_date < TODAY - 7` → 遅延候補抽出（**遷移はしない**）
- `delivery_alerts` 購読者へ通知
- メール文面は `config/mail/batch_delivery_delay.yml` で管理

**TDD：** 自動遷移を**しない**ことを明示テスト（`@Test void doesNotAdvancePendingToShipped()`）。

### 4-5. Step 3-5: `SessionAndTokenSweepJob`（設計書 §3.1 ⑤）

```sql
DELETE FROM market_sessions WHERE expires_at < NOW();
DELETE FROM market_customers_password_reset_tokens WHERE expires_at < NOW() OR used = TRUE;
```

通知なし（CloudWatch Logs に件数のみ）。

### 4-6. Step 3-6: `ApplyScheduledPricesJob`（設計書 §3.1 ⑥／r7）

**処理（1 トランザクション per SKU）：**

```java
@Transactional
public void applyOne(ProductSkuScheduledPrice scheduled) {
    // 2-1. 現行価格を非アクティブ化
    skuPriceRepo.deactivateActive(scheduled.getSkuId(),
            scheduled.getApplyDate().minusDays(1));

    // 2-2. 新価格を現行として INSERT
    ProductSkuPrice newPrice = new ProductSkuPrice();
    newPrice.setSkuId(scheduled.getSkuId());
    newPrice.setPrice(scheduled.getScheduledPrice());
    newPrice.setStartDate(scheduled.getApplyDate());
    newPrice.setEndDate(null);
    newPrice.setActive(true);
    skuPriceRepo.save(newPrice);

    // 2-3. 予約レコードを適用済みに
    scheduled.setIsPending(false);
    scheduled.setAppliedAt(LocalDateTime.now());
    scheduledRepo.save(scheduled);
}
```

`apply_date <= TODAY` かつ `is_pending = TRUE` の全件をループ。冪等性は `is_pending` フラグで担保。

**手動起動 API：** `POST /api/console/batch/ApplyScheduledPricesJob/run`（admin 限定）。

**TDD：**
- 未来日 `apply_date` で `is_pending = TRUE` を 1 件作成 → 翌日のバッチで現行価格に昇格
- `apply_date = TODAY` を 1 件 → 当日のバッチで適用
- 同 SKU で `is_pending = TRUE` が既に存在する状態で `RegisterScheduledSkuPriceService.set()` → UPSERT で 1 件のみ
- 2 度連続実行 → 2 度目は `is_pending = FALSE` で対象 0 件

### 4-7. Step 3-7（オンデマンド）: `RebuildInventoriesJob` / `BootstrapInventoryAdjustmentJob` / `RecalculateDeliveryScheduleJob`

設計書 §3.4 通り。`BatchManualTriggerController` 経由で admin が起動。

**`RebuildInventoriesJob`（H-5）:**
```sql
-- 倉庫 1 想定で再構築（複数倉庫運用時は分配ロジックを Step 3 で再定義）
UPDATE inventories i
   SET i.quantity = (
       SELECT COALESCE(SUM(t.quantity), 0)
         FROM product_skus s
         JOIN product_sku_stock_transactions t ON t.sku_id = s.id
        WHERE s.product_id = i.product_id
       )
 WHERE i.warehouse_id = 1;
```

**`BootstrapInventoryAdjustmentJob`（H-6）:**
```java
@Transactional
public void run() {
    List<ProductSkuStock> targets = skuStockRepo.findByQuantityGreaterThan(0);
    for (ProductSkuStock s : targets) {
        boolean alreadyBootstrapped = txRepo.existsBySkuIdAndReferenceType(s.getSkuId(), "bootstrap");
        if (alreadyBootstrapped) continue;  // 冪等性チェック（J-7）
        inventoryAdjustment.adjust(s.getSkuId(), s.getQuantity(),
                "bootstrap", null, null, "[bootstrap] initial inventory");
    }
}
```

### 4-8. 完了条件（Step 3）
- 6 ジョブ + オンデマンド 3 ジョブのすべての TDD ケースが緑
- `cron = "0 30 3 * * *"` の `@SchedulingConfigurer` テストで JST 起動を確認
- `BATCH_SCHEDULER_ENABLED=false` で全 6 ジョブの Bean が非登録になることを `@SpringBootTest` で確認（M-2）

---

## 5. Step 4 — 月次・年次ジョブ ✅ 完了（2026-05-08）

### 5-0. 実装ログ（2026-05-08）

| 区分 | 概要 |
|------|------|
| 追加スキーマ | `operation_logs_archive` / `console_notifications_archive` を `schema.sql` に追加（PK ＋ 必要最低限の Index 1 本ずつ）。`ops/healthcheck/required_tables.txt` も更新 |
| 追加 Entity / Repository | `OperationLogArchive` / `ConsoleNotificationArchive` ＋ それぞれの `JpaRepository`、`SalesAggregationRepository`（4 軸 + 総合計の UNION ネイティブクエリ）、`MonthlySalesReportRepository#findByAxes` / `YearlySalesReportRepository#findByAxes`（NULL 軸対応 UPSERT 検索）、`PostalAddressRepository#findMaxUpdatedAt`、`OperationLogRepository#findByCreatedAtBefore`、`ConsoleNotificationRepository#findArchiveCandidates` |
| 追加 Job | Step 4-1 `PostalAddressIntegrityCheckJob` / 4-2 `MonthlySalesReportJob` / 4-3 `YearlySalesReportJob` / 4-4 `OperationLogArchiveJob` / 4-5 `ConsoleNotificationsArchiveJob` |
| 追加共通 Bean | `BatchClockConfig`（`Clock` Bean ／ テスト容易性確保）、`YamlPropertySourceFactory`（将来の YAML プロパティソース用に新設・本フェーズでは未使用） |
| 設定 | `application.properties` / `application-test.properties` に `amazia.batch.postal-check.sample-codes` を追加（CSV）。月次売上レポートの cron は `amazia.batch.monthly.postal-check-cron` を **流用**（同 04:30 JST に直列実行）。新規環境変数は追加していない |
| TDD | 5 ファイル / 14 ケースすべて緑（`PostalAddressIntegrityCheckJobTest` / `MonthlySalesReportJobTest` / `YearlySalesReportJobTest` / `OperationLogArchiveJobTest` / `ConsoleNotificationsArchiveJobTest`）。R-15 冪等性は MSR_3 / YSR_3 で検証 |
| バッチ全体回帰 | `mvn -Dtest='com.example.batch.*Test' test` で 45 件すべて緑 |

> 月次・年次ジョブは時刻依存テストを避けるため、各ジョブに `Clock` を注入し、テストでは
> `aggregateAndPersist(YearMonth)` / `aggregateAndPersist(short)` / `archiveBefore(threshold)`
> / `archiveAt(now)` を直接呼ぶ形に分離した（規約 4-1 と整合）。

### 5-0. Step 4-0: `PostalCsvImportJob`（月次／設計書 §3.2 ⓪／2026-05-08 追加）

> **追加経緯：** 設計書 r8 までは「取込本体は phase13 で 03:00 JST に @Scheduled 実装済」を前提にしていたが、phase13 の実体は `ApplicationRunner` ベースの手動コマンド起動のみで、`@Scheduled` 化されていなかった。phase17 で取込結果の整合性チェック（Step 4-1）が成立する前提を満たすため、本ジョブで取込本体も定期化する。

`@Scheduled(cron = "${amazia.batch.monthly.postal-import-cron}")` で毎月 1 日 03:00 JST。

- 既存 `ImportPostalCsvService.execute()` を呼び出すだけのシン Job 実装（リトライ・バックオフは Service が担当）
- `batch_executions` には取込件数を `target_count` / `success_count` に記録、例外時は `FAILED` + `error_summary`
- 手動起動経路（`--import-postal-csv`）は本番事故時のリカバリ救済として併存

#### 環境変数（規約 4-3 セット更新）
- `BATCH_MONTHLY_POSTAL_IMPORT_CRON`（既定 `0 0 3 1 * *`）を `application.properties` / `application-test.properties` / `docker-compose.yml` の 3 点セットで追加

#### TDD ケース（PostalCsvImportJobTest）
- POSTAL_IMPORT_1 正常系：`ImportPostalCsvService.execute()` を 1 回呼び、件数を `batch_executions.target/success_count` に記録
- POSTAL_IMPORT_2 異常系：Service が例外を投げると `status=FAILED` + `error_summary` に例外メッセージ
- POSTAL_IMPORT_3 取込 0 件：`status=SUCCESS, target_count=0` で記録（後段 PostalAddressIntegrityCheckJob が件数閾値で WARN を出す責務）

### 5-1. Step 4-1: `PostalAddressIntegrityCheckJob`（月次／設計書 §3.2 ①）

`@Scheduled(cron = "${amazia.batch.monthly.postal-check-cron}")` で毎月 1 日 04:30 JST。
**前提：** Step 4-0 の `PostalCsvImportJob` が同日 03:00 JST に取込本体を実行済。

- 件数チェック：`postal_addresses` の総件数が直近 12 ヶ月の中央値 ± 5%
- `MAX(updated_at)` が当日以内
- サンプル郵便番号引き：`config/batch/postal_sample_codes.yml`（`100-0001` / `530-0001` 等）

NG 時は `postal_alerts` 購読者へ通知。自動ロールバックなし。

### 5-2. Step 4-2: `MonthlySalesReportJob`（月次／設計書 §3.2 ②）

**集計：**
- 商品別 / 決済方法別 / 配送方法別 / 予約 vs 通常 の 4 軸
- NULL 軸を「総合計」として併記（R-11 r9 候補での分割化までは設計書通り）
- UPSERT で再実行可能（R-15）

**通知：** 完了通知を Console 画面 URL 付きで送信（`sales_alerts`）。

### 5-3. Step 4-3: `YearlySalesReportJob`（年次／設計書 §3.3 ①）

`@Scheduled(cron = "${amazia.batch.yearly.cron}")` で毎年 1 月 1 日 05:00 JST。

`monthly_sales_reports` を年単位で集計し `yearly_sales_reports` に投入。

### 5-4. Step 4-4: `OperationLogArchiveJob`（年次／設計書 §3.3 ②）

`operation_logs` から 1 年超のレコードを `operation_logs_archive` に移送。INSERT → DELETE を 1 トランザクション。

> ⚠ `operation_logs_archive` テーブルが既存に無ければ Step 1 で追加（PK + 最低限のインデックス 1 本）。

### 5-5. Step 4-5: `ConsoleNotificationsArchiveJob`（年次／J-2 / r6 で新設）

設計書 §3.3 ③ のアーカイブ条件をそのまま実装：
- 既読から 1 年経過
- 抑制ダイジェスト送出済から 1 年経過
- 無条件 1 年経過

### 5-6. 完了条件（Step 4）
- 月次・年次 4 ジョブの TDD 緑
- UPSERT による同月二重 INSERT 防止が確認済（R-15）

---

## 6. Step 5 — フォルトインジェクション

### 6-1. Step 5-1: `FaultInjectionLog` 投入 Service

`FaultInjectionLogger`（共通）：environment 値は `Environment.getActiveProfiles()` から導出（`prod` 以外を CHECK 制約に通す）。

### 6-2. Step 5-2: `SalesMismatchInjector`（@Profile("!production")）

`BankTransferMockClient.verify()` の戻り値層のみ書き換え。DB は変えない。

### 6-3. Step 5-3: `InventoryMismatchInjector`（H-7）

```java
@Profile("!production")
@Component
public class InventoryMismatchInjector {

    @Autowired private InventoryAdjustmentService adjustment;
    @Autowired private RandomGeneratorAdapter random;
    @Autowired private FaultInjectionLogger logger;

    public void inject(long productId) {
        if (!shouldFire(random)) return;
        Long skuId = pickFirstSkuOf(productId);
        int delta = random.nextIntBetween(-3, 3);
        if (delta == 0) delta = 1;  // 0 は契約違反
        adjustment.adjust(skuId, delta, "fault_injection", null, null,
                "[fault_injection][inventory] simulated drift");
        logger.log("InventoryMismatchInjector", ...);
    }
}
```

### 6-4. Step 5-4: `DeliveryTroubleInjector`（H-7 / R-2）

- 対象：`shipping_status_id = PENDING` の `deliveries`
- 遷移：`PENDING → CANCELED / DELIVERY_FAILED / RESCHEDULED`（Repository 直接呼出でバリデーションバイパス。**この 1 ジョブのみに許容**）
- 補償 SKU TX：`type='adjustment', sku_id=sales.sku_id, quantity=+1, reference_type='fault_injection', reference_id=sales.id, comment='[fault_injection][delivery][quantity_dummy] {status} simulation (sales_id=N)'`

### 6-5. Step 5-5: `TriggerFaultInjectionJob`（@Profile("!production") / M-4）

オンデマンドで 1 回だけ各 Injector を呼び出す。本番では Bean 自体が DI に登録されないため、`POST /api/console/batch/TriggerFaultInjectionJob/run` が 404 を返す。

### 6-6. 完了条件（Step 5）
- ステージング環境で `SIMULATION_FAULT_INJECTION=true` 起動 → 各 `*-rate` で発火
- 本番プロファイル起動時に Injector / `TriggerFaultInjectionJob` の Bean 不在を検証（`getBean()` で `NoSuchBeanDefinitionException`）
- `fault_injection_logs.environment='production'` の INSERT を直接 SQL で試して CHECK で拒否されることを検証
- 補償 SKU TX が `[fault_injection][...]` 接頭辞で記録されることを検証（H-7）

---

## 7. Step 5.5 — 価格スケジュール Core API + Console Pass-through

### 7-1. Step 5.5-1: Core API（設計書 §13.5.1）

| メソッド | パス | Service / Controller |
|----------|------|---------------------|
| GET | `/api/skus/{id}/prices` | 既存 `GetProductSkuPriceService` を改修して `is_active = TRUE` の 1 件のみ返す |
| POST | `/api/skus/{id}/prices` | 既存 `CreateProductSkuPriceService` を改修して「既存 active を `is_active = FALSE` に降格 → 新規 INSERT を `is_active = TRUE`」のトランザクション処理に置換 |
| GET | `/api/skus/{id}/scheduled-price` | `GetScheduledSkuPriceService` 新設。`is_pending = TRUE` の 1 件 or 204 |
| PUT | `/api/skus/{id}/scheduled-price` | `RegisterScheduledSkuPriceService` 新設（UPSERT・`apply_date >= today` バリデーション） |
| DELETE | `/api/skus/{id}/scheduled-price` | `DeleteScheduledSkuPriceService` 新設（`is_pending = TRUE` を物理削除） |
| GET | `/api/skus/{id}/prices/history` | `ListSkuPriceHistoryService` 新設（任意。`start_date DESC`） |

### 7-2. Step 5.5-2: Console Pass-through（設計書 §13.5.2）

`amazia-console/app/ScheduledPrice/Controller/` 配下に Pass-through 5 本。ルート定義は `routes/api/Sku.php` に集約（規約 2-1 補足4）。Phase16 Step 5 で確立した SKU API ルーティング規約を踏襲。

### 7-3. テスト
- Core JUnit：`is_active` 切替の冪等性、`apply_date < today` で 422、UPSERT の挙動
- Console PHPUnit：Pass-through 各種

---

## 8. Step 6 — Console UI（バッチ実行履歴・通知センター・手動起動）

### 8-0. Step 6-0: Core 取得系 API（履歴一覧・通知センター取得・既読）

> **追加経緯（2026-05-08）：** 設計書 r8 §11 のステップ表は Step 6 を「Console UI」として括っているが、Step 6-1 / 6-2 を成立させるには Console から呼び出す **Core 側の取得系 API**（バッチ履歴一覧・通知センター取得・既読更新）が必要。Step 5 / 5.5 のスコープは「業務バッチ本体」「価格スケジュール CRUD」までで、本系統の API は未実装のため、本実装計画で Step 6-0 として先行追加する。設計書側にも r9 候補ではなく本フェーズスコープとして §13.7 に追記する。

| メソッド | パス | Service / Controller | 備考 |
|----------|------|---------------------|------|
| GET | `/api/console/batch/executions` | `ListBatchExecutionService` 新設 | `started_at DESC`・`job_name` / `status` フィルタ・LIMIT/OFFSET ページング |
| GET | `/api/console/batch/executions/{id}` | `GetBatchExecutionService` 新設 | 詳細（`error_summary` を含む単一行） |
| GET | `/api/console/batch/notifications` | `ListConsoleNotificationService` 新設 | `target_user_id = X-User-Id` または `target_subscription_tag IN (購読中タグ)` の未読のみ。`level` / `target_subscription_tag` フィルタ |
| PUT | `/api/console/batch/notifications/{id}/read` | `MarkConsoleNotificationReadService` 新設 | `read_by_user_id` / `read_at` 更新 |

#### 共通仕様
- パッケージ：`com.example.batch.controller` / `com.example.notification.controller`（ドメイン単位／規約 2-1）
- 認証ヘッダ：`X-User-Id`（既存 `BatchManualTriggerController` 準拠）
- 一覧 API のレスポンス形式：`{"items":[...], "total":N, "page":P, "size":S}`（既存 OperationLog 等と整合）
- 通知センター取得時は **JOIN 不要のクエリ**：`(target_user_id = :userId AND read_by_user_id IS NULL) OR (target_subscription_tag IN :subscribedTags AND read_by_user_id IS NULL)`

#### TDD ケース
- `ListBatchExecutionService`：`status='RUNNING'` のみ抽出、`job_name` 指定時の絞り込み、空結果（`total=0, items=[]`）
- `GetBatchExecutionService`：未存在 ID で 404、正常 ID で `error_summary` を含む詳細
- `ListConsoleNotificationService`：購読タグ未登録ユーザは `total=0`、`level=ERROR` フィルタの絞り込み、`suppressed=true` レコードは UI 一覧から除外（ダイジェスト経路で吸収済のため）
- `MarkConsoleNotificationReadService`：他ユーザ宛通知の既読化拒否（403）、二重既読化は冪等

### 8-1. Step 6-1: バッチ実行履歴画面（`/batch/executions`）

- `started_at DESC` で表示
- フィルタ：`job_name` / `status`
- 詳細モーダル：`error_summary` をプリフォーマット表示
- ページネーション：`showTotal` で「N-M / 全 K 件」（phase16 Step 11 と同じ規約）

### 8-2. Step 6-2: 通知センター画面（`/batch/notifications`）

- ログイン中ユーザの `notification_subscriptions` で購読しているタグの未読のみ表示
- 既読操作で `read_by_user_id` / `read_at` 更新
- フィルタ：`level` / `target_subscription_tag`

### 8-3. Step 6-3: 手動起動画面（`/batch/manual`）

- admin のみ表示（ロールチェックは `auth/admin` ミドルウェア）
- ボタン：`RebuildInventoriesJob` / `RecalculateDeliveryScheduleJob` / `BootstrapInventoryAdjustmentJob` / `ApplyScheduledPricesJob`（手動起動可能なものだけ）
- `TriggerFaultInjectionJob` は本番では非表示（404 がフォールバック）
- ボタン押下時に確認ダイアログ → POST → 結果 Snackbar
- 操作ログ：`screen_name='ConsoleBatchManagementPage'`, `api_name='POST /api/console/batch/{job_name}/run'`

### 8-4. Step 6-4: phase11 への要請事項のフック実装（13.0 / N-9 / K-3）

phase11 の `CreateUserService` / `UpdateUserService` に：
- admin 化時：全タグへ UPSERT
- admin → user 降格時：全行を `email_enabled=false, in_app_enabled=false`

タグ一覧は `config/notifications.php`（`subscription_tags`）に集約。

### 8-5. テスト
- Console PHPUnit：通知センター / 履歴 / 手動起動の権限チェック・既読遷移
- Core JUnit：`CreateUserService` / `UpdateUserService` フックの UPSERT 動作

### 8-6. 完了条件（Step 6）
- 3 画面の vitest / 手動 E2E が緑
- 操作ログが正しい命名で記録される

---

## 9. Step 6.5 — SKU 詳細モーダル「価格管理」タブの 3 ブロック化

設計書 §13.5.3 のレイアウトに従って、`SkuList.vue` 内のモーダルから呼ばれる `PriceManagementTab.vue` を新設：

```
[価格管理] タブ
┌─ 現行価格 ────────────────────┐
│ 価格 / 適用日 / [価格を変更（即時）] │
└────────────────────────────────┘

┌─ 予約変更 ────────────────────┐
│ 変更価格 / 変更予定日 / [設定/更新/取消] │
└────────────────────────────────┘

┌─ 履歴（折りたたみ） ──────────┐
│ price / start_date 〜 end_date │
└────────────────────────────────┘
```

### 9-1. API 関数

`features/skus/api/skus.js`：
- `getCurrentSkuPrice(skuId)` / `registerCurrentSkuPrice(skuId, data)`（既存をリネーム）
- `getScheduledSkuPrice(skuId)` / `setScheduledSkuPrice(skuId, data)` / `clearScheduledSkuPrice(skuId)`（新設）
- `getSkuPriceHistory(skuId)`（任意）

### 9-2. テスト

- 手動 E2E：phase16 Step 5 規約に従い vitest なし → 動作確認シートで担保
- API 単体は Core JUnit / Console PHPUnit 側で済

---

## 10. Step 7 — SES テンプレート

### 10-1. テンプレート YAML 配置

`amazia-core/src/main/resources/config/mail/`：
- `batch_inventory_inconsistency.yml`
- `batch_sales_mismatch.yml`
- `batch_delivery_delay.yml`
- `batch_postal_integrity_failed.yml`
- `batch_job_failed.yml`（汎用）
- `batch_digest.yml`（M-6 ダイジェスト用）

### 10-2. 既存 SES 統合（phase13）の流用

`com.example.shared.mail.SesMailSender` を再利用。リトライは `BatchRetryClassifier` の `MailSendException` 経路と整合。

### 10-3. テスト
- 各テンプレートのプレースホルダ展開
- WARN 以上で SES 送信、INFO は SES 送信されないことを mock で確認

---

## 11. Step 8 — E2E（不整合データ → バッチ → 通知到達）

### 11-1. シナリオ

設計書 §12.3 全シナリオを実装：

| # | シナリオ | 前提 | 期待 |
|---|---------|------|------|
| E2E-1 | 不整合 → 通知到達 | `inventories.quantity` を直接 +1 | inventory_alerts 購読者の Inbox に SES + 通知センター未読 +1 |
| E2E-2 | フォルト確率の統計検証 | ステージング `SIMULATION_FAULT_INJECTION=true` で 1 日運転 | 各 *-rate と統計的に整合（許容誤差内） |
| E2E-3 | `BATCH_ENABLED=false` alias | 起動 | 業務 / 手動停止 / **ダイジェストは継続**（J-1 / N-7） |
| E2E-4 | 業務 OFF + 手動 ON シナリオ | `BATCH_SCHEDULER_ENABLED=false` ＋ `BATCH_MANUAL_TRIGGER_ENABLED=true` | 定期は登録されないが手動 200 |
| E2E-5 | 手動 OFF | `BATCH_MANUAL_TRIGGER_ENABLED=false` | API 503 / フラグ戻すと再起動なしで 200 |
| E2E-6 | 業務 OFF + ダイジェスト ON | `BATCH_SCHEDULER_ENABLED=false` ＋ `BATCH_DIGEST_ENABLED=true` | ダイジェストは継続発火 |
| E2E-7 | ダイジェスト OFF 単独 | `BATCH_DIGEST_ENABLED=false` | 抑制レコード蓄積 → ON に戻すと 1 通でまとめて送出（K-4） |
| E2E-8 | 本番 TriggerFaultInjectionJob | 本番プロファイル | API 404（M-4） |
| E2E-9 | 価格スケジュール（r7） | `apply_date = TOMORROW` で予約変更登録 → 翌日 03:30 待機 | 現行価格が新価格に切替・履歴に旧価格 / `is_pending = FALSE` |

### 11-2. 完了条件
- 全 9 シナリオが緑
- 確率テスト（E2E-2）はシード固定（R-14 候補）or モック化でフレーキー化を回避

---

## 12. Step 9 — `docker compose down -v` 完走

設計書 §12.1 Docker 初回起動テスト：

1. `docker compose down -v && docker compose up --build` を実行
2. 全テーブル（`batch_executions` / `console_notifications` / `notification_subscriptions` / `fault_injection_logs` / `monthly_sales_reports` / `yearly_sales_reports` / `product_sku_scheduled_prices`）が作成
3. SKU TX bootstrap INSERT が 1 度のみ実行
4. 初回 `@Scheduled` 起動時刻まで待機して 1 回だけ実行
5. **`spring.sql.init.continue-on-error=false` で完走**（既存 phase15 の Docker テストと同パターン）

---

## 13. Step 10 — ドキュメント反映

### 13-1. DB 設計書

| ファイル | 追加 / 改修 |
|---------|-----------|
| `docs/database_design/TBL_batch_executions.md` | 新規 |
| `docs/database_design/TBL_console_notifications.md` | 新規 |
| `docs/database_design/TBL_notification_subscriptions.md` | 新規 |
| `docs/database_design/TBL_fault_injection_logs.md` | 新規 |
| `docs/database_design/TBL_monthly_sales_reports.md` / `TBL_yearly_sales_reports.md` | 新規 |
| `docs/database_design/TBL_product_sku_prices.md` | `is_active` 追加・「履歴は物理削除しない」を追記 |
| `docs/database_design/TBL_product_sku_scheduled_prices.md` | 新規 |
| `docs/database_design/README.md` | 上記ファイルを一覧表に追加 |
| `docs/database_design/ER_diagram.md` | 新テーブルとリレーションを追加 |
| `ops/healthcheck/required_tables.txt` | 新規テーブルを追加（**Step 9 の Docker 完走確認に組み込み**） |

### 13-2. API 設計書

| ファイル | 追加 |
|---------|------|
| `docs/api_design/Core_API.md` | バッチ系（`/api/console/batch/...`）+ 価格スケジュール系（`/api/skus/{id}/scheduled-price` 等）を追加 |
| `docs/api_design/Console_API.md` | Pass-through を追加 |

### 13-3. 進捗 / 次タスク

| ファイル | 更新 |
|---------|------|
| `次タスク.txt` フェーズ17 行 | ✅ 完了マーク + 完了日（2026-MM-DD）+ 実装計画リンク |
| `Amazia/docs/progress/phase11_20.md`（存在すれば） | フェーズ17 完了行 |
| `phase17_batch_processing.md` r8 § 11 ステップ一覧 | 各 Step を ✅ 完了 |

### 13-4. トラブルシュート雛形

`docs/troubles/` に未来トラブル投入用の雛形：
- `XXX_batch_lock_leak.md`（ConcurrentHashMap ロックが解放されない事故）
- `XXX_digest_double_send.md`（再起動跨ぎダイジェスト二重送信）

実発生時に連番化（[user memory: trouble_doc_consolidation] と整合）。

### 13-5. 完了条件（Step 10）

設計書 r8 §11 に従い、本フェーズ完了の定義は以下の **全てが ✅** になること：

- [ ] Step 0 〜 10 の全 TDD ケースが緑（Core / Console / Market 該当層）
- [ ] `docs/database_design/` に新規 7 ファイル + 既存 1 ファイル改修
- [ ] `docs/api_design/Core_API.md` / `Console_API.md` 反映
- [ ] `ops/healthcheck/required_tables.txt` 反映
- [ ] `amazia-core/src/main/resources/schema.sql` に Step 1-1 〜 1-7 が冪等で実装
- [ ] `docker compose down -v && docker compose up --build` 完走（Step 9）
- [ ] phase11 側のフック（13.0 / 8-4）実装済み
- [ ] `次タスク.txt` フェーズ17 行に ✅ 完了 + 完了日

---

## 14. 申し送り（後続フェーズへ）

### 14-1. phase18 以降への申し送り（設計書 §13.4 ＋ 本計画派生）
- `product_sku_stock_transactions_archive` 新設（年次アーカイブ／H-10）
- `inquiries.target_type='delivery'` 連動：配送遅延通知メールに「問い合わせ作成リンク」を含める（phase18 完了後）
- 通知センターのスレッド表示（phase19 お知らせ管理と統合可能性）
- `operation_logs.reason_code` カラム追加：バッチ comment プレフィックスを reason_code へ昇格（phase14 P14-5 / phase15 RRR-5 と連動）

### 14-2. 設計書 r9 候補（本計画では未取り込み）
- R-11：`monthly_sales_reports` のテーブル分割化
- R-12：`PARTIAL` のアラート閾値確定
- R-13：`Asia/Tokyo` 統一を 3 層で担保（JVM 起動オプション / docker-compose / DB セッション TZ）
- R-14：確率テストのフレーキー化対策（`RandomGenerator` 抽象化）
- R-15：UPSERT 戦略は本計画で先行採用済（Step 1-5）
- J-8：`@Scheduled(fixedRate)` のダイジェスト初回発火遅延短縮
- J-9：`PayloadHasher` ユーティリティクラス仕様化
- J-10：`DigestNotificationDispatchJob` 起動間隔の config 化

### 14-3. SKU TX 構造的限界（H-6）
`product_sku_stocks.quantity = 0` の SKU について、`InventoryConsistencyCheckJob` は不整合を検知できない（`0 = SUM(空集合) = 0` で偶然一致）。SKU TX に CHECK 制約を追加する将来対応までは構造的限界。Step 3-1 の TDD コメントに「将来 CHECK 追加時に期待値反転」を明記。

### 14-4. t3.micro バッチ実行中の応答遅延（2026-05-08 顕在化）

phase17 で `PostalCsvImportJob`（KEN_ALL.CSV 12 万件 INSERT）等の重量バッチが追加されたが、
EC2 t3.micro（vCPU 2 / 1 GB RAM / `-Xmx384m`）でバッチ実行中の同時負荷は phaseX-4 軽負荷試験
の対象外であり、Market 顧客・Console 管理者の API 応答が **5〜30 秒の遅延 or タイムアウト**
する可能性がある。

定期バッチ cron は深夜帯（日次 03:30 / 月次 03:00 + 04:30 / 年次 1/1 05:00）に集中しているため、
**深夜帯メンテナンスウィンドウ（02:30-05:30 JST）** で Market を「メンテ画面」表示にする方針が
合理的。

**対応：** [phaseX-8 設計書](../design/phaseX/phaseX-8_maintenance_window_for_batch_load.md) として切り出し済。
phase17 では実装しない。本フェーズ完了後に phaseX-8 を独立フェーズとして実施する。
