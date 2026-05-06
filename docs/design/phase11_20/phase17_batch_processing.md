# フェーズ17：バッチ処理（改訂版 r6）

## ステータス
🔲 未着手（フェーズ13 〜 16 完了後に着手予定）

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 初版日不明 | 初稿（日次／月次／年次バッチとトラブル関数の枠組み） |
| r1 | 2026-05-06 | フェーズ13 〜 16 完了前提への全面ブラッシュアップ。`inventories` / `inventory_movements`（phase14）・`deliveries`（phase15）・`postal_addresses`（phase13）・`market_customers` / `market_sessions`（phase13）の実在を前提に再設計。Spring `@Scheduled` 一本化（無料枠方針）、トラブル関数の本番 OFF 強制（機能フラグ + 環境分岐）、SES 通知統合、JST 0:00 基準、H2 互換、`docker compose down -v` 完走の各ガードレールを反映 |
| r2 | 2026-05-06 | レビューコメント R-1 〜 R-16 を反映。R-1 〜 R-5（🔴必須）：振込確認の `mode` 化／`DeliveryTroubleInjector` の補償 `inventory_movements` 必須化／`SalesReconciliationJob` SQL の倉庫合算化／整合性 SQL の `GROUP BY` 修正／在庫初期化補填を phase15 マイグレーションへ移管しオンデマンド独立。R-6 〜 R-10（🟡着手前確定）：リトライ対象を I/O 系例外に限定／多重起動防止を JVM 内 `ConcurrentHashMap` + 起動時クリーンアップで明文化／`BATCH_ENABLED=false` の挙動定義／`notification_subscriptions` 新設で購読者解決を構造化／レートリミットを `payload_hash` ＋ ダイジェスト方式へ。phase11 のロールが admin / user の 2 種のみである事実を反映し、`inventory_manager` 等の細粒度ロールは購読タグへ変更 |
| r3 | 2026-05-06 | r2 改訂時の反映漏れ・新規論点 N-1 〜 N-14 を反映。🔴 必須：旧表記（`inventory_manager` / `super_admin` / `target_role`）の本文残存を一掃／10 章リスク表を r2 同期／11 章 Step 0 を r3 に更新／補償 movement の符号と暫定値を `+1` 固定で確定／`BootstrapInventoryAdjustmentJob` の補填値ルール明記。🟡 着手前確定：振込確認 × フォルトインジェクションの組合せマトリクス追加／ダイジェスト発火を `DigestNotificationDispatchJob` ＋ `digest_sent_at` で永続化／多重起動防止のテストを並行実行ベースへ／`notification_subscriptions` の自動購読フックを phase11 への要請事項へ／`BATCH_ENABLED` を `BATCH_SCHEDULER_ENABLED` ＋ `BATCH_MANUAL_TRIGGER_ENABLED` に分割。🟢：N-11 〜 N-14 を 14.1 に残置 |
| r4 | 2026-05-06 | r3 改訂時の反映漏れ・新規論点 M-1 〜 M-14 を反映。🔴 必須：3 章共通制御に手動起動 503 ガード追加（M-1）／`@ConditionalOnProperty` の対象を業務バッチクラス個別に明文化＋`DigestNotificationDispatchJob` を独立フラグ化（M-2）／admin → user 降格時の購読論理停止を 13.0 に追加（M-3）／`TriggerFaultInjectionJob` 自体に `@Profile("!production")` を付与し API 404 を成立させる（M-4）／2 軸分割の E2E テスト追加（M-5）／ダイジェスト送信を「タグ集計→ユーザ単位 SES」の 2 重ループに明文化（M-6）。🟡 着手前確定：alias YAML の保守性とロードマップ明記（M-7）／`quantity = 0` 商品の bootstrap 扱いを `> 0` に限定（M-8）／`payload_hash` を NOT NULL 既定値で衝突回避（M-9）／`matchIfMissing` 残置の意図注記（M-10）。🟢：M-11 〜 M-14 を 14.1 に残置 |
| r5 | 2026-05-06 | r4 改訂時の波及漏れ・新規論点 K-1 〜 K-14 を反映。🔴 必須：6.4.1 に `payload_hash` フォールバック規則を追記し 5.2 / 6.4.3 と整合（K-1）／12.1 Docker 初回起動テストを 2 軸分割に同期（K-2）／13.0 を UPSERT 一本化で初回／再昇格の分岐排除（K-3）／12.3 E2E に `BATCH_DIGEST_ENABLED=false` 単独シナリオ追加（K-4）／`DigestNotificationDispatchJob` の手動起動 API 不在を明記（K-5）。🟡 着手前確定：オンデマンドジョブ Bean のステートレス前提を 3 章共通制御に追記（K-6）／10 章リスク表に「`BATCH_DIGEST_ENABLED=false` 長期運用時の抑制レコード滞留」を追加（K-7）／12.1 に「`quantity = 0` 商品の検知不能性」テストを追加（K-8）／13.3 に M-12 タイムスタンプ補完を昇格（K-9）／7 章末尾に env-vars セット更新チェックリスト参照を追加（K-10）。🟢：K-11 〜 K-14 を 14.1 に残置 |
| r6 | 2026-05-06 | r5 改訂時の最終波及漏れ J-1 〜 J-10 を反映。🔴 必須：`BATCH_ENABLED` alias が `BATCH_DIGEST_ENABLED` には作用しない設計意図を 2.3 / 12.1 / 12.3 に明文化（J-1）／K-7 で確定した方針を `ConsoleNotificationsArchiveJob` として 3.3 に新設（J-2）／10 章 PARTIAL アラートを「R-12（r6 候補）で確定予定」に修正（J-3）。🟡 着手前確定：14.1.2 から M-12 重複記述を削除し 13.3 に集約（J-4）／フォールバック `payload_hash` を `batch_execution_id` ベースから **`job_name` ベース**へ変更（連続失敗が抑制対象になるよう／J-5）／7 章チェックリストに本番 Validator 起動失敗の検証項目を追加（J-6）／M-13（`BootstrapInventoryAdjustmentJob` 冪等性）を 14.1.2 から 13.3 に昇格（J-7）。🟢：J-8 〜 J-10 を 14.1 に残置 |

## 範囲
- Amazia Core（Spring Boot）— バッチ本体
- Amazia Console — バッチ実行履歴・通知センター・トラブル関数の操作 UI
- DB 設計 — `batch_executions` 等のメタテーブル新設、既存テーブルへの整合性チェック付加
- 運用 — CloudWatch Logs / SES / Slack（任意）通知

---

# 1. 機能概要

- 日次・月次・年次の定期バッチを Spring `@Scheduled` で実装し、整合性チェック・ステータス更新・データ更新を自動化する
- すべてのバッチは **`batch_executions` テーブル**に実行履歴（成功／失敗・所要時間・対象件数・原因）を記録し、Console 側で参照できる
- 重大な不整合・失敗は **権限者に通知**（SES メール + Console 通知センター）
- 運用テストのため、**ランダム関数によるトラブル発生シミュレーション**を導入する。ただし**本番環境では機能フラグで強制 OFF**にし、誤発動を構造的に防ぐ
- **無料枠完走方針**（[user memory: free_tier_first]）に従い、ECS Scheduled Task / Lambda / EventBridge 単独運用は採用せず、Spring `@Scheduled` で完結させる

## 1.1 フェーズ13 〜 16 完了前提（r1 の出発点）

本書はフェーズ13 〜 16 の実装が完了している前提で記述する。本書から見て**既に存在する**主要資産は以下のとおり：

| 資産 | 出自 | 本書での扱い |
|------|------|-------------|
| `market_customers` / `market_sessions` / `market_customers_password_reset_tokens` | phase13 | セッション期限切れの掃除バッチ等で参照 |
| `postal_addresses` テーブル | phase13 | **月次の整合性チェック対象**（取込本体は phase13 の `@Scheduled` で実施済。本書では「取込結果の検査」と「失敗時の通知」を担当） |
| `inventories` / `inventory_movements` / `warehouses` | phase14 〜 phase15 | 在庫整合性チェックの**主参照**。`products.stock` は完全移行済（phase14 Step E）で **存在しない前提** |
| `sales` / `sales_return` / `payment_methods` / `shipping_methods` / `shipping_statuses` | phase14 | 売上・返品集計、配送ステータス更新の参照 |
| `deliveries` / `inbounds` | phase15 | 配送ステータス自動更新・入荷再計算結果の検査 |
| 配送ステータス遷移マスタ（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED + CANCELED / DELIVERY_FAILED / RESCHEDULED） | phase14 拡張済 | 本書で「未対応ステータスへの遷移」は引き続き Service バリデーションで拒否される前提 |
| `operation_logs.screen_name` / `api_name` | phase14 | バッチからの記録は `screen_name = 'BatchScheduler'` 等の規約で運用 |
| Console UI 改善（phase16） | phase16 | バッチ履歴画面・通知センターは phase16 の UI 規約に沿う |

「既に廃止されたカラム（`products.stock`）」「未だ追加されていないカラム（`operation_logs.reason_code`）」は r1 でも前提を変えない。

---

# 2. バッチ実行基盤

## 2.1 採用方針

| 候補 | 採否 | 理由 |
|------|------|------|
| **Spring `@Scheduled`（amazia-core 同居）** | **採用** | 無料枠で完結（追加コスト 0）。phase13 の郵便番号取込で既に採用済。EC2 t3.micro（X-4 完了済構成）の `-Xmx384m` 内で運用可能 |
| AWS Lambda + EventBridge | 不採用 | 無料枠だが、Java コールドスタート・運用基盤分散コスト・バッチ専用の Python/Node ランタイム導入は学習目的に対し過剰 |
| ECS Scheduled Task | 不採用 | Fargate は無料枠外（[user memory: aws_infra_facts]） |
| Lambda + SQS | 不採用 | スパイク吸収用途で本書のバッチには不要 |
| cron on EC2（OS レベル） | 不採用 | Spring の DI / 既存 Service 再利用が困難。`@Scheduled` で十分 |

## 2.2 Spring `@Scheduled` 運用ルール

- スケジューラ専用 Bean は `com.example.batch.scheduler.*` に配置（既存 `com.example.market.postal.*` の `@Scheduled` との責務分離）
- すべての `@Scheduled` メソッドは **薄いラッパー**で、実体は Service 層に委譲する（規約 1-1）
- 1 メソッド = 1 ジョブ = 1 トランザクション境界。複数ジョブを 1 メソッドに詰め込まない
- **JST 基準**で実行時刻を指定する（`spring.jackson.time-zone=Asia/Tokyo` / DB セッション TZ も `Asia/Tokyo` に統一済の前提）。サマータイムは [phase14 と同じく現時点でスコープ外](../phase11_20/phase14_shipping.md)
- **`@SchedulerLock`（ShedLock）等のクラスタロックは導入しない**：本番 EC2 は単一インスタンス（[user memory: aws_infra_facts] EIP `13.54.203.95`）。冗長化された場合は将来課題

## 2.3 `application.yml` 設定例（参考）

```yaml
amazia:
  batch:
    enabled: ${BATCH_ENABLED:true}            # フェーズ起動時に false で全停止可能
    timezone: Asia/Tokyo
    daily:
      cron: "0 30 3 * * *"                    # 毎日 03:30 JST
    monthly:
      postal-check-cron: "0 30 4 1 * *"       # 毎月 1 日 04:30 JST（取込本体は phase13 の 03:00 で先行）
    yearly:
      cron: "0 0 5 1 1 *"                     # 毎年 1 月 1 日 05:00 JST
    bank-transfer-verification:
      mode: ${BANK_TRANSFER_VERIFICATION_MODE:disabled}   # disabled / mock-match / mock-mismatch-rate（R-1 対応）
    sales-reconciliation:
      target-warehouse-ids: ${SALES_RECONCILIATION_WAREHOUSES:1}  # CSV。複数倉庫対応の起点（R-3 対応）
    rate-limit:
      duplicate-suppression-minutes: 60       # 同 payload は 60 分抑制
      digest-after-suppression: true          # 抑制後 1 時間に件数ダイジェストを 1 通送る（R-10 対応）
  simulation:
    fault-injection:
      enabled: ${SIMULATION_FAULT_INJECTION:false}     # 本番は false 固定（後述）
      sales-mismatch-rate: ${SIM_SALES_RATE:0.05}
      inventory-mismatch-rate: ${SIM_INV_RATE:0.05}
      delivery-trouble-rate: ${SIM_DELIVERY_RATE:0.10}
```

### `amazia.batch` 停止スイッチの挙動定義（R-8 対応／N-10 で 2 軸分割：r3）

r2 では `BATCH_ENABLED` の 1 軸で「定期と手動を同時に止める／同時に動かす」の 2 状態しか表現できなかった。実運用の不具合調査時に「定期だけ止めて、手動で `RebuildInventoriesJob` を走らせたい」というシナリオを表現できないため、r3 で **2 軸に分割**する：

| 環境変数 | プロパティ | 既定 | 用途 |
|---------|-----------|------|------|
| `BATCH_SCHEDULER_ENABLED` | `amazia.batch.scheduler-enabled` | `true` | **業務バッチクラス**（`InventoryConsistencyCheckJob` / `SalesReconciliationJob` / `DeliveryStatusAdvanceJob` / `SessionAndTokenSweepJob` / `PreorderStatusRefreshJob` / `PostalAddressIntegrityCheckJob` / `MonthlySalesReportJob` / `YearlySalesReportJob` / `OperationLogArchiveJob`）の ON / OFF。**TaskScheduler Bean / `@Async` 用 Executor は止めない** |
| `BATCH_MANUAL_TRIGGER_ENABLED` | `amazia.batch.manual-trigger-enabled` | `true` | 手動起動 API の ON / OFF。`false` のとき `POST /api/console/batch/{job}/run` は **HTTP 503** を返す（M-1） |
| `BATCH_DIGEST_ENABLED`（**r4 で新規／M-2 対応**） | `amazia.batch.notifications.digest-enabled` | `true` | `DigestNotificationDispatchJob` の ON / OFF。`BATCH_SCHEDULER_ENABLED` から**独立**。「定期業務バッチは止めても通知ダイジェストは送りたい」ニーズを満たす |

### `@ConditionalOnProperty` の適用先（M-2 対応：r4 で明文化）

実装での適用粒度の混乱を防ぐため、**業務バッチクラスごとに個別に `@ConditionalOnProperty` を付与**する：

```java
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class InventoryConsistencyCheckJob { ... }
```

**やってはいけない実装：**
- `TaskScheduler` Bean 自体に `@ConditionalOnProperty` を付ける（→ `@Async` 等が巻き添えで止まる）
- `SchedulerConfigurer` 集約クラス 1 つに `@ConditionalOnProperty` を付ける（→ `DigestNotificationDispatchJob` まで止まる）

**`DigestNotificationDispatchJob` は別フラグ：**
ダイジェストジョブは **`BATCH_DIGEST_ENABLED`（既定 `true`）** で独立制御。「不具合調査時に定期業務バッチは止めるが通知ダイジェストは継続したい」シナリオ（N-10 で `BATCH_SCHEDULER_ENABLED=false` ＋ `BATCH_MANUAL_TRIGGER_ENABLED=true` を導入した動機）を、ダイジェストにも一貫させる：

```java
@Component
@ConditionalOnProperty(name = "amazia.batch.notifications.digest-enabled",
                       havingValue = "true", matchIfMissing = true)
public class DigestNotificationDispatchJob { ... }
```

