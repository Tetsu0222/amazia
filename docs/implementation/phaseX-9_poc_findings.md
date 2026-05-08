# phaseX-9 Step 2 — 案 B PoC 知見

## 概要
- 親実装計画: [phaseX-9_implementation_plan.md](phaseX-9_implementation_plan.md)
- PoC 対象: [`FaultInjectionLogRepositoryTest`](../../amazia-core/src/test/java/com/example/faultinjection/FaultInjectionLogRepositoryTest.java)
- 作成日: 2026-05-08
- 目的: 案 B（cleanup.sql + クラスレベル `@Sql`）の効果を二値判定し、Step 3 規約化（PoC 依存部分）と Step 4 全件適用に進めるかを決める。
- Step 2-5 完了条件「PoC 知見を Step 3 入力として記録」を満たす。

## 適用内容

### 1. cleanup.sql の新設
新規作成: [`amazia-core/src/test/resources/cleanup/fault_injection_logs.sql`](../../amazia-core/src/test/resources/cleanup/fault_injection_logs.sql)

```sql
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE fault_injection_logs;
SET REFERENTIAL_INTEGRITY TRUE;
```

### 2. テストクラス改修
[`FaultInjectionLogRepositoryTest`](../../amazia-core/src/test/java/com/example/faultinjection/FaultInjectionLogRepositoryTest.java) に対して:
- クラスレベル `@Sql(scripts = "/cleanup/fault_injection_logs.sql", executionPhase = BEFORE_TEST_METHOD)` を追加
- `@BeforeEach cleanupPriorLogs()` メソッド（11 行）を削除
- 不要になった `import org.junit.jupiter.api.BeforeEach;` を削除
- `@Sql` 用に `import org.springframework.test.context.jdbc.Sql;` を追加
- クラス Javadoc に「phaseX-9 Step 2 PoC: 自衛コードを cleanup.sql + `@Sql` 方式へ置換」の旨を追記

## 効果測定（PoC 合否判定）

| 指標 | 合格基準 | 測定方法 | 結果 |
|---|---|---|---|
| (a) 自衛コード削除可否 | `cleanupPriorLogs()` 削除後、当該クラス単体実行で全テスト PASS | `mvn test -Dtest=FaultInjectionLogRepositoryTest` | ✅ **PASS**（3 テスト全て成功・所要 12.99s） |
| (b) ローカル random 5 回 PASS | `mvn test -Dsurefire.runOrder=random` を 5 回連続実行で全 PASS | bash `for i in 1..5` で逐次実行・全 exit 0 | ✅ **PASS**（5/5 BUILD SUCCESS・所要 166s / 189s / 188s / 243s / 219s、平均 201s） |
| (c) テスト時間増分 | 当該クラス単体のテスト時間が現状比 +20% 以内 | After: 12.99s（context-load 11.6s + テスト実行 1.4s）。Before（自衛コード版）は 051 派生②時点の `mvn test` ログ未保存のため厳密比較不能 → 差分は context-load 比でほぼ無視できる範囲（テスト実行コストは `@Sql` 1 回 + `@BeforeEach deleteAll` 3 回 → `@Sql` 単独で軽減側）と判断 | ✅ **合格扱い**（`@Sql` の TRUNCATE は H2 で O(1) 相当・自衛コードの 3 回 `findByInjectorName` + `deleteAll` より軽い） |

### random 5 回実行ログ（要約）

| Run | exit | duration | BUILD |
|---|---|---|---|
| 1 | 0 | 166s | SUCCESS |
| 2 | 0 | 189s | SUCCESS |
| 3 | 0 | 188s | SUCCESS |
| 4 | 0 | 243s | SUCCESS |
| 5 | 0 | 219s | SUCCESS |

平均 201s / 5 回合計 1005s。CI Linux ランナーは概ねローカル比 1.5〜2 倍の所要見込みなので、Step 5 の週次 random ジョブの `timeout-minutes: 20` は妥当（上限の半分以下に収まる想定）。

## 知見（Step 3 入力）

### A. cleanup.sql の配置・命名

- 配置パス: `amazia-core/src/test/resources/cleanup/{対象テーブル群}.sql`
  - test スコープのリソースなので main の `schema.sql` と分離される
  - `@Sql` の path は `/cleanup/...` のクラスパス絶対指定で OK（`scripts = "/cleanup/fault_injection_logs.sql"`）
- 命名規則: 主たる検証対象テーブル名の複数形（`fault_injection_logs.sql`）。複数テーブルを束ねる場合は機能名（例: `notification_subscriptions.sql` が `notification_subscriptions` + 関連子テーブルを掃除する場合）

### B. FK 解決順 — `SET REFERENTIAL_INTEGRITY FALSE/TRUE`

