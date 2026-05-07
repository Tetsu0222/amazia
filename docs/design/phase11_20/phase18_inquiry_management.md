# フェーズ18：問い合わせ管理（改訂版 r2）

## ステータス
🔲 未着手（phase15 / phase17 完了後に着手予定）

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 初版日不明（git 履歴未取得） | 初稿（フェーズ18 問い合わせ管理の基本設計：inquiries / inquiry_messages の最小スキーマと画面要件のみ）|
| r1 | 2026-05-07 | 実装着手レベルへブラッシュアップ。(1) phase15 r5 から要請された `target_type` / `target_id` 多態参照を本書で正式定義（[phase15 §phase18 への要請事項](phase15_delivery_management.md) 対応）。(2) `sender_type` / `sender_id` で Market 顧客（`market_customers`）と Console 管理者（`users`）を分離。(3) phase17 r6 の `console_notifications` / `notification_subscriptions`（subscription_tag = `inquiry_alerts`）と通知経路を統合。ベルマークは 30 秒ポーリング方式（`/api/console/inquiries/unread-count`）で実装。(4) Console / Market の API・画面・operation_logs 規約・config 駆動・テスト観点まで明記し、規約 1-1 / 1-2 / 2-1 / 4-1 と完全整合。(5) 添付ファイルは本フェーズスコープ外として明示。 |
| r2 | 2026-05-07 | レビューコメント [phase18_inquiry_management_review_r1.md](phase18_inquiry_management_review_r1.md) RV-1 〜 RV-12 を反映。🔴必須：(RV-1) `console_notifications` の NOT NULL カラム漏れを補完し、§6.1 に `title` / `body` テンプレート列を追加（`config('inquiry.notification_templates')` 管理）。(RV-2) `inquiry_replied` の `payload_hash` から `inquiry_messages.id` を除去し `SHA-256('inquiry_replied:' + inquiries.id)` に修正（60 分以内連投を 1 通に集約／phase17 R-10 整合）。(RV-3) §13.2 を「phase17 r7 候補（§14.1）への追加要請」と明確に位置づけ直し。🟡推奨：(RV-4) `market_customers` 実カラム名を `name_last` / `name_first` に統一し、Service 層で `display_name` を組み立てる方針を明記。(RV-5) `InquiryTargetOwnershipValidator` を §14 Java フォルダ構成・§10 Step B に明記。(RV-6) Console 認証が JWT であることを phase11 §3.1 / §4.4 で確認済（記述維持）。(RV-7) §5.2 に phase13 §2.2 への根拠リンク追記＋「変更不要」明示。(RV-8) `useVisibilityPolling` Composable を §2.1.1 / §14 に追加。🟢任意：(RV-9) Market POST DTO から `is_internal_note` を除外する DTO 分離方針を §5.2 / §5.3 に明記。(RV-10) Step 0 / A の完了条件に `docs/database_design/` / `docs/api_design/` / `ops/healthcheck/required_tables.txt` 更新を組込み（[CLAUDE.md](../../../CLAUDE.md) 整合）。(RV-11) `assigned_user_id` 将来拡張余地を §14 に追記。 |

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
| 通知種別の拡張（ベルマークに「ワークフロー承認待ち」「在庫異常」等を集約） | ベルマーク UI 自体は本書で実装するが、表示元データソースは「`inquiries.status='NEW'` の件数」のみとする。集約構造は phase19（お知らせ）と合わせて将来検討。本書では `Bell.vue` コンポーネントが「件数取得 API URL」を props で受け取る形にしておき、後続フェーズが API を差し替えるだけで集約できる構造のみ確保 |
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
| `NotificationDispatcher.dispatch(...)` | phase17 §6.2 | 本フェーズの通知発火点から呼び出す |

phase15 / phase17 で残された **「phase18 が確定する責務」** は本書で全て確定する：
1. `inquiries.target_type` / `target_id` の正式定義（phase15 RR-7 の依存解決）
2. ベルマーク UI コンポーネントと未対応件数 API（元設計書「ベルマーク（通知アイコン）」）
3. 自動クローズの実施有無 → 本書ではスコープ外とし、phase17 オンデマンドバッチへ要請（§13）

---

# 2. 機能詳細

## 🖥 2.1 Amazia Console（管理画面）

### 2.1.1 ベルマーク（全画面ヘッダー共通）