| `BATCH_SCHEDULER_ENABLED` | `BATCH_MANUAL_TRIGGER_ENABLED` | 動作 | シナリオ |
|---|---|---|---|
| `true` | `true` | 定期 OK / 手動 OK | 通常運用（既定） |
| `false` | `true` | 定期 NG / 手動 OK | **不具合調査時：定期は止め、人手で `RebuildInventoriesJob` 等を走らせたい** |
| `true` | `false` | 定期 OK / 手動 NG | 通常運用中に手動起動 API のみ閉鎖（侵入対策の一時措置） |
| `false` | `false` | 全停止 | 緊急停止 |

`BATCH_DIGEST_ENABLED` は上記 2 軸とは独立に動く。例：`BATCH_SCHEDULER_ENABLED=false ＋ BATCH_DIGEST_ENABLED=true` のとき、業務バッチは停止していてもダイジェスト送信は継続（M-2）。

旧 `BATCH_ENABLED` は r3 で **両者を同時に切り替える alias**として残置：

```yaml
amazia:
  batch:
    # BATCH_ENABLED は r3 で alias 化。r5 で削除予定（13.3 ロードマップ参照／M-7）
    scheduler-enabled: ${BATCH_SCHEDULER_ENABLED:${BATCH_ENABLED:true}}
    manual-trigger-enabled: ${BATCH_MANUAL_TRIGGER_ENABLED:${BATCH_ENABLED:true}}
    notifications:
      digest-enabled: ${BATCH_DIGEST_ENABLED:true}    # 業務バッチ停止と独立（M-2）
```

これにより既存設定（`BATCH_ENABLED=false`）を変えなくても r2 までの動作（全停止）が再現される。粒度を上げたい場合のみ新環境変数を個別設定する。

**`BATCH_ENABLED` alias の作用範囲（J-1 対応：r6 で明文化）：**
`BATCH_ENABLED` alias は **`BATCH_SCHEDULER_ENABLED` と `BATCH_MANUAL_TRIGGER_ENABLED` の 2 つにのみ作用**し、**`BATCH_DIGEST_ENABLED` には作用しない**（YAML を見ても `digest-enabled: ${BATCH_DIGEST_ENABLED:true}` であり、`BATCH_ENABLED` フォールバックを意図的に持たせていない）。これは N-7 の永続化方針 ＋ K-4 / K-7 の「再起動跨ぎ欠損ゼロ」を最優先するための意図的設計：

| 設定 | 業務バッチ | 手動 API | ダイジェスト |
|------|-----------|---------|------------|
| `BATCH_ENABLED=false`（alias 単独） | OFF | OFF | **継続動作** |
| `BATCH_ENABLED=false ＋ BATCH_DIGEST_ENABLED=false` | OFF | OFF | OFF |

`BATCH_ENABLED=false` で「全停止」を期待した運用者が「ダイジェストだけ動いていた」と混乱しないよう、12.1 / 12.3 のテスト記述・運用ドキュメントでこの仕様を明示する。

**`matchIfMissing = true` を残す意図（M-10 対応：r4 で注記）：**
上記 alias YAML が解決された後、Spring プロパティは常に値を持つため `matchIfMissing = true` は事実上発動しない条件になる。それでも各 `@ConditionalOnProperty` に `matchIfMissing = true` を残すのは、**将来 alias を撤去（r5 想定／13.3）した際の安全弁**として機能させるため。alias 撤去と同時にプロパティ未設定環境が一時的に発生する可能性があるが、`matchIfMissing = true` がそれをカバーする。

緊急時に **再デプロイなしで全バッチ停止**できる（CloudWatch アラーム連動）構造は維持される。

---

# 3. バッチ処理詳細

実行の前提として、すべてのバッチは以下の共通制御を備える：

1. **`batch_executions` への INSERT**（開始時：`status = RUNNING`、終了時：`SUCCESS` / `FAILED` / `PARTIAL` に更新）。INSERT/UPDATE はバッチ本体とは別の `REQUIRES_NEW` トランザクションで行い、本体ロールバック時もステータス記録は残す
2. **同一バッチの多重起動防止（R-7 対応：r2 で確定）**：単一 EC2 / 単一 JVM 前提のため **JVM 内 `ConcurrentHashMap<jobName, AtomicBoolean>` ベースの軽量ロック**を採用。`SELECT ... FOR UPDATE` は使わない（クラスタロックは導入しない方針 2.2 と整合）
3. **起動時クリーンアップ（R-7 補強・14章レビュー観点を本文へ昇格）**：JVM 異常終了で `batch_executions.status = RUNNING` のまま残ったレコードがあると、JVM 内ロック復帰後も「進行中」と誤認するリスクは無い（プロセスを跨いで `ConcurrentHashMap` は引き継がれない）。ただしレポート上は不正確になるため、`ApplicationReadyEvent` のリスナーで `RUNNING` のまま `started_at` が 24 時間以上前のレコードを **`status = FAILED, error_summary = '[recovery] orphaned by JVM restart'` に強制遷移**させるクリーンアップを起動時に必ず実行
4. **失敗時のリトライ（R-6 対応：r2 で対象限定）**：リトライ対象は **I/O 系の一過性例外のみ**：
   - `org.springframework.mail.MailSendException`（SES 送信失敗）
   - `java.net.SocketTimeoutException` / `org.springframework.web.client.ResourceAccessException`（外部 API タイムアウト）
   - 5xx を含む `RestClientResponseException`
   - `org.springframework.dao.TransientDataAccessException` / `CannotAcquireLockException`（DB の一時切断・ロック競合）

   指数バックオフ（1s → 2s → 4s）で最大 3 回。`org.springframework.dao.DataIntegrityViolationException` / `IllegalStateException` / 業務例外は **即 `FAILED` 確定**（同じ SQL を 3 回流しても結果は変わらない）
5. **CloudWatch Logs 出力**：Logback の MDC に `batch_id` / `job_name` を入れて追跡可能にする
6. **手動起動 API のガード（M-1 対応：r4 で追加・K-6 補強：r5 でステートレス前提）**：オンデマンドジョブを起動する `POST /api/console/batch/{job_name}/run` は、共通の `BatchManualTriggerController` で `amazia.batch.manual-trigger-enabled` を `@Value` 経由で取得し、`false` のとき **HTTP 503**（`Service Unavailable`）を返す。`@ConditionalOnProperty` ではなく Controller 側で判定する理由は、**オンデマンドジョブ Bean 自体は生かしたまま（DI 解決可能なまま）API だけを閉じる**設計のため。Bean を非登録にすると、`BATCH_MANUAL_TRIGGER_ENABLED` を後から `true` に戻しても再起動が必要になり、緊急時の柔軟性を失う。**オンデマンドジョブ Bean はステートレス（インスタンスフィールドで処理途中状態を持たない）として実装する。`BATCH_MANUAL_TRIGGER_ENABLED=false` 中もリソースを保持し続けないため、長期 OFF → ON への切替で副作用は出ない**（K-6 対応：r5 で明記）

---

## 📅 3.1 日次バッチ（毎日 03:30 JST）

日次のジョブは独立に実行される（1 ジョブ失敗が他に波及しない構成）。

### ① 入荷数と在庫数の整合性チェック（`InventoryConsistencyCheckJob`）

**目的：** `inventory_movements` の累積（=「あるべき在庫」）と `inventories.quantity` の現在値が乖離していないかを検査する。

**判定ロジック（R-4 対応：r2 で `GROUP BY` 修正）：**

`inventories` は `UNIQUE (product_id, warehouse_id)`（phase15 RRR-3）が保証されているため、`GROUP BY` には `i.quantity` を含めない。`HAVING` 側で `MAX(i.quantity)`（実質単一値）と SUM を比較する：

```sql
-- 商品×倉庫ごとに、movement の累積と現在在庫を突合
SELECT
  i.product_id,
  i.warehouse_id,
  MAX(i.quantity)                 AS current_qty,    -- UNIQUE 前提なので MAX = 単一値
  COALESCE(SUM(m.quantity), 0)    AS expected_qty
FROM inventories i
LEFT JOIN inventory_movements m
  ON  m.product_id = i.product_id
  AND m.warehouse_id = i.warehouse_id
GROUP BY i.product_id, i.warehouse_id
HAVING MAX(i.quantity) <> COALESCE(SUM(m.quantity), 0);
```

**前提：** `inventories` は `UNIQUE (product_id, warehouse_id)` 制約（phase15 設計書 L399 参照）が必ず成立している。本ジョブは UNIQUE 制約に依存している点を明示する。

**初期値の前提（R-5 対応：r2 で初期化責務を移管）：**
本ジョブは **棚卸初期値が `inventory_movements` に登録済みの前提**で動作する。具体的には：

- phase15 RRRR-1 のマイグレーションで `inventories` を作成・複製した直後に、**同一マイグレーション内で `inventory_movements` への `movement_type='adjustment', comment='[bootstrap] initial inventory'` を全商品分 INSERT する**ことを phase15 r5 へ要請する（後述「13. phase18 以降への要請事項」を **「13. 他フェーズへの要請事項」に拡張**）。
- 本ジョブは初期化補填を**行わない**。未登録の場合は単に不一致として通知され、Console 経由で `BootstrapInventoryAdjustmentJob`（オンデマンド／3.4 参照）を **admin が手動起動**する運用とする。
- **「自動補正はしない」原則との整合（N-5 対応）：** `BootstrapInventoryAdjustmentJob` は **admin の人為承認を伴う一発限りの救済 Job** であり、定期バッチが踏まないことが本原則の根拠。`inventory_movements` の現在値を「DB の現状＝真」と決め打って `adjustment` で記録する点は「自動補正」と境界が近いが、起動経路が手動であることと、対象が「初期値レコードの欠落」だけに限定されることで原則との緊張は局所化されている。
- **`inventories.quantity = 0` 商品の検知不能性（M-8 対応）：** 初期 `stock = 0` の商品は phase15 r5 マイグレーション（`WHERE stock > 0`）でも `BootstrapInventoryAdjustmentJob`（`quantity > 0` 限定）でも `inventory_movements` への bootstrap レコードは作られない。結果として、本ジョブの判定 SQL では `inventories.quantity = 0` ＝ `SUM(movements) = 0` で **偶然一致するため検知できない**。これは phase14 S14-7 の `CHECK (quantity != 0)` が緩和されるまで構造的に避けられず、phase14 r4 完了時点で本ジョブの初期データ前提を再評価する必要がある。
- 「自動補正はしない」原則（次節）と矛盾しないよう、**初期化処理は定期バッチから完全に切り離す**。

**不一致時：**
- 対象商品の担当者へ SES 通知（メール宛先は `notification_subscriptions.subscription_tag = 'inventory_alerts'` の購読者を対象とする／6.2 参照）
- Console 通知センターにも投入（後述 3.5）
- `operation_logs` に `action='inventory_inconsistency_detected'`, `screen_name='BatchScheduler'`, `comment='[inventory_check] product_id=... expected=... actual=...'` を記録
- **自動補正はしない**：人間の判断を経て `adjustment` で補正する運用

**異常系の自衛：** Phase14 で導入された `CHECK (movement_type × quantity 符号)` と `CHECK (inventories.quantity >= 0)` により、DB レベルで物理的な異常は既にブロックされている。本ジョブは「DB 制約をすり抜けた論理的乖離」のみを対象とする。

---

### ② 発売日・予約ステータスの自動更新（`PreorderStatusRefreshJob`）

**前提：** Phase14 で予約ステータスは **リアルタイム判定**（バッチ不要）と確定済み。判定基準は **JST 0:00**。

**本書のバッチが行うこと：**
- 「リアルタイム判定 API のキャッシュ層（Spring Cache / 後述）が古い値を返している」ケースの**強制無効化**のみを担当する
- 商品マスタの状態を直接書き換えることはしない（YAGNI）
- ただし **公開日／発売日／予約開始日が今日と一致した商品**を抽出し、検索インデックス（将来導入）の更新トリガを発火する枠だけ確保

**抽出 SQL：**

```sql
SELECT id FROM products
WHERE public_date    = CURRENT_DATE
   OR release_date   = CURRENT_DATE
   OR preorder_start = CURRENT_DATE;
```

**通知：** 本ジョブは原則通知しない（運用ノイズ削減）。失敗時のみ通知。

---

### ③ 売上と在庫数の照合 + 振込確認疑似 API（`SalesReconciliationJob`）

**前提：** Phase14 r3 で「再構築 SQL」が確定済み（予約購入は出荷済みのみ販売減算に算入）。本ジョブはこれを再実行して `inventories` との整合を見る。

**判定ロジック（R-3 対応：r2 で倉庫合算化）：**

`WHERE i.warehouse_id = 1` のハードコードを廃止し、`amazia.batch.sales-reconciliation.target-warehouse-ids`（既定 `1`、CSV）で対象倉庫を `@Value` 経由で取得する。複数倉庫運用が始まったら CSV を増やすだけで合算が成立する：

```sql
-- 商品ごとの「あるべき在庫」を再計算（phase14 r3 と完全一致／対象倉庫合算）
SELECT
  p.id AS product_id,
  COALESCE(SUM(i.quantity), 0) AS current_qty,
  ( COALESCE((SELECT SUM(inb.quantity) FROM inbounds inb
              WHERE inb.product_id = p.id
                AND inb.warehouse_id IN (:targetWarehouseIds)), 0)
  - COALESCE((SELECT SUM(s.quantity) FROM sales s
              LEFT JOIN deliveries d ON d.sales_id = s.id
              WHERE s.product_id = p.id
                AND ( s.is_preorder = false
                   OR (s.is_preorder = true AND d.shipped_date IS NOT NULL))
             ), 0)
  + COALESCE((SELECT SUM(sr.quantity) FROM sales_return sr
              JOIN sales s ON sr.sales_id = s.id
              WHERE s.product_id = p.id AND sr.status = 'REFUNDED'
             ), 0)
  ) AS expected_qty
FROM products p
LEFT JOIN inventories i
  ON  i.product_id = p.id
  AND i.warehouse_id IN (:targetWarehouseIds)
GROUP BY p.id;
```

`current_qty <> expected_qty` のものを通知対象とする。

**注：** `sales` / `sales_return` / `inbounds` の販売・返品・入荷は商品単位の集計で済むが、入荷側のみ `warehouse_id` で絞ることで「倉庫別運用に切り替わった際に他倉庫の入荷を取り込み忘れる」事故を防ぐ。

**振込確認疑似 API（R-1 対応：r2 で `mode` 化）：**

本番でも疑似 API が常時 MISMATCH を吐いて誤通知が出る事故を防ぐため、`amazia.batch.bank-transfer-verification.mode` で振る舞いを制御する：

| `mode` | 動作 | 既定環境 |
|--------|------|---------|
| `disabled`（**本番既定**） | 振込確認部分そのものをスキップ。`SalesReconciliationJob` は在庫照合のみ実施 | `application-prod.yml` |
| `mock-match` | `BankTransferMockClient.verify()` が常に MATCH を返す | `application-staging.yml` |
| `mock-mismatch-rate` | `amazia.simulation.fault-injection.sales-mismatch-rate` の確率で MISMATCH を返す（**`SIMULATION_FAULT_INJECTION=true` の環境でのみ有効**） | `application-dev.yml` |

**`mode` とフォルトインジェクションの組合せマトリクス（N-6 対応：r3 で明文化）：**

`SIMULATION_FAULT_INJECTION` と `BANK_TRANSFER_VERIFICATION_MODE` の両方が起動時 Validator で検査される：

