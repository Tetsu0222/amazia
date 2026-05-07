# フェーズ18 問い合わせ管理 設計書 r1 レビューコメント

- **対象**：[phase18_inquiry_management.md](phase18_inquiry_management.md) r1（2026-05-07）
- **レビュー日**：2026-05-07
- **実装状況**：未着手（schema.sql / Java / PHP / Vue / React すべて未作成。`docs/database_design/` `docs/api_design/` `ops/healthcheck/required_tables.txt` も未更新）。本レビューは設計書本体に対するもの。

---

## サマリ

設計書としての完成度は高く、phase15 / phase17 との接続点・config 駆動・Step 段取り・採用しなかった選択肢の明示まで揃っており、この粒度で着手して差し支えない水準です。ただし下記 **🔴必須3件 / 🟡推奨5件 / 🟢任意4件** の指摘について、Step 0 着手前に取り込みをお願いします。

特に 🔴 RV-1 / RV-2 は phase17 r6 のスキーマと**直接矛盾**しており、現状のままでは Step A の `console_notifications` INSERT が NOT NULL 制約違反で必ず失敗します。

---

## 🔴 必須対応（実装着手前にブロッカー）

### RV-1：`console_notifications` の NOT NULL カラム漏れ（INSERT 実装不能）

**該当**：[§2.2.4 投稿後の挙動](phase18_inquiry_management.md)・[§6.1 通知発火点](phase18_inquiry_management.md)

**指摘**：phase17 r6 の [§5.2](phase17_batch_processing.md) では `console_notifications` の NOT NULL カラムは以下：

| カラム | NOT NULL | phase18 r1 での扱い |
|--------|---------|--------------------|
| `level` | ✅ | ✅ `INFO` 指定済 |
| `target_subscription_tag` | ✅ | ✅ `inquiry_alerts` 指定済 |
| `title` | ✅ | ❌ **未定義** |
| `body` | ✅ | ❌ **未定義** |
| `payload_hash` | ✅ | ✅ ハッシュ式定義済 |
| `suppressed` | ✅ | （DB DEFAULT で可）|

phase18 r1 §6.1 の表は `subscription_tag` / `level` / `payload_hash` の 3 列しか記述がなく、`title` / `body` の文言定義が抜けています。`NotificationDispatcher.dispatch(...)` のシグネチャ（phase17 §6.2.2 擬似コード）にも `title` / `body` が必要なはずですが、本書では payload 構造が空欄です。

**修正依頼**：§6.1 の表に `title` / `body` のテンプレート列を追加してください。例：

| イベント | title | body |
|---------|-------|------|
| 新規問い合わせ作成 | `[問い合わせ] 新規 #{inquiries.id} {subject}` | `{userName} さんから新規問い合わせが登録されました。\n対象: {target_label}\n件名: {subject}` |
| 既存問い合わせに顧客返信 | `[問い合わせ] 返信 #{inquiries.id}` | `{userName} さんが #{inquiries.id} に返信しました。` |
| 管理者ステータス変更 | `[問い合わせ] ステータス変更 #{inquiries.id}` | `#{inquiries.id} を {old_status} → {new_status} に変更しました。` |

文言は `config('inquiry.notification_templates')` で管理（規約 1-2 / 3-1）。

---

### RV-2：phase17 §3.5 通知センターの「`source_job` カラム NULL」記述が phase17 r6 と乖離

**該当**：[§1.1 前提](phase18_inquiry_management.md) `console_notifications` 行：「`source_job` カラムは NULL（バッチではないため）」

**指摘**：phase17 §5.2 では `source_job VARCHAR(100) NULL` と定義されており NULL 自体は許容されますが、`source_batch_execution_id` も同じく NULL になります。一方 phase17 r6 §6.4.1 のフォールバック規則では「主要キーが取得できない通知は `SHA-256('no-payload:' + job_name)` を投入」とあり、`job_name` が暗黙の必須情報になっています。

phase18 の問い合わせ通知は「ジョブ起源ではない」ため `source_job=NULL` 自体は妥当ですが、phase17 §6.4.2 の `DigestNotificationDispatchJob` 擬似コードは `target_subscription_tag` でのみ集計するので動作はします。**しかし phase17 §6.4.1 のフォールバック規則と「同一 `payload_hash` の通知は 60 分以内なら抑制」との関係で 1 点問題があります**：

