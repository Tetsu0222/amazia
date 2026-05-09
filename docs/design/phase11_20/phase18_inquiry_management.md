# フェーズ18：問い合わせ管理（改訂版 r4）

## ステータス
✅ 実装完了（2026-05-09）。実機検証はユーザー側で順次実施

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 初版日不明（git 履歴未取得） | 初稿（フェーズ18 問い合わせ管理の基本設計：inquiries / inquiry_messages の最小スキーマと画面要件のみ）|
| r1 | 2026-05-07 | 実装着手レベルへブラッシュアップ。(1) phase15 r5 から要請された `target_type` / `target_id` 多態参照を本書で正式定義（[phase15 §phase18 への要請事項](phase15_delivery_management.md) 対応）。(2) `sender_type` / `sender_id` で Market 顧客（`market_customers`）と Console 管理者（`users`）を分離。(3) phase17 r6 の `console_notifications` / `notification_subscriptions`（subscription_tag = `inquiry_alerts`）と通知経路を統合。ベルマークは 30 秒ポーリング方式（`/api/console/inquiries/unread-count`）で実装。(4) Console / Market の API・画面・operation_logs 規約・config 駆動・テスト観点まで明記し、規約 1-1 / 1-2 / 2-1 / 4-1 と完全整合。(5) 添付ファイルは本フェーズスコープ外として明示。 |
| r2 | 2026-05-07 | レビューコメント [phase18_inquiry_management_review_r1.md](phase18_inquiry_management_review_r1.md) RV-1 〜 RV-12 を反映。🔴必須：(RV-1) `console_notifications` の NOT NULL カラム漏れを補完し、§6.1 に `title` / `body` テンプレート列を追加（`config('inquiry.notification_templates')` 管理）。(RV-2) `inquiry_replied` の `payload_hash` から `inquiry_messages.id` を除去し `SHA-256('inquiry_replied:' + inquiries.id)` に修正（60 分以内連投を 1 通に集約／phase17 R-10 整合）。(RV-3) §13.2 を「phase17 r7 候補（§14.1）への追加要請」と明確に位置づけ直し。🟡推奨：(RV-4) `market_customers` 実カラム名を `name_last` / `name_first` に統一し、Service 層で `display_name` を組み立てる方針を明記。(RV-5) `InquiryTargetOwnershipValidator` を §14 Java フォルダ構成・§10 Step B に明記。(RV-6) Console 認証が JWT であることを phase11 §3.1 / §4.4 で確認済（記述維持）。(RV-7) §5.2 に phase13 §2.2 への根拠リンク追記＋「変更不要」明示。(RV-8) `useVisibilityPolling` Composable を §2.1.1 / §14 に追加。🟢任意：(RV-9) Market POST DTO から `is_internal_note` を除外する DTO 分離方針を §5.2 / §5.3 に明記。(RV-10) Step 0 / A の完了条件に `docs/database_design/` / `docs/api_design/` / `ops/healthcheck/required_tables.txt` 更新を組込み（[CLAUDE.md](../../../CLAUDE.md) 整合）。(RV-11) `assigned_user_id` 将来拡張余地を §14 に追記。 |
| r3 | 2026-05-07（同日中の連続改訂） | r2 再レビュー [phase18_inquiry_management_review_r1.md §r2 再レビュー](phase18_inquiry_management_review_r1.md) RV2-1 〜 RV2-5 を反映。🔴必須：(RV2-1) §2.2.4「投稿後の挙動」に残存していた古い `payload_hash=SHA-256('inquiry:'+inquiry.id)` 表記を削除し、`NotificationDispatcher.dispatch('inquiry_alerts', INFO, payload)` 呼び出し起点 + §6.1 への参照に集約（重複定義による drift 排除）。🟡推奨：(RV2-2) §13.2 の「`R-17` / `R-18` 等」表記を「phase17 r7 の prefix 規則に従う新規 ID（`R-` / `N-` / `M-` / `K-` / `J-` は既出のため、phase17 著者の選定によりアルファベット繰上げ）」へ修正。🟢任意：(RV2-3) §14.1 の `ALTER TABLE ADD COLUMN IF NOT EXISTS` を MySQL 8.0.29 未満互換の `information_schema` チェック方式へ書き換え（本プロジェクトは `mysql:8.0` タグ運用で 8.0.29 以降の保証がないため安全側）。(RV2-4) §14.1 拡張余地を `UpdateInquiryStatusService` に加え `ReplyInquiryService` 側にも展開。(RV2-5) 改訂履歴の同日連続改訂であることを注記。 |
| r4 | 2026-05-09 | 実装着手後に判明した既存実装との齟齬を本書本文に反映（実装済みの内容に合わせる）。🔴必須：(IMP-1) 設定ファイル形式は `application.yml` ではなく **`application.properties`**（既存 phase11 〜 phase17 と同じ）。本書 §7 / §6.1 のサンプルコードを properties 表記に修正。(IMP-2) 通知テンプレート文字列の `#{inquiry_id}` / `#{target_id}` は Spring `@Value` の SpEL 構文（`#{...}`）と衝突するため **`No.{inquiry_id}` / `No.{target_id}`** に変更。本書 §6.1 / §7 のテンプレ列・設定例を修正。(IMP-3) `payload_hash` の実体は phase17 `BatchAlertNotifier.buildPayloadHash` の挙動により **`SHA-256('inquiry_alerts:' + payloadIdentity)`**（`subscriptionTag` プレフィクス付き）。本書 §6.1 / §11.1 の算出式を「phase17 既存実装に準拠」と注記して修正。(IMP-4) Market 側は **Laravel Pass-through 不在**（`amazia-market/` は React + Vite SPA 単独構成）。本書 §5.2 / §10 / §使用ファイル一覧を「Market は React 直接 Core（`/api/customer/*` Behavior は phase13 §2.2 で構築済）」に修正。(IMP-5) operation_logs の記録位置は **Core 側 Service**（既存 phase14 / 15 / 17 踏襲）。本書 §2.1.4 の Pass-through 層記録案を Core 集約に修正。(IMP-6) Console には独立した `Header.vue` が存在せず、ベルマークは **`App.vue` のサイドバー（`a-layout-sider`）にメニュー項目として組込**。本書 §2.1.1 / §10 / §使用ファイルの組込先を修正。(IMP-7) Console SPA は **Vitest 未導入**（`amazia-console/resources/vue/package.json` 確認済）。本書 §11.2 SPA テストはスコープ外として注記し `npm run build` 緑で代替。Vitest 導入は phaseX-N 申し送り。(IMP-8) Market `MyPageInquiryNew` の `TargetTypeSelector` は最小機能優先で **対象 ID 数値入力**に簡略化（動的セレクタは phase19 / 後続で拡張可能）。(IMP-9) phase18 完了に伴い phase17 r7 候補へ `InquiryArchiveJob` / `InquiryStaleAlertJob` を申し送り済（[phase17_implementation_plan.md](../../implementation/phase17_implementation_plan.md) §14-2-a）。 |

## 範囲
- Amazia Console（管理画面：問い合わせ一覧・スレッド・ベルマーク通知）
- Amazia Market（顧客側：マイページ問い合わせ機能）
- Amazia Core（API：inquiries / inquiry_messages CRUD・未対応件数集計）
- DB 設計（`inquiries` / `inquiry_messages` 新設、`notification_subscriptions` への `inquiry_alerts` 追加）
- 通知連携（phase17 `console_notifications` の `subscription_tag = 'inquiry_alerts'` ／ベルマークバッジは `/api/console/inquiries/unread-count` ポーリング）

## 本フェーズのスコープ外
| 機能 | 理由 / 取り扱い |
|------|----------------|
| 添付ファイル | 元設計書「将来的に検討」のまま据え置き。S3 / EFS 配置・容量制限・ウィルススキャン・MIME 検証が必要で、AWS 無料枠と工数を圧迫するため。本書ではスキーマに `attachments` 系カラムを持たせない（拡張時は別テーブル `inquiry_attachments` を追加する想定とだけメモ）|
| リアルタイム push（WebSocket / SSE） | 30 秒ポーリングで十分（Console は管理者のみアクセス・件数規模も小さい）。phase17 r6 で SSE 基盤は `console_notifications` 用に整備されているが、本フェーズで本配線する工数を避ける。`SseEmitter` 統合は後続フェーズで検討 |
| 問い合わせの優先度設定 | YAGNI。`status`（NEW / IN_PROGRESS / DONE）で運用は回る前提。優先度導入は実運用で必要性が出た段階で別フェーズ |
| 問い合わせの自動クローズ（一定期間返信なしで DONE） | phase17 のオンデマンドバッチに切り出す（後述 §13 phase17 への要請事項参照）|
| 通知種別の拡張（ベルマークに「ワークフロー承認待ち」「在庫異常」等を集約） | ベルマーク UI 自体は本書で実装するが、表示元データソースは「`inquiries.status='NEW'` の件数」のみとする。集約構造は phase19（お知らせ）と合わせて将来検討。本書ではベルマーク件数取得を `useVisibilityPolling(fetcher, intervalMs)` Composable（r4 / IMP-6）で抽象化しているため、後続フェーズが別コンポーネントで `fetcher` を差し替えるだけで集約できる構造のみ確保 |
| Market 側ユーザの未読バッジ | Market は元設計に未記載。マイページ内の問い合わせメニューにアイコンバッジを出す必要が出たら別フェーズ。本書では Market 側未読件数 API は実装しない |

---

# 1. 機能概要（再掲・整理）

- Amazia Market にログイン顧客が問い合わせを作成・閲覧・返信できる機能を追加する。
- Amazia Console に管理者向けの問い合わせ管理機能（一覧・スレッド・ステータス変更・返信）を追加する。
- 問い合わせはスレッド形式（`inquiries`：親 / `inquiry_messages`：時系列メッセージ）で管理する。
- Console ヘッダーに **ベルマーク（通知アイコン）** を設置し、未対応（`status = 'NEW'`）件数をバッジ表示する。
- 問い合わせの作成 / 返信 / ステータス変更時、phase17 の `console_notifications` / SES 通知（subscription_tag = `inquiry_alerts`）にレコードを投入し、購読 admin に通知する。

## 1.1 前提（phase11 〜 phase17 完了前提）

| 既存資産 | 出自 | 本書での扱い |
|---------|------|-------------|
| `users(id, email, role_id, active_flag, ...)` | phase11 | Console 管理者の参照先。`inquiry_messages.sender_type='admin_user'` のとき `sender_id = users.id` |
| `roles(id, name)`（admin / user の 2 種） | phase11 | 通知購読対象は admin ロールに自動投入される（phase17 §6.2 と整合）|
| `market_customers(id, ...)` | phase13 | Market 顧客の参照先。`inquiries.user_id = market_customers.id` ／ `inquiry_messages.sender_type='market_customer'` のとき `sender_id = market_customers.id` |
| `market_sessions` | phase13 | Market の認証セッション。本フェーズの Market 側 API は `MARKET_SESSION_ID` Cookie で認証 |
| `products(id, ...)` | phase8 | `inquiries.target_type='product'` のとき `target_id = products.id` |
| `sales(id, user_id, sku_id, ...)` | phase14 | `inquiries.target_type='sales'` のとき `target_id = sales.id` |
| `deliveries(id, sales_id, ...)` | phase15 | `inquiries.target_type='delivery'` のとき `target_id = deliveries.id`（[phase15 §phase18 への要請事項](phase15_delivery_management.md) と整合）|
| `operation_logs` | phase14 | Console 操作の記録先 |
| `notification_subscriptions(user_id, subscription_tag, ...)` | phase17 | `subscription_tag = 'inquiry_alerts'` を新規投入 |
| `console_notifications` | phase17 | 通知センター。本フェーズの新規通知は `target_subscription_tag = 'inquiry_alerts'`、`source_job` カラムは NULL（バッチではないため）|
| `BatchAlertNotifier.dispatch(level, subscriptionTag, title, body, payloadIdentity, sourceJob, sourceBatchExecutionId)` | phase17 §6.2（`com.example.notification.service.BatchAlertNotifier`）| 本フェーズの通知発火点から呼び出す（r4 / IMP-3：phase17 の実体クラス名に修正。本書 r3 までは仮称 `NotificationDispatcher.dispatch(...)` 表記）|

