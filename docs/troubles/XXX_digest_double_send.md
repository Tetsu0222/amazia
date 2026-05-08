# XXX: ダイジェスト二重送信（雛形 / 未発生）

> **ステータス：** 🟢 雛形（未発生）。実発生時に連番化（次空き連番にリネームし、本ファイルは削除）。
> **作成経緯：** phase17 Step 10（2026-05-09）で再起動跨ぎダイジェストの永続化（N-7）が破綻したケースとして枠予約。

## 想定される発症箇所

- `DigestNotificationDispatchJob`（`amazia-core/src/main/java/com/example/batch/job/DigestNotificationDispatchJob.java`）
- `DigestDispatchService.dispatchOneTag`（`amazia-core/src/main/java/com/example/notification/service/DigestDispatchService.java`）
- 影響を受ける購読者：`inventory_alerts` / `sales_alerts` / `delivery_alerts` / `postal_alerts` / `batch_failure` の購読者

## 想定される症状

- 同一購読タグに対して **同じ件数 / 同じ代表通知** のダイジェストメールが 2 通以上届く
- `console_notifications` テーブルで同一 `id` に対して `digest_sent_at` が複数回 UPDATE されたログが残る（実際は最後の UPDATE しか残らないので、観測は SES 送信ログ側）
- 5 分間隔の自動実行で同じ集計ウィンドウ（`created_at < NOW() - INTERVAL 60 MINUTE`）に対して 2 回送出が走る

## 想定される根本原因

- `DigestDispatchService.dispatchOneTag` は `@Transactional(REQUIRES_NEW)` で「SES 送信 → `digest_sent_at` UPDATE」を 1 トランザクションで実行するが、以下のいずれかで破綻しうる：
  1. SES 送信成功 → DB UPDATE 失敗（コネクション断 / 楽観ロック衝突）→ 次回起動時に同レコードを再集計 → 2 通目送信
  2. 5 分間隔の `@Scheduled(fixedRateString = "${amazia.batch.notifications.digest-interval-ms:300000}")` が、JVM クラッシュからの復帰直後に「前回成功したつもり」と「実は UPDATE 未完」が二重カウント
  3. JVM 多重起動（`docker compose up -d --scale amazia-core=2` 等）→ `BatchJobLockRegistry` は in-memory で複数 JVM 間ロックは効かない → 2 つの JVM が同時に `dispatchOneTag` を走らせる

## なぜ CI で検知できないか

- 5 分間隔の時系列・トランザクション境界を跨ぐ事象。`DigestRestartCatchUpE2ETest`（phase17 Step 8）は単一 JVM 内で「OFF 期間の蓄積 → ON で 1 通送出 → `digest_sent_at` 全件埋まる」を検証するが、SES 送信成功後の DB UPDATE 失敗パスはモック差し替えでも再現が難しい。
- 多重 JVM の同時起動は単一 EC2 構成（phase17 設計書スコープ）の前提を超えており、原則発生しないシナリオ。

## 暫定回避策（実発生時）

1. SES のバウンス通知や CloudWatch ログから「同 `subscriptionTag` に対して 5 分以内に 2 通以上飛んだ」ケースを抽出
2. `console_notifications` で `digest_sent_at IS NOT NULL` かつ `suppressed = TRUE` のレコードを再走査し、同 `payload_hash` で 2 通以上送信履歴があるか確認
3. 確認できた場合、当該 `console_notifications` の `digest_sent_at` を NULL にしないこと（再々送を呼ぶ）。SES 受信者には人手でお詫び連絡が必要

## 再発防止（実装側）

| 観点 | 対策 |
|------|------|
| トランザクション境界 | `dispatchOneTag` の SES 送信を **DB UPDATE の後** に並べ替える（先 UPDATE → SES 送信失敗時は MailSendException でリトライ）。ただし「UPDATE 成功 → SES 失敗」では「送ってないのに送ったことになる」逆方向の問題が生じるため、**両方向のリスクを比較した設計判断**が必要 |
| 多重 JVM | 単一 EC2 / 単一 JVM 前提（phase17 設計書）を逸脱しないこと。phase21 以降で複数インスタンス化する場合は **ShedLock / DB row-lock ベースのロック**に移行する |
| 観測 | SES 送信時に `console_notifications.id` のリストをログ出力し、同 ID が短時間に 2 回現れたら CloudWatch アラート |
| 設計書反映 | 設計書 §6.4.2 に「SES 送信と `digest_sent_at` UPDATE の順序」を明文化（現在 r8 では擬似コードで先 SES → 後 UPDATE になっており、本トラブル発生時に再評価対象） |

## AI協働観点

- AI の判断ミス：（実発生時に記入）
- 人間が止めるべきだった点：（実発生時に記入）
- 該当アンチパターン：（実発生時に記入）