phase18 §6.1 の `payload_hash` は以下：

```
inquiry_created:  SHA-256('inquiry_created:'  + inquiries.id)
inquiry_replied:  SHA-256('inquiry_replied:'  + inquiries.id + ':' + inquiry_messages.id)
inquiry_status:   SHA-256('inquiry_status:'   + inquiries.id + ':' + new_status)
```

`inquiry_replied` は `inquiry_messages.id` を含むため**毎回ユニーク**です。これは phase17 r6 J-5 の「`batch_execution_id` ベースだと毎回ユニークになり連続失敗が抑制されず R-10 本来の意図に反する」と**同じ反パターン**です。一方で `inquiry_status` は同じ inquiry へ連続でステータスを動かすと抑制されてしまい（管理者が NEW→IN_PROGRESS→DONE と数分で動かしたケース）、後段の遷移が握り潰されます。

**修正依頼**：以下のいずれかへ振り直してください。

| イベント | 推奨 payload_hash 算出元 | 理由 |
|---------|------------------------|------|
| 新規作成 | `SHA-256('inquiry_created:' + inquiries.id)` | 同一 inquiry が 60 分以内に「新規」として複数発火することはない（INSERT は 1 回） |
| 顧客返信 | `SHA-256('inquiry_replied:' + inquiries.id)` ← `messages.id` を**含めない** | 60 分以内の連投を 1 通に集約。連投通知の意図に整合（phase17 R-10 ） |
| ステータス変更 | `SHA-256('inquiry_status:' + inquiries.id + ':' + new_status)` | 同一遷移の二重押下のみ抑制、別遷移は別ハッシュで残す（現案維持）|

ステータス変更の現案は妥当でしたが、返信側だけ修正が必要です。§6.1 / §11.1 のテストケース（`SHA-256('inquiry_created:'+id)` 一致のアサート）と合わせて見直してください。

---

### RV-3：自動クローズの責務の所在が phase17 §3.4 と矛盾（要請が phase17 内で受理できる構造になっていない）

**該当**：[§13.2 phase17 への要請事項](phase18_inquiry_management.md)「自動クローズ Job → アーカイブ Job」「長期未対応の警告 → `InquiryStaleAlertJob`」

**指摘**：phase17 r6 §3.4 のオンデマンドバッチ表は r5 で **凍結**されており（§14 r7 候補にも本件は無い）、phase17 側に「`InquiryArchiveJob` / `InquiryStaleAlertJob` を後付けする」枠が**現時点で用意されていません**。phase17 側の §13.4 phase18 要請事項（同フェーズ完成版）にも本2件の記載は無く、本書 §13.2 で phase18 から phase17 へ要請する構図は **phase17 側の同意フェーズが切れてから差し込んだ非同期要請**になっています。

phase17 が現状「🔲未着手」のため、phase17 実装着手前にこの2 Job を取り込むなら問題ありませんが、**phase17 が先に実装に入った場合、本書 §13.2 が宙に浮きます**。

**修正依頼**：以下のいずれかをお願いします：

(A) phase18 r2 で §13.2 を「phase17 r7 候補（§14.1）への追加要請」と明確に位置づけ直し、phase17 側にも r7 改訂で受理してもらう（推奨）。

(B) phase18 のスコープ内に `InquiryStaleAlertJob` 相当を含めてしまい、phase17 への要請を単純な「`subscription_tag` 許容値追加」だけに縮める。

ベル 30 秒ポーリングだけ実装する本書スコープであれば (A) が低コストで、phase17 側を待つ構造としては自然です。

---

## 🟡 推奨対応（実装の手戻りリスク）

### RV-4：`market_customers` の表示名カラム名の確認（実カラム名と差異の可能性）

**該当**：[§2.1.2 問い合わせ一覧画面](phase18_inquiry_management.md) ／ §5.3 レスポンス例 `userName`

**指摘**：本書 §2.1.2 では「`market_customers.last_name` + `first_name`」と書かれていますが、現行 `schema.sql` の `market_customers` は `name_last` / `name_first` の順序で命名されている可能性があります（phase13 由来）。命名差異があると JOIN クエリが ColumnNotFound で失敗します。

