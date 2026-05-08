# フェーズX-9 実装計画（テスト分離設計の体系化と H2 共有問題の抹本対策）

## 概要
- 対象設計書: [phaseX-9_test_isolation_redesign.md](../design/phaseX/phaseX-9_test_isolation_redesign.md)（**実装フェーズ移行確定 / 2026-05-08**）
- 対象範囲: Amazia Core テスト基盤 / `docs/ai_context/` 規約類 / `.github/workflows/` CI ワークフロー
- 段取り: 設計書「実装 Step（確定）」の **Step 0 → 1 → 2 → 3 → 4 → 5 → 6** の 7 段階で実施
- 作成日: 2026-05-08
- 親フェーズ: なし（時系列フェーズ非依存の横断的品質改善フェーズ）
- 同系列フェーズ: [phaseX-6](../design/phaseX/phaseX-6_post_deploy_schema_healthcheck.md)（スキーマ層の構造的盲点）/ [phaseX-7](../design/phaseX/phaseX-7_ai_collaboration_antipatterns.md)（AI 協働層の構造的盲点）
- 直接の起因: [051_ci_apply_scheduled_prices_test_h2_isolation_and_market_e2e_timeout.md](../troubles/051_ci_apply_scheduled_prices_test_h2_isolation_and_market_e2e_timeout.md) の派生①〜③
- 直系後続フェーズ: **Phase 21（仮称：Testcontainers 移行による本番 DB 等価テスト基盤）** — 本フェーズ Step 6 で設計書スコープ定義のみ作成

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | Step 0 → 1 → 2 → 3 → 4 → 5 → 6 を厳守。Step 跨ぎの先回り実装禁止。**Step 1（規約 PoC 非依存部分）を Step 2（PoC）の前に確定**することで、PoC 結果に規約が引きずられて場当たり的になるのを防ぐ |
| 案選定 | **案 B（cleanup.sql + `@Sql`）+ 規約化 + 週次 CI random 順序ジョブ**。**案 A は CI 時間予算破綻（不採用）**。**案 C（Testcontainers）は Phase 21 へ分離**（無料枠完走方針 / メモリ `feedback_free_tier_first` 準拠） |
| TDD | 本フェーズはテスト基盤改修であり、検証対象は「random 順序 5 回連続 PASS」「自衛コード削除後も PASS」「CI 週次ジョブが意図通り発火・Issue 起票」の 3 点。新規プロダクトコードは増やさない |
| コーディング規約 | [coding_guidelines.md](../coding_guidelines.md) 厳守。テスト規約の追記は [test_insights.md カテゴリ 7-2](../ai_context/test_insights.md) に集約 |
| 環境変数 | 本フェーズで新規環境変数の追加は予定なし。万一発生した場合のみ `docker-compose.yml` + `application-test.properties` + Console `phpunit.xml` をセット更新（規約 4-3） |
| テスト値 | テストファイル内のハードコードは禁止、`@Value` / `config()` 経由（規約 4-1） |
| メモリ事項 | t3.micro `-Xmx384m` 制約のため、cleanup.sql で TRUNCATE 対象を最小化（全テーブル一括 TRUNCATE は H2 でも GC 圧を生む）。クラス単位で対象テーブル群を絞る運用にする |
| 無料枠最優先 | Step 5 の random 順序ジョブは **週次 cron** とし PR 毎チェックには含めない（GitHub Actions 月 2,000 分・flaky による開発体験劣化を回避） |
| AP-009 / TPL-009 | phaseX-7 メモリ方針「新規パターン 2 件以上累積で AP 化」のとおり、051 派生①〜③で 4 件累積済み。本フェーズ Step 1 で正式化 |
| CI 失敗時の Issue 自動起票 | `actions/github-script` で `mvn test` 失敗時に Issue を自動作成。ラベル `flaky-test` / `random-order` を付与。同一タイトルの Open Issue が既にある場合は重複作成しない |
| ドキュメント反映 | `test_insights.md` カテゴリ 7-2 / `ai_collaboration_antipatterns.md` AP-009 / `prompt_templates.md` TPL-009 / 設計書 phaseX-9 完了の定義チェックボックス を **同フェーズ内**で更新（CLAUDE.md「DB / API 設計書のメンテナンスルール」と同思想） |