| `SIMULATION_FAULT_INJECTION` | `mode` | 結果 | 想定環境 |
|------------------------------|--------|------|---------|
| `false` | `disabled` | OK | **本番**（既定） |
| `false` | `mock-match` | OK | ステージング |
| `false` | `mock-mismatch-rate` | **起動失敗（Validator）** | 不正な組合せ |
| `true` | `disabled` | OK（注入は走るが Sales 系 Injector は無効化） | dev（Sales 不整合をテストしない場合） |
| `true` | `mock-match` | OK | ステージング（注入有り、Sales は MATCH 固定） |
| `true` | `mock-mismatch-rate` | OK | dev（Sales 不整合を確率的に発生させたい） |

これにより「`mode=mock-mismatch-rate` を本番に潜り込ませる」ルートが構造的に塞がる。本物の銀行 API 接続が完了した時点で `mode=production`（新設）に切り替える将来拡張を予約する。

---

### ④ 配送ステータスの自動更新（`DeliveryStatusAdvanceJob`）

**前提：** Phase15 で配送ステータス遷移は Service 層でガード済み。本ジョブは**期日到達による状態遷移**のみ担当する。

**遷移ルール（バッチ駆動分のみ）：**

| 現在 | 条件 | 次 |
|------|------|----|
| SHIPPED | `delivered_date IS NULL` かつ `scheduled_date < TODAY - 7` | （遷移しない・通知のみ）「配送遅延の疑い」 |
| DELIVERED | `delivered_date < TODAY - 14` かつ `RETURN_REQUESTED` 未到来 | （遷移しない・操作ログのみ）「クールダウン期間終了」 |

**注意：** `PENDING → SHIPPED` / `SHIPPED → DELIVERED` の遷移はオペレーターの手動操作（出荷登録・配達完了登録）が責務であり、**バッチで自動遷移させない**。バッチが状態を勝手に進めると、phase14 r3 で確定した「予約購入の出荷時在庫減算」のフックが意図せず発火する事故になる。

**配送遅延通知：**
- 抽出した遅延候補を SES でユーザに通知（注文者）+ Console 通知センターに権限者向け
- メール文面は `config/mail/delivery_delay.yml` で管理（規約 3-1）

---

### ⑤ セッション・トークンの掃除（`SessionAndTokenSweepJob`／r1 で追加）

**目的：** Phase13 で導入された `market_sessions` / `market_customers_password_reset_tokens` の期限切れレコードを物理削除し、t3.micro の DB 容量を圧迫させない。

**処理：**
```sql
DELETE FROM market_sessions WHERE expires_at < NOW();
DELETE FROM market_customers_password_reset_tokens
  WHERE expires_at < NOW() OR used = TRUE;
```

**通知：** 件数のみ CloudWatch Logs に記録。SES 通知はしない。

---

## 📅 3.2 月次バッチ（毎月 1 日 04:30 JST）

### ① 郵便番号データ整合性チェック（`PostalAddressIntegrityCheckJob`）

**前提：** 取込本体（KEN_ALL.CSV ダウンロード → 全件洗い替え）は phase13 で 03:00 JST に実装済。本書バッチは**取込結果の検査**を担当する（取込から 90 分後に実行）。

**チェック項目：**
1. `postal_addresses` の総件数が直近 12 ヶ月の中央値 ± 5% に収まっているか（急減・急増の検知）
2. `MAX(updated_at)` が当日（取込日）以内であること
3. サンプル郵便番号（`config/batch/postal_sample_codes.yml` で管理）の数件が引けること（例：`100-0001` / `530-0001`）

いずれかが NG なら権限者へ通知。**自動ロールバックはしない**（phase13 が全件洗い替え方式のため、人手復旧）。

### ② 月次売上レポート生成（`MonthlySalesReportJob`／r1 で追加）

- 前月分の `sales` を商品別・決済方法別・配送方法別・予約／通常別で集計
- 結果は `monthly_sales_reports` テーブル（後述）に保存
- 担当権限者へ SES で「レポート生成完了」通知（ファイル添付ではなく Console 画面の URL）

---

## 📅 3.3 年次バッチ（毎年 1 月 1 日 05:00 JST）

### ① 年次売上レポート生成（`YearlySalesReportJob`）

- 前年 1 〜 12 月の `monthly_sales_reports` を集計し `yearly_sales_reports` に保存

### ② 古い `operation_logs` のアーカイブ（`OperationLogArchiveJob`）

- **保持期間：1 年**（phase13 と整合）
- 1 年超のレコードを `operation_logs_archive` に移送（INSERT → DELETE を 1 トランザクション）
- アーカイブ先テーブルはインデックスを最低限に絞り、t3.micro のディスク圧迫を抑える

### ③ 古い `console_notifications` のアーカイブ（`ConsoleNotificationsArchiveJob`／J-2 対応：r6 で新設）

K-7 で確定した「`BATCH_DIGEST_ENABLED=false` 長期運用時の抑制レコード滞留」リスクへの実装ガイドとして、`console_notifications` の年次アーカイブを本ジョブで担う。

**アーカイブ条件：** 以下のいずれかを満たすレコード
- `read_at IS NOT NULL` かつ `read_at < NOW() - INTERVAL 1 YEAR`（既読から 1 年経過）
- `suppressed = true` かつ `digest_sent_at IS NOT NULL` かつ `digest_sent_at < NOW() - INTERVAL 1 YEAR`（ダイジェスト送出済みから 1 年経過）
- `created_at < NOW() - INTERVAL 1 YEAR`（未読・未抑制でも 1 年経過したものは強制アーカイブ）

**処理：**
- 対象を `console_notifications_archive` に INSERT → 元テーブルから DELETE を 1 トランザクション
- アーカイブ先テーブルは PK ＋ `(target_subscription_tag, created_at DESC)` 1 本のみで、t3.micro のディスク圧迫を抑える
- 抑制中で `digest_sent_at IS NULL` のレコードは**アーカイブしない**（ダイジェスト送出を待つ）。ただし上記 3 つ目の「`created_at < NOW() - INTERVAL 1 YEAR`」が効くため、永遠に滞留することはない

**通知：** 件数のみ CloudWatch Logs に記録。SES 通知はしない。

### ④ ログローテーション

- CloudWatch Logs の保持期間設定（1 年）は IaC 側で管理。本ジョブでは「保持期間が想定値か」を describe して逸脱を検知するのみ

---

## 📅 3.4 オンデマンドバッチ（Console から手動起動）

定期外で手動実行できるバッチ。phase11 のロールは admin / user の 2 種のみのため、権限欄は **admin 限定** に統一する。

> **全ジョブ共通（M-1 対応）：** `BATCH_MANUAL_TRIGGER_ENABLED=false` のとき、本表の全ジョブの `POST /api/console/batch/{job}/run` は **HTTP 503** を返す。Bean 自体は登録されているため、フラグを `true` に戻せば再デプロイ無しで即時復活する。

| 名前 | 用途 | 権限 / 環境 |
|------|------|------|
| `RebuildInventoriesJob` | `inventory_movements` から `inventories.quantity` を完全再構築 | admin のみ |
| `RecalculateDeliveryScheduleJob` | 在庫切れで NULL になっている `deliveries.scheduled_date` の再計算（phase15 の入荷再計算と同等処理） | admin のみ |
| `BootstrapInventoryAdjustmentJob`（**r2 で追加／R-5 対応・r3 で補填値ルール明記／N-5・r4 で対象限定／M-8・r6 で冪等性警告フラグ追加／J-7**） | phase15 マイグレーションが古いまま稼働している環境に対する救済措置として、`inventory_movements` に棚卸初期値 `adjustment` レコードが無い場合に補填する一発限り Job。**補填値は「現在の `inventories.quantity` をそのまま `adjustment` として記録」**（`products.stock` は phase14 Step E で削除済のため取得不能。「DB の現状＝真」と決め打つ唯一の選択肢）。**対象は `inventories.quantity > 0` の商品のみ**（phase14 S14-7 の `CHECK (quantity != 0)` が phase14 r4 で緩和されるまで `quantity = 0` の INSERT は制約違反となるため）。`quantity = 0` の商品は「初期化不要」と判定しスキップ。通常は phase15 r5 のマイグレーションで完了しているため発動不要。**⚠ 実装時に冪等性チェック必須（13.3 r6 ロードマップ参照／J-7）：処理前に `inventory_movements WHERE comment LIKE '[bootstrap]%' AND product_id = :pid` を検索し、既存があればスキップ。2 回目起動による在庫 2 倍ズレ事故を防ぐ** | admin のみ |
| `TriggerFaultInjectionJob` | トラブル関数を 1 回だけ即時実行（テスト目的） | admin のみ + 開発／ステージング環境のみ |

---

## 📅 3.5 通知センター（Console）

- `console_notifications` テーブル（5.2）を SSE / ポーリングで購読
- 重要度（INFO / WARN / ERROR）と **`target_subscription_tag` / `target_user_id`** でフィルタ（r2 で購読タグ化／R-9）
- 既読／未読管理あり
- SES メール通知は通知センターと**同じ payload を別経路で送る**設計（重複は許容、欠損ゼロを優先）。SES は `level >= WARN` のみ送出（INFO はメールしない）
- 抑制された通知（`suppressed = true`）は通知センター側には残し、ダイジェスト 1 通で件数を要約配信（R-10）

---

# ⚠ 4. トラブル関数（フォルトインジェクション）

## 4.1 設計原則（r2 で四重防御に拡張）

1. **本番環境では機能フラグで強制 OFF**：
   - `amazia.simulation.fault-injection.enabled` を本番 `application-prod.yml` で **`false` 固定**（環境変数での上書き禁止）
   - 本番起動時に `enabled=true` を検出したら **`ApplicationContext` ロード失敗**で停止する Validator を組み込む（カテゴリ7-2 の H2 起動失敗パターンを逆手に取った安全装置）
2. **`@Profile("!production")` による Bean 非登録（r2 で本文化／r4 で対象を Job まで拡大／M-4 対応）**：以下のすべての Bean に `@Profile("!production")` を付与し、本番プロファイル時はクラスがクラスパスから読まれても **DI コンテナに登録されない**：
   - `SalesMismatchInjector` / `InventoryMismatchInjector` / `DeliveryTroubleInjector`（注入実体）
   - **`TriggerFaultInjectionJob`（オンデマンド Job クラス自体／r4 で追加）** ：これにより `POST /api/console/batch/TriggerFaultInjectionJob/run` の呼び出しは **本番では `@Autowired Optional<TriggerFaultInjectionJob>` が空 → Controller が DI 解決失敗を 404（`Job not found`）として返す**。「API 自体が 404」が成立する根拠はここ
   - `TriggerFaultInjectionController` の Job 探索ロジックは `Map<String, OnDemandJob>` を `@Autowired` で受け取り、本番では `TriggerFaultInjectionJob` がそもそも Map に入らないため、`jobs.get("TriggerFaultInjectionJob")` が `null` で 404 確定
   誤って機能フラグが ON 化されても、呼び出し元（`@Autowired Optional<SalesMismatchInjector>` / Job ルックアップ Map）が空になり、注入処理は走らない
3. **発火履歴を `fault_injection_logs` に記録**：いつ・どのジョブで・どの確率で・誰が承認したか
4. **`fault_injection_logs.environment='production'` を DB CHECK で物理拒否**（5.3）：万が一前 3 段がすり抜けても DB が INSERT を拒否する
5. **トラブル関数の発火は必ず `inventory_movements` の `movement_type='adjustment'` で記録**：在庫整合性チェックが「人為的に注入されたズレ」を後で説明可能にする（R-2 対応で `DeliveryTroubleInjector` も含む）

これにより **機能フラグ + 起動時 Validator + `@Profile` + DB CHECK + `inventory_movements` 補償** の五重防御となる。

## 4.2 各関数

### ① 売上不一致関数（`SalesMismatchInjector`）
- 振込確認疑似 API のレスポンスを一定確率で `MISMATCH` に書き換える
- DB は変更しない（疑似 API のレスポンス層のみ）

### ② 在庫数不一致関数（`InventoryMismatchInjector`）
- `inventories.quantity` を ±1 〜 ±3 の範囲でランダム調整
- 同時に `inventory_movements` に `movement_type='adjustment', comment='[fault_injection] simulated drift'` を INSERT
- 整合性チェックバッチが「これは人為的注入」と区別できる

### ③ 配送トラブル関数（`DeliveryTroubleInjector`）

**対象限定（R-2 対応：r2 で確定）：**
本注入は **`shipping_status_id = PENDING` の `deliveries`（出荷未完了）に限定**する。これにより：

- phase14 r3 で確定した「予約購入の出荷時在庫減算フック」は `PENDING → SHIPPED` 遷移時にのみ発火するため、`PENDING → CANCELED / DELIVERY_FAILED / RESCHEDULED` の遷移では**そもそも在庫減算フックは動かない**（=「フックが発火せずズレ続ける」問題が起きない）
- 通常購入（`is_preorder = false`）は注文確定時に既に在庫減算済みのため、本注入で `PENDING` を別状態に飛ばしても在庫整合性は崩れない
- ただし、`PENDING → CANCELED` の場合は本来であれば在庫を**復元**すべきケースが現実運用には存在する（注文取消による在庫戻し）。本注入はあくまで**シミュレーション**であり、実業務の取消フローを発火させるわけではない。よってこの「本来の復元処理」が走らないことによる帳簿ズレを**補償レコードで明示的に説明**する

**補償 `inventory_movements` レコードの必須化（R-2 対応：r2 で確定）：**

本注入が `PENDING → CANCELED / DELIVERY_FAILED / RESCHEDULED` を実施する際は、対象 `deliveries.sales_id` から `sales.product_id` / `sales.quantity` / `sales.is_preorder` を取得し、**「人為注入による帳簿ズレ」を `inventory_movements` の `movement_type='adjustment'` で必ず記録**する。これにより `InventoryConsistencyCheckJob`（3.1 ①）が「これは人為的注入」と区別できる：

| 注入後の状態 | 補償 movement の `quantity` | comment |
|--------------|--------------------------|---------|
| `PENDING → CANCELED`（通常購入：注文時減算済の在庫を「業務上は復元すべきだが本注入では復元しない」） | `0`（数値ズレなし） | `[fault_injection][delivery] cancel without inventory restore (sales_id=N)` |
| `PENDING → CANCELED`（予約購入：未だ減算されていない） | `0` | `[fault_injection][delivery] cancel of preorder (sales_id=N)` |
| `PENDING → DELIVERY_FAILED` / `RESCHEDULED` | `0` | `[fault_injection][delivery] {status} simulation (sales_id=N)` |

`quantity = 0` は phase14 S14-7 の `CHECK (quantity != 0)` 制約と衝突するため、**`movement_type='adjustment'` のみ `quantity = 0` を許容するよう本書で `CHECK` 制約を緩和することを phase14 r4 へ要請**する（後述 13.1）。

**phase14 S14-7 の符号 CHECK の解釈（N-4 対応：r3 で明文化）：**
phase14 設計書 r3 の S14-7 は `movement_type='adjustment'` を「**符号自由**（棚卸補正は ± 双方あり得る）」と定義している（phase14_shipping.md の対応箇所を 13.1 から参照可能にする）。よって `adjustment` の `quantity` は正・負どちらでも CHECK は通る。緩和されるまでの暫定運用では **`+1` 固定**を採用し、テスト期待値の確定性を保証する：