**修正依頼**：`amazia-core/src/main/resources/schema.sql` の `market_customers` カラム定義を確認のうえ、本書 §2.1.2 を実カラム名へ統一してください。本書側を `display_name` という抽象名にしておき、Service 層で `name_last + ' ' + name_first` 連結に閉じ込めるのも手です（規約 2-3 の Shared 思想と整合）。

---

### RV-5：Market 側「他人の deliveries / sales を target_id に指定」の所有者検証ロジックの所在

**該当**：[§2.2.4 対象 ID の入力 UI](phase18_inquiry_management.md)・§11.3 異常系

**指摘**：「`deliveries` JOIN `sales` で `sales.user_id = ログイン市場顧客`」という記述はありますが、これは Market UI のプルダウン**初期表示**を絞る話なのか、サーバ側 Validator なのかが文中で曖昧です。§11.3 のテストケースには「自分の購入履歴外の `deliveries.id` を指定すると拒否」がありますが、対応する Service／Validator のクラス名が §10 / §14 に明記されていません。

**修正依頼**：§14 の Java フォルダ構成に `com.example.inquiry.validator.InquiryTargetOwnershipValidator`（仮）を明記し、`CreateInquiryService` から呼び出す旨を §10 Step B に追記してください。Service の責務とは別に独立 Validator にしておく方が、`product` / `sales` / `delivery` ごとに分岐する所有者検証ロジックを 1 ファイルに閉じ込められます（規約 1-1 の「Service にロジックを寄せる」と矛盾しません）。

---

### RV-6：JWT 認証 vs `MARKET_SESSION_ID` の前提整合確認

**該当**：[§5.1 認証](phase18_inquiry_management.md)「phase11 で確定済の **JWT 認証**」

**指摘**：phase11 / phase14 / phase15 の現行 Console は Laravel セッション（`SESSION` Cookie）+ Spring Core 側 Pass-through で動いており、JWT は実装されていない可能性があります（要確認）。本書 §5.1 で「JWT 認証 / Authorization: Bearer ...」と書くと Step B で実装担当者が認証ミドルウェアを新規実装し始める可能性があります。

**修正依頼**：

- 既存 Console 認証が JWT であれば本記述は妥当 → そのまま。
- **既存が Laravel セッション + CSRF であれば**、本書 §5.1 を「phase11 で確定済の Console 認証経路をそのまま継承する」へ書き換える（Bearer 表記は削除）。

少なくとも認証方式は phase11 完成版へリンクさせ、実装担当者が再発明しない構造にしてください。

---

### RV-7：CloudFront Behavior 既存性の根拠リンク

**該当**：[§5.2 CloudFront Behavior](phase18_inquiry_management.md)「`/api/customer/*` Behavior に乗る」

**指摘**：phase13 ／ phase11 ステップ9（[user memory: phase11 HTTPS ポリシー](.) の CloudFront + DuckDNS + 1ドメイン構成）で `/api/customer/*` Behavior が既に存在する旨は事実関係として正しいですが、本書からは根拠ファイルへのリンクがありません。phaseX-3 の CloudFront 設計書または phase13 の該当節への参照リンクを追加すると、Step B の CloudFront 担当が確認しやすくなります。

**修正依頼**：§5.2 に phase13 該当節（または phaseX-3）へのリンクを追記。リンク不要なら「変更不要」を太字に。

---

### RV-8：30 秒ポーリングの「タブ非表示時停止」の実装責務

**該当**：[§2.1.1 ベルマーク](phase18_inquiry_management.md)・§11.2 SPA テスト

**指摘**：「タブ非表示時はポーリングを停止し、再表示時に即時 1 回 fetch」というロジックは Bell.vue 内に閉じ込めるべきか、SPA 共通の Visibility hook に切り出すべきかが未指定です。phase19 / phase20 で同様のポーリング UI を増やす場合（お知らせ・通知センター本体）、`useDocumentVisibility()` 相当の合成可能 hook を §2.1.1 で先に決めておくと再利用が効きます。

**修正依頼**：§2.1.1 に「`useVisibilityPolling(fetchUrl, intervalMs)` Composable として実装し、Bell.vue から呼ぶ」旨を 1 行追加。phase19 への伏線にもなります（規約 2-3 Shared）。