### 設計書からの「本フェーズのスコープ外」確認

| 項目 | 取り扱い |
|------|---------|
| Testcontainers 移行（案 C） | Phase 21 へ分離（Step 6 で設計書スコープ定義のみ作成） |
| Market（Vitest）/ Console（PHPUnit）側のテスト分離見直し | スコープ外（H2 共有問題は Core 起因） |
| 全テストの再設計 | スコープ外。既存テストの **検証意図を保ったまま** 分離方法のみ差し替える |
| `scheduler-enabled=true` 13 クラスの非決定性排除（設計書 S4） | 部分対応に留める。Step 0 で棚卸しのみ実施し、構造的解決は Phase 21 へ申し送る |
| `@Transactional` 不在 6 クラス（マルチスレッド検証 / REQUIRES_NEW 検証 / アーカイブ系）の本格再設計 | Step 0 で棚卸し・Step 4 で個別判断（cleanup.sql で対応可能なものは対応、本格再設計は Phase 21） |

---

## 1. Step 0 — 妥協点の棚卸し（0.5 日）

派生③で revert された `@Transactional` 不在 6 クラスと `scheduler-enabled=true` 13 クラスを「Step 4 で cleanup.sql 適用するか / Phase 21 引継ぎか」で分類する。

### 1-1. 棚卸しドキュメントの作成

**新規作成**: `docs/implementation/phaseX-9_concession_inventory.md`

| 列 | 内容 |
|---|---|
| クラス名 | テストクラスの FQCN |
| 種別 | `@Transactional` 不在 / `scheduler-enabled=true` / 両方 |
| 検証意図 | なぜ `@Transactional` を付けられなかったのか（マルチスレッド / REQUIRES_NEW / アーカイブ等） |
| 汚染源か | 他テストへ残置を出すか（Yes/No） |
| 汚染受け側 | 他テストの残置を受けるか（Yes/No） |
| Step 4 対応 | `@Sql` 適用 / 個別 cleanup / Phase 21 申送り のいずれか |
| 備考 | 関連 troubles / 制約事項 |

### 1-2. 棚卸し対象クラスの収集方法

```powershell
# @Transactional 不在の @SpringBootTest クラス抽出
Grep -Pattern "@SpringBootTest" -Path amazia-core\src\test -OutputMode files_with_matches
# 上記結果から @Transactional を含まないファイルを差分抽出

# scheduler-enabled=true 利用クラス抽出
Grep -Pattern "scheduler-enabled\s*=\s*true|scheduler\.enabled\s*=\s*true" -Path amazia-core\src\test -OutputMode files_with_matches
```

**完了条件**: 棚卸し表に 19 クラス前後（不在 6 + scheduler 13・重複可能性あり）が分類済みで、各クラスの「Step 4 対応」列が確定している。

---

## 2. Step 1 — 規約化（PoC 非依存部分）（0.5 日）

### 2-1. AP-009 の追加

**対象ファイル**: [docs/ai_context/ai_collaboration_antipatterns.md](../ai_context/ai_collaboration_antipatterns.md)

#### AP-009: テスト分離不足 + 単発 PR で類似クラス見落とし

| 節 | 内容 |
|---|---|
| 症状 | H2 共有 DB で件数アサーション失敗が連鎖。1 クラスへ `@Transactional` 付与で対症療法を繰り返し、最終的に全クラス一括付与で本来の検証意図を踏み潰す |
| 出典 | 051 派生①（`ApplyScheduledPricesJobTest.APP_3`）/ 派生②（`FaultInjectionLogRepositoryTest` / `NotificationSubscriptionRepositoryTest`）/ 派生③前半（46 クラス一括付与・うち 6 クラス revert）/ 派生③後半（REQUIRES_NEW 貫通の自衛コード追加） |
| 起こりやすい場面 | 新規 Repository テスト追加時 / 件数アサーションを伴うテスト修正時 / 「他テストでは PASS なのに自分のテストだけ失敗」を見たとき |
| 対応プロンプトスニペット | TPL-009 を双方向リンク |
| 判定の 3 軸 | (a) AI が「単発症状の対症療法」に逃げ込んだ / (b) 人間が「他クラスへの影響」を確認するゲートを設けなかった / (c) 構造的に H2 共有が前提 |
| 改善のレバー | 規約による事前明文化（test_insights カテゴリ 7-2） + cleanup.sql 方式の採用 + 週次 random 順序 CI |