| 状況 | 暫定 `quantity` | 緩和後の最終形 |
|------|----------------|---------------|
| `[fault_injection][delivery]` 全パターン | **`+1` 固定**（`-1` は使わない） | `0`（緩和後） |

`±1` の二択は r2 までの記述で「テスト時にどちらが入るか不定」を生む懸念があったが、r3 で **`+1` 一択**に確定する。`comment` 接頭辞 `[fault_injection][delivery][quantity_dummy]` でダミー値であることを引き続き明示する。

**Repository 直接呼び出しの維持：**
- phase14 で「マスタ存在 ≠ 入力許容」（Q14-4）であり、Service 層の許容ステータスリストから `CANCELED / DELIVERY_FAILED / RESCHEDULED` への遷移は拒否される。本注入は **Service 層を経由せず Repository 直接呼び出し**でバリデーションをバイパスする
- Market の購入履歴にも反映される（`deliveries` を見て表示しているため／phase15 R-11 と整合）
- バイパスを許すのはこの 1 ジョブのみ。`@Profile("!production")` で `DeliveryTroubleInjector` Bean 自体を本番では存在させない（4.4 と整合）

## 4.3 確率調整

- `application-{profile}.yml` の `amazia.simulation.fault-injection.*-rate` で個別調整
- ステージング既定値：5% / 5% / 10%
- 開発既定値：環境変数で上書き可能（テスト時に 100% にして必ず発火させたい等）

## 4.4 環境フラグの整合性検査（テスト観点）

- 起動時 Validator のテスト：`application-prod.yml` を読み込ませた状態で `enabled=true` を強制すると `ApplicationContext` がロード失敗することを JUnit で検証

---

# 5. DB 設計（追加分）

## 5.1 `batch_executions`（バッチ実行履歴）

| カラム | 型 | NULL | 説明 |
|--------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| job_name | VARCHAR(100) | NOT NULL | `InventoryConsistencyCheckJob` 等 |
| status | VARCHAR(20) | NOT NULL | `RUNNING` / `SUCCESS` / `FAILED` / `PARTIAL` |
| started_at | DATETIME | NOT NULL | |
| finished_at | DATETIME | NULL | |
| target_count | INT | NULL | 処理対象件数 |
| success_count | INT | NULL | |
| failure_count | INT | NULL | |
| error_summary | TEXT | NULL | 失敗時のスタックトレース要約 |
| triggered_by | VARCHAR(50) | NOT NULL | `scheduler` / `manual:user_id=N` |
| created_at | DATETIME | NOT NULL | |

**インデックス：** `(job_name, started_at DESC)` / `(status)`

## 5.2 `console_notifications`（通知センター／r2 で購読タグ化）

| カラム | 型 | NULL | 説明 |
|--------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| level | VARCHAR(10) | NOT NULL | `INFO` / `WARN` / `ERROR` |
| target_subscription_tag | VARCHAR(50) | NOT NULL | 受信購読タグ（`inventory_alerts` / `sales_alerts` 等。phase11 のロールではなく `notification_subscriptions.subscription_tag` と対応／R-9 対応） |
| target_user_id | BIGINT | NULL | 個別ユーザ宛の場合のみセット。NULL = タグ全員宛 |
| title | VARCHAR(200) | NOT NULL | |
| body | TEXT | NOT NULL | |
| payload_hash | VARCHAR(64) | NOT NULL | 重複抑制キー（R-10 対応／M-9 対応：r4 で NULL 衝突回避のため `NOT NULL` 化／J-5 対応：r6 でフォールバックを `job_name` ベースへ）。ハッシュ算出不能な通知は `SHA-256('no-payload:' + job_name)` を投入し、空文字や NULL を入れない |
| suppressed | BOOLEAN | NOT NULL | `TRUE` のときメール送出されなかった抑制レコード（既定 `FALSE` ／R-10 対応） |
| digest_sent_at | DATETIME | NULL | ダイジェスト送出済み時刻（NULL = 未送出／N-7 対応） |
| read_by_user_id | BIGINT | NULL | 既読ユーザ（NULL = 未読） |
| read_at | DATETIME | NULL | |
| source_job | VARCHAR(100) | NULL | 発信元ジョブ（紐付け） |
| source_batch_execution_id | BIGINT | NULL | `batch_executions.id` への FK |
| created_at | DATETIME | NOT NULL | |

**インデックス：**
- `(target_subscription_tag, read_by_user_id, created_at DESC)` ：タグ別の未読取得
- `(target_user_id, read_by_user_id, created_at DESC)` ：個別宛の未読取得
- `(payload_hash, created_at DESC)` ：抑制判定の高速化（R-10）
- `(suppressed, digest_sent_at, created_at)` ：ダイジェスト未送出レコードの抽出高速化（N-7）

## 5.3 `fault_injection_logs`

| カラム | 型 | NULL | 説明 |
|--------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| injector_name | VARCHAR(100) | NOT NULL | `SalesMismatchInjector` 等 |
| triggered_at | DATETIME | NOT NULL | |
| triggered_by | VARCHAR(50) | NOT NULL | `scheduler` / `manual:user_id=N` |
| environment | VARCHAR(20) | NOT NULL | `dev` / `staging`（本番に出ない構造的保証） |
| target_summary | TEXT | NULL | 影響を受けた `sales.id` / `inventories.id` 等 |
| created_at | DATETIME | NOT NULL | |

`environment = 'production'` の INSERT を **DB の `CHECK` 制約で物理拒否**する：

```sql
ALTER TABLE fault_injection_logs
  ADD CONSTRAINT chk_fault_logs_no_prod
  CHECK (environment IN ('dev', 'staging'));
```

これにより、万が一機能フラグの起動時 Validator が漏れても、本番 DB は INSERT を拒否し、誤発動の傷を最小化する。**DB レベルで二重防御**（phase14 S14-7 と同思想）。

## 5.4 `monthly_sales_reports` / `yearly_sales_reports`

| カラム | 型 | 説明 |
|--------|-----|------|
| id | BIGINT PK | |
| year | SMALLINT | |
| month | TINYINT | （月次のみ） |
| product_id | BIGINT NULL | NULL = 全商品集計 |
| payment_method_id | BIGINT NULL | |
| shipping_method_id | BIGINT NULL | |
| is_preorder | BOOLEAN NULL | |
| total_amount | BIGINT NOT NULL | |
| total_quantity | INT NOT NULL | |
| created_at | DATETIME NOT NULL | |

集計軸が NULL のレコードは「総合計」を表す（オーバービュー）。インデックスは `(year, month, product_id)` / `(year, month, payment_method_id)` 等。

---

# 6. 通知設計

## 6.1 SES 送信（phase13 と統合）

- 送信元：`no-reply@amazia-portfolio.dedyn.io`（phase13 でドメイン認証済）
- バッチ通知用テンプレートは `config/mail/batch_*.yml` で管理：
  - `batch_inventory_inconsistency.yml`
  - `batch_sales_mismatch.yml`
  - `batch_delivery_delay.yml`
  - `batch_postal_integrity_failed.yml`
  - `batch_job_failed.yml`（ジョブ失敗時の汎用）
- リトライは phase13 と同じ指数バックオフ（1s → 2s → 4s）

## 6.2 購読者解決アルゴリズム（R-9 対応：r2 で新設）

**前提（phase11 反映）：** phase11 の `roles` は **admin / user の 2 種のみ**。`inventory_manager` のような細粒度ロールは存在しない。本書の通知購読は「役職」ではなく**通知タグ**で表現する。

### 6.2.1 `notification_subscriptions` テーブル（新規）

| カラム | 型 | NULL | 説明 |
|--------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| user_id | BIGINT | NOT NULL | `users.id` への FK（phase11） |
| subscription_tag | VARCHAR(50) | NOT NULL | 購読タグ（`inventory_alerts` / `sales_alerts` / `delivery_alerts` / `postal_alerts` / `batch_failure` 等） |
| email_enabled | BOOLEAN | NOT NULL | SES メール送信を有効にするか（`true` 既定） |
| in_app_enabled | BOOLEAN | NOT NULL | Console 通知センター表示を有効にするか（`true` 既定） |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

**インデックス：** `UNIQUE (user_id, subscription_tag)` / `idx_subscription_tag (subscription_tag)`

**初期データ：** マイグレーション時に **admin ロールの全ユーザを全タグに自動購読**させる（既定の安全側）。タグからの脱退は Console UI から個別に行う。

### 6.2.2 解決アルゴリズム

```
NotificationDispatcher.dispatch(subscription_tag, level, payload):
    subscribers = SELECT u.email, ns.email_enabled, ns.in_app_enabled
                  FROM notification_subscriptions ns
                  JOIN users u ON u.id = ns.user_id
                  WHERE ns.subscription_tag = :subscription_tag
                    AND u.active_flag = true

    for sub in subscribers:
        if sub.email_enabled and level >= WARN:
            SES.send(to=sub.email, ...)         # WARN/ERROR のみメール（INFO はメールしない）
        if sub.in_app_enabled:
            INSERT console_notifications (target_subscription_tag=:subscription_tag,
                                           target_user_id=sub.user_id, ...)
```

`console_notifications` のスキーマは r2 で `target_subscription_tag VARCHAR(50)` ＋ `target_user_id BIGINT NULL` に置き換え済（5.2 参照）。本書 r3 以降は購読タグベースで一貫している。

### 6.2.3 各ジョブと購読タグの対応

| ジョブ | `subscription_tag` |
|--------|------------------|
| `InventoryConsistencyCheckJob` | `inventory_alerts` |
| `SalesReconciliationJob`（在庫照合不一致） | `inventory_alerts` |
| `SalesReconciliationJob`（振込不一致） | `sales_alerts` |
| `DeliveryStatusAdvanceJob`（遅延候補） | `delivery_alerts` |
| `PostalAddressIntegrityCheckJob` | `postal_alerts` |
| バッチ失敗（汎用） | `batch_failure` |

## 6.3 Slack Webhook（任意・将来）

`amazia.batch.notifications.slack-webhook-url` を空文字で**既定 OFF**。値が設定されていれば追加で投げる。

## 6.4 通知のレートリミット（R-10 対応：r2 で具体化）

r1 の `(job_name, target_role)` 抑制（旧表記）では、異なる商品の在庫不一致が両方握りつぶされる検知漏れが発生する。r2 では以下の 2 段階に変更（r3 で `target_role` を `subscription_tag` に置換済の前提）：

### 6.4.1 重複抑制（payload_hash 単位）

- 抑制キーを `(job_name, subscription_tag, payload_hash)` とする。`payload_hash` は **通知本文の主要キー**（例：`inventory_alerts` なら `product_id` × `warehouse_id`、`delivery_alerts` なら `deliveries.id`）から SHA-256 で算出
- 同一 `payload_hash` の通知は **直近 `amazia.batch.rate-limit.duplicate-suppression-minutes`（既定 60 分）以内なら抑制**
- 異なる商品・異なる配送の不整合は別通知として発火するため、検知漏れは起きない
- **フォールバック規則（K-1 対応：r5 で明記／J-5 対応：r6 で `job_name` ベースに修正）：** 主要キーが取得できない通知（バッチ汎用失敗通知 `batch_failure` 等）は **`SHA-256('no-payload:' + job_name)` をフォールバック値**として投入する（5.2 / 6.4.3 の `payload_hash NOT NULL` と整合）。**NULL や空文字は投入しない**。`batch_execution_id` ベースだと毎回ユニークになり連続失敗が抑制されず R-10 本来の意図に反するため、**`job_name` ベース**にして「同一ジョブの連続失敗は抑制 + ダイジェスト集計に乗る」動作を成立させる。これにより SQL の `payload_hash IS NULL` を一致条件で扱う必要がなく、`(job_name, subscription_tag, payload_hash)` の `=` 比較で抑制判定が一貫する

### 6.4.2 ダイジェスト方式（抑制された件数を漏らさない／N-7 対応：r3 で永続化／M-6 対応：r4 で送信単位明文化）

- 抑制したレコードは `console_notifications.suppressed = true` で残しつつ、SES は送らない
- ダイジェスト送出は **専用ジョブ `DigestNotificationDispatchJob` が 5 分間隔（`@Scheduled(fixedRate = 300_000)`）** で走る
- 集計と送信は **「購読タグで集計 → タグの購読ユーザ全員に個別 SES 送信」の 2 重ループ**（M-6 対応）。SES 仕様上、宛先は個別 to で送るのが原則（BCC 集約は使わない）

**擬似コード（M-6 対応：r4 で明示）：**

```
DigestNotificationDispatchJob.run():
    for each subscription_tag in config('notifications.subscription_tags'):
        suppressed_records = SELECT id, created_at
                             FROM console_notifications
                             WHERE suppressed = true
                               AND digest_sent_at IS NULL
                               AND created_at < NOW() - INTERVAL 60 MINUTE
                               AND target_subscription_tag = :subscription_tag
        if suppressed_records is empty:
            continue

        body = format(
            "{tag}: 直近 {window} で {count} 件の通知が抑制されました。" +
            "詳細は Console 通知センター参照",
            tag=subscription_tag,
            window="<最古 created_at から現在まで>",
            count=len(suppressed_records))

        # タグの購読者全員に個別送信（SES は宛先 1 件ごとに to を切る）
        subscribers = NotificationDispatcher.resolveSubscribers(
                          subscription_tag, level=WARN)
        for subscriber in subscribers:
            SES.send(to=subscriber.email, subject=..., body=body)

        # 送信完了したレコードのみ digest_sent_at を更新（次回起動の二重送信防止）
        UPDATE console_notifications
            SET digest_sent_at = NOW()
            WHERE id IN (suppressed_records.ids)
```

**「タグ単位集計 → ユーザ単位送信」の意義（M-6 対応）：**
- 1 つの `subscription_tag` に対して 5 人購読者がいれば **SES は 5 通**送られる（タグ集約 1 通ではない）
- 各メールの本文は同一だが宛先は個別。SES の `to` を個別に切ることでスパムフィルタ・配信統計・解除管理が個別に動く
- `digest_sent_at` の UPDATE は **タグ単位 1 回**（ユーザ × タグの掛け算でのレース回避）。1 ユーザだけ SES が失敗した場合でも `digest_sent_at` は埋まる前提で、SES 失敗は `MailSendException` のリトライ（3 章共通制御 R-6）に委譲

**永続化の根拠（N-7 対応）：** Spring `TaskScheduler` の単発タスク登録方式（r2 の暗黙仕様）は JVM 再起動で消えるため、抑制中に再起動されると永久にダイジェストが飛ばない。専用ジョブ + DB 永続化に切り替えることで、再起動跨ぎでも検知漏れが起きない。

**手動起動 API の不在（K-5 対応：r5 で明記）：** `DigestNotificationDispatchJob` は **5 分間隔の自動実行のみ**を提供し、`POST /api/console/batch/{job}/run` 経由の手動起動 API は提供しない。よって `BATCH_MANUAL_TRIGGER_ENABLED` の影響は受けない。テスト時は JUnit から直接 Bean を呼ぶか、`BATCH_DIGEST_ENABLED` をトグルして検証する。3.4 オンデマンドバッチ表にも本ジョブは含めない。

これにより、件数規模が大きい不整合で SES が洪水化することなく、かつ「合計 N 件中 1 件のみ通知」の検知漏れも構造的に防げる。

### 6.4.3 `console_notifications` の追加カラム（5.2 の r2 修正・r3 で `digest_sent_at` 追加）