- Console SPA の共通ヘッダー（既存 `Header.vue` ／ phase16 で導入済み）に `Bell.vue` コンポーネントを追加する。
- 表示位置：ヘッダー右側、ユーザーメニューの左隣（phase16 の UI 規約に従う）。
- バッジ：`inquiries.status = 'NEW'` の件数を 99 件まで数値表示、100 件以上は `99+` と表示。
- 件数取得は **30 秒間隔のポーリング**で `/api/console/inquiries/unread-count` を呼び出す。タブ非表示時（`document.hidden`）はポーリングを停止し、再表示時に即時 1 回 fetch して再開する（無料枠 / EC2 t3.micro 負荷削減）。
- クリック → 「問い合わせ一覧画面」へ遷移（`/inquiries`）。
- 未対応 0 件のときはバッジを非表示（アイコンのみ）。

#### ポーリング Composable の切り出し（RV-8 対応：r2 で追加）

タブ非表示停止・再表示時即時 fetch のロジックは `Bell.vue` 内に直接書かず、**Vue Composable `useVisibilityPolling(fetchUrl, intervalMs)` として共通化**する：

- 配置：`resources/vue/composables/useVisibilityPolling.ts`（規約 2-3 Shared 思想：複数ユースケースで使う・ドメイン非依存）
- シグネチャ：`useVisibilityPolling<T>(fetchUrl: string, intervalMs: number): { data: Ref<T | null>, error: Ref<Error | null> }`
- 内部で `document.visibilitychange` を購読し、非表示時は `clearInterval`、再表示時は即時 1 回 fetch + `setInterval` 再開
- `Bell.vue` は本 Composable を呼ぶだけのシン UI に保つ
- phase19（お知らせ）／後続フェーズの集約 Bell でも再利用可能（§13.3 申し送りの伏線）

#### 拡張性の確保（元設計書「今後、問い合わせ以外も表示できるよう」対応）
- `Bell.vue` は `props.fetchUrl: string` を受け取る。phase18 では `'/api/console/inquiries/unread-count'` を渡すが、後続フェーズで「全通知集約」エンドポイントが用意された段階で URL を差し替えるだけで済む構造にする。
- バッジ算出ロジックは `Bell.vue` 内に閉じ、API のレスポンス形式は `{ count: number }` 固定で受ける。

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

| action | target_type | target_id | comment 規約 |
|--------|-------------|-----------|-------------|
| `reply_inquiry` | `inquiries` | inquiries.id | `[admin_reply] message_id=N`（投稿された inquiry_messages.id）|
| `update_inquiry_status` | `inquiries` | inquiries.id | `[status_change] 旧:NEW → 新:IN_PROGRESS reason='...'`（reason は任意）|
| `add_internal_note` | `inquiries` | inquiries.id | `[internal_note] message_id=N` |

`screen_name` / `api_name` の規約（phase15 RR-10 / phase17 §8 と整合）：
- `screen_name`：`ConsoleInquiryListPage` / `ConsoleInquiryDetailPage`
- `api_name`：`POST /api/console/inquiries/{id}/messages` / `PATCH /api/console/inquiries/{id}/status` 等

`comment` プレフィックス（`[admin_reply]` / `[status_change]` / `[internal_note]`）は `config/app/Inquiry.php` の enum で管理（phase15 RRR-5 と同思想）。

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

#### 対象 ID の入力 UI（target_type 連動）

| `target_type` | 入力 UI | 検証 |
|--------------|---------|------|
| `delivery` | 顧客の購入履歴から `deliveries` をプルダウン選択（直近 3 ヶ月分）| `deliveries` JOIN `sales` で `sales.user_id = ログイン市場顧客` を強制 |
| `product` | 商品検索（既存 Market の商品一覧コンポーネント流用）| `products.id` の存在 + `is_active = TRUE`（phase16 Step 1）|
| `sales` | 顧客の購入履歴から `sales` をプルダウン選択 | `sales.user_id = ログイン市場顧客` を強制 |
| 空（汎用） | 入力欄なし | `target_id = NULL` |

種別の選択肢と表示名は `config/app/Inquiry.php` の `target_types` で定義（規約 3-1）。

#### バリデーション
- フレームワーク（Laravel FormRequest / Spring `@Valid`）の標準バリデーションを使用。
- 独自ルール（件名・本文の文字数、対象 ID と target_type の整合性）は `config('inquiry.validation')` に集約（規約 1-2）。

