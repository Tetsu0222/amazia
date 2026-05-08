# XXX: バッチロックリーク（雛形 / 未発生）

> **ステータス：** 🟢 雛形（未発生）。実発生時に連番化（次空き連番にリネームし、本ファイルは削除）。
> **作成経緯：** phase17 Step 10（2026-05-09）でバッチ運用上の警戒すべきトラブルとして枠予約。

## 想定される発症箇所

- `BatchJobLockRegistry`（`amazia-core/src/main/java/com/example/batch/config/BatchJobLockRegistry.java`）
- `AbstractBatchJob.run()` の `try { ... } finally { lockRegistry.release(jobName()); }` 経路
- 影響を受けるジョブ：scheduler 起動・手動起動の双方（`InventoryConsistencyCheckJob` 等 11 本）

## 想定される症状

- ジョブを 1 度起動した後、`POST /api/console/batch/{jobName}/run` または次回 cron 起動が **「skip: another instance is running」ログのみで実行されない**
- `batch_executions` に新規 RUNNING 行が作成されない（前回完了の SUCCESS / FAILED 以降、新行ゼロ）
- JVM 再起動でだけ復旧する（`OrphanedRunningSweeper` も走らない症状）

## 想定される根本原因

- `BatchJobLockRegistry.locks` は `ConcurrentHashMap<jobName, AtomicBoolean>` で in-memory 管理。`AbstractBatchJob.run()` が `try { ... } finally { lockRegistry.release(jobName()); }` で確実に解放する設計だが、以下のいずれかで破綻しうる：
  1. `lockRegistry` が proxy 化されて `null` を参照（field 注入の AOP 衝突。phase17 Step 8 で類似事例あり：`DigestNotificationDispatchJob` の `@Transactional` 衝突を `DigestDispatchService` に分離して回避済み）
  2. `recorder.start()` 内で `RuntimeException` 以外の `Error`（OOM 等）が飛び `finally` まで到達しない
  3. JVM クラッシュ → 次回起動で `OrphanedRunningSweeper` が `RUNNING` を `FAILED` に巻き取るが、`lockRegistry` は `ApplicationReadyEvent` 後の状態（in-memory）なので影響なし。**疑うべきは 1 と 2 のみ**

## なぜ CI で検知できないか

- ロックリークは「2 回連続実行して 2 回目が skip される」という時系列依存の振る舞い。ユニットテストは `try/finally` の経路を 1 回ずつ検証するが、proxy 化や `Error` のような実行時エラーは型レベル / mock 経路では再現できない。
- `BatchJobLockRegistryTest`（[user memory: phaseX9_concession_inventory] に記載済）が存在するが、in-memory 状態の解放は単一 JVM 内の振る舞いで、運用環境での GC 圧 / OOM 状況とは異なる。

## 暫定回避策（実発生時）

1. `docker compose restart amazia-core` で JVM 再起動（in-memory `locks` リセット）
2. 同事象が `BatchExecution.status = RUNNING` の永続化と組み合わさっていれば、`OrphanedRunningSweeper` が次回起動時に巻き取る
3. 連発する場合：`BatchJobLockRegistry` の `release()` を `CompletableFuture.completedFuture(...)` 経由でなく **必ず synchronous** に呼ぶ実装に固定（v1 では既にそうなっているが将来リファクタで変わる可能性あり）

## 再発防止（実装側）

| 観点 | 対策 |
|------|------|
| AOP proxy 衝突 | `AbstractBatchJob` の field 注入と `@Transactional` を共存させない（Step 8 の `DigestDispatchService` 分離パターンを継承） |
| OOM 起因 | t3.micro `-Xmx384m` で `BatchJobLockRegistry` の `ConcurrentHashMap` が肥大化することはないが、bulk 書き込みジョブ（`PostalCsvImportJob`）は依然 OOM 候補。`SoftReference` 導入は不要だが、**`Error` が飛んだ際に `release()` を `finally` で呼ぶ**契約は維持する |
| 観測 | `BatchExecution.status = RUNNING` のレコードが 1 時間以上残存している場合、CloudWatch から SES アラートを飛ばす（phase17 設計書 §3 共通制御 3 / `OrphanedRunningSweeper` を 1 時間しきい値で前倒し動作させる代替設計）|

## AI協働観点

- AI の判断ミス：（実発生時に記入）
- 人間が止めるべきだった点：（実発生時に記入）
- 該当アンチパターン：（実発生時に記入）