### 2-2. TPL-009 の追加（双方向リンク）

**対象ファイル**: [docs/ai_context/prompt_templates.md](../ai_context/prompt_templates.md)

#### TPL-009: テスト分離影響の事前確認プロンプト

```
これから {対象クラス} に件数アサーションを伴うテストを追加します。
着手前に以下を確認してください：
1. このテストは @SpringBootTest を使うか。Yes なら H2 共有 DB に乗る
2. 検証対象テーブルへ書き込む他テスト（特に @Transactional REQUIRES_NEW 経由）が他に何件あるか grep で列挙
3. それらの残置がロールバックを貫通する可能性があるか（FaultInjectionLogger / BatchAlertNotifier / BatchExecutionRecorder 等）
4. 貫通する場合、@Transactional だけでは不十分。cleanup.sql + @Sql を検討する
5. 規約：docs/ai_context/test_insights.md カテゴリ 7-2 を参照
6. 関連 AP：AP-009（テスト分離不足 + 単発 PR で類似クラス見落とし）
```

### 2-3. test_insights.md カテゴリ 7-2 の PoC 非依存部分追記

**対象ファイル**: [docs/ai_context/test_insights.md](../ai_context/test_insights.md) カテゴリ 7-2

追記内容（PoC 非依存）:

1. **`@Transactional` 付ける/付けない判断軸**
   - 付ける条件: クラスのすべてのテストが書き込みを伴い、書き込みが REQUIRES_NEW を経由しないこと
   - 付けない条件: マルチスレッド検証 / REQUIRES_NEW 経由検証 / トランザクション境界そのものの検証 / バッチアーカイブ等のテーブル間移動検証
   - 「付けない」と判断した場合は **クラス Javadoc 冒頭に理由を明記**（一括付与の踏み潰し防止）

2. **件数アサーション規約**
   - 「全件カウント」は他テスト残置で破綻する。**自テスト所有 ID（fixture id / setUp で発番した ID）でフィルタしたカウント**を使う
   - `findByInjectorName` のような自衛フィルタを使う場合は、フィルタ条件と「なぜ全件で見られないのか」をコメントで明記

3. **スナップショット差分規約**
   - before スナップショットは必ず「自テスト対象データのみ」で計算。`productId` / `marketCustomerId` 等のフィルタを before/after の両方に適用すること（051 派生③ INV_2 起因）

4. **AP-009 / TPL-009 への双方向リンク**

### 2-4. Step 1 完了条件

- [ ] AP-009 が `ai_collaboration_antipatterns.md` に追加されている
- [ ] TPL-009 が `prompt_templates.md` に追加され、AP-009 と双方向リンクされている
- [ ] `test_insights.md` カテゴリ 7-2 に PoC 非依存規約 4 項目が追記されている
- [ ] CLAUDE.md からの参照（AP-001〜008 → AP-001〜009）に齟齬がないか確認

---

## 3. Step 2 — 案 B PoC（`FaultInjectionLogRepositoryTest`）（1 日）

### 3-1. PoC 対象選定理由

[`FaultInjectionLogRepositoryTest`](../../amazia-core/src/test/java/com/example/faultinjection/FaultInjectionLogRepositoryTest.java) は以下の特徴を持ち、効果測定に最適：

- 既に `@BeforeEach cleanupPriorLogs()` の自衛コードが入っている（051 派生③後半）
- `FaultInjectionLogger` の REQUIRES_NEW 経由貫通が直接の汚染源
- 関連クラスが明確（`SalesMismatchInjector` / `InventoryMismatchInjector` / `DeliveryTroubleInjector`）→ TRUNCATE 対象が絞りやすい
- **自衛コードの削除可否**で案 B の効果を二値判定できる

### 3-2. cleanup.sql の作成

**新規作成**: `amazia-core/src/test/resources/cleanup/fault_injection_logs.sql`