```
suppressed         BOOLEAN     NOT NULL DEFAULT FALSE  -- 抑制された通知（メール送出されなかった）
payload_hash       VARCHAR(64) NOT NULL                -- 抑制キー（M-9 で NOT NULL 化。算出不能時は SHA-256('no-payload:' + job_name)／J-5）
digest_sent_at     DATETIME    NULL                    -- ダイジェスト送出済み時刻（NULL = 未送出／N-7）
```

5.2 のテーブル定義は以下の追加カラムも持つ前提に r3 で更新する：

---

# 7. 環境変数（新規）

phase17 で追加する環境変数（[user memory: env_vars_and_tests] に従い、`docker-compose.yml` / `phpunit.xml` / `application-test.properties` をセット更新）：

| 変数名 | 用途 | 既定値 |
|--------|------|-------|
| `BATCH_ENABLED` | バッチ全停止スイッチ。r3 で `BATCH_SCHEDULER_ENABLED` ＋ `BATCH_MANUAL_TRIGGER_ENABLED` の alias に格下げ（R-8） | `true` |
| `BATCH_SCHEDULER_ENABLED` | 業務バッチクラスの ON/OFF（`@ConditionalOnProperty` を**業務バッチクラス個別に付与**／M-2） | `true` |
| `BATCH_MANUAL_TRIGGER_ENABLED` | 手動起動 API の ON/OFF（`false` のとき Controller 側で 503／M-1） | `true` |
| `BATCH_DIGEST_ENABLED` | `DigestNotificationDispatchJob` の ON/OFF。`BATCH_SCHEDULER_ENABLED` から独立（M-2） | `true` |
| `SIMULATION_FAULT_INJECTION` | トラブル関数 ON/OFF | `false`（本番は `false` 固定の Validator あり） |
| `SIM_SALES_RATE` | 売上不一致確率 | `0.05` |
| `SIM_INV_RATE` | 在庫不一致確率 | `0.05` |
| `SIM_DELIVERY_RATE` | 配送トラブル確率 | `0.10` |
| `BATCH_DAILY_CRON` | 日次 cron 上書き | `0 30 3 * * *` |
| `BATCH_MONTHLY_POSTAL_CHECK_CRON` | 月次郵便整合性チェック cron | `0 30 4 1 * *` |
| `BATCH_YEARLY_CRON` | 年次 cron | `0 0 5 1 1 *` |
| `BATCH_NOTIFICATIONS_SLACK_WEBHOOK_URL` | 任意 | `""` |
| `BANK_TRANSFER_VERIFICATION_MODE` | 振込確認モード（`disabled` / `mock-match` / `mock-mismatch-rate`／R-1） | `disabled`（本番） |
| `SALES_RECONCILIATION_WAREHOUSES` | 売上再計算の対象倉庫 ID（CSV／R-3） | `1` |
| `BATCH_RATE_LIMIT_SUPPRESSION_MINUTES` | 同 payload 抑制時間（R-10） | `60` |
| `BATCH_RATE_LIMIT_DIGEST_ENABLED` | 抑制後ダイジェスト送信（R-10） | `true` |

**Step 1 着手前の確認チェックリスト（K-10 対応：r5 で追加）：**
上記すべての新規変数について、`coding_guidelines.md` 4-3「新規環境変数追加時のチェックリスト」に従い、以下のセット更新漏れを Step 1（DB マイグレーション）着手前に必ずチェックする：

- [ ] `docker-compose.yml` の amazia-core サービスに全新規変数を追記
- [ ] `application-test.properties` にテスト用既定値を追記
- [ ] `.env.example`（存在すれば）にも追加
- [ ] テストコードが `@Value` / `config()` 経由で値を参照していること（ハードコード禁止）
- [ ] **本番 Validator の動作確認（J-6 対応：r6 で追加）**：`application-prod.yml` を読み込んだ状態で、以下のいずれかを環境変数で強制した場合、`ApplicationContext` ロードが起動時 Validator で失敗することを 1 度確認する：
  - `SIMULATION_FAULT_INJECTION=true`（4.1.1 / N-6）
  - `BANK_TRANSFER_VERIFICATION_MODE=mock-mismatch-rate`（N-6 マトリクス）

これは [user memory: env_vars_and_tests] と整合し、過去の 009 教訓（環境変数管理漏れ）を再発させないための構造的ガード。本番 Validator のテストは Step 1（DB マイグレーション）着手前に 1 度実行し、フォルトインジェクション四重防御の起動段階が機能していることを確認する。

---

# 8. 操作ログ規約（phase14 命名規約に準拠）

- `screen_name`：`BatchScheduler`（定期）/ `ConsoleBatchManagementPage`（手動起動／phase15 RR-10 命名規則）
- `api_name`：手動起動の場合は `POST /api/console/batch/{job_name}/run`
- `comment` プレフィックス（phase15 RRR-5 と同じ思想で集計可能化）：
  - `[batch_inventory_check]`
  - `[batch_sales_reconciliation]`
  - `[batch_delivery_advance]`
  - `[batch_postal_check]`
  - `[fault_injection]`

将来 `operation_logs.reason_code` カラムが追加されたら、上記プレフィックスは `reason_code` へ昇格する（phase14 P14-5 / phase15 RRR-5 と整合）。

---

# 9. 採用しなかった選択肢

| 案 | 不採用理由 |
|---|----------|
| EventBridge + Lambda（バッチ全面） | 無料枠・運用分散コスト・Java コールドスタート（phase13 で同様判断済） |
| Spring Batch（Chunk 指向）一式導入 | 件数規模が小さい（KEN_ALL.CSV 12 万件以外は数千件オーダー）、`@Scheduled` + 単純 SQL で十分。依存追加で `-Xmx384m` を圧迫 |
| ShedLock / Quartz クラスタ | 単一 EC2 構成のため不要 |
| バッチ専用コンテナの分離 | t3.micro で 2 コンテナ分の Java VM は無料枠を超える |
| トラブル関数を本番でも有効化 | 顧客影響リスクが見合わない。本書では構造的に本番禁止 |
| `inventories` を直接書き換える整合性自動補正 | 人為ミスを増幅させる。検知のみで運用判断に委ねる |
| 配送ステータスの自動遷移（PENDING → SHIPPED 等） | phase14 r3 の「予約購入は出荷時減算」フックが意図せず発火する事故を招く |

---

# 10. リスクと対策

| リスク | 対策 |
|------|------|
| `@Scheduled` の重複起動（再起動直後など） | **JVM 内 `ConcurrentHashMap<jobName, AtomicBoolean>` ベースの軽量ロック**（R-7／3章共通制御）。`SELECT ... FOR UPDATE` は採用しない |
| JVM 異常終了で `batch_executions.status = RUNNING` が孤立 | **`ApplicationReadyEvent` 起動時クリーンアップ**で 24 時間以上前の `RUNNING` を `FAILED, error_summary='[recovery] orphaned by JVM restart'` に強制遷移（R-7／3章共通制御） |
| バッチ実行で EC2 メモリ逼迫 | `-Xmx384m`（[user memory: phaseX4_t3micro_recovery]）内で動作するよう、SQL は **ストリーミングで集約**（`ScrollableResults` か LIMIT/OFFSET ページング）。`inventories` 全件を Java ヒープに乗せない |
| トラブル関数の本番誤発動 | **機能フラグ + 起動時 Validator + `@Profile("!production")` による Bean 非登録 + DB CHECK 制約 + `inventory_movements` 補償** の **五重防御**（4.1） |
| 振込確認疑似 API が本番で常時 MISMATCH を吐く誤通知 | **`BANK_TRANSFER_VERIFICATION_MODE=disabled` を本番既定**として振込確認部分自体をスキップ（R-1／3.1 ③） |
| 不適切なリトライによる二重発火・無意味な再実行 | リトライ対象を **I/O 系一過性例外のみ**に限定。`DataIntegrityViolationException` 等は即 `FAILED`（R-6／3章共通制御） |
| バッチ失敗の検知漏れ | SES 通知（`level >= WARN`）+ Console 通知 + CloudWatch Logs Metric Filter（`FAILED` 検出）でアラーム。`PARTIAL` の扱い（同等アラート対象とするか、別閾値で扱うか）は **R-12（r6 候補／14.1）で確定予定**（J-3 対応：r6 で「未確定」明示） |
| 通知の洪水化 | `(job_name, subscription_tag, payload_hash)` で重複抑制 + 抑制から 60 分後にダイジェスト 1 通（R-10／6.4） |
| `inventory_movements` の累積コストの肥大化 | 年次アーカイブ（`OperationLogArchiveJob` と同パターンで `inventory_movements_archive` を将来追加） |
| **`BATCH_DIGEST_ENABLED=false` 長期運用時の抑制レコード滞留（K-7 対応：r5 で方針確定／J-2 対応：r6 で `ConsoleNotificationsArchiveJob` 実装ガイド明記）** | `console_notifications.suppressed=true AND digest_sent_at IS NULL` が `-Xmx384m` ＋ t3.micro で蓄積するリスク（日次 5 件 ×30 日 ×6 タグで 900 件以上）。**3.3 ③ で `ConsoleNotificationsArchiveJob` を新設**し、既読から 1 年・抑制ダイジェスト送出済から 1 年・無条件 1 年経過のいずれかでアーカイブ。短期運用ならフラグを `true` に戻して滞留分を 1 通で吸収（再起動跨ぎ欠損ゼロ／K-4 E2E と整合） |
| KEN_ALL.CSV のフォーマット変更 | 月次整合性チェック（3.2 ①）でサンプル引きが失敗 → 通知 → 人手対応 |

---

# 11. ステップ一覧

| # | ステップ | 対象 | 状態 |
|---|---------|------|------|
| 0 | 設計書 r6 確定（本書） | docs | 🟡 本セッション |
| 1 | DB マイグレーション（`batch_executions` / `console_notifications` / `fault_injection_logs` / `*_sales_reports`） | core | 🔲 |
| 2 | バッチ共通基盤（`AbstractBatchJob` / `batch_executions` 制御 / リトライ / 通知統合） | core | 🔲 |
| 3 | 日次ジョブ 5 本実装 + JUnit | core | 🔲 |
| 4 | 月次・年次ジョブ実装 + JUnit | core | 🔲 |
| 5 | フォルトインジェクション + 起動時 Validator + 環境分離 | core | 🔲 |
| 6 | Console UI（バッチ実行履歴・通知センター・手動起動） | console | 🔲 |
| 7 | SES テンプレートと通知統合 | core / AWS | 🔲 |
| 8 | E2E（不整合データを仕込み → 日次バッチ → 通知到達まで） | 全体 | 🔲 |
| 9 | `docker compose down -v && docker compose up --build` 完走確認（カテゴリ9） | 全体 | 🔲 |
| 10 | ドキュメント更新（README / トラブル記録雛形 / phase11_20 進捗） | docs | 🔲 |

---

# 12. TDD テストケース

## 12.1 Amazia Core / JUnit

### 正常系
- 日次の `InventoryConsistencyCheckJob` で `inventories.quantity == SUM(inventory_movements.quantity)` が成立するデータ群について不整合 0 件と判定できる
- **`quantity = 0` 商品の検知不能性（K-8 / M-8）**：`inventories.quantity = 0` かつ `inventory_movements` レコードが存在しない商品について、本ジョブは「不整合 0 件」と判定する（`0 = SUM(空集合) = 0` で偶然一致）。**phase14 r4 で `CHECK (quantity != 0)` が緩和されるまで構造的限界として現時点では正常動作**。テストコメントに「phase14 r4 完了時に本テストの期待値を反転させる必要がある」旨を明記し、緩和タイミングでの再評価ポイントを残す
- `SalesReconciliationJob` の再構築 SQL 結果が phase14 r3 の再構築 SQL と一致する（**予約購入は出荷済みのみ販売減算に算入**）
- `DeliveryStatusAdvanceJob` で `scheduled_date < TODAY - 7` の `SHIPPED` を遅延候補として抽出し通知ペイロードを生成する
- `SessionAndTokenSweepJob` で `expires_at < NOW()` の `market_sessions` / `market_customers_password_reset_tokens` が削除される
- `PostalAddressIntegrityCheckJob` でサンプル郵便番号が引け、件数が中央値 ± 5% 以内なら成功
- `MonthlySalesReportJob` が前月分の `sales` を商品別・決済方法別・配送方法別・予約／通常別で集計して `monthly_sales_reports` へ保存する
- `YearlySalesReportJob` が `monthly_sales_reports` から年次集計を生成する
- `OperationLogArchiveJob` で 1 年超の `operation_logs` が `operation_logs_archive` へ移送される
- バッチ実行ごとに `batch_executions` に `RUNNING → SUCCESS/FAILED/PARTIAL` の遷移が記録される
- **`DigestNotificationDispatchJob`（N-7 / M-6）**：5 分間隔で起動し、`suppressed=true AND digest_sent_at IS NULL AND created_at < NOW() - INTERVAL 60 MINUTE` のレコードを購読タグ単位で集計する
- **ユーザ単位 SES 送信（M-6）**：購読タグに購読者 5 人がいる場合、**SES は 5 通**送られる（同一本文・個別 to）。BCC 集約は禁止
- 送出後は対象レコードに `digest_sent_at` がセットされる（タグ単位 1 回の UPDATE）
- ダイジェスト送出後の再起動でも、`digest_sent_at IS NOT NULL` レコードは二重送出されない（永続化の検証）
- 一部の SES 送信のみ失敗した場合、`digest_sent_at` は更新済（再送リトライは `MailSendException` リトライ機構に委譲）
- 全バッチが JST 0:00 / 03:30 / 04:30 / 05:00 基準で `Asia/Tokyo` に従って起動する（`@SchedulingConfigurer` の TZ アサート）

### 異常系
- **多重起動防止（R-7 / N-8 対応：r3 で並行実行ベースへ）**：同一ジョブを 2 つの `Thread` から `CountDownLatch` で同時にトリガし、以下を確認する。リフレクションによる実装詳細検証はせず、振る舞いだけを検証する：
  - 1 スレッドのみが `batch_executions` に新規 `RUNNING` を作成する
  - もう 1 スレッドはスキップされ `batch_executions` に新規行を作らない
  - 先行スレッドの完了後、改めて後続スレッドを起動すると今度は通常どおり動作する（ロックが解放されている）
- **ロック解放の保証（N-8 補強）**：先行スレッドのバッチ本体内で例外を投げた場合でも、`finally` 節でロックが解放され、後続起動が成立することを検証（リフレクションを使うのはこの解放確認の `AtomicBoolean` 状態確認のみ）
- **起動時クリーンアップ**：`started_at` が 24 時間以上前の `RUNNING` レコードが `ApplicationReadyEvent` 後に `FAILED, error_summary='[recovery] ...'` に強制遷移する（R-7）
- 3 回リトライ後の失敗で `batch_executions.status = FAILED` + SES 通知 + `console_notifications` への INSERT が同時に行われる
- **リトライ対象限定（R-6）**：`MailSendException` / `SocketTimeoutException` / `RestClientResponseException`（5xx） / `TransientDataAccessException` は 3 回リトライされる
- **リトライ対象外（R-6）**：`DataIntegrityViolationException` / `IllegalStateException` / 業務例外は **1 回で `FAILED` 確定**（リトライしない）
- `InventoryConsistencyCheckJob` が `inventories.quantity != SUM(movements)` の商品を検出し、`inventory_alerts` 購読者へ通知を生成する
- **`SalesReconciliationJob` の倉庫合算（R-3）**：`SALES_RECONCILIATION_WAREHOUSES=1,2` 設定下で 2 倉庫合算の `expected_qty` が算出され、片方倉庫だけの不整合も検知される
- **振込確認モード（R-1）**：
  - `mode=disabled` のとき `BankTransferMockClient.verify()` 自体が呼ばれず、`SalesReconciliationJob` は在庫照合のみ実施する
  - `mode=mock-match` のとき `verify()` は常に MATCH を返す
  - `mode=mock-mismatch-rate` ＋ `SIMULATION_FAULT_INJECTION=false` の組合せは **起動時 Validator で `ApplicationContext` ロード失敗**