phase15 / phase17 で残された **「phase18 が確定する責務」** は本書で全て確定する：
1. `inquiries.target_type` / `target_id` の正式定義（phase15 RR-7 の依存解決）
2. ベルマーク UI コンポーネントと未対応件数 API（元設計書「ベルマーク（通知アイコン）」）
3. 自動クローズの実施有無 → 本書ではスコープ外とし、phase17 オンデマンドバッチへ要請（§13）

---

# 2. 機能詳細

## 🖥 2.1 Amazia Console（管理画面）

### 2.1.1 ベルマーク（サイドバーメニュー組込 / r4 で実装に整合）

> **r4 実装注記（IMP-6）**：本書 r3 までは「共通ヘッダー（`Header.vue`）右側にベルアイコンを置く」想定だったが、Console SPA の実構造には独立した `Header.vue` がなく、`App.vue` がサイドバー（`a-layout-sider`）でナビゲーションを担う構成だった。実装は **`App.vue` のサイドバーメニュー項目「問い合わせ」に `<a-badge>` バッジを組込**する形にした。元設計の「未対応件数を常時可視化する」「30 秒ポーリング」「タブ非表示時停止」の意図は完全に維持。

- Console SPA の **サイドバー（`App.vue` の `a-layout-sider`）の「問い合わせ」メニュー項目**に未対応件数バッジを表示する `InquiryBellBadge` コンポーネントを追加する。
- バッジ：`inquiries.status = 'NEW'` の件数を 99 件まで数値表示、100 件以上は `99+` と表示。
- 件数取得は **30 秒間隔のポーリング**で `/api/console/inquiries/unread-count` を呼び出す。タブ非表示時（`document.hidden`）はポーリングを停止し、再表示時に即時 1 回 fetch して再開する（無料枠 / EC2 t3.micro 負荷削減）。
- メニュークリック → 「問い合わせ一覧画面」へ遷移（`/inquiries`）。
- 未対応 0 件のときはバッジを非表示（メニューラベルのみ）。

#### ポーリング Composable の切り出し（RV-8 対応：r2 で追加 / r4 で配置確定）

タブ非表示停止・再表示時即時 fetch のロジックは `InquiryBellBadge.vue` 内に直接書かず、**Vue Composable `useVisibilityPolling(fetcher, intervalMs)` として共通化**する：

- 配置：`amazia-console/resources/vue/src/composables/useVisibilityPolling.js`（規約 2-3 Shared 思想：複数ユースケースで使う・ドメイン非依存。r4 では JS 実装。Console は TS 未導入のため）
- シグネチャ：`useVisibilityPolling(fetcher: () => Promise<T>, intervalMs: number): { data: Ref<T | null>, error: Ref<Error | null> }`（r4：URL 文字列ではなく fetcher 関数を受ける構造に変更。axios クライアント / interceptor を経由するため）
- 内部で `document.visibilitychange` を購読し、非表示時は `clearInterval`、再表示時は即時 1 回 fetch + `setInterval` 再開
- `InquiryBellBadge.vue` は本 Composable を呼ぶだけのシン UI に保つ
- phase19（お知らせ）／後続フェーズの集約 Bell でも再利用可能（§13.3 申し送りの伏線）

#### 拡張性の確保（元設計書「今後、問い合わせ以外も表示できるよう」対応）
- `InquiryBellBadge.vue` は内部で `getUnreadInquiryCount()` 固定だが、Composable 側で `fetcher` を差し替え可能。後続フェーズで「全通知集約」エンドポイントが用意された段階で別コンポーネントに置き換えれば再利用できる構造。
- API のレスポンス形式は `{ count: number }` 固定で受ける。

### 2.1.2 問い合わせ一覧画面（`/inquiries`）

| 列 | 内容 | データソース |
|----|------|-------------|
| 件名 | `inquiries.subject` | `inquiries` |
| ユーザー名 | 顧客の表示名（`market_customers.name_last` + `name_first`） | JOIN `market_customers`（RV-4 対応：実カラム名に統一）|
| 対象 | `target_type` の表示名 + 短い識別子（例：「配送 #123」「商品 #SKU-A」「（汎用）」） | `target_type` / `target_id` |
| ステータス | NEW（未対応）/ IN_PROGRESS（対応中）/ DONE（完了） | `inquiries.status` |
| 最終更新 | `inquiries.updated_at` | `inquiries` |

#### フィルタ
- ステータス：NEW / IN_PROGRESS / DONE / すべて（既定：すべて）
- 期間：作成日 from / to（任意）
- ユーザー名：部分一致（任意）
- 対象種別（target_type）：delivery / product / sales / null（汎用）/ すべて（既定：すべて）

#### ソート
- 既定：`updated_at DESC`（最終更新が新しい順）
- 切替：`updated_at ASC`（古い順）

#### ページング
- 1 ページ 50 件。サーバー側で `LIMIT / OFFSET`。

### 2.1.3 問い合わせ本文（スレッド画面 `/inquiries/{id}`）

#### レイアウト
- 上部：件名 / ユーザー名 / ステータスドロップダウン / 対象（クリックで対象画面へ遷移：例 `target_type='delivery'` → `/deliveries/{target_id}` ）
- 中央：メッセージ吹き出し時系列（顧客は左、管理者は右）
- 下部：返信入力欄（textarea）+ 「返信送信」ボタン

#### ステータス変更
- ドロップダウンで NEW → IN_PROGRESS → DONE を選択。
- 遷移ルール（Service 層でガード）：

| 現在 → 次 | NEW | IN_PROGRESS | DONE |
|----------|-----|-------------|------|
| NEW | - | ✅ | ✅（飛ばし完了も許容：簡易対応で即終了するケース）|
| IN_PROGRESS | ✅（差し戻し）| - | ✅ |
| DONE | ✅（再オープン）| ✅ | - |

巻き戻し（DONE → NEW など）は元設計書では未定義だったが、運用上「完了後に追加質問が来た」「誤って完了にした」ケースに対応するため許容する。Service 層で許容遷移を `config/app/Inquiry.php` に enum 定義し、Service が参照する（規約 1-2 / 3-1）。

#### 返信投稿
- 管理者の返信は `inquiry_messages` に `sender_type='admin_user', sender_id=ログイン users.id` で INSERT。
- 投稿成功時、`inquiries.updated_at` を NOW() に更新（DB トリガではなく Service 層で同一トランザクション）。
- 投稿後の `status` は **変更しない**。「対応中」に進めたい場合はドロップダウンで明示的に変更させる（暗黙遷移は規約 1-1 のビジネスロジック乱立を防ぐ思想と整合）。

#### 管理者コメント欄（任意）
- 元設計書の「管理者コメント欄（任意）」は **顧客に見せない内部メモ** として実装する。
- スレッド画面の最下部に「内部メモ」アコーディオンを設置し、管理者間でのみ共有。
- 実体は `inquiry_messages` に `is_internal_note = TRUE` で保存（後述 §3.2）。
- Market 側 API は `is_internal_note = FALSE` のレコードのみ返す。

### 2.1.4 Console 操作の operation_logs 記録

> **r4 実装注記（IMP-5）**：本書 r3 までは「Console Pass-through 層（Laravel）で operation_logs を書く」案だったが、既存 phase14 / 15 / 17 では **Core 側 Service が `OperationLogRepository.save(...)` で書き込む**運用が踏襲されており、Console には operation_logs テーブルへの直接アクセス基盤（Model / 接続）も存在しない。実装は既存方針に揃え、**Core 側 `ReplyInquiryService` / `UpdateInquiryStatusService` が記録**する。Console Pass-through Service / Controller は Core を呼ぶだけのシン層に保つ。`comment` プレフィックスの管理場所は Core 側 `application.properties` の `amazia.inquiry.operation-log-prefixes.*` に統合（顧客側操作は記録対象外）。

| action | target_type | target_id | comment 規約 |
|--------|-------------|-----------|-------------|
| `reply_inquiry` | `inquiries` | inquiries.id | `[admin_reply] message_id=N`（投稿された inquiry_messages.id）|
| `update_inquiry_status` | `inquiries` | inquiries.id | `[status_change] 旧:NEW → 新:IN_PROGRESS reason='...'`（reason は任意）|
| `add_internal_note` | `inquiries` | inquiries.id | `[internal_note] message_id=N` |

`screen_name` / `api_name` の規約（phase15 RR-10 / phase17 §8 と整合）：
- `screen_name`：`ConsoleInquiryDetailPage`（一覧画面では記録対象操作なし。投稿・状態変更はすべて詳細画面起点）
- `api_name`：`POST /api/console/inquiries/{id}/messages` / `PATCH /api/console/inquiries/{id}/status`

`comment` プレフィックス（`[admin_reply]` / `[status_change]` / `[internal_note]` / `[customer_reply]`）は **Core 側 `application.properties` の `amazia.inquiry.operation-log-prefixes.*`** で管理（r4 / IMP-5）。Console 側 `config/app/Inquiry.php` にも同等のキー `operation_log_prefixes.*` を持たせ、将来 Console 側でも参照する可能性を残しているが、本フェーズでは未使用。

---

## 🛒 2.2 Amazia Market（顧客側）

### 2.2.1 マイページ「問い合わせ」メニュー

- Market マイページ（`/mypage`）のサブメニューに「問い合わせ」を追加（phase13 で実装済みのマイページ構成に追加）。
- 認証必須：未ログインは `/login?redirect=/mypage/inquiries` にリダイレクト（phase13 §認証経路と整合）。

### 2.2.2 問い合わせ一覧画面（`/mypage/inquiries`）

| 列 | 内容 |
|----|------|
| 件名 | `inquiries.subject` |
| ステータス | NEW（未対応）/ IN_PROGRESS（対応中）/ DONE（完了）（顧客向けの表示名は `config/app/Inquiry.php` の i18n キーで定義）|
| 最終更新 | `inquiries.updated_at` |

- ログイン顧客自身の `inquiries.user_id = market_customers.id` のみ取得（Service 層で `WHERE user_id = :sessionUserId` を強制）。
- ページング：1 ページ 20 件。

### 2.2.3 問い合わせ本文（スレッド `/mypage/inquiries/{id}`）

- 顧客は新規メッセージを投稿可能（`inquiry_messages.sender_type='market_customer'`）。
- 管理者からの返信（`sender_type='admin_user'` 且つ `is_internal_note=FALSE`）も時系列表示。
- **`is_internal_note=TRUE` のレコードは API レスポンス上にも含めない**（バックエンドでフィルタ）。
- 顧客の返信投稿は **status を NEW へ強制復帰させない**。Console と同様、status は管理者が手動で動かす。

### 2.2.4 新規問い合わせ登録（`/mypage/inquiries/new`）

#### 入力項目

| 項目 | 必須 | 備考 |
|------|------|------|
| 件名 | 必須 | 1 〜 100 文字 |
| 本文 | 必須 | 1 〜 4000 文字（DB は TEXT、上限は config）|
| 対象種別 | 任意 | `delivery` / `product` / `sales` / 空（汎用）|
| 対象ID | 対象種別と連動 | 種別を選択した場合は必須。種別ごとの入力 UI（後述）|

