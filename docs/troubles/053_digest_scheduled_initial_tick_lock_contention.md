# 053: `DigestNotificationDispatchJob` の `@Scheduled` 初期 tick がテスト fixture とロックを競合し SES が呼ばれない

## ステータス
✅ 解決済（2026-05-09）

> 本 NNN は phase17 Step 10 で枠予約していた **`XXX_batch_lock_leak.md`**（バッチロックリーク雛形）を昇格したもの。
> 症状（"skip: another instance is running" のみで実行されない）は雛形と完全一致したが、**根本原因は雛形の想定（AOP proxy / OOM）とは異なり、`@Scheduled` 初期 tick とテスト本体の競合**だった。

## 発症箇所
- ローカル `mvn test`（amazia-core / 全 534 件）
- 失敗テスト: [DigestRestartCatchUpE2ETest.E2E_7_OFF期間に蔓延した抑制通知3件が_ON復帰時に1通のダイジェストにまとめて送出される:79](../../amazia-core/src/test/java/com/example/batch/e2e/DigestRestartCatchUpE2ETest.java#L79)
- 052 の調査中、ローカル `mvn test` 全件実行で偶発的に顕在化（CI ログには載っていなかった）

## 症状
```
[ERROR] DigestRestartCatchUpE2ETest.E2E_7_OFF期間に蔓延した抑制通知3件が_ON復帰時に1通のダイジェストにまとめて送出される:79
Wanted but not invoked:
sesClient.sendEmail(<Capturing argument: SendEmailRequest>);
Actually, there were zero interactions with this mock.
```

ジョブ本体ログ（`target/surefire-reports/TEST-...DigestRestartCatchUpE2ETest.xml` 抜粋）：
```
2026-05-09T09:21:43.612 INFO ... Started DigestRestartCatchUpE2ETest in 11.276 seconds
2026-05-09T09:21:44.259 INFO ... [DigestNotificationDispatchJob] skip: another instance is running
```

`[main]` スレッドで「skip: another instance is running」が出ている — これはテスト本体（`digestJob.run("scheduler")`）が `BatchJobLockRegistry.tryAcquire` に失敗して弾かれた跡。

## 根本原因

### 構造

[DigestNotificationDispatchJob.java:36-38](../../amazia-core/src/main/java/com/example/batch/job/DigestNotificationDispatchJob.java#L36-L38) は **`@ConditionalOnProperty(name = "amazia.batch.notifications.digest-enabled", havingValue = "true", matchIfMissing = true)`** だけでガードされており、他 12 ジョブのような `@ConditionalOnProperty(scheduler-enabled, true)` ガードは**意図的に持たない**（[phase17 設計書 §2.3 / M-2 / J-1](../design/phase11_20/phase17_batch_processing.md#L1379)：「`BATCH_DIGEST_ENABLED` は `BATCH_SCHEDULER_ENABLED` から独立。業務バッチ停止 + ダイジェストだけ動かす運用シナリオが要件」）。

[Main.java:7-8](../../amazia-core/src/main/java/com/example/Main.java#L7-L8) で `@EnableScheduling` が**無条件で**有効化されているため、テストが `digest-enabled=true` を上書きすると：

1. context 起動完了
2. **直後**に Spring `TaskScheduler` が `@Scheduled(fixedRateString = "${...digest-interval-ms:300000}")` の **initialDelay=0（fixedRate デフォルト）の初回 tick** を発火
3. scheduled 経路で `BatchJobLockRegistry.tryAcquire(JOB_NAME)` が成功 → `execute()` 走行（`recorder.start` の REQUIRES_NEW DB 書き込み等で数十 ms 程度）
4. **同時に** テスト本体メソッドが `[main]` で `persistSuppressed` x3 → `persistAdminWithSubscription` → `digestJob.run("scheduler")` を呼ぶ
5. テスト本体の `tryAcquire` が **scheduled の方の release より先**に走り `false` で弾かれる
6. `AbstractBatchJob.run` は「skip: another instance is running」を吐いて即 return → SES が一度も呼ばれない
7. `verify(sesClient, atLeastOnce())` が `Wanted but not invoked` で失敗

scheduled 経路は `findBySuppressedTrueAndDigestSentAtIsNullAndCreatedAtBefore` の結果が空（fixture INSERT 前）なので `BatchResult.of(0,0,0)` で即 return。**つまり scheduled 側はロックを「無意味に取って即離す」だけだが、その僅かな時間にテスト本体の手動 run と当たる**。

### 設計書 M-2 / J-1 との整合性

[phase17_batch_processing.md:1051](../design/phase11_20/phase17_batch_processing.md#L1051) は明確に「`BATCH_SCHEDULER_ENABLED=false ＋ BATCH_DIGEST_ENABLED=true` で起動 → 業務バッチは止まるが、`DigestNotificationDispatchJob` は 5 分間隔で起動し続ける」と書いており、**`DigestNotificationDispatchJob` に `@ConditionalOnProperty(scheduler-enabled)` を追加して他ジョブと揃える修正は設計書 M-2 / J-1 に反する**（「業務バッチ停止 + ダイジェスト動かす」運用シナリオが壊れる）。

したがって本件の修正は「**Scheduled の独立軸は維持**しつつ、**初回 tick が起動直後に走らないよう initialDelay を入れる**」方針となる。

## なぜ CI で検知できなかったか
- CI（`Deploy to EC2` ワークフロー）は本テスト群を `mvn test` で回しているが、本症状は local Windows 順序でも発火する確定的バグ
- 052 の調査中、ローカル `mvn test` 全件実行で初めて顕在化。当該テストは過去の surefire レポートでも `tests=1, failures=1` だったが、CI では別の test failure（052 系の 1 件）でビルド全体が止まっていたため**本件の存在自体が気付かれなかった**
- 雛形 `XXX_batch_lock_leak.md`（phase17 Step 10 で予約・本 NNN にリネーム済）が **「skip ログのみで実行されない」症状を予言**していたが、原因として挙げた候補（AOP proxy / OOM / JVM クラッシュ）は外していた。本件はより素朴な「Scheduled 初期 tick」が原因

## 修正内容

### 修正① 本番コード：`@Scheduled` に `initialDelayString` を追加

[DigestNotificationDispatchJob.java:60-69](../../amazia-core/src/main/java/com/example/batch/job/DigestNotificationDispatchJob.java#L60-L69)：

```java
@Scheduled(
        fixedRateString = "${amazia.batch.notifications.digest-interval-ms:300000}",
        initialDelayString = "${amazia.batch.notifications.digest-interval-ms:300000}")
public void scheduledRun() {
    run("scheduler");
}
```

`initialDelayString` を `fixedRateString` と同じ値（既定 5 分）に設定。これにより JVM 起動完了直後ではなく**5 分後**に初回 tick が走るようになる。

副次的メリット：本番運用でも「context 起動完了直後（認証 / DB プール / その他バックグラウンドが落ち着く前）に dispatch 経路を走らせる」というリスクが減る。

### 修正② テスト：`digest-interval-ms` を 24h にオーバーライド

[DigestRestartCatchUpE2ETest.java:44-50](../../amazia-core/src/test/java/com/example/batch/e2e/DigestRestartCatchUpE2ETest.java#L44-L50)：

```java
@SpringBootTest(properties = {
        "amazia.batch.notifications.digest-enabled=true",
        "amazia.batch.rate-limit.suppression-minutes=60",
        // 053: @Scheduled 初期 tick とテスト fixture / 手動 run の競合を避けるため
        // テスト期間中は scheduled tick が発火しないよう間隔を 24h に延ばす（initialDelay 防御の二重化）。
        "amazia.batch.notifications.digest-interval-ms=86400000"
})
```

修正① の `initialDelay` で起動直後の競合は防げるが、**テスト期間中に 5 分以上経過することは現実的に起きえない**ため `digest-interval-ms` を 24h に延ばすことで scheduler 経路を完全に黙らせる。これで「`@Scheduled` の二度目以降の tick が長尺テスト中に当たる」可能性も併せて潰せる。

### 修正対象外：`BatchEnabledFlagMatrixE2ETest$E2E_6_SchedulerOffDigestOn`

E2E_6 も `digest-enabled=true` を上書きしているが、`digestJob.run` を呼ばず **Bean の存在確認のみ**を行うため、initial tick が裏で走っても assert は失敗しない。最小限の修正に留めるため対象外とした（必要時に同パターンで延長可能）。

### 検証

- ✅ ローカル `mvn test -Dtest=DigestRestartCatchUpE2ETest,InventoryInconsistencyToNotificationE2ETest` が `Tests run: 2, Failures: 0, Errors: 0` / `BUILD SUCCESS`（2026-05-09 09:31）
- ✅ ローカル `mvn test` 全 534 件が `Tests run: 534, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`（2026-05-09 09:37 / 3 分 15 秒）
- ⏳ push 後の `Deploy to EC2` ワークフロー / `Weekly Test Random Order` で緑確認

## 再発防止

| 観点 | 対策 |
|------|------|
| **`@Scheduled` を持つ Bean は initial tick の影響を考える** | 起動直後の認証・DB プール・他 Bean 初期化と競合しうる。原則として `initialDelay` を `fixedRate` 1 周期相当（または十分な秒数）に設定する規律を `operational_insights.md` に追記候補 |
| **テストで `@Scheduled` 経路を持つ Bean を Bean 化する場合の防御** | クラス専用 properties で Bean を明示的に有効化するテストは、対応する `@Scheduled` の `fixedRate` / cron をテスト中に発火しないオーバーライド値に設定する（例：interval を 24h に伸ばす）。`test_insights.md` カテゴリ 7-2 に追記候補 |
| **雛形と実発生の照合** | 本件は雛形 XXX_batch_lock_leak.md の症状（"skip: another instance is running"）と一致したが、原因は雛形の想定と異なった。雛形の「想定される根本原因」節は**確定的に書きすぎず、症状を起こしうる経路を網羅する観点リスト**として運用すべき。今回 053 として昇格・本文を上書きで正確化 |

## AI協働観点
- **AI の判断ミス**：052 の修正後にローカル `mvn test` を全件回した結果別件が顕在化したが、初回反応では「自分の修正が壊した」と疑い revert に動いた。実際は既存バグで revert 後も再現したため切り分けに 1 サイクル消費。**ローカルで `target/surefire-reports/*.xml` を直接読めば修正前から落ちていたことが事前に分かった**（ファイルのタイムスタンプと test report XML の `failures=1` で）
- **人間が止めるべきだった点**：当該テストの初出時点（phase17 Step 8）で「`@Scheduled` を持つ Bean のテストに initial tick 防御が必要」というレビュー観点があれば事前に止まった。ただし設計書 M-2 / J-1 の独立軸そのものは正しいため、レビュー観点は「設計を変える」ではなく「テスト fixture を壊さない設計」に向ける必要がある
- **該当アンチパターン**：[AP-009「テスト分離不足 + 単発 PR で類似クラス見落とし」](../ai_context/ai_collaboration_antipatterns.md#ap-009-テスト分離不足--単発-pr-で類似クラス見落とし) のサブパターン候補。「`@Scheduled` を持つ Bean をテストで明示的に Bean 化するときの初期 tick 防御」観点。052 の AP-009 サブパターンと併せて、**「テスト fixture が触らない時間帯に勝手に動く Bean がいないか」**という観点で AP-009 への追記を検討