#### 投稿後の挙動
- `inquiries` を `status='NEW'` で INSERT。
- 同一トランザクションで初回メッセージを `inquiry_messages` に INSERT（`sender_type='market_customer'`, `is_internal_note=FALSE`）。
- 同一トランザクションで `console_notifications` に INSERT（`target_subscription_tag='inquiry_alerts'`, `level='INFO'`, `payload_hash=SHA-256('inquiry:'+inquiry.id)`）+ SES（INFO のためメール送出は §6 に従い行わない。WARN/ERROR ではないため `console_notifications` のみ）。
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

### CloudFront Behavior（phase13 と整合）
- `/api/customer/inquiries/*` は phase13 で追加済の `/api/customer/*` Behavior に乗る。CloudFront Behavior 追加は不要。

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

## 6.1 通知発火点

| イベント | subscription_tag | level | payload_hash 算出元 |
|---------|------------------|-------|--------------------|
| Market 顧客が新規問い合わせを作成 | `inquiry_alerts` | `INFO` | `SHA-256('inquiry_created:' + inquiries.id)` |
| Market 顧客が既存問い合わせに返信 | `inquiry_alerts` | `INFO` | `SHA-256('inquiry_replied:' + inquiries.id + ':' + inquiry_messages.id)` |
| Console 管理者がステータス変更 | `inquiry_alerts` | `INFO` | `SHA-256('inquiry_status:' + inquiries.id + ':' + new_status)` |

phase17 §6.2.2 の `NotificationDispatcher.dispatch(...)` を呼び出す。`level=INFO` のため SES メール送出は行われず、`console_notifications` への INSERT のみ実施される（phase17 §6.2.2 の「`level >= WARN` のみメール」ルールに従う）。

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

phase18 で追加する環境変数（[user memory: env_vars_and_tests] に従い、`docker-compose.yml` / `phpunit.xml` / `application-test.properties` をセット更新）：

| 変数名 | 用途 | 既定値 |
|--------|------|-------|
| `INQUIRY_SUBJECT_MAX_LENGTH` | 件名の最大文字数（バリデーション）| `100` |
| `INQUIRY_MESSAGE_MAX_LENGTH` | メッセージ本文の最大文字数 | `4000` |
| `INQUIRY_LIST_PAGE_SIZE_CONSOLE` | Console 一覧の 1 ページ件数 | `50` |
| `INQUIRY_LIST_PAGE_SIZE_MARKET` | Market 一覧の 1 ページ件数 | `20` |
| `INQUIRY_BELL_POLLING_INTERVAL_MS` | Console ベルマークのポーリング間隔（ms）| `30000` |

**Step 1 着手前の確認チェックリスト**（`coding_guidelines.md` §4-3 に従う）：

- [ ] `docker-compose.yml` の amazia-core サービスに全新規変数を追記
- [ ] `application-test.properties` にテスト用既定値を追記
- [ ] `phpunit.xml`（Console / Market）にテスト用既定値を追記
- [ ] `.env.example`（存在すれば）にも追加
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

`config/app/Inquiry.php`（PHP 側 / Console・Market 共通の Pass-through 用）と `application.yml` の `amazia.inquiry.*`（Spring 側 / Core）に以下を定義：

```yaml
amazia:
  inquiry:
    statuses: [NEW, IN_PROGRESS, DONE]
    allowed-status-transitions:
      NEW:         [IN_PROGRESS, DONE]
      IN_PROGRESS: [NEW, DONE]
      DONE:        [NEW, IN_PROGRESS]
    target-types: [delivery, product, sales]   # NULL は「汎用」を表す
    subject-max-length: ${INQUIRY_SUBJECT_MAX_LENGTH:100}
    message-max-length: ${INQUIRY_MESSAGE_MAX_LENGTH:4000}
    page-size-console: ${INQUIRY_LIST_PAGE_SIZE_CONSOLE:50}
    page-size-market:  ${INQUIRY_LIST_PAGE_SIZE_MARKET:20}
    notification-tag: inquiry_alerts
    operation-log-prefixes:
      admin-reply:    "[admin_reply]"
      customer-reply: "[customer_reply]"
      status-change:  "[status_change]"
      internal-note:  "[internal_note]"
```

PHP 側は `config/app.php` で明示読込：

```php
return [
    // 既存...
    'inquiry' => require __DIR__.'/app/Inquiry.php',
];
```

---