#### 対象 ID の入力 UI（target_type 連動 / r4 で簡略化）

> **r4 実装注記（IMP-8）**：本書 r3 までは「target_type 連動の動的セレクタ（顧客の `deliveries` / `sales` 直近 3 ヶ月プルダウン、商品検索コンポーネント流用）」を想定していたが、実装は **最小機能優先で対象 ID の数値入力**に簡略化した。理由：(a) 顧客が ID を確認する手段はマイページの注文・配送履歴（フェーズ14 / 15 で実装済み）に既に存在、(b) 動的セレクタの実装コストに比べ、UX 上の効果がフェーズ18 段階では限定的、(c) サーバ側の所有者検証（`InquiryTargetOwnershipValidator`）が IDOR を完全に塞ぐため、不正 ID を入れても 403 で弾かれて顧客に害は無い。動的セレクタは phase19 / 後続フェーズで拡張可能（UI のみの差し替えで Service 層は無変更）。

| `target_type` | 入力 UI（r4 実装） | サーバ側検証 |
|--------------|---------|------|
| `delivery` | 数値入力（`type="number"`）| `deliveries` を取得 → `deliveries.sales_id` 経由 `sales.user_id = ログイン市場顧客` を強制（`InquiryTargetOwnershipValidator.validateDelivery`）|
| `product` | 数値入力（`type="number"`）| `products.id` の存在 + `products.is_active = TRUE`（phase16 Step 1 / `validateProduct`）|
| `sales` | 数値入力（`type="number"`）| `sales.user_id = ログイン市場顧客` を強制（`validateSales`）|
| 空（汎用） | 入力欄を非表示 | `target_id = NULL` |

種別の選択肢と表示名は **Market 側 `MyPageInquiryNew.jsx` の `TARGET_TYPE_OPTIONS` 定数**で管理（r4：Market は React + Vite 単独構成で Laravel `config/app/Inquiry.php` を参照しないため。表示名 i18n は将来課題）。Core 側 `application.properties amazia.inquiry.target-types` は許容種別の列挙のみを保持し、`@Value` 経由で Service 層・Validator が利用する。

#### バリデーション
- フレームワーク（Laravel FormRequest / Spring `@Valid`）の標準バリデーションを使用。
- 独自ルール（件名・本文の文字数、対象 ID と target_type の整合性）は `config('inquiry.validation')` に集約（規約 1-2）。

#### 投稿後の挙動
- `inquiries` を `status='NEW'` で INSERT。
- 同一トランザクションで初回メッセージを `inquiry_messages` に INSERT（`sender_type='market_customer'`, `is_internal_note=FALSE`）。
- 同一トランザクションで phase17 `BatchAlertNotifier.dispatch(level='INFO', subscriptionTag='inquiry_alerts', title, body, payloadIdentity, sourceJob=null, sourceBatchExecutionId=null)` を呼び出す（r4 / IMP-3：phase17 の実体 API に合わせて表記を更新。本書 r3 までは仮称 `NotificationDispatcher.dispatch(...)` 表記だったが、実装は phase17 実装の `BatchAlertNotifier`）。`payload` の組み立てルール（`payload_hash` / `title` / `body`）は §6.1 を参照（新規作成イベントは `payloadIdentity = 'inquiry_created:' + inquiries.id`、phase17 の `buildPayloadHash` により実体は `SHA-256('inquiry_alerts:inquiry_created:' + inquiries.id)`）。`title` / `body` は `application.properties amazia.inquiry.notification-templates.created.*` から組み立て。`level=INFO` のため SES メール送出は行われず、`console_notifications` への INSERT のみ実施される（§6.1 後段と整合／RV2-1 対応：r3 で §6.1 への参照に集約）。
- リダイレクト先：`/mypage/inquiries/{id}`（投稿完了画面ではなくスレッド画面に直接遷移）。

---

# 3. DB 設計

## 3.1 `inquiries` テーブル（新規：問い合わせ親）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK（AUTO_INCREMENT）|
| user_id | BIGINT | NOT NULL | 問い合わせ顧客（`market_customers.id` への FK）|
| subject | VARCHAR(100) | NOT NULL | 件名 |
| status | VARCHAR(20) | NOT NULL | `NEW` / `IN_PROGRESS` / `DONE`（既定 `NEW`、CHECK 制約あり）|
| target_type | VARCHAR(20) | NULL | 対象種別（`delivery` / `product` / `sales` / NULL=汎用）|
| target_id | BIGINT | NULL | 対象ID（`target_type` と組み合わせて参照）|
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 最終更新日時（メッセージ追加・ステータス変更で更新）|

### 制約・備考
- `FOREIGN KEY (user_id) REFERENCES market_customers(id)`
- `CHECK (status IN ('NEW', 'IN_PROGRESS', 'DONE'))`
- `CHECK (target_type IN ('delivery', 'product', 'sales') OR target_type IS NULL)`
- `CHECK ((target_type IS NULL AND target_id IS NULL) OR (target_type IS NOT NULL AND target_id IS NOT NULL))`：`target_type` と `target_id` は同時 NULL / 同時 NOT NULL（DB レベルでの整合性）
- `target_id` への FK は **物理的には張らない**（多態参照のため。整合性は Service 層で `target_type` ごとの存在検証を行う／規約 1-1）

### インデックス方針

| インデックス | 用途 |
|-------------|------|
| `idx_inquiries_status_updated_at` (`status`, `updated_at DESC`) | ベルマーク件数取得（`WHERE status='NEW'`）と Console 一覧（`ORDER BY updated_at DESC`）の両方を 1 本でカバー |
| `idx_inquiries_user_id_updated_at` (`user_id`, `updated_at DESC`) | Market マイページ自分の問い合わせ一覧 |
| `idx_inquiries_target` (`target_type`, `target_id`) | 「この配送に関する問い合わせ一覧」など対象起点での検索 |

### マイグレーション仕様（schema.sql 末尾に冪等追記）

```sql
-- フェーズ18 r1: inquiries
CREATE TABLE IF NOT EXISTS inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subject VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    target_type VARCHAR(20) NULL,
    target_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_inquiries_status_updated_at (status, updated_at),
    KEY idx_inquiries_user_id_updated_at (user_id, updated_at),
    KEY idx_inquiries_target (target_type, target_id),
    CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES market_customers(id),
    CONSTRAINT chk_inquiries_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT chk_inquiries_target_type CHECK (target_type IN ('delivery', 'product', 'sales') OR target_type IS NULL),
    CONSTRAINT chk_inquiries_target_pair CHECK (
        (target_type IS NULL AND target_id IS NULL)
        OR (target_type IS NOT NULL AND target_id IS NOT NULL)
    )
);
```

`spring.sql.init.continue-on-error=true` の冪等運用に従う（[phase14 §D](phase14_shipping.md) と整合）。

---

## 3.2 `inquiry_messages` テーブル（新規：スレッドメッセージ）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK（AUTO_INCREMENT）|
| inquiry_id | BIGINT | NOT NULL | 親問い合わせID（`inquiries.id` への FK）|
| sender_type | VARCHAR(20) | NOT NULL | `market_customer` / `admin_user`（CHECK 制約あり）|
| sender_id | BIGINT | NOT NULL | 送信者ID（`sender_type` と組み合わせて参照）|
| message | TEXT | NOT NULL | 本文（最大 4000 文字、Service 層で検証）|
| is_internal_note | BOOLEAN | NOT NULL | TRUE = 管理者間内部メモ（Market 非表示）。既定 FALSE |
| created_at | DATETIME | NOT NULL | 作成日時 |

### 制約・備考
- `FOREIGN KEY (inquiry_id) REFERENCES inquiries(id) ON DELETE CASCADE`：問い合わせ削除時にメッセージも消える（運用上 inquiries は物理削除しない方針だが念のため）
- `CHECK (sender_type IN ('market_customer', 'admin_user'))`
- `CHECK (is_internal_note = FALSE OR sender_type = 'admin_user')`：内部メモを書けるのは管理者のみ（DB レベルで矛盾防止）
- `sender_id` への FK は **物理的には張らない**（`sender_type` で参照先テーブルが切り替わる多態参照のため）。整合性は Service 層で `sender_type` ごとに `users` / `market_customers` の存在検証を行う

### インデックス方針

| インデックス | 用途 |
|-------------|------|
| `idx_inquiry_messages_inquiry_id_created_at` (`inquiry_id`, `created_at`) | スレッド表示（時系列順）|

### マイグレーション仕様

```sql
-- フェーズ18 r1: inquiry_messages
CREATE TABLE IF NOT EXISTS inquiry_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inquiry_id BIGINT NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    sender_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    is_internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    KEY idx_inquiry_messages_inquiry_id_created_at (inquiry_id, created_at),
    CONSTRAINT fk_inquiry_messages_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id) ON DELETE CASCADE,
    CONSTRAINT chk_inquiry_messages_sender_type CHECK (sender_type IN ('market_customer', 'admin_user')),
    CONSTRAINT chk_inquiry_messages_internal_note_admin CHECK (is_internal_note = FALSE OR sender_type = 'admin_user')
);
```

---

## 3.3 `notification_subscriptions` への `inquiry_alerts` 追加（phase17 連携）

phase17 §6.2.1 で導入された `notification_subscriptions` テーブルに、本フェーズで以下を追加投入する。

```sql
-- フェーズ18 r1: 既存 admin ユーザを inquiry_alerts に自動購読
INSERT IGNORE INTO notification_subscriptions
    (user_id, subscription_tag, email_enabled, in_app_enabled, created_at, updated_at)
SELECT u.id, 'inquiry_alerts', TRUE, TRUE, NOW(), NOW()
FROM users u
JOIN roles r ON u.role_id = r.id
WHERE r.name = 'admin' AND u.active_flag = TRUE;
```

phase17 §6.2.1 の「マイグレーション時に admin ロールの全ユーザを全タグに自動購読させる」既定方針に従い、本マイグレーションでも `inquiry_alerts` を admin に既定 ON で投入する。脱退は Console 側 UI（phase17 で実装される購読管理画面）から個別に行う。

---

# 4. ステータス遷移ルール（Service 層でガード）

## 4.1 状態と遷移可否

ステータス：`NEW` / `IN_PROGRESS` / `DONE`

| 現在 → 次 | NEW | IN_PROGRESS | DONE |
|----------|-----|-------------|------|
| NEW | - | ✅ | ✅（飛ばし完了：簡易対応で即終了）|
| IN_PROGRESS | ✅（差し戻し）| - | ✅ |
| DONE | ✅（再オープン）| ✅ | - |

許容遷移リストは `config('inquiry.allowed_status_transitions')` に enum 定義し、`UpdateInquiryStatusService` が参照（規約 1-2 / 3-1）。

## 4.2 遷移と updated_at の同期

- ステータス変更時、`UpdateInquiryStatusService` は `inquiries.status` 更新と `inquiries.updated_at = NOW()` 更新を同一トランザクションで実行。
- `operation_logs` への `update_inquiry_status` 記録も同一トランザクション。

## 4.3 異常系（Service 層で例外）

- 許可されていない遷移要求 → `IllegalInquiryStatusTransitionException`（HTTP 400）
- 存在しない `inquiries.id` → `EntityNotFoundException`（HTTP 404）
- 自分の問い合わせ以外を Market 顧客が触ろうとした場合 → `ForbiddenAccessException`（HTTP 403）

---

# 5. API 設計

## 5.1 Console API（`/api/console/inquiries/*`）