- `DeliveryStatusAdvanceJob` は `PENDING → SHIPPED` / `SHIPPED → DELIVERED` の自動遷移を**行わない**（バッチで状態を進めない契約）
- バッチ失敗で例外が発生してもトランザクション境界が守られ、`batch_executions` のステータスだけは `REQUIRES_NEW` の独立トランザクションで更新される
- **通知レートリミット（R-10）**：
  - 同一 `(job_name, subscription_tag, payload_hash)` の通知が 60 分以内に重複しない（payload_hash 単位）
  - 異なる `payload_hash`（例：商品 A と商品 B の在庫不整合）は両方とも通知される（検知漏れ無し）
  - 抑制された通知は `console_notifications.suppressed = true` で残る
  - ダイジェスト 1 通が抑制から 1 時間後に「N 件抑制されました」と送られる
- バッチ実行中の DB 接続切断でリトライが発火し、3 回失敗で `FAILED` 確定する
- **`BATCH_SCHEDULER_ENABLED=false`（R-8 / N-10）**：定期バッチが登録されない（`@ConditionalOnProperty` で Bean 非登録）が、`BATCH_MANUAL_TRIGGER_ENABLED=true` のままなら手動起動 API は 200 を返す（不具合調査シナリオ）
- **`BATCH_MANUAL_TRIGGER_ENABLED=false`（N-10）**：手動起動 API が **HTTP 503** を返す
- **`BATCH_ENABLED=false`（後方互換 alias）**：`BATCH_SCHEDULER_ENABLED` ＋ `BATCH_MANUAL_TRIGGER_ENABLED` の両方が `false` になり、定期も手動も停止する。**ただし `BATCH_DIGEST_ENABLED` は影響を受けず、ダイジェストは継続動作する**（J-1 / N-7 整合）

### フォルトインジェクションの構造的安全装置（r2 で五重防御）
- `application-prod.yml` を読み込んだ状態で `SIMULATION_FAULT_INJECTION=true` を環境変数注入すると `ApplicationContext` ロードが失敗する（起動時 Validator）
- **`@Profile("!production")` の検証**：本番プロファイル時、`SalesMismatchInjector` / `InventoryMismatchInjector` / `DeliveryTroubleInjector` の Bean が `ApplicationContext` に存在しない（`getBean()` で `NoSuchBeanDefinitionException`）
- `fault_injection_logs.environment='production'` を INSERT しようとすると DB の CHECK 制約で拒否される
- ステージング環境で `SalesMismatchInjector` が起動すると、疑似 API のレスポンス層のみが書き換えられ DB は変わらない
- ステージング環境で `InventoryMismatchInjector` が発火すると、`inventories.quantity` 変更と同時に `inventory_movements` に `movement_type='adjustment', comment='[fault_injection][inventory] ...'` が INSERT され、整合性チェックバッチがそのレコードを「説明可能なズレ」と認識する
- **`DeliveryTroubleInjector` の対象限定（R-2）**：注入対象は `shipping_status_id = PENDING` の `deliveries` のみで、`SHIPPED` 以降には注入しない（在庫減算フックが既に発火済のため）
- **`DeliveryTroubleInjector` の補償 movement（R-2）**：`PENDING → CANCELED / DELIVERY_FAILED / RESCHEDULED` 遷移時に必ず `inventory_movements` に `movement_type='adjustment', comment='[fault_injection][delivery] ...'` が INSERT される。これがないと `InventoryConsistencyCheckJob` が翌朝以降毎回不整合を検出する事故になる
- `DeliveryTroubleInjector` が `shipping_status_id` を CANCELED / DELIVERY_FAILED / RESCHEDULED に変更しても、Service 層の遷移ガードはバイパスされる（Repository 直接呼び出しの保証）。**この 1 ジョブのみ**に許される
- **暫定ダミー quantity（R-2 / N-4）**：phase14 r4 で `CHECK` が緩和されるまで、`DeliveryTroubleInjector` の補償 movement の `quantity` は **`+1` 固定**のダミー値となり、`comment` 接頭辞 `[fault_injection][delivery][quantity_dummy]` で識別できる（r3 で `+1` 一択に確定）

### `@Scheduled` 設定値の config 経由化（[user memory: env_vars_and_tests]）
- cron 値・確率値・通知 Webhook URL のすべてを `@Value("${amazia.batch...}")` 経由で取得し、テストコード内にハードコードしない
- `phpunit.xml` 相当として `application-test.properties` に上記すべての変数を明示的に定義

### H2 互換性（カテゴリ7-2）
- 新規テーブル（`batch_executions` / `console_notifications` / `fault_injection_logs` / `*_sales_reports`）は **JPA Entity だけで H2 にスキーマ生成可能**な範囲に収める
- MySQL 専用構文（`ON UPDATE CURRENT_TIMESTAMP` / インライン INDEX / `CHECK` 構文の差異）は migration ファイルにのみ書き、`application-test.properties` は `schema-locations=` 空指定の方針を維持
- `fault_injection_logs.environment` の `CHECK` 制約は H2 / MySQL 双方で通る構文（`CHECK (environment IN (...))`）に統一

### Docker 初回起動（カテゴリ9）
- `docker compose down -v && docker compose up --build` から起動して `batch_executions` / `console_notifications` / `fault_injection_logs` テーブルが作成され、初回 `@Scheduled` 起動時刻まで待機して 1 回だけ実行される
- 全業務バッチクラスが `BATCH_SCHEDULER_ENABLED=false` で起動時に Bean 非登録となる（K-2 / M-2）
- `DigestNotificationDispatchJob` も `BATCH_DIGEST_ENABLED=false` で Bean 非登録（独立フラグ／M-2）
- 後方互換：`BATCH_ENABLED=false`（alias）で両者が連動して `false` になり、全停止する（r5 ロードマップで deprecated 化予定／13.3）

## 12.2 Amazia Console / PHPUnit

- バッチ実行履歴画面で `batch_executions` の一覧が `started_at DESC` で表示される
- 失敗ジョブの `error_summary` が表示される
- 通知センターで `console_notifications` が **`target_subscription_tag` フィルタ**および `target_user_id` で絞り込まれる（R-9 整合）
- ログイン中ユーザの `notification_subscriptions` で購読しているタグの未読のみが画面に出る
- 既読操作で `read_by_user_id` / `read_at` が更新される
- 手動起動 UI から `RebuildInventoriesJob` を実行するには **admin 権限が必要**（401 / 403 を返す検証／phase11 のロール構造に合わせる）
- `BootstrapInventoryAdjustmentJob` の手動起動も admin 権限必須（401 / 403 を返す検証）
- `TriggerFaultInjectionJob` の手動起動は本番環境で **`@Profile("!production")` により Bean が存在せず、API 自体が 404 を返す**（UI 側もメニューに出ない／4.1 五重防御と整合）
- 手動起動時に `operation_logs` に `screen_name='ConsoleBatchManagementPage'` / `api_name='POST /api/console/batch/{job_name}/run'` が記録される

## 12.3 E2E

- 不整合データを仕込み（`inventories.quantity` を直接 +1）→ 日次バッチ実行 → `inventory_alerts` 購読者の Inbox に SES メール到達 + Console 通知センター未読バッジ +1
- ステージングで `SIMULATION_FAULT_INJECTION=true` にして 1 日運転 → 各 `*_rate` の発火回数が確率と統計的に整合する（許容誤差内）
- `BATCH_ENABLED=false`（後方互換 alias）で再起動 → 業務バッチと手動 API は停止し `batch_executions` に新規行が増えない。**ただし `DigestNotificationDispatchJob` は `BATCH_DIGEST_ENABLED` が独立のため継続動作**（J-1 / N-7 整合）。「全停止」を完全に達成するには `BATCH_ENABLED=false` ＋ `BATCH_DIGEST_ENABLED=false` の併用が必要
- **`BATCH_SCHEDULER_ENABLED=false` ＋ `BATCH_MANUAL_TRIGGER_ENABLED=true` で起動（M-5 / N-10 想定シナリオ）** → 定期バッチは登録されず `batch_executions` に新規行は増えないが、**手動で `RebuildInventoriesJob` を起動すると 200 で実行される**（不具合調査シナリオ）
- **`BATCH_MANUAL_TRIGGER_ENABLED=false` で起動（M-5）** → `POST /api/console/batch/{job}/run` が **HTTP 503** を返し、Bean 自体は生きているのでフラグを `true` に戻すと再起動なしで即時 200 復帰
- **`BATCH_SCHEDULER_ENABLED=false` ＋ `BATCH_DIGEST_ENABLED=true` で起動（M-2 想定シナリオ）** → 業務バッチは止まるが、`DigestNotificationDispatchJob` は 5 分間隔で起動し続け、抑制された通知のダイジェストが届く
- **`BATCH_DIGEST_ENABLED=false` 単独で起動（K-4 / M-2）** → `DigestNotificationDispatchJob` Bean が非登録となり、抑制された通知が `digest_sent_at` 未設定のまま蓄積する。フラグを `true` に戻して再起動すると、蓄積分が 1 通のダイジェストにまとめて送出される（再起動跨ぎでの欠損ゼロ／N-7 永続化との整合性確認）
- **本番プロファイル起動時（M-4）** → `POST /api/console/batch/TriggerFaultInjectionJob/run` が **HTTP 404** を返す（`TriggerFaultInjectionJob` Bean 自体が `@Profile("!production")` で非登録）

---

# 13. 他フェーズへの要請事項（r2 で拡張）

## 13.0 phase11（社員認証）への要請事項（r3 で新規／N-9 対応）

| 項目 | 内容 |
|------|------|
| `users` ロール = admin の作成・昇格時の `notification_subscriptions` UPSERT フック（K-3 対応：r5 で UPSERT 一本化） | phase11 の `CreateUserService` / `UpdateUserService`（ロール変更）に、対象ユーザのロールが `admin` になった時点で `notification_subscriptions` の全タグ（`inventory_alerts` / `sales_alerts` / `delivery_alerts` / `postal_alerts` / `batch_failure`）を **`INSERT ... ON DUPLICATE KEY UPDATE email_enabled=true, in_app_enabled=true`（UPSERT）** するフックを追加する。これにより「初回 admin 化（過去履歴なし）」と「再 user → admin 昇格（過去の論理停止データあり）」が**同じコードパス**で処理され、`UNIQUE (user_id, subscription_tag)` 違反を回避できる。これがないと「マイグレーション後に作られた新規 admin だけ通知が来ない」運用バグが入る。`active_flag = false` への遷移時はレコードを物理削除せず、`email_enabled=false, in_app_enabled=false` の論理停止に切り替えるだけで履歴を残す |
| **admin → user 降格時の購読論理停止（M-3 対応：r4 追加・r5 で UPSERT 整合）** | phase11 の `UpdateUserService`（ロール変更）で対象ユーザが `admin → user` に降格された時点で、対象ユーザの `notification_subscriptions` 全行を `email_enabled=false, in_app_enabled=false` に **論理停止**する。物理削除しない理由は「再昇格時の購読履歴復元」のため。再昇格時の処理は上記行の UPSERT が単独で担い、降格／昇格それぞれの分岐ロジックは不要。これがないと **降格された元 admin に通知が飛び続ける情報漏洩リスク**が残る |
| 自動購読タグの一覧の単一情報源化 | 自動購読すべきタグ一覧は本書 6.2.3 が真の出典。phase11 側のフックは本書のタグ一覧を `config('notifications.subscription_tags')` 経由で取得し、ハードコードしない（規約 1-2 / 4-1） |

## 13.1 phase14 r4 への要請事項（r2 で新規／r3 で N-4 関連追記）

| 項目 | 内容 |
|------|------|
| `inventory_movements` の `CHECK (quantity != 0)` 制約緩和（R-2 / N-4 対応） | `movement_type='adjustment'` のときのみ `quantity = 0` を許容する。phase14 S14-7 の符号 CHECK は維持し、`adjustment` だけ「数値ズレなし・コメントだけ残す」用途を許容。**緩和されるまで、本書の `DeliveryTroubleInjector` は `quantity = +1` 固定を入れ `comment` 接頭辞 `[fault_injection][delivery][quantity_dummy]` で暫定運用する**（r3 で `+1` 一択に確定） |
| phase14 S14-7 符号 CHECK の正式定義の参照確保（N-4 対応） | phase14 r3 設計書では「`adjustment` のみ符号自由（棚卸補正は ± 双方あり得る）」と定義済。本書 4.2 ③ から phase14 設計書の対応箇所（`movement_type × quantity 符号の DB CHECK` セクション）への相互参照を r3 で明示。phase14 r4 で改訂が入った場合は本書も同期する |
| 振込確認の本物 API 接続後の `mode=production`（R-1 対応） | 将来課題として枠予約。本接続が完了するフェーズで `BankTransferMockClient` を `BankTransferClient` インターフェース + 本実装に差し替え |

## 13.2 phase15 r5 への要請事項（r2 で新規）

| 項目 | 内容 |
|------|------|
| `inventory_movements` への棚卸初期値 INSERT（R-5 対応） | RRRR-1 の `INSERT INTO inventories ... SELECT id, 1, stock, NOW() FROM products` の直後に、**同一マイグレーション内で `INSERT INTO inventory_movements (product_id, warehouse_id, movement_type, quantity, comment, created_at) SELECT id, 1, 'adjustment', stock, '[bootstrap] initial inventory', NOW() FROM products WHERE stock > 0` を必ず実行**。これにより本書 `InventoryConsistencyCheckJob` は初期化補填ロジックなしで動作する |

## 13.3 本書 r5 へのロードマップ（M-7 対応：r4 で新規）

| 項目 | 内容 |
|------|------|
| `BATCH_ENABLED` alias の deprecated 化 | 本書 r5 で `BATCH_ENABLED` を **deprecated 扱い**にし、起動時に WARN ログを出す（`"BATCH_ENABLED is deprecated. Use BATCH_SCHEDULER_ENABLED / BATCH_MANUAL_TRIGGER_ENABLED instead."`）。1 サイクル運用で互換確認後、r6 で完全削除 |
| `matchIfMissing = true` の継続維持 | alias 削除後の一時的なプロパティ未設定状態を吸収する安全弁として r5 / r6 共に維持（M-10 対応） |
| 設定の Java 側集約検討 | YAML の `${BATCH_SCHEDULER_ENABLED:${BATCH_ENABLED:true}}` ネスト解決はネスト深くなると保守性が落ちる。alias 削除完了時に `@ConfigurationProperties` で Java 側に集約する案を r5 で評価（M-7 (a) 案） |
| **改訂履歴タイムスタンプの一回限り後付け補完（K-9 対応：r5 で昇格／M-12 由来）** | r1 〜 r5 行に作成時刻を後付け（例：`2026-05-06 10:30`）し、r6 以降は `YYYY-MM-DD HH:mm` 形式で記録するルールを明記する。同日内多重改訂を時系列で追跡可能にする一回限りの補完作業として r6 着手時に実施 |
| **`BootstrapInventoryAdjustmentJob` の冪等性保証（J-7 対応：r6 で昇格／M-13 由来）** | 「処理前に `inventory_movements WHERE comment LIKE '[bootstrap]%' AND product_id = :pid` を検索し、既存があればスキップ」を r6 で本文化。Step 5 〜 6 で本ジョブを実装する場合、設計書本体は r5 で凍結のまま **3.4 表の警告フラグ**で実装担当者へ「冪等性チェック必須」を伝える運用とする。本ロードマップが r6 で取り込まれた段階で、3.4 表の警告フラグを正式仕様に置換 |