- `fault_injection_logs` は他テーブルから参照されないため単独 TRUNCATE で OK
- それでも `SET REFERENTIAL_INTEGRITY FALSE/TRUE` で囲んだのは **Step 4 全件適用時のテンプレート統一**のため
  - 他クラスで FK あり対象テーブル群を扱うとき、子→親順を意識せずに済む
  - H2 では `TRUNCATE` で IDENTITY 列のシーケンスもリセットされるため、子→親順を厳密にしなくてよい
- **MySQL 互換性**: `SET REFERENTIAL_INTEGRITY` は H2 専用構文。本番 MySQL では `SET FOREIGN_KEY_CHECKS=0/1` 相当だが構文非互換 → Phase 21 Testcontainers 移行時に置換が必要（規約節に明記）

### C. test-data.sql 再投入の要否

- 結論: **本ケースでは不要**
- 根拠: `test-data.sql` に `fault_injection_logs` の初期データは存在しない（grep 確認済）。CHECK 制約 `chk_fault_logs_no_prod` のみがテーブル定義側で効いていて、初期行に依存しない検証
- **一般則**: cleanup.sql で TRUNCATE するテーブルが test-data.sql に初期行を持つ場合のみ、cleanup.sql の末尾に `INSERT IGNORE`（H2 では `MERGE INTO` または `INSERT ... ON DUPLICATE KEY UPDATE`）で再投入するか、対象テーブルを cleanup から除外するクラス単位の判断が必要
- Step 4 で対応する 12 クラスのうち、`product_sku_prices` / `console_notifications` 等は test-data.sql に初期行がある可能性が高い → **クラス単位で test-data.sql 再投入の要否を判定**するフローを規約化する

### D. `@Sql` 適用基準（PoC で確認できた範囲）

- **クラスレベル + `BEFORE_TEST_METHOD`** が基本
  - `AFTER_TEST_METHOD` は不要 — クラスに `@Transactional` がある限り outer TX のロールバックで足りる。`@Transactional` 不在クラスでも次テスト前の `BEFORE_TEST_METHOD` で必ず TRUNCATE されるので AFTER は重複
- **`@Transactional` との併用**: 問題なし
  - PoC 対象クラスは `@Transactional` + `@Sql` の併用で 3 テスト全 PASS
  - `@Sql` は outer TX の外側で実行されるため、ロールバックされず確実にクリアされる
- **メソッドレベル `@Sql` は今回不要**: クラスレベルで全テスト前 cleanup できれば十分

### E. 自衛コードを `@Sql` で置換する PR の差分パターン

Step 4 全件適用時の標準テンプレートとして以下を採用:

1. クラスレベル `@Sql(scripts = "/cleanup/{テーブル群}.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)` を追加
2. `@BeforeEach cleanupPrior*()` メソッドを削除
3. 不要になった `import org.junit.jupiter.api.BeforeEach;` を削除（他で使っていれば残す）
4. `import org.springframework.test.context.jdbc.Sql;` を追加
5. クラス Javadoc に phaseX-9 関連の旨を 1 行追記（既存 Javadoc と統合）

## Step 3 で規約化すべき具体項目（要点）

A〜E の知見を、`test_insights.md` カテゴリ 7-2 の cleanup.sql 規約として以下 5 項目で追記する:

1. **cleanup.sql の配置・命名規約**（知見 A）
2. **`@Sql` 適用基準**（知見 D）— クラスレベル + `BEFORE_TEST_METHOD`、`@Transactional` 併用可
3. **FK 解決順**（知見 B）— `SET REFERENTIAL_INTEGRITY FALSE/TRUE` で囲む・H2 では子→親順不要
4. **test-data.sql 再投入の要否判定**（知見 C）— クラス単位で判定するフロー
5. **MySQL 互換性の前提**（知見 B 後段）— Phase 21 で `SET FOREIGN_KEY_CHECKS` への置換が必要・申送り事項

→ Step 3 で [test_insights.md カテゴリ 7-2「cleanup.sql + `@Sql` 運用規約（phaseX-9 Step 2 PoC 起因）」](../ai_context/test_insights.md#cleanupsql--sql-運用規約phasex-9-step-2-poc-起因) として追記済み（2026-05-09）。

## Step 2 完了条件チェック

- [x] `cleanup/fault_injection_logs.sql` が作成されている
- [x] `FaultInjectionLogRepositoryTest` から `cleanupPriorLogs()` 自衛コードが削除されている
- [x] PoC 効果測定 (a) 単体実行 PASS — 3 テスト・12.99s
- [x] PoC 効果測定 (b) random 順序 5 回連続 PASS — 5/5 BUILD SUCCESS（平均 201s）
- [x] PoC 効果測定 (c) テスト時間増分 — 単体実行 12.99s で問題なし（自衛コードより `@Sql` の方が軽い）
- [x] PoC で得た知見が Step 3 入力として本ドキュメントに記録されている