| メソッド | パス | 用途 | 認可 |
|----------|------|------|------|
| GET | `/api/console/inquiries/unread-count` | ベルマーク用：`status='NEW'` の件数 | admin / user 両方 |
| GET | `/api/console/inquiries` | 一覧（フィルタ・ソート・ページング）| admin / user 両方 |
| GET | `/api/console/inquiries/{id}` | 詳細（メッセージ含む。internal note も表示）| admin / user 両方 |
| PATCH | `/api/console/inquiries/{id}/status` | ステータス変更 | admin / user 両方 |
| POST | `/api/console/inquiries/{id}/messages` | 返信投稿（`is_internal_note` で内部メモ切替）| admin / user 両方 |

phase11 のロールは admin / user のみで権限粒度が細かくないため、本フェーズでは admin / user 両方が問い合わせを操作可能とする（運用負荷を一般 user にも分散できる構造）。phase17 と同様、ロール細分化は将来課題。

### 認証
- phase11 で確定済の **JWT 認証**を経由（`Authorization: Bearer ...`）。
- Spring Security のフィルタで `users.active_flag = TRUE` を検証。

### Pass-through（Laravel Console → Spring Core）
- Console 側 Laravel は `routes/api/Inquiry.php` で本書 API を Pass-through する（規約 2-1 補足4 と整合）。`config/app.php` に `'inquiry' => require __DIR__.'/app/Inquiry.php'` を追加。
- Pass-through Service / Controller は Core を呼ぶだけのシン層に保つ（`operation_logs` の記録は Core 側 Service で完結 / IMP-5）。
- **Console 側 config 参照は `config('app.inquiry.*')` 形式**（既存 `config('app.delivery.*')` / `config('app.auth.*')` と整合 / r4：本書 r3 までは `config('inquiry.*')` 表記だったが、Laravel の `config/app.php` 経由で登録された値は `app.*` プレフィクスでアクセスするのが既存運用）。

## 5.2 Market API（`/api/customer/inquiries/*`）

| メソッド | パス | 用途 |
|----------|------|------|
| GET | `/api/customer/inquiries` | 自分の問い合わせ一覧 |
| GET | `/api/customer/inquiries/{id}` | 自分の問い合わせ詳細（`is_internal_note=FALSE` のみ）|
| POST | `/api/customer/inquiries` | 新規作成（初回メッセージを同梱）|
| POST | `/api/customer/inquiries/{id}/messages` | 返信投稿 |

### 認証
- phase13 で確定済の **`MARKET_SESSION_ID` Cookie + CSRF（`X-CSRF-Token`）** を経由。
- Service 層で `WHERE user_id = :sessionUserId` を**必ず**付与（IDOR 対策）。Controller では `:sessionUserId` を Cookie 経由のセッションから取得し、リクエストパラメータとして受け取らない。

### Market POST DTO の分離（RV-9 対応：r2 で明記）

Market 側 Controller は **`is_internal_note` フィールドを持たない専用 DTO**（`MarketCreateInquiryRequest` / `MarketReplyInquiryRequest`）を使う。Console 側 DTO（`ConsoleReplyInquiryRequest`）とは別クラスにし、Market 側 Service は内部メモを構造的に作成不能にする。これにより：

- DB の `CHECK (is_internal_note = FALSE OR sender_type = 'admin_user')` と二重防御になる
- Mass Assignment 攻撃（リクエストに `isInternalNote: true` を付与する試み）の攻撃面を Controller 入口で塞ぐ
- §5.3 のリクエスト DTO 例（Market 側）にも `isInternalNote` を**載せない**

### CloudFront Behavior（phase13 と整合・RV-7 対応：r2 で根拠リンク追記）
- `/api/customer/inquiries/*` は phase13 §2.2 で追加済の `/api/customer/*` Behavior（[phase13_market_auth.md](phase13_market_auth.md) §2.2 Behavior 表 優先度3）に乗る。
- **本フェーズで CloudFront 側の変更は不要**（Behavior 追加・キャッシュ設定変更ともに無し）。Step B の CloudFront 担当者は phase13 §2.2 の表を確認のみ。

### Market 側の構造（r4 / IMP-4：実装に整合）

> **r4 実装注記（IMP-4）**：本書 r3 までの「Market 側 Laravel Pass-through（`amazia-market/app/Inquiry/...`）」は **実装環境に整合しない**。`amazia-market/` は React 19 + Vite + MUI の SPA 単独構成（`composer.json` / `app/` 配下なし）であり、Laravel フレームワーク自体が存在しない。実装は **React → Core 直接呼び出し**（CloudFront `/api/customer/*` Behavior は phase13 §2.2 で構築済みなので追加変更不要）。

- **Market 側に Pass-through 層は存在しない**。React コード（`amazia-market/src/features/inquiry/api/inquiry.js`）から `axios` で直接 Core の `/api/customer/inquiries/*` を呼び出す。
- 認証は `MarketSessionAuthFilter`（Core 側）が `MARKET_SESSION_ID` Cookie を検証、CSRF は `MarketCsrfFilter`（Core 側）が `X-CSRF-Token` ヘッダを検証。axios の `withCredentials: true` + `interceptors.request.use(...)` で CSRF トークンを自動付与する既存パターン（`features/orders/api/salesReturn.js` 等）を踏襲。
- ファイル構成：
  - `amazia-market/src/features/inquiry/api/inquiry.js`（API クライアント）
  - `amazia-market/src/features/inquiry/pages/MyPageInquiryList.jsx` / `MyPageInquiryDetail.jsx` / `MyPageInquiryNew.jsx`
  - ルートは `amazia-market/src/App.jsx` の `Routes` に `/mypage/inquiries` / `/mypage/inquiries/new` / `/mypage/inquiries/:id` を `<ProtectedRoute>` 配下で追加
- `MarketCreateInquiryRequest` / `MarketReplyInquiryRequest` の Mass Assignment 防御（RV-9）は **Core 側 Java Record DTO** で構造的に保証（Market 側 React からは `is_internal_note` フィールド名を含む payload を送ろうとしても、Core 側 DTO に該当フィールドが無いため Jackson が無視する。さらに `ReplyInquiryService` 内で `senderType='market_customer'` + `isInternalNote=true` の組合せを `InquiryValidationException` で拒否する三重防御）。

## 5.3 入出力 DTO（要点）

### POST `/api/customer/inquiries` リクエスト

```json
{
  "subject": "配送が遅れています",
  "message": "本文...",
  "targetType": "delivery",
  "targetId": 123
}
```

### GET `/api/console/inquiries/{id}` レスポンス

```json
{
  "id": 1,
  "userId": 42,
  "userName": "山田 太郎",
  "subject": "配送が遅れています",
  "status": "IN_PROGRESS",
  "targetType": "delivery",
  "targetId": 123,
  "createdAt": "2026-05-07T10:00:00+09:00",
  "updatedAt": "2026-05-07T11:30:00+09:00",
  "messages": [
    {
      "id": 1,
      "senderType": "market_customer",
      "senderId": 42,
      "senderName": "山田 太郎",
      "message": "本文...",
      "isInternalNote": false,
      "createdAt": "2026-05-07T10:00:00+09:00"
    },
    {
      "id": 2,
      "senderType": "admin_user",
      "senderId": 5,
      "senderName": "管理者A",
      "message": "確認します",
      "isInternalNote": false,
      "createdAt": "2026-05-07T11:30:00+09:00"
    }
  ]
}
```

`senderName` は API 側で JOIN して埋めて返す（フロントは表示するだけ）。Market API では `is_internal_note=TRUE` のレコードは `messages` 配列から除外。

---

# 6. 通知連携（phase17 統合）

## 6.1 通知発火点（RV-1 / RV-2 対応：r2 で `title` / `body` 追加・`payload_hash` 修正 / r4 で実装に整合）

phase17 r6 §5.2 で `console_notifications` の **`title` / `body` は NOT NULL** であるため、本書で文言テンプレートを確定する。文言は **Core 側 `application.properties` の `amazia.inquiry.notification-templates.*`**（r4 / IMP-1：本書 r3 までは `application.yml` 表記だったが、本プロジェクトは properties 形式運用）で管理し、ハードコードしない（規約 1-2 / 3-1）。

> **r4 実装注記（IMP-2）**：本書 r3 までのテンプレ列で使われていた `#{inquiry_id}` / `#{target_id}` は Spring `@Value` の SpEL 構文（`#{...}`）と衝突するため、`No.{inquiry_id}` / `No.{target_id}` に変更。意図（id を表示）は不変、見た目のみ変更。
>
> **r4 実装注記（IMP-3）**：phase17 の `BatchAlertNotifier.buildPayloadHash(subscriptionTag, payloadIdentity, sourceJob)` の実体は `SHA-256(subscriptionTag + ':' + payloadIdentity)` を生成する（phase17 §6.2.2）。本書 r3 の `payload_hash 算出元` 列は phase18 から phase17 に渡す `payloadIdentity` 部分を示しており、実体の `payload_hash` は `subscriptionTag='inquiry_alerts'` プレフィクス付きになる。下表を「`payloadIdentity`」列として再解釈し、phase17 既存実装を変えずに統合する方針を確定。

| イベント | subscription_tag | level | payloadIdentity（dispatch 引数） | 実体の payload_hash | title テンプレート | body テンプレート |
|---------|------------------|-------|---------------------------------|---------------------|------------------|-----------------|
| Market 顧客が新規問い合わせを作成 | `inquiry_alerts` | `INFO` | `inquiry_created:` + inquiries.id | `SHA-256('inquiry_alerts:inquiry_created:' + inquiries.id)` | `[問い合わせ] 新規 No.{inquiry_id} {subject}` | `{user_name} さんから新規問い合わせが登録されました。\n対象: {target_label}\n件名: {subject}` |
| Market 顧客が既存問い合わせに返信 | `inquiry_alerts` | `INFO` | `inquiry_replied:` + inquiries.id（**`messages.id` を含めない** / RV-2） | `SHA-256('inquiry_alerts:inquiry_replied:' + inquiries.id)` | `[問い合わせ] 返信 No.{inquiry_id}` | `{user_name} さんが No.{inquiry_id} に返信しました。\n件名: {subject}` |
| Console 管理者がステータス変更 | `inquiry_alerts` | `INFO` | `inquiry_status:` + inquiries.id + `:` + new_status | `SHA-256('inquiry_alerts:inquiry_status:' + inquiries.id + ':' + new_status)` | `[問い合わせ] ステータス変更 No.{inquiry_id}` | `No.{inquiry_id} を {old_status} → {new_status} に変更しました。\n件名: {subject}` |

### `payload_hash` 設計の意図（RV-2 対応）

| イベント | 抑制粒度 | 意図 |
|---------|---------|------|
| `inquiry_created` | inquiry 単位 | 同一 inquiry が 60 分以内に「新規」として複数発火することはない（INSERT は 1 回限定）|
| `inquiry_replied` | inquiry 単位（**`messages.id` を含めない**）| 60 分以内の連投を 1 通に集約。phase17 R-10 の「同 payload は 60 分抑制 + ダイジェストで件数集計」の本来意図に整合。`messages.id` を含めると毎回ユニーク化して抑制が効かず、phase17 r6 J-5 の `batch_execution_id` ベースと同じ反パターンになるため除外 |
| `inquiry_status` | inquiry × 遷移先単位 | 同一遷移の二重押下のみ抑制、別遷移（NEW→IN_PROGRESS と IN_PROGRESS→DONE）は別ハッシュで残す |

### `target_label` の組み立て（r4 / IMP-2 で `#` 表記を `No.` に）

`body` テンプレートの `{target_label}` プレースホルダは Service 層 `InquiryTargetLabelResolver` で組み立てる。テンプレート文字列は `application.properties amazia.inquiry.target-labels.*` に格納：

