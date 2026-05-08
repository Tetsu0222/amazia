# 051: CI 失敗 — `ApplyScheduledPricesJobTest` の H2 テスト分離不足 + Market E2E のタイムアウト張り付き

## ステータス
✅ 解決済（2026-05-08）

## 発症箇所
- CI: GitHub Actions `Deploy to EC2` ワークフロー / Run [#25548702763](https://github.com/Tetsu0222/amazia/actions/runs/25548702763)
- 失敗ジョブ:
  - `amazia-core テスト` — `ApplyScheduledPricesJobTest.APP_3_2度実行しても_2回目は対象0件で冪等` ([ApplyScheduledPricesJobTest.java:91-108](../../amazia-core/src/test/java/com/example/batch/ApplyScheduledPricesJobTest.java#L91-L108))
  - `amazia-market テスト` — `auth_flow.e2e.test.jsx` の `Market 認証フロー E2E > 登録 → ログイン → マイページ → ログアウト の一連フロー` ([auth_flow.e2e.test.jsx#L117](../../amazia-market/src/test/auth_flow.e2e.test.jsx#L117))
- 起因コミット: [`633901ef`](https://github.com/Tetsu0222/amazia/commit/633901ef) `価格予約変更機能の実装`

本トラブルは「同じ CI run で同時に発生した CI 失敗 2 件」をまとめて記録する（性質は別だが同じ修正サイクル内）。

## 症状

### 症状①: amazia-core テスト失敗
```
[ERROR] ApplyScheduledPricesJobTest.APP_3_2度実行しても_2回目は対象0件で冪等:102
        2 回目は is_pending=true が無く対象 0 件 ==> expected: <0> but was: <1>
[ERROR] Tests run: 524, Failures: 1, Errors: 0, Skipped: 0
```

`ApplyScheduledPricesJob` を 2 回連続で実行したとき、2 回目は対象 0 件（冪等）であるべきだが、`batch_executions.target_count = 1` になっている。

### 症状②: amazia-market テスト失敗
```
src/test/auth_flow.e2e.test.jsx > Market 認証フロー E2E > 登録 → ログイン → マイページ → ログアウト の一連フロー
Error: Test timed out in 5000ms.
```

会員登録 → ログイン → マイページ → ログアウトを 1 シナリオで通す Vitest E2E が、デフォルトタイムアウト 5000ms に張り付いて落ちる。

## 根本原因

### 原因①: ApplyScheduledPricesJobTest のテスト分離不足（H2 持ち越し）

[ApplyScheduledPricesJobTest.java:36-39](../../amazia-core/src/test/java/com/example/batch/ApplyScheduledPricesJobTest.java#L36-L39) のクラス宣言:

```java
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class ApplyScheduledPricesJobTest {  // ← @Transactional が付いていない
```

`@Transactional` が付いていないため、テスト終了時に DB がロールバックされず H2 に永続化されたまま残る。同パッケージ `scheduledprice` の他テスト（`RegisterScheduledSkuPriceServiceTest` `DeleteScheduledSkuPriceServiceTest` `GetScheduledSkuPriceServiceTest` `ProductSkuScheduledPriceRepositoryTest`）はいずれもクラスレベル `@Transactional` を付与しており自動ロールバックされる ([RegisterScheduledSkuPriceServiceTest.java:31](../../amazia-core/src/test/java/com/example/scheduledprice/RegisterScheduledSkuPriceServiceTest.java#L31) など)。

`ApplyScheduledPriceService` 自身は[`@Transactional`](../../amazia-core/src/main/java/com/example/scheduledprice/service/ApplyScheduledPriceService.java#L35) で `is_pending=false` への更新を確実にコミットしている（**冪等性のロジックは正しい**）。問題はテスト側で、`mvn test` 全体実行時に **このクラスより前に動いた他のテストが H2 に残した「`is_pending=true && apply_date <= today`」のレコード**が、`APP_3` の 2 回目 `job.run` で `findByApplyDateLessThanEqualAndIsPendingTrue` クエリに引っかかる。

#### 切り分け実測（ローカル）

| 実行範囲 | APP_3 結果 |
|---|---|
| `ApplyScheduledPricesJobTest` 単体 | ✅ PASS（1回目=1/1, 2回目=0/0） |
| `ApplyScheduledPricesJobTest.APP_1 + APP_2 + APP_3` メソッド指定 | ✅ PASS |
| `com.example.batch.*Test`（68件） | ✅ PASS |
| `mvn test` 全体（524件） | ❌ FAIL（2回目=1/1） |

`batch` パッケージ単体では PASS、`mvn test` 全体で FAIL。**汚染源は `batch` パッケージ外のテスト**だが、surefire のデフォルト実行順序は filesystem 依存で再現が壊れやすい。本件の本質は「特定のテストクラスが汚染源」ではなく「**`@Transactional` 無しテストが他クラスのテストキャッシュ共有 H2 上にレコードを残しうる**」という構造問題。

### 原因②: Market E2E が Vitest デフォルトタイムアウト 5000ms に張り付き

[auth_flow.e2e.test.jsx:117-195](../../amazia-market/src/test/auth_flow.e2e.test.jsx#L117-L195) の「登録 → ログイン → マイページ → ログアウト」シナリオは、

- `userEvent.type` で姓名・郵便番号・生年月日・メール・パスワード×2 を入力（10 フィールド超）
- 郵便番号 → 住所 API（モック）→ 自動補完待機
- 登録 submit → ログイン画面遷移 → 再度フォーム入力 → ログイン → マイページ確認 → ログアウト → ヘッダ再描画確認

を 1 つの `it()` で通す重量級シナリオ。ローカル（Windows）でも実測 **3455ms**（`vitest run --reporter=verbose` 計測値）と、デフォルトタイムアウト 5000ms の約 70% を消費している。CI（GitHub Actions Ubuntu ランナー）はローカルより遅いため、5000ms を恒常的に超過する。

価格予約変更機能の実装コミット（633901ef）自体に Market E2E の追加・変更は含まれていないが、`@testing-library/user-event` v14 系の `userEvent.type` は文字単位で内部 setTimeout を挟むため、ランナー差で容易に閾値を跨ぐ。

## なぜ CI で検知できなかったか

### 原因①について
- `ApplyScheduledPricesJobTest` を実装した時点（おそらく phase17 step3-6 タイミング）では surefire の実行順序によって他クラスのレコードが先に走らない実行プランで通っていた。**冪等性ロジック単体では正しい**ため、TDD では検知できない構造（テスト同士の相互作用問題）。
- 同パッケージ `scheduledprice` のテストはすべて `@Transactional` を付けていたが、`batch` パッケージの本テストだけ付け忘れていた。コードレビューで「並びの他テストと揃えるべき」観点が漏れた。

### 原因②について
- ローカルでは PASS するため、コミット時点では検知できない（CI ランナーの実行速度差で初めて顕在化）。
- Vitest のテストごとに「想定実行時間」を見直す運用が確立しておらず、長尺シナリオでも `it()` 既定値（5000ms）のまま放置されていた。

## 修正内容

### 修正① ApplyScheduledPricesJobTest にクラスレベル `@Transactional` を付与

`scheduledprice` パッケージの他テストと並べる方針。`@SpringBootTest` + `@Transactional` は Spring Test の標準的な組み合わせで、テストメソッド終了時に自動ロールバック。

```java
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional  // ← 追加
class ApplyScheduledPricesJobTest {
```

注意: `ApplyScheduledPriceService.applyOne` は `@Transactional` 付き Service。テストクラスに `@Transactional` を付けるとテストトランザクションに参加するが、Spring の `Propagation.REQUIRED`（デフォルト）なら参加・ロールバックで問題ない。**ただし `BatchExecutionRecorder` が `@Transactional(propagation = REQUIRES_NEW)` で別トランザクションに記録する場合、テストロールバック後も `batch_executions` 行が残る可能性がある** — テストはこれを assertEquals で参照しているので、修正と同時に挙動を確認する。

### 修正② Market E2E の該当シナリオにテストレベルタイムアウトを 15 秒へ拡張

```js
it('登録 → ログイン → マイページ → ログアウト の一連フロー', async () => {
  // ...
}, 15000);  // ← 第3引数で個別タイムアウト指定
```

Vitest は `it()` の第 3 引数（または `{ timeout: 15000 }`）で個別タイムアウトを指定できる。ファイル全体で底上げするなら `vitest.config.js` の `test.testTimeout` で対応するが、本件は重量級 E2E シナリオが 1 件だけのため局所修正に留める。

### 検証
- ✅ `mvn test`（524件）が `Tests run: 524, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` で完走（2026-05-08 ローカル）
- ✅ `npx vitest run src/test/auth_flow.e2e.test.jsx`（7件）が全 PASS（2026-05-08 ローカル）。該当シナリオの実測 3366ms（拡張後 15000ms に対し十分余裕あり）
- ⏳ 修正後コミットを push して GitHub Actions の `Deploy to EC2` ワークフローが test-core / test-market 両方グリーン（次のデプロイで確認）

### 検証時に確認した `@Transactional` × `REQUIRES_NEW` の挙動

[BatchExecutionRecorder.java](../../amazia-core/src/main/java/com/example/batch/service/BatchExecutionRecorder.java) は `@Transactional(propagation = REQUIRES_NEW)` で `batch_executions` を独立トランザクションに記録する設計。テストクラスに `@Transactional` を付けると以下の挙動になる:

| 操作 | TX 関係 | テスト終了時 |
|---|---|---|
| `persistProductWithSku` / `persistActivePrice` / `persistScheduled` | テスト outer TX | ロールバックで消える |
| `applyService.applyOne`（`@Transactional` REQUIRED） | outer に参加 | ロールバック対象 |
| `BatchExecutionRecorder.start/success`（REQUIRES_NEW） | 別 TX 即コミット | **`batch_executions` に残る** |

`latestExecution()` の `findByJobNameOrderByStartedAtDesc(JOB_NAME).get(0)` は JOB_NAME 単位で最新を返すため、同一テスト内で `job.run` 直後に呼べば自テストの行が上に来る。テスト分離は崩れない。524 件完走で実証済み。

## 再発防止

| 観点 | 対策 |
|------|------|
| **Spring Boot テストの `@Transactional` 付与の規律**：DB を直接触る `@SpringBootTest` テストはクラスレベル `@Transactional` を原則付ける | `test_insights.md` カテゴリに「Spring Boot テストの分離方針」を新設。`@Transactional` を付けない例外（`AbstractBatchJob` の `BatchExecutionRecorder` のように `REQUIRES_NEW` を検証するケース等）はテストクラス JavaDoc に理由を明記する規律へ |
| **CI 単体ローカル相互再現性の検知**：「ローカル単体 PASS / CI 全体 FAIL」を踏んだ事例として AP に追記 | `ai_collaboration_antipatterns.md` に「単体テストでは PASS でも `mvn test` 全体で FAIL するテスト分離不足」を新規 AP として検討（既存 AP-* に該当が無い場合 AP-009 候補） |
| **Vitest E2E の閾値運用**：長尺 E2E シナリオはタイムアウトを明示する | `test_insights.md` に「Vitest の長尺 E2E は `it(..., 15000)` で明示」観点を追加。テスト追加時に `vitest run --reporter=verbose` でローカル実行時間を確認し、デフォルト閾値の 50% 超なら明示拡張する規律 |
| **CI 失敗時の差分切り分けランブック**：「単体 PASS / 全体 FAIL」を踏んだとき即座に試す観点（`@Transactional` 有無確認・パッケージ単位再実行・surefire 順序変更）の手順を残す | `operational_insights.md` カテゴリに「CI 失敗時のテスト分離切り分け手順」追記 |

## AI協働観点
- **AI の判断ミス**：phase17 step3-6 で `ApplyScheduledPricesJobTest` を新規作成した際、`scheduledprice` パッケージの既存 4 テストがすべて `@Transactional` を付けている事実を確認せず、`batch` パッケージのテンプレ（他のジョブテストが `@Transactional` 無しで書かれている）に寄せて書いた。**「並びの兄弟テストの規律」と「同じドメイン領域（scheduledprice）の規律」のどちらに合わせるべきかという判断が必要だったが、機械的にディレクトリ近接性で寄せた**。実際は「DB 永続化を伴う Service／Repository テスト」かどうかで決めるべきで、`ApplyScheduledPricesJobTest` は両方触る重量テストだったため `scheduledprice` 側に寄せるのが正解だった。
- **人間が止めるべきだった点**：レビュー時点では `mvn test` を回して PASS していたため検知できない（surefire の実行順依存で隠れていた）。コードレビューで「`@Transactional` 付け忘れ」を指摘するチェックポイントが規約化されていれば事前に止まっていた。
- **該当アンチパターン**：既存 AP-* に「単体 PASS / 全体 FAIL のテスト分離不足」は無い。AP-009 として追加候補（既存 AP の対応プロンプトテンプレでテストヘルパー観点はあるが、テストクラスのライフサイクル分離は未カバー）。

---

## 派生不具合: `SalesMismatchInjectorTest` と `SyncNotificationSubscriptionsServiceTest` の `@Transactional` 付け忘れによる連鎖 CI 失敗

### ステータス
✅ 解決済（2026-05-08）

### 発症箇所
- 失敗テスト①: [FaultInjectionLogRepositoryTest.findByInjectorNameOrderByCreatedAtDesc_で検索できる](../../amazia-core/src/test/java/com/example/faultinjection/FaultInjectionLogRepositoryTest.java#L54-L63) `:62` `expected: <1> but was: <2>`
- 失敗テスト②: [NotificationSubscriptionRepositoryTest.findBySubscriptionTagAndEmailEnabledTrue_で配信先を解決できる](../../amazia-core/src/test/java/com/example/notification/NotificationSubscriptionRepositoryTest.java#L60-L74) `:72` `expected: <1> but was: <2>`
- 起因コミット: 親トラブル①修正と同じ `633901ef` 付近の `mvn test` 全実行時に CI でのみ顕在化（GitHub Actions Run [#25549...](https://github.com/Tetsu0222/amazia/actions)）

### 症状（CI ログ）
```
Failures:
Error: FaultInjectionLogRepositoryTest.findByInjectorNameOrderByCreatedAtDesc_で検索できる:62 expected: <1> but was: <2>
Error: NotificationSubscriptionRepositoryTest.findBySubscriptionTagAndEmailEnabledTrue_で配信先を解決できる:72 expected: <1> but was: <2>
Tests run: 524, Failures: 2, Errors: 0, Skipped: 0
```

両テストとも「自テストで 1 件 INSERT → find して 1 件期待」のシンプルな確認だが**2 件返る**。両テスト自体には `@Transactional` が付いており自テストの INSERT はロールバックされる。**追加の 1 件は別テストの残置レコード**。

### 根本原因

**汚染源 ①** [SalesMismatchInjectorTest](../../amazia-core/src/test/java/com/example/faultinjection/SalesMismatchInjectorTest.java)（`@Transactional` 不在）:
- `SMI_2` / `SMI_4` で `injectorName="SalesMismatchInjector"` の `fault_injection_logs` 行を H2 に永続化
- クラス内では `@BeforeEach cleanupPriorLogs()` で `SalesMismatchInjector.INJECTOR_NAME` の行を削除する自衛があり**自クラス内の連鎖は防げていた**
- しかし**他クラス（FaultInjectionLogRepositoryTest）に対する影響**は対処されておらず、surefire 順序次第で `SalesMismatchInjector` 名のログが残置 → `FaultInjectionLogRepositoryTest.findByInjectorNameOrderByCreatedAtDesc_で検索できる` が同名 1 件追加の状況で 2 件返る

**汚染源 ②** [SyncNotificationSubscriptionsServiceTest](../../amazia-core/src/test/java/com/example/notification/SyncNotificationSubscriptionsServiceTest.java)（`@Transactional` 不在 / `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` のみ）:
- `admin` / `senior_admin` / `eternal_advisor` ロールテストで 1 ユーザーにつき `ALL_TAGS = ["inventory_alerts", "sales_alerts", "delivery_alerts", "postal_alerts", "batch_failure"]` の 5 件すべてを `email_enabled=TRUE` で UPSERT
- `@DirtiesContext` は ApplicationContext を再生成するが [application-test.properties:1](../../amazia-core/src/test/resources/application-test.properties#L1) の `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1` 設定により **JVM 終了まで H2 が残る**（同名 DB に新 context が再接続）。データはクリアされない
- CI 順序で `NotificationSubscriptionRepositoryTest` がこの後に動くと、`subscription_tag="delivery_alerts" && email_enabled=TRUE` 条件で残置 1 件 + 自テスト 1 件 = 2 件返る

### なぜ親トラブル①修正と同型なのに別 PR で踏んだか
親修正（051 本文）で `ApplyScheduledPricesJobTest` の `@Transactional` 付け忘れ**1 件のみ**を修正。当該クラスはローカル `mvn test` 全件で PASS していたが、surefire のテスト実行順序が **Windows ローカルと Linux CI で異なる** ため、**Linux 順序でのみ顕在化する別の汚染源**は親修正の検証では検出されなかった。**「ローカル 524 件 PASS」は CI 緑を保証しない**ことを実証した事例。

### 修正内容
両テストにクラスレベル `@Transactional` を追加（051 本文の修正①と同じ方針）:

```java
// SalesMismatchInjectorTest
@SpringBootTest(properties = { ... })
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional  // ← 追加
class SalesMismatchInjectorTest { ... }

// SyncNotificationSubscriptionsServiceTest
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional  // ← 追加（@DirtiesContext は context 再生成用途で残置。性能改善は別議論）
class SyncNotificationSubscriptionsServiceTest { ... }
```

### 検証
- ✅ ローカル `mvn test`（524件）が `Failures: 0, Errors: 0` / `BUILD SUCCESS`（2026-05-08）
- ⏳ push 後 CI で test-core 緑を確認

### 残課題
本派生で踏んだ 2 件の汚染源は塞いだが、`amazia-core` 内の `@SpringBootTest` テストには `@Transactional` 不在のクラスが他にも多数存在する（grep で 30+ クラス）。**次のコミットで surefire 順序が再変動すると別の同型バグが顕在化する可能性**は残る。本来は test_insights.md カテゴリ 7-2「`@SpringBootTest` のクラスライフサイクル分離」のチェックリスト運用で全クラスを棚卸しすべきだが、過剰修正回避の観点から**今回 CI で実際に踏んだ 2 件のみ修正**に留めた。次フェーズで `@SpringBootTest` 全クラスの `@Transactional` 棚卸しを別タスクで実施することを推奨。

---

## 派生不具合②: `expected: <1> but was: <3>` 型の連鎖と一括 `@Transactional` 棚卸し

### ステータス
✅ 解決済（2026-05-08）

### 発症箇所
- 派生①の修正後 push でも CI が再失敗：`FaultInjectionLogRepositoryTest:62 expected: <1> but was: <3>`
- 続いて random 順序ローカル検証で `ApplyScheduledPricesJobTest.APP_3:104 expected: <0> but was: <1>` が再現

### 根本原因
派生①では「CI で実際に踏んだ 2 件」のみ `@Transactional` を追加したが、`SalesMismatchInjector` 名のレコードを書きうる**他の faultinjection 系テスト（`TriggerFaultInjectionJobTest` / `FaultInjectionLoggerTest` など）も `@Transactional` 不在**で、CI Linux 順序の変動でこれらが先に動くと残置が**さらに増える**（2 件残置 → expected: 1 but was: 3）。

加えて `ApplyScheduledPricesJobTest.APP_3` の再失敗は、`@Transactional` 付き同士のテストでも特定の random 順序で**別経由（スケジューラ発火等）の残置**を完全には防げない場合があり、テスト側に**自衛コード**が必要だったため。

### 修正内容

#### ① `@SpringBootTest` テストへの `@Transactional` 一括棚卸し
`amazia-core/src/test` 配下の `@SpringBootTest` を使う `@Transactional` 不在テスト 46 クラスのうち、Spring Boot Test を実際には使わない 2 クラス（`BatchProductionValidatorTest` / `BatchProductionValidatorContextLoadTest` — 直接 `new` または `AnnotationConfigApplicationContext` 使用）を除く 44 クラスに `@Transactional` を一括付与。

うち 6 クラスは `@Transactional` 付与で逆効果（**outer TX 内の変更を別 TX や別スレッドから検証する設計**）になり revert：
- `SessionAndTokenSweepJobTest` — sweep ジョブの削除を後段で `findById` 検証（outer TX 内の delete は flush タイミングが読みにくい）
- `BatchJobLockRegistryTest` — マルチスレッド検証（別スレッドは outer TX に参加しない）
- `ConsoleNotificationsArchiveJobTest` / `OperationLogArchiveJobTest` — テーブル間移動（outer TX 内のアーカイブ動作を `findById` 系で検証）
- `PostalCsvImportJobTest` — `batch_executions` の REQUIRES_NEW 記録を `target_count` で件数検証
- `PasswordResetCustomerControllerTest` — token invalidate の同期確認

最終的に **38 クラスに `@Transactional` 付与** + **6 クラスは設計上の理由で不在を維持**。

#### ② `ApplyScheduledPricesJobTest` に自衛クリーンアップ追加
[ApplyScheduledPricesJobTest.java](../../amazia-core/src/test/java/com/example/batch/ApplyScheduledPricesJobTest.java) に `@BeforeEach` を追加し、テスト開始時に `is_pending=true && apply_date<=today` の既存レコードを削除：

```java
@BeforeEach
void cleanupPendingSchedulesForToday() {
    // 同 ApplicationContext 共有の H2（DB_CLOSE_DELAY=-1）に他テストが残した
    // is_pending=true && apply_date<=today レコードを掃除する自衛コード（051 派生②）。
    // クラス @Transactional 内でロールバック対象なので他テストへの副作用はない。
    scheduledRepository.deleteAll(
            scheduledRepository.findByApplyDateLessThanEqualAndIsPendingTrue(LocalDate.now()));
}
```

**`SalesMismatchInjectorTest.cleanupPriorLogs`（既存）と同型のパターン**。`@Transactional` 付与による分離だけでは防げない外乱（スケジューラ発火・REQUIRES_NEW 由来の残置等）に対する二重防御。

### 検証
- ✅ ローカル `mvn test`（524件）が `BUILD SUCCESS`
- ✅ ローカル `mvn test -Dsurefire.runOrder=random` を **5 回連続** で全て `BUILD SUCCESS`（再現性のあった APP_3 が再度落ちないことを確認）
- ⏳ push 後 CI で test-core 緑を確認

### 学び
- **「CI で実際に踏んだものだけ修正」はモグラ叩きを生む**。同型問題（H2 共有 + テスト分離不足）は**潜在クラスを一括棚卸しすべき**だった
- **`@Transactional` 付与は万能ではない**。outer TX 内の変更を別 TX/別スレッドから検証する設計、`@Modifying` クエリの flush タイミング、テーブル間アーカイブなどでは**逆に失敗する**ため、設計意図を読んで個別判断が必要
- **random 順序での複数回ローカル検証** は CI Linux 順序での再現性をある程度保証する補助手段になる（決定的ではないが、デフォルト順序のみの PASS よりは堅い）

### AI協働観点（派生②）
- **AI の判断ミス**：派生①修正時に「他にも同型 `@Transactional` 不在テストはないか」を **`grep -L "@Transactional"`** で機械的に網羅すべきだった。CI で踏んだ 2 件のみで満足したのは「最小スコープ」と「網羅予防」の判断軸を取り違えた
- **人間が止めるべきだった点**：派生①修正後の CI 緑待機時に「他にも踏みうるか」をユーザー側で問えば早期に網羅修正に切り替えられた。実際は 2 回 push を経てユーザー判断で抹本対策に切り替えた
- **該当アンチパターン**：AP-009 候補（テスト分離不足）に**「単発バグの修正で類似クラスを見落とす」横断観点**（既存）を組み合わせた事例。横断観点節「単発バグ修正で類似クラスを見落とす共通根」が、**「テスト分離」というメタな観点でも適用される**ことを示した

### AI協働観点
- **AI の判断ミス**：親修正（051）の検証で「ローカル `mvn test` 524件 PASS」をもって CI 緑を予測したが、**surefire の実行順序差を考慮していなかった**。Linux と Windows の filesystem 順序差は典型的な落とし穴で、「ローカル全件 PASS = CI 緑保証」ではないことを認識しておくべきだった
- **人間が止めるべきだった点**：親修正時点で「他テストにも同じ問題が潜在する可能性」を network 的に確認するレビュー観点があれば、`grep -L "@Transactional"` で潜在汚染源を一覧化して事前修正できた
- **該当アンチパターン**：親トラブル AP-009 候補（テスト分離不足）と同型。**「ローカル PASS = CI 緑」と思い込む盲点**を AP-009 のサブパターンとして併記する価値あり