## 13.4 phase18（問い合わせ管理）以降への要請事項

| 項目 | 内容 |
|------|------|
| `inquiries.target_type='delivery'` 連動（phase18） | 配送遅延通知のメールに「問い合わせ作成リンク」を含める。phase18 完了後に文面を改訂 |
| 通知センターのスレッド表示（phase19 お知らせ管理と統合） | `console_notifications` と `notices` のスキーマを調整。本書の `console_notifications` を将来統合する余地を残す |
| `operation_logs.reason_code` カラム追加 | バッチ comment プレフィックスを reason_code へ昇格（phase14 P14-5 / phase15 RRR-5 と連動） |
| `inventory_movements_archive` 新設（年次アーカイブ） | 本書 3.3 ② の `OperationLogArchiveJob` と同パターン |

---

# 14. レビュー観点（r7 で取り込み候補）

r2 で R-1 〜 R-10、r3 で N-1 〜 N-10、r4 で M-1 〜 M-10、r5 で K-1 〜 K-10、r6 で J-1 〜 J-7 を本書に取り込んだ。残る🟢 R-11 〜 R-16 / N-11 〜 N-14 / M-11 〜 M-14 / K-11 〜 K-14 / J-8 〜 J-10 を r7 候補として下記に残す：

## 14.1 r7 候補（レビューコメント由来）

- **R-11：`monthly_sales_reports` の集計軸 NULL 運用 → テーブル分割化**
  集計軸を `IS NULL` で表現する設計は、SQL を書くたびに条件付与が必要で事故りやすい。`monthly_sales_by_product` / `monthly_sales_by_payment_method` / `monthly_sales_by_shipping_method` / `monthly_sales_by_preorder` の 4 テーブルに分割し、組合せ爆発を本書時点で止める案を r3 で評価する。

- **R-12：CloudWatch Logs Metric Filter のアラート閾値**
  `FAILED` だけでなく `PARTIAL` も同等にアラート対象とする。`PARTIAL` を「成功した分は処理済 / 失敗した分は人手対応必要」と明示し、見逃しを防ぐ。

- **R-13：`Asia/Tokyo` 統一を 3 層で担保**
  JVM 起動オプション `-Duser.timezone=Asia/Tokyo` / `docker-compose.yml` の `TZ` 環境変数 / DB セッション TZ（MySQL は `default-time-zone='+09:00'`、H2 は `BUILT_IN_TIMEZONE`）の 3 点セットを r3 で本書に明記する。カテゴリ7-2 の H2/MySQL 互換性問題の発生源を潰す。

- **R-14：確率テストのフレーキー化対策**
  12 章の「確率と統計的に整合する」記述はシード固定 or モック化しないと CI 偶発失敗を招く。r3 で **`Random` を `RandomGenerator` インターフェースに抽象化し、テストは決定論的モック / 本番は `SecureRandom`** に切り替える DI 構造を明記。

- **R-15：`*_sales_reports` のリラン戦略**
  手動再実行で同月二重 INSERT を防ぐため、`UNIQUE (year, month, product_id, payment_method_id, shipping_method_id, is_preorder)` 制約 + UPSERT（`ON DUPLICATE KEY UPDATE`）戦略を 5.4 に追記。R-11 の分割テーブル化方針と合わせて r3 で確定する。

- **R-16：`[fault_injection]` 接尾辞による検索性向上**
  `[fault_injection][inventory]` / `[fault_injection][delivery]` / `[fault_injection][sales]` のように接尾辞で対象種別を区別する規約を 8 章に追記。r2 の `DeliveryTroubleInjector` 補償 movement で先行採用した接尾辞を全 Injector に展開する。

## 14.1.1 N-11 〜 N-14（r4 候補）

- **N-11：`payload_hash` 算出ルールの統一**
  6.4.1 では「主要キーから SHA-256」とだけあり、JSON / CSV / `String.format` のどれを使うかが未定義。`PayloadHasher` ユーティリティクラスを新設し、`SHA-256(subscription_tag + ':' + key1=value1 + ',' + key2=value2 + ...)` ／キーは sort 順固定 ／ NULL 値は文字列 `"<null>"` で表現、と統一仕様を r4 で明記する。

- **N-12：改訂履歴のタイムスタンプ詳細化** → **K-9（r5）で 13.3 ロードマップに昇格済**。本セクションでの記載は J-4 対応で削除。詳細は 13.3 を参照。

- **N-13：`BootstrapInventoryAdjustmentJob` の `triggered_by` 表記**
  オンデマンドバッチも `batch_executions.triggered_by='manual:user_id=N'` で記録される前提だが、本 Job は DB 状態を物理的に変える救済 Job であり、定期バッチと同じ表に並ぶと運用画面のノイズになる。専用カテゴリ `triggered_by='bootstrap:user_id=N'` を導入し、運用画面で識別可能にする案を r4 で評価。

- **N-14：`[fault_injection][quantity_dummy]` 接尾辞のクリーンアップ計画**
  phase14 r4 で `CHECK (quantity != 0)` の `adjustment` 限定緩和が完了した後、過去に挿入された `[quantity_dummy]` レコードを通常の `quantity = 0` に書き換えるか、放置するかの方針が r3 では未定義。放置でも問題はないが、移行マイグレーションを用意するか「放置で OK」を明文化するかを r4 で決定する。

## 14.1.2 M-11 〜 M-14（r5 候補）

- **M-11：`DigestNotificationDispatchJob` を 3 章に分類**
  通知ダイジェストは設計上 5 分間隔の重要バッチだが、3.1 日次・3.2 月次・3.3 年次・3.4 オンデマンドのいずれにも分類されていない（6.4.2 に埋もれている）。読者が 3 章を順に読んでも気づけないため、r5 で **「3.6 高頻度バッチ」セクションを新設**し、`DigestNotificationDispatchJob` をそこに記載。実行頻度・SLA・期待件数も明記する。

- **M-12：改訂履歴のタイムスタンプ詳細化** → **K-9（r5）で 13.3 ロードマップに昇格済**。本セクションでの記載は J-4 対応で削除。詳細は 13.3 を参照。

- **M-13：`BootstrapInventoryAdjustmentJob` の冪等性保証** → **J-7（r6）で 13.3 ロードマップに昇格**。本セクションでの記載は r6 で削除し、設計書本体は r5 で凍結したまま実装担当者への警告として 3.4 表に「実装時に冪等性チェック必須（r6 ロードマップ参照）」のフラグを追記済。詳細は 13.3 / 3.4 を参照。

- **M-14：オンデマンドバッチの実行ボタン 2 連打防御**
  3 章共通制御の `ConcurrentHashMap` ロックが重複実行をカバーしているが、Console UI 側で「実行中ボタン非活性化」が phase16 規約でカバーされているかは本書から読めない。r5 で phase16 設計書を確認し、フロント側のダブルクリック耐性も担保されているか相互参照する。

## 14.1.3 K-11 〜 K-14（r6 候補）

- **K-11：`@ConfigurationProperties` 集約 r5 評価のフォーマット**
  13.3 の 3 行目「設定の Java 側集約検討」は r5 で評価とのことだが、評価結果次第で 2.3 のネスト YAML が大幅に書き換わるため、r6 着手前に **評価フォーマット（メリット / デメリット / 採用判定基準）** を 13.3 か 14.1 に明記しておくと r6 改訂が高速化する。

- **K-12：`fault_injection_logs` の保持期間**
  5.3 にカラム定義はあるが、`OperationLogArchiveJob` のような年次アーカイブ対象になるかが未定義。フォルトインジェクションは dev / staging のみなので件数は多くないが、ステージング長期運用時の肥大化が懸念。**保持期間：1 年（`operation_logs` と同等）** を 3.3 ② か 5.3 末尾に明記推奨。

- **K-13：`DigestNotificationDispatchJob` の SLA**
  M-11（r5 候補：3.6 高頻度バッチ節新設）と統合する想定。現在「5 分間隔」とだけあるが、**「抑制から最大何分後に届くことを保証するか」** が読み手に分かりにくい。`60 分後発火 + 5 分間隔チェック ＝ 最大 65 分遅延` の SLA を 6.4.2 で明文化すると、運用時の「ダイジェスト遅すぎる？」判断基準になる。

- **K-14：並行実行テストとテストランナー並列化の衝突**
  N-8 で `CountDownLatch` ベースの並行実行テストに移行したが、`@SpringBootTest` 環境で JUnit 5 `@Execution(CONCURRENT)` 等の並列化設定と衝突する可能性。**テスト戦略として「並列化禁止」（`@Execution(SAME_THREAD)`）を本書 12.1 に明記**しておくと、r6 で実装担当者が悩まずに済む。

## 14.1.4 J-8 〜 J-10（r7 候補）

- **J-8：`@Scheduled(fixedRate)` のダイジェスト初回発火遅延**
  Spring `fixedRate` 仕様では `ApplicationReadyEvent` 直後の 300 秒後に最初の発火が走る。起動直後に抑制レコードが既にあった場合、最大 5 分の遅延が発生する。`@Scheduled(fixedDelay = 300_000, initialDelay = 60_000)` 等で起動直後の遅延を短縮するかは r7 で評価。

- **J-9：`PayloadHasher` ユーティリティクラス仕様化（N-11 と統合）**
  K-1 と J-5 で `payload_hash` 算出ルールが本書内で 3 種類（主要キー連結 / `no-payload:job_name` / `inventory_alerts` の `product_id` × `warehouse_id` 等）に増えた。N-11 で予定されている `PayloadHasher` ユーティリティクラスの仕様化は r7 の優先度を 🟡 に上げる検討余地あり。

- **J-10：`DigestNotificationDispatchJob` 起動間隔の config 化**
  6.4.2 で `@Scheduled(fixedRate = 300_000)` とハードコード。これは規約 4-1 の config 経由化原則と微妙に矛盾する。`amazia.batch.notifications.digest-interval-ms` 等で config 化する案を r7 で検討。

## 14.2 r2 / r3 / r4 / r5 / r6 で本文化済（旧レビュー観点からの昇格）

| 旧観点 | 本書反映先 |
|--------|-----------|
| `RUNNING` レコードの起動時クリーンアップ | 3 章共通制御（R-7 補強・r2） |
| SES 送信は WARN 以上のみ | 6.2.2 解決アルゴリズム（r2） |
| `@Profile` 分離の追加 | 4.1 設計原則（五重防御に拡張・r2） |
| 旧表記（`inventory_manager` / `super_admin` / `target_role`）の一掃 | 全文（N-1 / r3） |
| 10 章リスク表の同期 | 10 章（N-2 / r3） |
| `BootstrapInventoryAdjustmentJob` 補填値ルール | 3.4 表（N-5 / r3） |
| 振込確認 × フォルトインジェクションの組合せマトリクス | 3.1 ③（N-6 / r3） |
| ダイジェスト永続化（`DigestNotificationDispatchJob` ＋ `digest_sent_at`） | 6.4.2 / 5.2（N-7 / r3） |
| 多重起動防止のテスト戦略を並行実行ベースへ | 12.1 異常系（N-8 / r3） |
| `notification_subscriptions` 自動購読フックを phase11 へ要請 | 13.0（N-9 / r3） |
| `BATCH_ENABLED` の 2 軸分割（`SCHEDULER` / `MANUAL_TRIGGER`） | 2.3 / 7 章 / 12.1（N-10 / r3） |
| 手動起動 503 ガードの 3 章共通制御本文化 | 3 章共通制御 / 3.4（M-1 / r4） |
| `@ConditionalOnProperty` 適用先の業務バッチクラス個別化 ＋ `BATCH_DIGEST_ENABLED` 独立フラグ | 2.3（M-2 / r4） |
| admin → user 降格時の購読論理停止 | 13.0（M-3 / r4） |
| `TriggerFaultInjectionJob` 自体への `@Profile("!production")` で API 404 | 4.1.2 / 12.2（M-4 / r4） |
| 2 軸分割の E2E テスト追加 | 12.3（M-5 / r4） |
| ダイジェスト送信を「タグ集計→ユーザ単位 SES」の 2 重ループ明文化 | 6.4.2 / 12.1（M-6 / r4） |
| `payload_hash` フォールバック規則の 6.4.1 整合 | 6.4.1（K-1 / r5） |
| Docker 初回起動テストの 2 軸分割同期 | 12.1（K-2 / r5） |
| `notification_subscriptions` 自動フックの UPSERT 一本化 | 13.0（K-3 / r5） |
| `BATCH_DIGEST_ENABLED=false` 単独 E2E | 12.3（K-4 / r5） |
| `DigestNotificationDispatchJob` の手動起動 API 不在を明記 | 6.4.2（K-5 / r5） |
| オンデマンドジョブ Bean のステートレス前提 | 3 章共通制御（K-6 / r5） |
| `BATCH_DIGEST_ENABLED=false` 長期運用時の抑制レコード滞留リスク | 10 章（K-7 / r5） |
| `quantity = 0` 商品の検知不能性テスト | 12.1 正常系（K-8 / r5） |
| 改訂履歴タイムスタンプ補完のロードマップ昇格 | 13.3（K-9 / r5） |
| env-vars セット更新チェックリスト参照 | 7 章末尾（K-10 / r5） |
| `BATCH_ENABLED` alias が `BATCH_DIGEST_ENABLED` には作用しない仕様の明文化 | 2.3 / 12.1 / 12.3（J-1 / r6） |
| `ConsoleNotificationsArchiveJob` の 3.3 ③ への新設 | 3.3 ③ / 10 章（J-2 / r6） |
| PARTIAL アラート閾値を「R-12 で確定予定」に修正 | 10 章（J-3 / r6） |
| 14.1.2 から M-12 重複記述を削除し 13.3 に集約 | 14.1.2（J-4 / r6） |
| `payload_hash` フォールバックを `batch_execution_id` ベースから `job_name` ベースへ修正 | 6.4.1 / 5.2 / 6.4.3（J-5 / r6） |
| 7 章チェックリストに本番 Validator 起動失敗の検証項目追加 | 7 章末尾（J-6 / r6） |
| M-13（`BootstrapInventoryAdjustmentJob` 冪等性）を 13.3 ロードマップに昇格 ＋ 3.4 表に警告フラグ | 13.3 / 3.4（J-7 / r6） |

---

# 15. レビューコメント対応サマリ（r1 → r2）

## r1 で対応済（再掲）
- 初版に対する全面ブラッシュアップ。フェーズ13〜16完了前提・無料枠方針・本番フォルト OFF 強制・五重テーブル新設

## r2 で新規対応（R-1 〜 R-16）