| `target_type` | `target_label` テンプレ（properties） | 展開例 |
|--------------|------------------|------|
| `delivery` | `配送 No.{target_id}` | `配送 No.42` |
| `product` | `商品 No.{target_id} ({product_name})` | `商品 No.7 (赤いTシャツ)` |
| `sales` | `注文 No.{target_id}` | `注文 No.123` |
| NULL | `（汎用）` | `（汎用）` |

`{target_id}` / `{product_name}` のプレースホルダは `InquiryTargetLabelResolver.resolve(...)` 内で `String.replace("{target_id}", ...)` で展開（Spring の `${...}` プロパティ展開ではないため、`@Value` を経由したテンプレ文字列に対しても安全）。

### dispatcher 呼び出し（r4 / IMP-3：phase17 実体 API）

phase17 `BatchAlertNotifier.dispatch(level, subscriptionTag, title, body, payloadIdentity, sourceJob, sourceBatchExecutionId)` を呼び出す（本書 r3 までは仮称 `NotificationDispatcher.dispatch(subscription_tag, level, payload)` 表記だったが、phase17 の実体クラスは `BatchAlertNotifier` で、引数は分解されている）。`sourceJob=null` / `sourceBatchExecutionId=null` を渡す（バッチ起源ではないため）。

- 内部で `payload_hash = SHA-256(subscriptionTag + ':' + payloadIdentity)` が生成され、`console_notifications` に `payload_hash` / `level` / `target_subscription_tag` / `title` / `body` / `suppressed` / `created_at` を NOT NULL で INSERT する（独立トランザクション `REQUIRES_NEW` / phase17 §6.4 と整合）。
- `level=INFO` のため SES メール送出は行われず、`console_notifications` への INSERT のみ実施される（phase17 §6.2.2 の「`level >= WARN` のみメール」ルールに従う）。
- 60 分以内の同一 `payload_hash` 連投は phase17 既存ロジックで `suppressed=TRUE` となる。

## 6.2 重複抑制（phase17 §6.4 と整合）

- phase17 の重複抑制（`amazia.batch.rate-limit.duplicate-suppression-minutes`、既定 60 分）は本書通知にも適用される。
- 同一の `payload_hash`（例：同じ問い合わせ ID への連続返信）は 60 分以内なら抑制され、`console_notifications.suppressed = TRUE` が立つ。
- 抑制された通知は phase17 §6.4.2 の `DigestNotificationDispatchJob` が 5 分間隔で集計し、ダイジェスト送信される。本書側で追加実装は不要。

## 6.3 ベルマーク件数との関係

- ベルマーク件数の真実の元は **`inquiries.status='NEW'` の COUNT** であり、`console_notifications` ではない。
- `console_notifications` は通知センターの履歴表示用（phase17 §3.5）で、本書ベルマーク UI とは別経路。
- ベルマーク件数 API（`/api/console/inquiries/unread-count`）の実装は **`SELECT COUNT(*) FROM inquiries WHERE status = 'NEW'`** の単純な集計（規約 1-1：Service 層で実装）。

---

# 7. 環境変数

phase18 で追加する環境変数（[user memory: env_vars_and_tests] に従い、`docker-compose.yml` / `phpunit.xml` / `application.properties` / `application-test.properties` をセット更新 / r4：Market 側 `phpunit.xml` は実装環境に存在しないためスコープ外）：

| 変数名 | 用途 | 既定値 |
|--------|------|-------|
| `INQUIRY_SUBJECT_MAX_LENGTH` | 件名の最大文字数（バリデーション）| `100` |
| `INQUIRY_MESSAGE_MAX_LENGTH` | メッセージ本文の最大文字数 | `4000` |
| `INQUIRY_LIST_PAGE_SIZE_CONSOLE` | Console 一覧の 1 ページ件数 | `50` |
| `INQUIRY_LIST_PAGE_SIZE_MARKET` | Market 一覧の 1 ページ件数 | `20` |
| `INQUIRY_BELL_POLLING_INTERVAL_MS` | Console ベルマークのポーリング間隔（ms）。Vue 側は `import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS` 経由（Vite は `VITE_*` 名前空間を `import.meta.env` に自動マッピングする）| `30000` |

**Step 1 着手前の確認チェックリスト**（`coding_guidelines.md` §4-3 に従う）：

- [ ] `docker-compose.yml` の amazia-core サービスに環境変数 4 個（`INQUIRY_SUBJECT_MAX_LENGTH` / `INQUIRY_MESSAGE_MAX_LENGTH` / `INQUIRY_LIST_PAGE_SIZE_CONSOLE` / `INQUIRY_LIST_PAGE_SIZE_MARKET`）を追記
- [ ] `application.properties` に `amazia.inquiry.*` ブロックを追加（r4 / IMP-1：yml ではなく properties）
- [ ] `application-test.properties` にテスト用既定値を追記
- [ ] `phpunit.xml`（Console）にテスト用 5 個を追記（`INQUIRY_BELL_POLLING_INTERVAL_MS` 含む。`amazia-market/phpunit.xml` は不存在のためスコープ外 / IMP-4）
- [ ] テストコードが `@Value` / `config()` 経由で値を参照していること（ハードコード禁止）

---

# 8. 操作ログ規約（phase14 / phase15 / phase17 と整合）

- `screen_name`：
  - Console 一覧 → `ConsoleInquiryListPage`
  - Console 詳細 → `ConsoleInquiryDetailPage`
  - Market 一覧 → `MarketInquiryListPage`
  - Market 詳細 → `MarketInquiryDetailPage`
  - Market 新規作成 → `MarketInquiryCreatePage`
- `api_name`：HTTP メソッド + 実 URL（例：`POST /api/console/inquiries/123/messages`）
- `comment` プレフィックス：`[admin_reply]` / `[customer_reply]` / `[status_change]` / `[internal_note]`

将来 `operation_logs.reason_code` カラムが追加されたら、上記プレフィックスは `reason_code` へ昇格する（[phase14 P14-5](phase14_shipping.md) / [phase15 RRR-5](phase15_delivery_management.md) と整合）。

---

# 9. config 駆動（規約 1-2 / 3-1）

**Console Pass-through 用** `amazia-console/config/app/Inquiry.php`（Laravel）と **Core 用** `amazia-core/src/main/resources/application.properties` の `amazia.inquiry.*`（Spring）に以下を定義する（r4 / IMP-1：本書 r3 までは yml 表記だったが、本プロジェクトは properties 形式運用 / IMP-2：`#{inquiry_id}` を `No.{inquiry_id}` に変更 / IMP-4：Market 側 Laravel config は不存在のため対象外）：

```properties
# Core: amazia-core/src/main/resources/application.properties
amazia.inquiry.statuses=NEW,IN_PROGRESS,DONE
# 形式：旧:新1,新2;旧:新1,新2 ... の単一行 CSV（Service 側で Map<String,List<String>> にパース）
amazia.inquiry.allowed-status-transitions=NEW:IN_PROGRESS,DONE;IN_PROGRESS:NEW,DONE;DONE:NEW,IN_PROGRESS
amazia.inquiry.target-types=delivery,product,sales
amazia.inquiry.subject-max-length=${INQUIRY_SUBJECT_MAX_LENGTH:100}
amazia.inquiry.message-max-length=${INQUIRY_MESSAGE_MAX_LENGTH:4000}
amazia.inquiry.page-size-console=${INQUIRY_LIST_PAGE_SIZE_CONSOLE:50}
amazia.inquiry.page-size-market=${INQUIRY_LIST_PAGE_SIZE_MARKET:20}
amazia.inquiry.notification-tag=inquiry_alerts
# 通知テンプレート（IMP-2：SpEL 衝突回避のため No. 表記）
amazia.inquiry.notification-templates.created.title=[問い合わせ] 新規 No.{inquiry_id} {subject}
amazia.inquiry.notification-templates.created.body={user_name} さんから新規問い合わせが登録されました。\n対象: {target_label}\n件名: {subject}
amazia.inquiry.notification-templates.replied.title=[問い合わせ] 返信 No.{inquiry_id}
amazia.inquiry.notification-templates.replied.body={user_name} さんが No.{inquiry_id} に返信しました。\n件名: {subject}
amazia.inquiry.notification-templates.status-changed.title=[問い合わせ] ステータス変更 No.{inquiry_id}
amazia.inquiry.notification-templates.status-changed.body=No.{inquiry_id} を {old_status} → {new_status} に変更しました。\n件名: {subject}
# target_label テンプレート（target_type 連動）
amazia.inquiry.target-labels.delivery=配送 No.{target_id}
amazia.inquiry.target-labels.product=商品 No.{target_id} ({product_name})
amazia.inquiry.target-labels.sales=注文 No.{target_id}
amazia.inquiry.target-labels.generic=（汎用）
# operation_logs.comment プレフィックス
amazia.inquiry.operation-log-prefixes.admin-reply=[admin_reply]
amazia.inquiry.operation-log-prefixes.customer-reply=[customer_reply]
amazia.inquiry.operation-log-prefixes.status-change=[status_change]
amazia.inquiry.operation-log-prefixes.internal-note=[internal_note]
```

```php
// Console Pass-through: amazia-console/config/app/Inquiry.php
// FormRequest（バリデーション）と Vue ビルド時の上限参照のみが使うサブセット。
return [
    'statuses' => ['NEW', 'IN_PROGRESS', 'DONE'],
    'allowed_status_transitions' => [
        'NEW'         => ['IN_PROGRESS', 'DONE'],
        'IN_PROGRESS' => ['NEW', 'DONE'],
        'DONE'        => ['NEW', 'IN_PROGRESS'],
    ],
    'target_types' => ['delivery', 'product', 'sales'],
    'subject_max_length' => (int) env('INQUIRY_SUBJECT_MAX_LENGTH', 100),
    'message_max_length' => (int) env('INQUIRY_MESSAGE_MAX_LENGTH', 4000),
    'page_size_console'  => (int) env('INQUIRY_LIST_PAGE_SIZE_CONSOLE', 50),
    'operation_log_prefixes' => [
        'admin_reply'    => '[admin_reply]',
        'customer_reply' => '[customer_reply]',
        'status_change'  => '[status_change]',
        'internal_note'  => '[internal_note]',
    ],
    'bell_polling_interval_ms' => (int) env('INQUIRY_BELL_POLLING_INTERVAL_MS', 30000),
];
```

PHP 側は `config/app.php` で明示読込（既存 phase14 / 15 / 17 と同方針）：

```php
return [
    // 既存...
    'inquiry' => require __DIR__.'/app/Inquiry.php',
];
```

**参照側コード**（IMP-5 整合）：
- Core 側：`@Value("${amazia.inquiry.subject-max-length}")` 等で各値を取得。
- Console Pass-through 層：`config('app.inquiry.message_max_length')` のように **`app.*` プレフィクス**で参照（既存 `config('app.delivery.*')` / `config('app.auth.*')` と整合）。
- Market 側：React コードに直接定数で持つか（最小機能優先で本フェーズはこちら）、将来 build 時環境変数で注入する。

---

# 10. 実装段取り（Step 0 → Step A → Step B）