# 10. 実装段取り（Step 0 → Step A → Step B）

| Step | 対象 | 主な作業 | 完了条件 |
|------|------|---------|---------|
| **Step 0** | 前提整備 | (1) `application.yml` / `application-test.properties` / `phpunit.xml` / `docker-compose.yml` に新規環境変数追加。(2) `config/app/Inquiry.php` 新規作成。(3) `config/app.php` への明示読込追加。(4) `routes/api.php` への `Inquiry.php` 明示読込追加。 | `mvn test` / `php artisan test` / `npm run test`（Console / Market）が既存テスト含めて全緑 |
| **Step A** | DB スキーマ作成 | (1) `amazia-core/src/main/resources/schema.sql` に `inquiries` / `inquiry_messages` の `CREATE TABLE IF NOT EXISTS` を冪等追記。(2) `notification_subscriptions` への `inquiry_alerts` 自動購読 INSERT IGNORE。(3) Core 側 JPA Entity（`Inquiry` / `InquiryMessage`）と Repository を新規作成。 | 起動時に H2 / 本番 MySQL の双方でテーブルが作成され、エラーなく Spring Boot 起動。phase15 / phase17 の既存テストが緑のまま |
| **Step B** | 機能実装 | (1) Core: `CreateInquiryService` / `ListInquiryService` / `GetInquiryService` / `ReplyInquiryService` / `UpdateInquiryStatusService` / `GetUnreadInquiryCountService` を新規作成。`NotificationDispatcher.dispatch('inquiry_alerts', INFO, ...)` のフック埋め込み。(2) Console SPA: `Bell.vue` / `InquiryList.vue` / `InquiryDetail.vue` 新規。Header.vue にベルマーク埋め込み。(3) Market SPA: `MyPageInquiryList.tsx` / `MyPageInquiryDetail.tsx` / `MyPageInquiryNew.tsx` 新規。マイページメニューに「問い合わせ」追加。(4) Console / Market の Pass-through ルート追加。(5) operation_logs 記録の埋め込み。 | Market から問い合わせ作成 → Console ベルマーク件数増加 → 管理者が返信 → Market 側で返信表示、の end-to-end が本番 HTTPS 構成（CloudFront → EC2）で通る。Core / Console / Market 全テスト緑 |

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
- 通知 dispatch（`NotificationDispatcher`）がモック呼び出し検証で正しく走る（`payload_hash` が `SHA-256('inquiry_created:'+id)` と一致）

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

### バックエンド（Pass-through 層）
- `GET /api/console/inquiries/unread-count` が Core を呼び出して `{ count: N }` を返す
- `PATCH /api/console/inquiries/{id}/status` が `operation_logs` に `update_inquiry_status` を記録する
- `POST /api/console/inquiries/{id}/messages` が `operation_logs` に `reply_inquiry` を記録する
- 内部メモ投稿（`is_internal_note=TRUE`）が `operation_logs` に `add_internal_note` を記録する

### SPA（Vitest / vue-test-utils）
- ベルマークに未対応件数が正しく表示される（バッジ数値・99+ 表示）
- 未対応 0 件のときバッジが非表示
- 件名クリックでスレッド画面に遷移する
- 管理者がスレッド画面から返信できる
- ステータスドロップダウンで遷移できる（許容遷移のみが UI 上で選択可能）
- `target_type='delivery'` の問い合わせから対象配送ページへリンク遷移できる
- ポーリング間隔は `INQUIRY_BELL_POLLING_INTERVAL_MS` を `@Value` ／ `import.meta.env` 経由で取得していること（ハードコード禁止のリント or 値検証）
- タブ非表示時にポーリングが停止し、再表示で再開すること

## 11.3 Amazia Market / PHPUnit + Vitest

### バックエンド（Pass-through 層）
- 問い合わせ登録が正常に行える（`POST /api/customer/inquiries`）
- 自分の問い合わせ一覧のみ取得できる（他顧客の問い合わせが含まれない）
- スレッド形式でメッセージが時系列表示される
- ユーザーが返信できる（`POST /api/customer/inquiries/{id}/messages`）
- 内部メモ（`is_internal_note=TRUE`）が API レスポンスに含まれない
- ステータスが正しく反映される（管理者がステータス変更後、Market 側で表示が更新される）
- `target_type='delivery'` で自分の購入履歴外の `deliveries.id` を指定すると拒否される