| ID | 優先度 | 反映先 | 対応 |
|----|--------|-------|------|
| R-1 | 🔴 必須 | 2.3 / 3.1 ③ / 7 章 | 振込確認を `mode=disabled / mock-match / mock-mismatch-rate` の 3 モードに分割。本番既定は `disabled`。`mock-mismatch-rate` × `SIMULATION_FAULT_INJECTION=false` の組合せは起動時 Validator で停止 |
| R-2 | 🔴 必須 | 4.2 ③ / 13.1 / 12 章 | `DeliveryTroubleInjector` の対象を `PENDING` 限定。`inventory_movements` への補償 `adjustment` レコード INSERT を必須化。phase14 r4 へ `CHECK (quantity != 0)` の `adjustment` 限定緩和を要請。緩和まではダミー `quantity = ±1` で運用 |
| R-3 | 🔴 必須 | 3.1 ③ / 7 章 | `SalesReconciliationJob` の `WHERE i.warehouse_id = 1` ハードコードを廃止。`amazia.batch.sales-reconciliation.target-warehouse-ids`（CSV）で対象倉庫を `@Value` 経由取得し、合算 SQL に変更 |
| R-4 | 🔴 必須 | 3.1 ① | `InventoryConsistencyCheckJob` の `GROUP BY` から `i.quantity` を除外。`HAVING MAX(i.quantity) <> SUM(...)` に修正。`UNIQUE (product_id, warehouse_id)` 前提を明示 |
| R-5 | 🔴 必須 | 3.1 ① / 3.4 / 13.2 | 在庫初期化補填の責務を phase15 r5 マイグレーションへ移管。本書定期バッチでは初期化補填しない。緊急救済用に `BootstrapInventoryAdjustmentJob` をオンデマンド独立 |
| R-6 | 🟡 確定 | 3 章共通制御 / 12 章 | リトライ対象を I/O 系一過性例外（`MailSendException` / `SocketTimeoutException` / `RestClientResponseException(5xx)` / `TransientDataAccessException`）に限定。`DataIntegrityViolationException` 等は即 `FAILED` |
| R-7 | 🟡 確定 | 3 章共通制御 / 12 章 | 多重起動防止を JVM 内 `ConcurrentHashMap<jobName, AtomicBoolean>` に変更（`SELECT ... FOR UPDATE` を不採用）。起動時クリーンアップ（24 時間以上前の `RUNNING` を `FAILED` 化）を `ApplicationReadyEvent` で必須化 |
| R-8 | 🟡 確定 | 2.3 | `BATCH_ENABLED=false` の挙動を「`@ConditionalOnProperty` で `@Scheduled` Bean を非登録」「手動起動は HTTP 503」と明文化 |
| R-9 | 🟡 確定 | 6.2 / 5.2 | `notification_subscriptions` テーブル新設。`console_notifications.target_role` を `target_subscription_tag` + `target_user_id` に置換。phase11 のロール（admin / user の 2 種のみ）を反映 |
| R-10 | 🟡 確定 | 6.4 / 5.2 | レートリミットを `(job_name, subscription_tag, payload_hash)` で抑制（payload_hash 単位）。抑制レコードは `console_notifications.suppressed = true` で残し、抑制から 1 時間後にダイジェスト 1 通を送る |
| R-11 〜 R-16 | 🟢 任意 | 14.1 | r3 では取り込まず r4 候補として残置 |

## r3 で新規対応（N-1 〜 N-14）

| ID | 優先度 | 反映先 | 対応 |
|----|--------|-------|------|
| N-1 | 🔴 必須 | 全文 | 旧表記（`inventory_manager` / `super_admin` / `target_role`）の本文残存を一掃。`subscription_tag` / `admin` / `target_subscription_tag` に統一。`TriggerFaultInjectionJob` の本番無効化は「UI 非表示」から「`@Profile("!production")` で API 自体が 404」に修正し 4.1 五重防御と整合 |
| N-2 | 🔴 必須 | 10 章 | リスク表を r2 同期。`SELECT ... FOR UPDATE` → JVM 内 `ConcurrentHashMap`、三重防御 → 五重防御、振込確認モード化、リトライ対象限定、レートリミット、起動時クリーンアップを反映 |
| N-3 | 🔴 必須 | 11 章 | Step 0 を「設計書 r3 確定」に更新 |
| N-4 | 🔴 必須 | 4.2 ③ / 12 章 / 13.1 | phase14 S14-7 の「`adjustment` 符号自由」定義を本書から参照可能化。`DeliveryTroubleInjector` 補償 movement の暫定値を `+1` 固定に確定（`±1` の不定を排除） |
| N-5 | 🔴 必須 | 3.1 ① / 3.4 | `BootstrapInventoryAdjustmentJob` の補填値ルールを「現在の `inventories.quantity` をそのまま `adjustment` で記録（`products.stock` は phase14 Step E で削除済のため唯一の選択肢）」と明記。「自動補正はしない」原則との整合（人為承認・手動起動・対象限定）を本文化 |
| N-6 | 🟡 確定 | 3.1 ③ | `SIMULATION_FAULT_INJECTION` × `BANK_TRANSFER_VERIFICATION_MODE` の 6 通り組合せマトリクスを表で明記。`mode=mock-mismatch-rate` × `SIMULATION_FAULT_INJECTION=false` のみ起動失敗 |
| N-7 | 🟡 確定 | 6.4.2 / 5.2 | ダイジェスト発火を `Spring TaskScheduler` の単発タスクから **専用ジョブ `DigestNotificationDispatchJob`（5 分間隔）+ `console_notifications.digest_sent_at` カラム**へ移行。JVM 再起動跨ぎでの検知漏れを構造的に防止 |
| N-8 | 🟡 確定 | 12.1 | 多重起動防止のテストをリフレクション主体から **2 スレッド並行実行 + `CountDownLatch`** ベースに変更。リフレクションは `finally` 解放確認の `AtomicBoolean` 状態確認のみに限定 |
| N-9 | 🟡 確定 | 13.0 / 6.2.1 | phase11 への要請事項として「`users` ロール = admin の作成・昇格時の `notification_subscriptions` 全タグ自動 INSERT フック」を新設。タグ一覧は本書 6.2.3 を `config('notifications.subscription_tags')` 経由で参照（ハードコード禁止） |
| N-10 | 🟡 確定 | 2.3 / 7 章 / 12.1 | `BATCH_ENABLED` を `BATCH_SCHEDULER_ENABLED`（定期）と `BATCH_MANUAL_TRIGGER_ENABLED`（手動）の 2 軸に分割。`BATCH_ENABLED` は両者の alias として後方互換維持。「定期だけ止めて手動で復旧操作」シナリオを表現可能化 |
| N-11 〜 N-14 | 🟢 任意 | 14.1.1 | r4 では取り込まず r5 候補として残置 |

## r4 で新規対応（M-1 〜 M-14）

| ID | 優先度 | 反映先 | 対応 |
|----|--------|-------|------|
| M-1 | 🔴 必須 | 3 章共通制御 / 3.4 | 手動起動 API のガードを 3 章共通制御 第 6 項に追加。`amazia.batch.manual-trigger-enabled=false` のとき Controller 側で HTTP 503。Bean は生かしたまま API のみ閉じる設計を明文化 |
| M-2 | 🔴 必須 | 2.3 / 7 章 | `@ConditionalOnProperty` の適用先を**業務バッチクラスごとに個別**と明記。`TaskScheduler` Bean / `SchedulerConfigurer` 集約クラスへの適用は禁止。`DigestNotificationDispatchJob` を `BATCH_DIGEST_ENABLED`（独立フラグ）で制御し、業務バッチ停止と独立に動作可能化 |
| M-3 | 🔴 必須 | 13.0 | admin → user 降格時に `notification_subscriptions` 全行を `email_enabled=false, in_app_enabled=false` に論理停止。再昇格時は UPSERT で復元。情報漏洩リスクを潰す |
| M-4 | 🔴 必須 | 4.1.2 / 12.2 | `TriggerFaultInjectionJob` 自体に `@Profile("!production")` を付与。`TriggerFaultInjectionController` が `Map<String, OnDemandJob>` ルックアップで `null` を返し API 自体が 404 となる経路を明文化 |
| M-5 | 🔴 必須 | 12.3 | E2E テストに 2 軸分割の 4 シナリオを追加：定期 OFF + 手動 ON / 手動 OFF / 業務 OFF + ダイジェスト ON / 本番 `TriggerFaultInjectionJob` 404 |
| M-6 | 🔴 必須 | 6.4.2 / 12.1 | ダイジェスト送信を「購読タグで集計 → タグの購読ユーザ全員に個別 SES 送信」の 2 重ループに明文化。擬似コードを掲載。BCC 集約は禁止。`digest_sent_at` UPDATE はタグ単位 1 回 |
| M-7 | 🟡 確定 | 13.3 | 本書 r5 へのロードマップ節を新設。`BATCH_ENABLED` alias を r5 で deprecated 化、r6 で削除。`@ConfigurationProperties` による Java 側集約を r5 で評価 |
| M-8 | 🟡 確定 | 3.4 表 / 3.1 ① | `BootstrapInventoryAdjustmentJob` の対象を `inventories.quantity > 0` に限定（phase14 r4 緩和まで `quantity = 0` の INSERT は CHECK 違反）。`InventoryConsistencyCheckJob` で初期 `stock = 0` 商品が偶然一致して検知不能となる構造的限界を明記 |
| M-9 | 🟡 確定 | 5.2 / 6.4.3 | `console_notifications.payload_hash` を NULL 許容から **NOT NULL** に変更。算出不能時は `SHA-256('no-payload:' + batch_execution_id)` を投入。NULL 同士が `=` で一致しない SQL 仕様による衝突回避 |
| M-10 | 🟡 確定 | 2.3 | `matchIfMissing = true` を残置する意図（alias 撤去後の安全弁）を本文に注記 |
| M-11 〜 M-14 | 🟢 任意 | 14.1.2 | r5 では取り込まず r6 候補として残置 |

## r5 で新規対応（K-1 〜 K-14）

| ID | 優先度 | 反映先 | 対応 |
|----|--------|-------|------|
| K-1 | 🔴 必須 | 6.4.1 | `payload_hash` のフォールバック規則（`SHA-256('no-payload:' + batch_execution_id)`）を 6.4.1 末尾に明記。NULL / 空文字を投入しないことを確定し、5.2 / 6.4.3 の `NOT NULL` 制約と整合 |
| K-2 | 🔴 必須 | 12.1 | Docker 初回起動テストを `BATCH_ENABLED=false`（旧）から `BATCH_SCHEDULER_ENABLED=false` ＋ `BATCH_DIGEST_ENABLED=false` ＋ alias 後方互換の 3 行に分割 |
| K-3 | 🔴 必須 | 13.0 | `notification_subscriptions` 自動フックを **`INSERT ... ON DUPLICATE KEY UPDATE`（UPSERT）** に統一。初回 admin 化と再昇格を同一コードパスで処理し、`UNIQUE (user_id, subscription_tag)` 違反と分岐ロジックを構造的に排除 |
| K-4 | 🔴 必須 | 12.3 | E2E に `BATCH_DIGEST_ENABLED=false` 単独シナリオを追加。フラグを `true` に戻して再起動した際、蓄積分が 1 通でまとめて送出されることを確認（再起動跨ぎ欠損ゼロ） |
| K-5 | 🔴 必須 | 6.4.2 | `DigestNotificationDispatchJob` は手動起動 API を提供しない（5 分間隔の自動のみ）ことを 6.4.2 末尾に明記。`BATCH_MANUAL_TRIGGER_ENABLED` の影響を受けない契約を明確化 |
| K-6 | 🟡 確定 | 3 章共通制御 | オンデマンドジョブ Bean をステートレス（インスタンスフィールドで状態を持たない）として実装する前提を 3 章 6 項末尾に追記。長期 OFF → ON 切替時の副作用を排除 |
| K-7 | 🟡 確定 | 10 章 | `BATCH_DIGEST_ENABLED=false` 長期運用時の抑制レコード滞留リスクを 10 章リスク表に追加。`console_notifications` を年次アーカイブ対象に含める方針を r6 で確定 |
| K-8 | 🟡 確定 | 12.1 正常系 | `inventories.quantity = 0` 商品の検知不能性を「現時点では構造的限界として正常動作」のテストケースとして 12.1 正常系に追加。phase14 r4 完了時に期待値を反転させるコメントを必須化 |
| K-9 | 🟡 確定 | 13.3 | M-12（改訂履歴タイムスタンプ補完）を 14.1.2（r6 候補）から 13.3（r5 → r6 ロードマップ）へ昇格 |
| K-10 | 🟡 確定 | 7 章末尾 | 7 章末尾に Step 1 着手前のチェックリスト（`docker-compose.yml` / `application-test.properties` / `.env.example` のセット更新）参照を追加。[user memory: env_vars_and_tests] と整合 |
| K-11 〜 K-14 | 🟢 任意 | 14.1.3 | r6 では取り込まず r7 候補として残置 |

## r6 で新規対応（J-1 〜 J-10）

| ID | 優先度 | 反映先 | 対応 |
|----|--------|-------|------|
| J-1 | 🔴 必須 | 2.3 / 12.1 / 12.3 | `BATCH_ENABLED` alias が `BATCH_SCHEDULER_ENABLED` ＋ `BATCH_MANUAL_TRIGGER_ENABLED` の 2 軸にのみ作用し、**`BATCH_DIGEST_ENABLED` には作用しない**設計意図を明文化（N-7 永続化方針との整合）。「全停止」を完全に達成するには `BATCH_ENABLED=false` ＋ `BATCH_DIGEST_ENABLED=false` の併用が必要であることを 12.1 異常系・12.3 E2E に注釈追加 |
| J-2 | 🔴 必須 | 3.3 ③ / 10 章 | K-7 で「方針確定」に留まっていた `console_notifications` 年次アーカイブを **`ConsoleNotificationsArchiveJob` として 3.3 ③ に新設**。アーカイブ条件（既読 1 年・抑制ダイジェスト送出済 1 年・無条件 1 年経過）と保護条件（抑制中 `digest_sent_at IS NULL` はアーカイブしない）を明記 |
| J-3 | 🔴 必須 | 10 章 | PARTIAL アラート閾値を「同等に検出」から「**R-12（r7 候補）で確定予定**」に修正。未確定領域を確定済として書く誤読を排除 |
| J-4 | 🟡 確定 | 14.1.2 | 14.1.2 と 13.3 の M-12 重複記述を 13.3 に集約。「どちらが正？」のノイズを排除 |
| J-5 | 🟡 確定 | 6.4.1 / 5.2 / 6.4.3 | `payload_hash` フォールバックを `SHA-256('no-payload:' + batch_execution_id)` から **`SHA-256('no-payload:' + job_name)`** に変更。`batch_execution_id` は毎回ユニークになり連続失敗が抑制されない問題（R-10 本来意図への反逆）を解消 |
| J-6 | 🟡 確定 | 7 章末尾 | チェックリストに本番 Validator 起動失敗の検証項目（`SIMULATION_FAULT_INJECTION=true` / `BANK_TRANSFER_VERIFICATION_MODE=mock-mismatch-rate` で起動失敗）を追加。フォルトインジェクション四重防御の起動段階確認を Step 1 着手前に必須化 |
| J-7 | 🟡 確定 | 13.3 / 3.4 | M-13（`BootstrapInventoryAdjustmentJob` 冪等性）を 14.1.2 から 13.3 ロードマップに昇格。設計書本体は r5 で凍結のまま、3.4 表の警告フラグで実装担当者へ「冪等性チェック必須」を伝える運用 |
| J-8 〜 J-10 | 🟢 任意 | 14.1.4 | r7 候補として残置 |