| Step | 対象 | 主な作業 | 完了条件 |
|------|------|---------|---------|
| **Step 0** | 前提整備 | (1) `application.properties` / `application-test.properties` / `phpunit.xml`（Console のみ） / `docker-compose.yml` に新規環境変数追加（r4 / IMP-1：yml ではなく properties）。(2) Console 側 `config/app/Inquiry.php` 新規作成（IMP-4：Market 側 Laravel config は不存在のため対象外）。(3) Console 側 `config/app.php` への明示読込追加。(4) Console 側 `routes/api.php` への `Inquiry.php` 明示読込追加。(5) **RV-10 対応**：本書を `docs/design/phase11_20/phase18_inquiry_management.md` として r2 で確定し、Step A 着手前にレビュアー承認を得る。 | `mvn test` / `php artisan test`（Console PHPUnit）/ `npm run test`（Market Vitest）が既存テスト含めて全緑。Console SPA は Vitest 未導入のため `npm run build` 緑で代替（IMP-7）。設計書 r2 がレビュー承認済み |
| **Step A** | DB スキーマ作成 + 設計書同期 | (1) `amazia-core/src/main/resources/schema.sql` に `inquiries` / `inquiry_messages` の `CREATE TABLE IF NOT EXISTS` を冪等追記。(2) `notification_subscriptions` への `inquiry_alerts` 自動購読 INSERT IGNORE。(3) Core 側 JPA Entity（`Inquiry` / `InquiryMessage`）と Repository を新規作成。**(4) RV-10 対応 — DB 設計書同期**：`docs/database_design/TBL_inquiries.md` / `TBL_inquiry_messages.md` 新設・`docs/database_design/README.md` ファイル一覧へ追記・`docs/database_design/ER_diagram.md` の Mermaid 図に inquiries / inquiry_messages とリレーションを追加・**`ops/healthcheck/required_tables.txt` に `inquiries` / `inquiry_messages` を追記**（CD のヘルスチェック対象化／[CLAUDE.md §主要テーブル定数の同期](../../../CLAUDE.md) 準拠）。 | 起動時に H2 / 本番 MySQL の双方でテーブルが作成され、エラーなく Spring Boot 起動。phase15 / phase17 の既存テストが緑のまま。**`docs/database_design/` 配下が r2 設計と完全一致し、`required_tables.txt` 反映済**（044 / phaseX-6 教訓の再発防止） |
| **Step B** | 機能実装 + API 設計書同期 | (1) Core: `CreateInquiryService` / `ListInquiryService` / `GetInquiryService` / `ReplyInquiryService` / `UpdateInquiryStatusService` / `GetUnreadInquiryCountService` を新規作成。phase17 `BatchAlertNotifier.dispatch(level, subscriptionTag, title, body, payloadIdentity, sourceJob, sourceBatchExecutionId)` のフック埋め込み（r4 / IMP-3：phase17 実体 API）。**(1-a) RV-5 対応**：`com.example.inquiry.validator.InquiryTargetOwnershipValidator` を新規作成し、`CreateInquiryService` から呼び出す（`target_type` が `delivery` / `product` / `sales` の場合に所有者検証ロジックを集約。phase11 のロール / phase13 の `market_customers.id` / phase14 の `sales.user_id` / phase15 の `deliveries → sales` 結合を 1 ファイルに閉じ込める）。**(1-b) IMP-5 対応**：`ReplyInquiryService` / `UpdateInquiryStatusService` 内で `OperationLogRepository.save(...)` を呼び `operation_logs` を記録（既存 phase14 / 15 / 17 と同方針。Console Pass-through 層では記録しない）。(2) Console SPA: `InquiryBellBadge.vue`（r4 / IMP-6：Bell 単独コンポではなくサイドバーメニュー組込用バッジ） / `InquiryList.vue` / `InquiryDetail.vue` 新規 + `useVisibilityPolling.js` Composable 新規（RV-8 / r4 / IMP-6：JS で実装。TS 未導入）。**App.vue サイドバーにベルマーク埋め込み**（r4 / IMP-6：Header.vue は不存在）。(3) Market SPA: `MyPageInquiryList.jsx` / `MyPageInquiryDetail.jsx` / `MyPageInquiryNew.jsx` 新規（r4 / IMP-4：JSX で `amazia-market/src/features/inquiry/` 配下）。`MyPage.jsx` に「問い合わせ」ボタン追加。**(3-a) IMP-4 対応**：Market は **React 直接 Core**（Laravel Pass-through 不在）。CloudFront `/api/customer/*` Behavior は phase13 §2.2 で構築済みのため変更不要。**(3-b) IMP-8 対応**：`MyPageInquiryNew.jsx` の対象 ID 入力は数値入力で簡略化（動的セレクタは phase19 / 後続で拡張可能）。(4) Console Pass-through ルート追加（Market 側はルート不要 / IMP-4）。(5) operation_logs 記録は Core 側で完結（IMP-5）。**(6) RV-10 対応 — API 設計書同期**：`docs/api_design/Core_API.md` に `/api/console/inquiries/*` 系・`/api/customer/inquiries/*` 系を追記、`docs/api_design/Console_API.md` に Pass-through 経路を追記、`docs/api_design/Market_API.md` に React 側呼び出しを追記。 | Market から問い合わせ作成 → Console ベルマーク件数増加 → 管理者が返信 → Market 側で返信表示、の end-to-end が本番 HTTPS 構成（CloudFront → EC2）で通る。Core / Console PHPUnit / Market Vitest 全テスト緑。Console SPA は Vitest 未導入のため `npm run build` 緑で代替（IMP-7）。**`docs/api_design/` 配下が実装と完全一致** |

実装担当者は **必ず Step 0 → A → B の順序**で進める（規約 4-3 の env-vars チェックリストを Step 0 で片付けないと Step A で起動失敗する）。

---

# 11. TDD テストケース

## 11.1 Amazia Core / JUnit

### 正常系
- 問い合わせが正しく登録できる（`CreateInquiryService`：`inquiries` + 初回 `inquiry_messages` が同一トランザクションで INSERT される）
- スレッドメッセージが時系列で正しく取得できる（`is_internal_note=FALSE` のみ Market 側、両方 Console 側）
- ステータス変更が正しく反映され、`updated_at` が更新される
- 許可された遷移（NEW→IN_PROGRESS / IN_PROGRESS→DONE / DONE→NEW など §4.1 表内）が成功する
- 未対応件数（`status='NEW'` の COUNT）が正しく取得できる
- `target_type='delivery'` で `target_id` を正しく保存・取得できる
- 通知 dispatch（phase17 `BatchAlertNotifier.dispatch`）がモック / 実 INSERT 検証で正しく走る（r4 / IMP-3：実体の payload_hash は `SHA-256('inquiry_alerts:inquiry_created:'+id)` / `SHA-256('inquiry_alerts:inquiry_replied:'+id)`（RV-2：`messages.id` を含めない）/ `SHA-256('inquiry_alerts:inquiry_status:'+id+':'+new_status)` と一致）
- 通知 dispatch 時に `console_notifications.title` / `body` が `application.properties amazia.inquiry.notification-templates.*` のテンプレートから組み立てられて NOT NULL で INSERT される（RV-1）
- 同一 inquiry への 60 分以内の連続返信が `payload_hash` 一致で抑制され、`console_notifications.suppressed=TRUE` が立つ（RV-2 整合：`messages.id` を含めないことで抑制が効くことの確認）

### 異常系（規約 4-2）
- 不正なステータス遷移（許容外の組み合わせ）を Service 層で例外として拒否
- 存在しない `inquiries.id` への操作で 404 相当の例外
- 他人の `market_customer.id` の `inquiries` を Market API で取得しようとした場合 403 相当
- `target_type='delivery'` だが `target_id` が NULL のリクエストを Service 層で例外（DB の CHECK と二重防御）
- `target_type='delivery'` の `target_id` が存在しない `deliveries.id` の場合 Service 層で検証失敗
- `target_type='delivery'` の `target_id` が **他顧客の deliveries** の場合（`deliveries → sales.user_id` で所有者検証）拒否
- `is_internal_note=TRUE` を Market 顧客が指定して投稿した場合の拒否（`sender_type=market_customer` と矛盾）
- 件名・本文が文字数上限を超えた場合の拒否（FormRequest / `@Valid` 標準バリデーション + `config('inquiry.subject-max-length')` 経由）

### config / 環境変数経由化テスト（規約 4-1）
- 件名・本文の上限文字数が `config('inquiry.message-max-length')` から取得されてバリデーションが動作することを、テスト用 `phpunit.xml` の値（例：`5`）でアサートする
- ベルマークポーリング間隔は SPA テストで `INQUIRY_BELL_POLLING_INTERVAL_MS` を上書きしたケースで確認

## 11.2 Amazia Console / PHPUnit + Vitest

### バックエンド（Pass-through 層 / PHPUnit / `Tests\Feature\Inquiry\InquiryProxyTest`）
- `GET /api/console/inquiries/unread-count` が Core を呼び出して `{ count: N }` を返す
- `GET /api/console/inquiries` のクエリ（`status` / `targetType` / `userName` / `page` / `size`）が Core に透過される
- `GET /api/console/inquiries/{id}` の 404 が透過される
- `POST /api/console/inquiries/{id}/messages` で `X-User-Id` ヘッダが Core に透過される（r4 / IMP-5：operation_logs は Core 側で記録）
- 内部メモ投稿の payload（`isInternalNote: true`）が透過される
- `PATCH /api/console/inquiries/{id}/status` が中継される
- 未知ステータス・文字数上限超過は Console FormRequest で 422 を返す
- 全 9 ケース緑（実装結果）

### SPA（Vue / r4：Vitest 未導入のためスコープ外）

> **r4 実装注記（IMP-7）**：`amazia-console/resources/vue/package.json` に Vitest が登録されておらず、phase18 単独でテストフレームワーク導入を行うのは規模超過。本書 r3 の SPA テストケース（旧 SPA-CSL-1〜7）は **`npm run build` 緑による構文・依存解決確認**で代替し、UI 動作は Step 6（通知統合検証）/ Step 7（end-to-end）の手動確認で担保する。Vitest 導入は phaseX-N（テスト基盤整備）として別フェーズ申し送り候補。

検証は手動 E2E で実施：
- ベルマーク（サイドバーメニューの `<a-badge>`）に未対応件数が正しく表示される（バッジ数値・99+ 表示）
- 未対応 0 件のときバッジが非表示
- 件名クリックでスレッド画面に遷移する
- 管理者がスレッド画面から返信できる
- ステータスドロップダウンで遷移できる（許容遷移のみが UI 上で選択可能）
- ポーリング間隔は `import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS` 経由で取得していること（DevTools の Network タブで確認）
- タブ非表示時にポーリングが停止し、再表示で再開すること（`document.visibilitychange` イベントの動作確認）

## 11.3 Amazia Market / Vitest（r4 / IMP-4：Pass-through 層は不存在のため Vitest のみ）

### SPA（Vitest / React Testing Library / `src/features/inquiry/pages/*.test.jsx`）
- 問い合わせ一覧の表示・空状態・エラー表示
- マイページから「問い合わせ」ボタンに遷移できる
- 新規作成画面で件名 / 本文 / 対象種別 / 対象 ID を入力して送信できる
- 対象種別の選択に応じて対象 ID 入力欄が出現する（r4 / IMP-8：プルダウンではなく数値入力）
- 文字数上限超過で送信ボタンが無効化される
- 全 6 ケース緑（実装結果）

### Core 経由のフルフロー検証（Core JUnit MockMvc / `InquiryE2EFlowTest`）
Market Vitest は React コンポーネント単独テストのため、Market → Core → Console の通信全体は **Core 側 MockMvc E2E テスト**でカバー：
- Market 作成 → Console 一覧取得 → ベルマーク件数確認 → Console 返信 → 内部メモ投稿 → Market 詳細で内部メモ除外確認 → ステータス変更 → Market 反映の 11 ステップ通し検証
- 他人の inquiry への Market アクセスは 403
- Session 無しの Market API は 401
- 全 3 ケース緑（実装結果）

## 11.4 phase17 通知統合テスト（Core / JUnit）