```sql
-- phaseX-9 Step 2 PoC: FaultInjectionLogRepositoryTest 用 cleanup
-- REQUIRES_NEW 経由でロールバックを貫通する fault_injection_logs を確実にクリアする
-- FK 解決順: fault_injection_logs は他テーブルから参照されないため単独 TRUNCATE で OK
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE fault_injection_logs;
SET REFERENTIAL_INTEGRITY TRUE;
```

**注**: H2 構文（`SET REFERENTIAL_INTEGRITY`）。MySQL 互換考慮は Phase 21 で Testcontainers 移行時に再評価。

### 3-3. テストクラスへの `@Sql` 適用

`FaultInjectionLogRepositoryTest` に対し:

```java
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
@Sql(
    scripts = "/cleanup/fault_injection_logs.sql",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class FaultInjectionLogRepositoryTest {
    // @BeforeEach cleanupPriorLogs() を削除し、Repository deleteAll の自衛コードを除去
}
```

**変更点**:
1. クラスレベル `@Sql` を追加
2. `@BeforeEach cleanupPriorLogs()` メソッドと付随コメント（既存 8 行）を削除
3. `@BeforeEach` 利用がなくなれば `import org.junit.jupiter.api.BeforeEach;` も削除

### 3-4. 効果測定（PoC の合否判定）

以下 3 指標すべて満たせば PoC 成功・Step 4 で全件適用へ進む。1 つでも不合格なら Step 4 着手前に追加調査。

| 指標 | 合格基準 | 測定方法 |
|---|---|---|
| (a) 自衛コード削除可否 | `cleanupPriorLogs()` 削除後、当該クラス単体実行で全テスト PASS | `mvn test -Dtest=FaultInjectionLogRepositoryTest` |
| (b) ローカル random 5 回 PASS | `mvn test -Dsurefire.runOrder=random` を 5 回連続実行で全 PASS | PowerShell ループで 5 回実行・全 exit 0 |
| (c) テスト時間増分 | 当該クラス単体のテスト時間が現状比 +20% 以内 | `mvn test -Dtest=FaultInjectionLogRepositoryTest` の所要時間を Before/After で比較 |

### 3-5. Step 2 完了条件

- [ ] `cleanup/fault_injection_logs.sql` が作成され、コミットされている
- [ ] `FaultInjectionLogRepositoryTest` から `cleanupPriorLogs()` 自衛コードが削除されている
- [ ] PoC 効果測定の 3 指標がすべて合格している
- [ ] PoC で得た知見（FK 解決順 / `SET REFERENTIAL_INTEGRITY` の挙動 / test-data.sql 再投入の要否）を Step 3 入力として `phaseX-9_poc_findings.md` に記録

---

## 4. Step 3 — 規約化（PoC 依存部分）（0.5 日）

### 4-1. test_insights.md カテゴリ 7-2 への cleanup.sql 規約追記

PoC 知見を反映して以下を追記:

1. **cleanup.sql の配置規約**
   - パス: `amazia-core/src/test/resources/cleanup/{対象テーブル群}.sql`
   - 命名: 主たる検証対象テーブル名の単数形 / 複数テーブル束ねるなら機能名（例: `fault_injection_logs.sql` / `notification_subscriptions.sql`）
   - 1 ファイルに複数 TRUNCATE を含めて良い（FK 解決順を `SET REFERENTIAL_INTEGRITY FALSE/TRUE` で囲む）

2. **`@Sql` 適用基準**
   - クラスレベル + `BEFORE_TEST_METHOD`（`AFTER_TEST_METHOD` は不要 — `@Transactional` ロールバックで足りる + `@Transactional` 不在クラスは次テスト前にクリーンアップされる）
   - 適用対象: 件数アサーションを行う Repository テスト / REQUIRES_NEW 経由汚染の受け側 / scheduler-enabled クラスのうち再現性が必要なもの

3. **FK 解決順**
   - `SET REFERENTIAL_INTEGRITY FALSE; ... TRUNCATE ...; SET REFERENTIAL_INTEGRITY TRUE;` で囲む
   - 子→親の順は不要（H2 では IDENTITY のリセットも TRUNCATE で行われる）

4. **test-data.sql との関係**
   - test-data.sql は起動時 1 回投入。cleanup.sql で TRUNCATE すると当該テーブルの初期データも消える
   - 初期データが必要な場合は cleanup.sql の末尾に `INSERT IGNORE` で再投入するか、対象テーブルを cleanup から除外する判断をクラス単位で行う