### SPA（Vitest / React Testing Library）
- マイページから「問い合わせ」メニューに遷移できる
- 新規作成画面で件名 / 本文 / 対象種別 / 対象ID を入力して送信できる
- 対象種別の選択に応じて対象ID の入力 UI（プルダウン / 検索）が切り替わる
- 文字数上限超過でフロントバリデーションエラーが表示される

## 11.4 phase17 通知統合テスト（Core / JUnit）
- 問い合わせ作成時に `console_notifications` が `target_subscription_tag='inquiry_alerts'`, `level='INFO'` で 1 件 INSERT される
- 同一 `inquiries.id` への連続作成（テスト上は不可だが、シミュレートで同一 `payload_hash` を発行した場合）が 60 分以内なら `suppressed=TRUE` で抑制される
- `notification_subscriptions` に `subscription_tag='inquiry_alerts'` を登録した admin が SES メール宛先に含まれる（INFO のためメール自体は送られないが、購読者解決ロジックは走る）。WARN レベルへの一時昇格テストでメール経路が呼び出されることを確認

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

## 13.2 phase17（バッチ処理）への要請事項

| 項目 | 内容 |
|------|------|
| `notification_subscriptions` への `inquiry_alerts` 追加 | 本書 §3.3 のマイグレーションで自動投入する。phase17 側で `subscription_tag` の許容値リスト（あれば）に `inquiry_alerts` を追加 |
| 自動クローズ Job | DONE → 完了から N 日経過した問い合わせの物理削除ではなく、別フェーズで **アーカイブ Job**（`InquiryArchiveJob`）として phase17 §3.4 オンデマンドバッチに追加する。本書ではスコープ外 |
| 長期未対応の警告 | NEW のまま N 日経過の問い合わせを WARN レベルで通知する `InquiryStaleAlertJob` を、phase17 §3.1 日次バッチに追加することを後続フェーズで検討。本書ではスコープ外 |

## 13.3 phase19（お知らせ）への申し送り

- phase19 のヘッダー通知エリアと本フェーズの Console ベルマークは **別系統の UI**。両者を統合する集約 Bell コンポーネントは将来課題。
- ベルマーク件数 API URL を `Bell.vue` の `props.fetchUrl` で差し替え可能にしている（§2.1.1）ため、phase19 / phase20 で「全通知集約」エンドポイントが整備されたら URL を切替えるだけで集約できる構造になっている。

---

# 14. 命名規約（phase14 / phase15 / phase17 との整合）

- フォルダ構成（規約 2-1）：
  - PHP（Laravel）：`app/Inquiry/Controller/{ListInquiryController.php, GetInquiryController.php, CreateInquiryController.php, ReplyInquiryController.php, UpdateInquiryStatusController.php, GetUnreadInquiryCountController.php}` / `app/Inquiry/Service/...`
  - Java（Spring）：`com.example.inquiry.controller.*` / `com.example.inquiry.service.*` / `com.example.inquiry.entity.*` / `com.example.inquiry.repository.*` / `com.example.inquiry.validator.*`
  - Vue：`resources/vue/features/Inquiry/{api,pages}/*`
- ルートファイル（規約 2-1 補足4）：`routes/api/Inquiry.php`（明示読込）。
- config（規約 2-1 補足3）：`config/app/Inquiry.php`（`config/app.php` で明示読込）。
- ファイル命名はユースケース単位（規約 2-2）：1 ファイル = 1 ユースケース。

---

# 15. レビューコメント対応サマリ

## r1 で新規対応（初版 → r1）
| ID | 優先度 | 対応 |
|----|--------|------|
| I-1 | 🔴 必須 | phase15 r5 から要請されていた `target_type` / `target_id` 多態参照を本書で正式定義。Service 層での所有者検証ルールも明記 |
| I-2 | 🔴 必須 | `sender_type` / `sender_id` で `market_customers` / `users` を分離。多態参照のため FK は張らず Service 層で整合性検証 |
| I-3 | 🔴 必須 | phase17 r6 の `console_notifications` / `notification_subscriptions`（`subscription_tag = 'inquiry_alerts'`）と統合。通知の発火点・level・payload_hash を明記 |
| I-4 | 🔴 必須 | ベルマーク件数の真実の元を `inquiries.status='NEW'` の COUNT に確定（`console_notifications` ではない）。30 秒ポーリング方式と Bell.vue の拡張性確保 |
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