実装は `InquiryNotificationIntegrationTest`（Step 6 で追加）と `InquiryServiceTest` の通知関連ケースで担保。すべて緑（実装結果）：

- 問い合わせ作成時に `console_notifications` が `target_subscription_tag='inquiry_alerts'`, `level='INFO'` で 1 件 INSERT される（CRT-2）
- 同一 `payloadIdentity` の 60 分以内連投が `payload_hash` 一致で `suppressed=TRUE` になる（SUP-1）
- 3 イベント（`inquiry_created` / `inquiry_replied` / `inquiry_status`）の payload_hash が独立し、ステータス変更で異なる遷移は別 hash になる
- `notification_subscriptions.subscription_tag='inquiry_alerts'` の購読者解決経路が動作する（INT_TAG）
- `console_notifications.title` / `body` が NOT NULL で展開済み（テンプレート差込済）で保存される（INT_console_notifications）

> WARN レベルへの一時昇格テストは本フェーズスコープ外（INFO 固定運用のため）。phase17 の `BatchAlertNotifierSesIntegrationTest` で WARN/ERROR 経路は既に検証済み。

---

# 12. 採用しなかった選択肢

| 候補 | 不採用理由 |
|------|-----------|
| WebSocket 通知 | EC2 t3.micro の `-Xmx384m` 構成（[user memory: phaseX4_t3micro_recovery]）と無料枠完走方針（[user memory: free_tier_first]）に対して、ConnectionManager 等の追加メモリ消費が見合わない。30 秒ポーリングで件数規模（管理者数〜数十・問い合わせ数〜数百/日 想定）に十分対応可能 |
| SSE（Server-Sent Events） | phase17 r6 で `console_notifications` 用に SSE 基盤は整備されているが、本フェーズで配線する工数を避ける。後続フェーズで「ベルマーク全体の集約 push」を実装する際に統合する |
| `inquiries.assigned_user_id`（担当者割当） | YAGNI。phase11 のロールが admin / user 2 種で、admin は基本全員対応可能な運用想定。担当者制が必要になれば別フェーズ |
| `inquiry_categories` マスタ | YAGNI。`target_type` で「配送 / 商品 / 注文 / 汎用」を区別できるため、別途カテゴリマスタは不要 |
| `inquiry_attachments` テーブル | スコープ外（§範囲）。S3 / EFS 配置・サイズ制限・MIME 検証・ウィルススキャン・無料枠インパクトの検討が必要で、本フェーズの完走を妨げるため後続に切り出す |
| `inquiries` の物理削除 / アーカイブ | 本書スコープ外。1 年経過した DONE は phase17 のオンデマンドバッチ `OperationLogArchiveJob` と同思想で別バッチに切り出す（§13）|

---

# 13. 他フェーズへの要請事項

## 13.1 phase15（配送管理）への確認事項

- phase15 §phase18 への要請事項で確定した `inquiries.target_type='delivery'` / `target_id=deliveries.id` を本書 r1 で正式に取り込み済。phase15 側の追加対応は不要。
- 配送に関する問い合わせから `deliveries` 詳細画面（Console）へのリンクは、本書 §2.1.3 で実装する。

## 13.2 phase17（バッチ処理）への要請事項（RV-3 対応：r2 で位置づけ修正）

phase17 r6 §3.4 オンデマンドバッチ表は r5 で凍結されているため、本書からの新規 Job 追加要請は **phase17 §14.1 r7 候補への追加要請**として明示的に位置づけ直す。phase17 r7 改訂時に下記 2 件を **新規 ID（phase17 r7 の prefix 規則に従う／`R-` / `N-` / `M-` / `K-` / `J-` は r2 〜 r6 で既出のため、phase17 著者の選定によりアルファベット繰上げ）で受理**してもらうことを前提とし、**phase17 r7 が確定するまでは本書 §13.2 の 2 件はスコープ外として保持**する（RV2-2 対応：r3 で prefix 命名の整合性を明示）。

phase17 著者との協議オプションとして、phase17 §14.1 r7 候補リストにプリエンプティブに「phase18 r3 から `InquiryArchiveJob` / `InquiryStaleAlertJob` の 2 件が追加要請されている」と申し送り 1 行を入れてもらう対応も可能（コーディネート工数は phase17 r7 着手時に確認）。

phase17 が先に r6 完了 → 本書実装着手という順序になった場合でも、本書スコープ（ベルマーク 30 秒ポーリング + 通知 INSERT）は phase17 §6.2 の `BatchAlertNotifier`（r4 / IMP-3：仮称 `NotificationDispatcher` ではなく実体クラス名）のみに依存し、Job 追加要請には依存しないため、本書実装は単独で完結する。

| 項目 | 内容 | phase17 への要請区分 |
|------|------|--------------------|
| `notification_subscriptions` への `inquiry_alerts` 追加 | 本書 §3.3 のマイグレーションで自動投入する。phase17 側で `subscription_tag` の許容値リスト（あれば）に `inquiry_alerts` を追加 | **r6 で受理可能**（既存の購読タグ追加メカニズムに合致／構造変更なし）|
| `InquiryArchiveJob`（DONE 完了から N 日経過のアーカイブ） | phase17 §3.4 オンデマンドバッチに追加する Job。本書ではスコープ外 | **phase17 r7 候補（§14.1）への追加要請**として依頼 |
| `InquiryStaleAlertJob`（NEW のまま N 日経過の警告） | phase17 §3.1 日次バッチに追加する Job（WARN レベル）。本書ではスコープ外 | **phase17 r7 候補（§14.1）への追加要請**として依頼 |

phase17 r7 が成立しない場合の代替案として、本書の後続フェーズ（phase18.5 仮）でこれら 2 Job を本書スコープ内に取り込む選択肢も残す（レビューコメント RV-3 案 (B) 相当）。ただし phase17 とのバッチ基盤共通化メリット（`@Scheduled` 統一・`batch_executions` 履歴・`@ConditionalOnProperty` 制御）を活かすなら案 (A) のほうが構造的に綺麗なため、本書 r2 では案 (A) を第一選択肢とする。

## 13.3 phase19（お知らせ）への申し送り

- phase19 のヘッダー通知エリアと本フェーズの Console ベルマークは **別系統の UI**。両者を統合する集約 Bell コンポーネントは将来課題。
- ベルマーク件数の取得は `useVisibilityPolling(fetcher, intervalMs)` Composable（r4 / IMP-6）で抽象化されているため、phase19 / phase20 で「全通知集約」エンドポイントが整備されたら、別コンポーネントで `fetcher` を差し替えるだけで集約できる構造になっている。

---

# 14. 命名規約（phase14 / phase15 / phase17 との整合 / r4 で実装に整合）

- **Console**（Laravel Pass-through + Vue 3 / Ant Design Vue）
  - PHP：`amazia-console/app/Inquiry/Controller/{GetUnreadInquiryCountController, ListInquiryController, GetInquiryController, ReplyInquiryController, UpdateInquiryStatusController}.php` / `amazia-console/app/Inquiry/Service/...`（**新規作成は Console 側のみ。Market は Pass-through 不在 / IMP-4**）
  - ルートファイル（規約 2-1 補足4）：`amazia-console/routes/api/Inquiry.php`（`routes/api.php` で明示読込）
  - config（規約 2-1 補足3）：`amazia-console/config/app/Inquiry.php`（`config/app.php` で明示読込・`config('app.inquiry.*')` で参照）
- **Core**（Spring Boot）
  - Java：`com.example.inquiry.controller.*` / `com.example.inquiry.service.*` / `com.example.inquiry.service.notification.InquiryNotificationDispatcher` / `com.example.inquiry.entity.*` / `com.example.inquiry.repository.*` / `com.example.inquiry.validator.*` / `com.example.inquiry.exception.*` / `com.example.inquiry.dto.*`
    - **`com.example.inquiry.validator.InquiryTargetOwnershipValidator`（RV-5 対応：r2 で明記）**：`target_type` ごとの所有者検証ロジックを 1 ファイルに集約。`CreateInquiryService` から呼び出される。`target_type='delivery'` のとき `deliveries → sales.user_id` で所有者一致検証、`target_type='sales'` のとき `sales.user_id` 直接検証、`target_type='product'` のとき `products.is_active = TRUE` 検証
    - **`com.example.inquiry.service.notification.InquiryNotificationDispatcher`（r4 / IMP-3 で追加）**：phase17 `BatchAlertNotifier` を呼ぶドメイン dispatcher。`title` / `body` テンプレ展開と `payloadIdentity` 組み立てを集約（`dispatchCreated` / `dispatchReplied` / `dispatchStatusChanged` の 3 メソッド）
- **Console SPA**（Vue / r4 で配置確定）
  - Vue ページ：`amazia-console/resources/vue/src/features/inquiry/pages/{InquiryList,InquiryDetail}.vue`
  - Vue API：`amazia-console/resources/vue/src/features/inquiry/api/inquiryApi.js`
  - Vue Component（**サイドバーメニュー組込用バッジ / IMP-6**）：`amazia-console/resources/vue/src/components/inquiry/InquiryBellBadge.vue`
  - **Vue Composable（RV-8 対応：r2 で追加 / r4 で配置確定）**：`amazia-console/resources/vue/src/composables/useVisibilityPolling.js`（規約 2-3 Shared 思想 / JS で実装。Console は TS 未導入）。phase19 以降の集約 Bell でも再利用予定
  - **App.vue（IMP-6）**：サイドバーメニュー（`a-layout-sider`）に `<InquiryBellBadge />` を組込
- **Market SPA**（React + Vite / r4 / IMP-4：Laravel 不在）
  - React ページ：`amazia-market/src/features/inquiry/pages/{MyPageInquiryList,MyPageInquiryDetail,MyPageInquiryNew}.jsx`
  - React API：`amazia-market/src/features/inquiry/api/inquiry.js`（axios + CSRF interceptor）
  - ルート：`amazia-market/src/App.jsx` の `<Routes>` に `/mypage/inquiries` / `/mypage/inquiries/new` / `/mypage/inquiries/:id` を `<ProtectedRoute>` 配下で追加
  - マイページ動線：`amazia-market/src/features/customer/pages/MyPage.jsx` に「問い合わせ」ボタン追加
- ファイル命名はユースケース単位（規約 2-2）：1 ファイル = 1 ユースケース。

## 14.1 将来拡張余地（RV-11 対応：r2 で追加 / RV2-3・RV2-4 対応：r3 で更新）

§12 で却下した `assigned_user_id`（担当者割当）を将来追加する場合に備え、以下の IF 互換性余地を残す：

### 14.1.1 Service 入力 DTO の拡張余地（RV2-4 対応：r3 で `ReplyInquiryService` 側にも展開）

`assigned_user_id` 導入時は「ステータス変更時に担当者を変える」だけでなく「**返信時に未割当ならログイン admin を担当者に自動セット**」のフックも必要になるため、以下の **両方の Service** の入力 DTO に拡張余地を残す：

- `UpdateInquiryStatusService` の入力 DTO（仮：`InquiryStatusMutationContext`）は `Map<String, Object>` ではなく明示的なフィールドを持つ Java Record / クラスとし、将来 `assignedUserId: Long` フィールドを追加可能にする
- `ReplyInquiryService` の入力 DTO（仮：`ReplyInquiryRequest`）にも同じく拡張余地を残し、将来「未割当時の自動再割当」フックを追加できるようにする
- 共通インターフェース `InquiryMutationContext`（仮）を抽出して両 Service が継承する形に整理してもよいが、本フェーズではユースケース別に独立 DTO を保つ（規約 2-2「1 ファイル = 1 ユースケース」と整合）

返信時の自動再割当を将来導入する場合の擬似コード（参考）：