---

## 🟢 任意（残しておくと将来役立つ）

### RV-9：`is_internal_note=TRUE` を Market POST で送信されたときの DB 制約は二重防御済か

§3.2 の `CHECK (is_internal_note = FALSE OR sender_type = 'admin_user')` で DB レイヤは守れていますが、Market 側 Controller で `is_internal_note` を**そもそも受け付けない DTO**にしておくと、攻撃面が一段減ります。§5.3 リクエスト DTO 例に `is_internal_note` を載せないことを §5.2 のメモで明記すると、実装担当者が DTO を分離してくれます。

### RV-10：`docs/database_design/` / `docs/api_design/` / `required_tables.txt` 更新の Step 0 組込み

[CLAUDE.md](../../CLAUDE.md) のメンテナンスルール（DB / API 設計書を**同フェーズ内で**更新）と、`ops/healthcheck/required_tables.txt` の同期ルールが §10 Step 0 / Step A の完了条件に明文化されていません。phase17 r5 では「テーブル追加で更新を忘れても CD は通る（過大検知しない設計）」とあるため、更新漏れが事故化する点は小さいですが、本書では「Step A 完了条件＝ schema.sql 反映 + `TBL_inquiries.md` / `TBL_inquiry_messages.md` 新設 + `required_tables.txt` 追記 + `ER_diagram.md` / `README.md` 更新」と明記してください。

### RV-11：採用しなかった選択肢に「`inquiries.assigned_user_id` の Service 層フックだけ用意」を追記

§12 で担当者割当は YAGNI として却下されていますが、`assigned_user_id` を将来 NULL 許容で追加するときの破壊的影響を最小化するため、`UpdateInquiryStatusService` の interface（仮：`InquiryMutationContext`）に「将来 `assignedUserId` を追加可能なフィールド余地」を残しておくのは設計上合理的です。任意レベルですが、§14 命名規約に 1 行追記しておくと将来フェーズの IF 互換性が楽になります。

### RV-12：r1 → r2 改訂履歴記載

本レビューを反映した r2 版を作成する際は、改訂履歴に「RV-1 / RV-2 / RV-3 を反映。`title` / `body` テンプレート明記、`inquiry_replied` の payload_hash を inquiries.id ベースへ修正、phase17 §13 への要請を r7 候補追加へ位置づけ直し」を明記してください。phase15 / phase17 と同じ改訂履歴粒度に揃えてもらえると、後段の依存解決トラッキングがしやすくなります。

---

## まとめ表（対応優先度）

| ID | 優先度 | 内容 | 修正先セクション |
|----|--------|------|----------------|
| RV-1 | 🔴 必須 | `console_notifications.title` / `body` 未定義（INSERT 不能） | §6.1 |
| RV-2 | 🔴 必須 | `inquiry_replied` の payload_hash がユニーク化（抑制が効かない） | §6.1 / §11.1 |
| RV-3 | 🔴 必須 | phase17 r6 §3.4 凍結に対する後付け要請の整合 | §13.2 |
| RV-4 | 🟡 推奨 | `market_customers.name_last`/`name_first` の実カラム名整合 | §2.1.2 / §5.3 |
| RV-5 | 🟡 推奨 | 所有者検証 Validator の所在明記 | §10 / §14 |
| RV-6 | 🟡 推奨 | JWT vs Laravel セッションの認証前提確認 | §5.1 |
| RV-7 | 🟡 推奨 | CloudFront `/api/customer/*` Behavior の根拠リンク | §5.2 |
| RV-8 | 🟡 推奨 | `useVisibilityPolling` Composable 切出し | §2.1.1 |
| RV-9 | 🟢 任意 | Market POST DTO から `is_internal_note` を除外 | §5.2 / §5.3 |
| RV-10 | 🟢 任意 | Step 0 / A 完了条件に DB / API 設計書更新を明記 | §10 |
| RV-11 | 🟢 任意 | `assigned_user_id` 将来拡張の余地 | §12 / §14 |
| RV-12 | 🟢 任意 | r1 → r2 改訂履歴粒度 | 改訂履歴 |

以上、よろしくお願いします。