5. **MySQL 互換性の前提**
   - cleanup.sql 内の `SET REFERENTIAL_INTEGRITY` は H2 専用構文。本番 MySQL では実行されない
   - Phase 21 Testcontainers 移行時に MySQL 互換構文（`SET FOREIGN_KEY_CHECKS=0/1`）への置換、または cleanup 機構自体の再設計が必要 — 申送り事項として明記

### 4-2. Step 3 完了条件

- [ ] `test_insights.md` カテゴリ 7-2 に cleanup.sql 規約 5 項目が追記されている
- [ ] PoC 知見ドキュメント（Step 2-5 で作成）が規約追記の根拠として参照されている

---

## 5. Step 4 — 案 B 全件適用（2〜3 日）

### 5-1. 適用対象の確定

Step 0 棚卸しの「Step 4 対応 = `@Sql` 適用」列のクラスに対して順次適用。優先順位:

1. **既に自衛コードが入っているクラス**（`@BeforeEach cleanupPrior*` パターン）
   - `FaultInjectionLogRepositoryTest`（PoC 済）
   - `ConsoleNotificationRepositoryTest`
   - `NotificationSubscriptionRepositoryTest`
   - その他 Step 0 棚卸しで識別したもの

2. **`@Transactional` 不在 6 クラスのうち、cleanup.sql で対応可能なもの**
   - 棚卸し時に「Step 4 対応 = `@Sql` 適用」と判定したクラス

3. **`scheduler-enabled=true` 13 クラスのうち、件数アサーションを行うもの**
   - 棚卸し時に「Step 4 対応 = `@Sql` 適用」と判定したクラス

### 5-2. 作業フロー（クラス単位）

各クラスについて以下を順に実施:

1. 検証対象テーブル群を特定（`@Test` 内の Repository 操作から逆引き）
2. cleanup.sql を新設 or 既存ファイルに追記（命名規約に従う）
3. クラスレベル `@Sql` 適用
4. 既存自衛コード（`@BeforeEach cleanupPrior*`）を削除
5. 当該クラス単体で `mvn test` 実行・PASS 確認
6. 次クラスへ

### 5-3. ローカル全体検証

全件適用後:

```powershell
# random 順序 5 回連続 PASS を確認
1..5 | ForEach-Object {
    Write-Host "=== Run $_ ==="
    mvn test -Dsurefire.runOrder=random
    if ($LASTEXITCODE -ne 0) { throw "Run $_ FAILED" }
}
```

### 5-4. Step 4 完了条件

- [ ] Step 0 棚卸しで「Step 4 対応 = `@Sql` 適用」と判定した全クラスへの適用完了
- [ ] 各クラスから自衛コード（`@BeforeEach cleanupPrior*`）が削除されている
- [ ] ローカル `mvn test -Dsurefire.runOrder=random` ×5 連続 PASS
- [ ] CI 本番（`deploy.yml` の通常ジョブ）で PASS

---

## 6. Step 5 — CI 週次 random 順序ジョブ追加（0.5 日）

### 6-1. ワークフロー新設

**新規作成**: `.github/workflows/weekly-test-random-order.yml`

```yaml
name: Weekly Test Random Order

on:
  schedule:
    # 毎週月曜 03:00 JST = 18:00 UTC 日曜
    - cron: '0 18 * * 0'
  workflow_dispatch:  # 手動実行も許可

jobs:
  random-order-test:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      - name: Run tests in random order
        id: test
        working-directory: amazia-core
        run: mvn test -Dsurefire.runOrder=random -B
        continue-on-error: true
      - name: Open issue on failure
        if: steps.test.outcome == 'failure'
        uses: actions/github-script@v7
        with:
          script: |
            const title = `[Flaky] Weekly random-order test failed: ${context.runId}`;
            const { data: existing } = await github.rest.issues.listForRepo({
              owner: context.repo.owner,
              repo: context.repo.repo,
              state: 'open',
              labels: 'flaky-test,random-order'
            });
            const body = [
              `Weekly random-order CI failed.`,
              ``,
              `- Run: ${context.serverUrl}/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}`,
              `- Commit: ${context.sha}`,
              `- 関連設計書: docs/design/phaseX/phaseX-9_test_isolation_redesign.md`,
              `- 関連トラブル: docs/troubles/051_*.md`,
              ``,
              `## 調査手順`,
              `1. Failed テストのクラス名を特定`,
              `2. test_insights.md カテゴリ 7-2 で cleanup.sql 規約を確認`,
              `3. 当該クラスの cleanup.sql 適用漏れがないか確認`,
              `4. AP-009 / TPL-009 を参照して類似クラス未対応がないか横展開チェック`,
            ].join('\n');
            if (existing.length === 0) {
              await github.rest.issues.create({
                owner: context.repo.owner,
                repo: context.repo.repo,
                title: title,
                body: body,
                labels: ['flaky-test', 'random-order']
              });
            } else {
              await github.rest.issues.createComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                issue_number: existing[0].number,
                body: `再発: ${context.serverUrl}/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}`
              });
            }
      - name: Fail job if test failed
        if: steps.test.outcome == 'failure'
        run: exit 1