```
ReplyInquiryService.reply(...):
    if (inquiry.assignedUserId == null && actor.role == 'admin') {
        inquiry.assignedUserId = actor.userId  // 未割当の最初の admin 返信で自動割当
    }
```

この拡張余地により、後続フェーズで担当者割当機能を導入する際、ステータス変更・返信の両経路で改修が分散せず 1 フェーズに集約できる。

### 14.1.2 スキーマ追加マイグレーション（RV2-3 対応：r3 で MySQL 互換性修正）

`inquiries` テーブルへの `assigned_user_id BIGINT NULL` カラム追加は本書 r3 では行わないが、将来追加する際のマイグレーション参考実装は以下とする。

**注意（RV2-3）**：`ALTER TABLE ... ADD COLUMN IF NOT EXISTS` は **MySQL 8.0.29 以降のみ対応**の構文。本プロジェクト本番 DB は `mysql:8.0` タグ運用（`docker-compose.yml`）でパッチバージョンが 8.0.29 以降である保証がなく、phase14 / phase15 / phase17 のマイグレーションも `CREATE TABLE IF NOT EXISTS` までで `ALTER TABLE ADD COLUMN IF NOT EXISTS` は前例がない。よって **`information_schema` を参照する冪等パターン**（H2 / MySQL 8.0 全パッチ互換）を採用する：

```sql
-- 将来 phase18.5 等で実施する場合の参考実装
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'inquiries'
                      AND column_name = 'assigned_user_id');
SET @sql := IF(@col_exists = 0,
               'ALTER TABLE inquiries ADD COLUMN assigned_user_id BIGINT NULL',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

H2（テスト環境）では `information_schema.columns` がサポートされるため同 SQL がそのまま動く。`PREPARE` / `EXECUTE` も H2 1.4.200 以降で互換。phase18.5 着手時に本番 MySQL バージョンを `SELECT VERSION();` で確認し、8.0.29 以降が確約されていれば素直な `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` に簡略化してもよい（その判断は実装担当者へ委譲）。

### 14.1.3 API レスポンス DTO の拡張余地

API レスポンス DTO（§5.3）は `assignedUserId` フィールドを追加してもクライアントを壊さないよう、フロントは「未知のフィールドを無視」する JSON パースを使うこと（Vue / React の標準実装は無視するため、既存コードのままで互換）。

---

これらは**任意拡張余地の予約**であり、本フェーズで `assigned_user_id` を実装することは意味しない。

---

# 15. レビューコメント対応サマリ

## r1 で新規対応（初版 → r1）
| ID | 優先度 | 対応 |
|----|--------|------|
| I-1 | 🔴 必須 | phase15 r5 から要請されていた `target_type` / `target_id` 多態参照を本書で正式定義。Service 層での所有者検証ルールも明記 |
| I-2 | 🔴 必須 | `sender_type` / `sender_id` で `market_customers` / `users` を分離。多態参照のため FK は張らず Service 層で整合性検証 |
| I-3 | 🔴 必須 | phase17 r6 の `console_notifications` / `notification_subscriptions`（`subscription_tag = 'inquiry_alerts'`）と統合。通知の発火点・level・payload_hash を明記 |
| I-4 | 🔴 必須 | ベルマーク件数の真実の元を `inquiries.status='NEW'` の COUNT に確定（`console_notifications` ではない）。30 秒ポーリング方式と `useVisibilityPolling` Composable による拡張性確保（r4 / IMP-6：実装は `InquiryBellBadge.vue`） |
| I-5 | 🟡 推奨 | 内部メモ（`is_internal_note`）を導入。元設計書「管理者コメント欄（任意）」の解釈を確定 |
| I-6 | 🟡 推奨 | ステータス遷移ルールを明文化（NEW / IN_PROGRESS / DONE 双方向許容、`config('inquiry.allowed_status_transitions')` で管理）|
| I-7 | 🟡 推奨 | 環境変数追加（5 個）と `coding_guidelines.md` §4-3 のチェックリスト適用 |
| I-8 | 🟡 推奨 | operation_logs の `screen_name` / `api_name` / `comment` プレフィックス規約を phase14 / phase15 / phase17 と整合 |
| I-9 | 🟡 推奨 | API 設計を Console / Market 別で確定。Market は IDOR 対策で Service 層 `WHERE user_id = :sessionUserId` 強制 |
| I-10 | 🟡 推奨 | 実装段取り（Step 0 → A → B）を明文化。Step 0 で env-vars チェックリストを片付ける流れ |
| I-11 | 🟢 任意 | 採用しなかった選択肢を明記（WebSocket / SSE / assigned_user_id / categories / attachments / 物理削除）|
| I-12 | 🟢 任意 | phase17 / phase19 への申し送り（`InquiryArchiveJob` / `InquiryStaleAlertJob` / 集約 Bell コンポーネント）|
| I-13 | 🟢 任意 | 添付ファイルを本フェーズスコープ外として明示。スキーマに痕跡を残さず、後続 `inquiry_attachments` テーブルとして拡張可能 |
| I-14 | 🟢 任意 | 命名規約（フォルダ / ファイル / 設定キー）を coding_guidelines §2 と整合。Java は by-domain、PHP は domain/Controller-Service/file 構成 |

## r2 で対応（[review_r1.md](phase18_inquiry_management_review_r1.md) RV-1 〜 RV-12）

| ID | 優先度 | 対応 |
|----|--------|------|
| RV-1 | 🔴 必須 | §6.1 通知発火点表に `title` / `body` テンプレート列を追加。`config('inquiry.notification_templates')` で管理し、`{user_name}` / `{target_label}` / `{old_status}` / `{new_status}` / `{subject}` / `{inquiry_id}` 等のプレースホルダを Service 層で埋め込む。`target_label` 組み立てルールも明記。phase17 §5.2 の `console_notifications.title` / `body` NOT NULL 制約と整合 |
| RV-2 | 🔴 必須 | `inquiry_replied` の `payload_hash` から `inquiry_messages.id` を除去し `SHA-256('inquiry_replied:' + inquiries.id)` に修正。60 分以内連投を 1 通に集約し、phase17 R-10 / J-5 の「同 payload は 60 分抑制 + ダイジェストで件数集計」の本来意図に整合させる。§11.1 のアサート式も合わせて修正 |
| RV-3 | 🔴 必須 | §13.2 を「phase17 r7 候補（§14.1）への追加要請」と明確に位置づけ直し（案 A 採用）。phase17 r6 §3.4 凍結の事実を踏まえ、`InquiryArchiveJob` / `InquiryStaleAlertJob` は phase17 r7 改訂で受理してもらう前提。本書実装はこの 2 Job に依存しないため単独完結する |
| RV-4 | 🟡 推奨 | §2.1.2 一覧画面のユーザー名カラムを `market_customers.last_name` / `first_name` から **実カラム名 `name_last` / `name_first`** に修正。Service 層で `display_name = name_last + ' ' + name_first` と組み立てる方針も明記 |
| RV-5 | 🟡 推奨 | `com.example.inquiry.validator.InquiryTargetOwnershipValidator` を §14 Java フォルダ構成に明記し、`target_type` ごとの所有者検証（delivery → sales JOIN / sales 直接 / product is_active）を 1 ファイルに集約。Step B の作業項目にも追加 |
| RV-6 | 🟡 推奨 | phase11 設計書 §3.1 / §4.4 で JWT 採用が明記されており、本書 §5.1 の「phase11 で確定済の JWT 認証」は妥当と確認。**記述変更なし**（レビュー時の前提確認結果を本サマリで明示） |
| RV-7 | 🟡 推奨 | §5.2 に phase13 §2.2 Behavior 表（優先度3 `/api/customer/*`）への根拠リンク追記、本フェーズで CloudFront 変更不要を**太字**で明示 |
| RV-8 | 🟡 推奨 | `useVisibilityPolling(fetchUrl, intervalMs)` Composable を §2.1.1 と §14 に追加。`resources/vue/composables/useVisibilityPolling.ts` 配置（規約 2-3 Shared）。phase19 集約 Bell の伏線 |
| RV-9 | 🟢 任意 | §5.2 に Market 側 DTO（`MarketCreateInquiryRequest` / `MarketReplyInquiryRequest`）を Console 側と分離し、`is_internal_note` フィールドを構造的に持たせない方針を明記。Mass Assignment 攻撃面を Controller 入口で塞ぐ（DB CHECK との二重防御）|
| RV-10 | 🟢 任意 | §10 Step 0 / Step A / Step B の完了条件に `docs/database_design/TBL_*.md` 新設・`README.md` / `ER_diagram.md` 更新・`ops/healthcheck/required_tables.txt` への `inquiries` / `inquiry_messages` 追記・`docs/api_design/Core_API.md` / `Console_API.md` / `Market_API.md` 更新を組込み（[CLAUDE.md §DB / API 設計書のメンテナンスルール](../../../CLAUDE.md) 整合・044 / phaseX-6 教訓の再発防止）|
| RV-11 | 🟢 任意 | §14.1「将来拡張余地」を新設。`assigned_user_id` 追加時の DTO（`InquiryMutationContext`）拡張余地・スキーマ追加マイグレーション・フロント JSON パース方針を 1 セクションに集約 |
| RV-12 | 🟢 任意 | r1 → r2 改訂履歴に RV-1 〜 RV-11 の取り込み内容を記載（phase15 / phase17 と同等粒度の改訂履歴形式を維持）|

## r3 で対応（[review_r1.md §r2 再レビュー](phase18_inquiry_management_review_r1.md) RV2-1 〜 RV2-5）

| ID | 優先度 | 対応 |
|----|--------|------|
| RV2-1 | 🔴 必須 | §2.2.4「投稿後の挙動」に r1 時代から残存していた古い `payload_hash=SHA-256('inquiry:'+inquiry.id)` 表記（§6.1 の r2 修正と矛盾していた）を削除し、`NotificationDispatcher.dispatch('inquiry_alerts', INFO, payload)` 呼び出し起点 + §6.1 への参照に集約。重複定義による drift を構造的に排除。`+ SES` の誤解を招く表記も「`level=INFO` のため SES メール送出は行われず、`console_notifications` への INSERT のみ実施」に書き換え |
| RV2-2 | 🟡 推奨 | §13.2 の「`R-17` / `R-18` 等の新規 ID」表記を「**新規 ID（phase17 r7 の prefix 規則に従う／`R-` / `N-` / `M-` / `K-` / `J-` は r2 〜 r6 で既出のため、phase17 著者の選定によりアルファベット繰上げ）**」へ修正。phase17 著者との協議オプションとして「phase17 §14.1 r7 候補リストへのプリエンプティブ申し送り」も併記 |
| RV2-3 | 🟢 任意 | §14.1.2 を新設し、`ALTER TABLE ADD COLUMN IF NOT EXISTS` を MySQL 8.0.29 未満互換の `information_schema.columns` チェック方式（PREPARE / EXECUTE 動的 SQL）へ書き換え。本プロジェクト本番 DB が `mysql:8.0` タグ運用で 8.0.29 以降の保証がない事実を明記。H2 互換性も担保 |
| RV2-4 | 🟢 任意 | §14.1.1 で拡張余地を `UpdateInquiryStatusService` のみから `ReplyInquiryService` 側にも展開。返信時の自動再割当フックの擬似コードも掲載。共通インターフェース `InquiryMutationContext` 抽出は本フェーズではユースケース別独立 DTO を保つ方針で見送り（規約 2-2 整合）|
| RV2-5 | 🟢 任意 | 改訂履歴の `r3` 行に「2026-05-07（同日中の連続改訂）」と注記。phase17 / phase15 で同日連続改訂の前例があり、表記を揃える方針を採用 |