```

### 6-2. ラベル準備

GitHub リポジトリ設定で以下ラベルを事前作成（手動 or `gh label create`）:

- `flaky-test`（色: 黄系）
- `random-order`（色: 青系）

### 6-3. 動作確認

1. PR で `weekly-test-random-order.yml` を追加
2. マージ後、`workflow_dispatch` で手動実行 → PASS 確認
3. 故意失敗テスト（一時的に `Assertions.fail()` を仕込む）で Issue 自動起票確認
4. 同タイトルの Open Issue がある状態で再失敗 → コメント追加のみ（重複起票しない）確認
5. 故意失敗を revert

**注意**: 故意失敗テストの確認は **別ブランチ**で行い、main にはマージしない。phaseX-6 の故意失敗デプロイ確認と同じ運用。

### 6-4. Step 5 完了条件

- [ ] `weekly-test-random-order.yml` が main にマージされている
- [ ] `workflow_dispatch` 手動実行で PASS 確認済み
- [ ] 別ブランチでの故意失敗テストで Issue 自動起票・重複防止が動作確認済み
- [ ] ラベル `flaky-test` / `random-order` がリポジトリに作成されている

---

## 7. Step 6 — Phase 21 設計書スコープ定義（0.5 日）

### 7-1. 新規作成

**新規作成**: `docs/design/phaseX/phaseX-10_testcontainers_migration.md`（Phase 21 暫定。正式番号は時系列フェーズ進行で確定）

ステータスは **🟡 スコープ定義（着手未確定）** で開始。phaseX-9 設計書と同フォーマットで以下を含む:

1. **位置付け**: phaseX-9 で残った妥協点の構造的解決 + H2 ドリフト系（027/037/038/044/045/049/050）の根絶
2. **背景**: なぜ phaseX-9 では足りなかったか（cleanup.sql は H2 専用構文・本番乖離は解消されない）
3. **phaseX-9 からの引継ぎ事項**:
   - `@Transactional` 不在 6 クラスのうち、cleanup.sql で対応しきれなかったクラス（マルチスレッド検証・テーブル間アーカイブ）
   - `scheduler-enabled=true` 13 クラスの非決定性
   - cleanup.sql の MySQL 互換性問題（`SET REFERENTIAL_INTEGRITY` → `SET FOREIGN_KEY_CHECKS`）
4. **抹本対策**: Testcontainers + per-class MySQL（phaseX-9 設計書 案 C を継承）
5. **コスト試算**: GitHub Actions CI 時間増分・Docker 利用料（無料枠範囲確認）
6. **完了の定義**: スコープ定義段階のチェックボックスのみ（実装フェーズは未着手）

### 7-2. phaseX-9 設計書からの相互リンク

phaseX-9 設計書「関連ドキュメント」節に Phase 21 設計書を追加。

### 7-3. Step 6 完了条件

- [ ] Phase 21 設計書が新規作成されている（スコープ定義段階）
- [ ] phaseX-9 で残った妥協点が引継ぎ事項として明記されている
- [ ] phaseX-9 設計書の「関連ドキュメント」に Phase 21 へのリンクが追加されている

---

## 8. フェーズ完了の定義

phaseX-9 全体の完了は以下すべてを満たすこと:

- [x] Step 0：妥協点棚卸し表（`phaseX-9_concession_inventory.md`）が完成
- [x] Step 1：AP-009 / TPL-009 / `test_insights.md` カテゴリ 7-2（PoC 非依存部分）が更新済み
- [x] Step 2：`FaultInjectionLogRepositoryTest` の PoC 3 指標すべて合格・知見ドキュメント作成済み
- [x] Step 3：`test_insights.md` カテゴリ 7-2（PoC 依存部分）が更新済み
- [x] Step 4：全件適用完了・自衛コード除去済み・ローカル random 5 回 PASS
- [x] Step 5：`weekly-test-random-order.yml` 稼働開始・故意失敗で Issue 自動起票確認済み
- [x] Step 6：Phase 21 設計書（スコープ定義段階）作成済み・phaseX-9 設計書へのリンク反映済み
- [x] CI 本番デプロイ（`deploy.yml`）が PASS（2026-05-09 amazia-core テスト 3m 44s で SUCCESS）
- [x] 設計書 `phaseX-9_test_isolation_redesign.md` の「実装フェーズ完了の定義」チェックボックス全項目チェック済み
- [x] `troubles/051_*.md` のステータス・派生ノートに「phaseX-9 で抹本対策実施済み」を追記
- [x] `troubles/README.md` の関連エントリ更新

**完了日**: 2026-05-09

---

## 9. リスクと対応

| リスク | 影響 | 対応 |
|---|---|---|
| cleanup.sql の H2 構文が将来 MySQL 互換性問題を招く | Phase 21 移行時に書き直し工数増 | 規約節に明示・Phase 21 申送り事項として明記済み |
| Step 4 全件適用で見落としクラスが残り、Step 5 週次 CI で初めて発覚 | flaky 残存 | Step 5 の Issue 自動起票で運用カバー（早期検知の仕組みそのものが目的） |
| GitHub Actions 無料枠超過 | CI 停止リスク | 週次 1 回（月 4〜5 回）+ 手動実行のみ。`amazia-core` の `mvn test` は約 3〜5 分なので月 25 分以内 |
| PoC 対象 `FaultInjectionLogRepositoryTest` の効果が限定的（FK 関係単純なので理想的ケース） | 他クラスでの効果が読みにくい | Step 3 規約化時に「FK 関係が複雑なケース」の節を追加・該当クラスを Step 4 で慎重に扱う |
| `@Transactional` 不在 6 クラスが cleanup.sql で対応できない場合 | Step 4 で全件適用が完了しない | Step 0 棚卸し時点で「Phase 21 申送り」として分類・本フェーズでは無理に対応しない |

---

## 10. 想定工数

| Step | 工数 |
|---|---|
| Step 0 妥協点棚卸し | 0.5 日 |
| Step 1 規約化（PoC 非依存） | 0.5 日 |
| Step 2 PoC | 1 日 |
| Step 3 規約化（PoC 依存） | 0.5 日 |
| Step 4 全件適用 | 2〜3 日 |
| Step 5 CI 週次ジョブ | 0.5 日 |
| Step 6 Phase 21 設計書 | 0.5 日 |
| **合計** | **5.5〜6.5 日** |

---

## 11. 関連ドキュメント

- 設計書: [phaseX-9_test_isolation_redesign.md](../design/phaseX/phaseX-9_test_isolation_redesign.md)
- 起因トラブル: [051_ci_apply_scheduled_prices_test_h2_isolation_and_market_e2e_timeout.md](../troubles/051_ci_apply_scheduled_prices_test_h2_isolation_and_market_e2e_timeout.md)
- 同系列メタ品質フェーズ: [phaseX-6_post_deploy_schema_healthcheck.md](../design/phaseX/phaseX-6_post_deploy_schema_healthcheck.md) / [phaseX-7_ai_collaboration_antipatterns.md](../design/phaseX/phaseX-7_ai_collaboration_antipatterns.md)
- 規約追記対象: [test_insights.md](../ai_context/test_insights.md) / [ai_collaboration_antipatterns.md](../ai_context/ai_collaboration_antipatterns.md) / [prompt_templates.md](../ai_context/prompt_templates.md)
- 後続: Phase 21（Testcontainers 移行・本フェーズ Step 6 で設計書スコープ定義のみ作成）
