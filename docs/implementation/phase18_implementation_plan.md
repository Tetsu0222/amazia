# フェーズ18 実装計画（問い合わせ管理）（r4）

## 概要
- 対象設計書: [phase18_inquiry_management.md](../design/phase11_20/phase18_inquiry_management.md)（**r4 / 2026-05-09**）
- 対象範囲: Amazia Core / Amazia Console / Amazia Market / DB 設計 / phase17 通知連携
- 実装ステータス: ✅ **実装完了（2026-05-09）**。本番デプロイ・実機 E2E はユーザー側で順次実施
- 段取り: 設計書 §10「Step 0 → Step A → Step B」を実装作業単位の **Step 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8** に分解
  - Step 0: 前提整備（環境変数・config・パッケージ確定）
  - Step 1: DB マイグレーション + Entity / Repository（設計書 Step A 相当）
  - Step 2: Core 共通基盤（`InquiryTargetOwnershipValidator` / DTO 群 / 例外定義）
  - Step 3: Core Service / Controller（CRUD + 通知発火）
  - Step 4: Console Pass-through + SPA（ベルマーク・一覧・スレッド）
  - Step 5: Market SPA（**React 直接 Core / r4 IMP-4：Pass-through 不在**）
  - Step 6: 通知統合の検証（phase17 `BatchAlertNotifier` との結線）
  - Step 7: E2E（Market → Console ベルマーク → Console 返信 → Market 表示）
  - Step 8: ドキュメント反映 + 本番デプロイ
- 作成日: 2026-05-08（r1 初版） / r4 改訂日: 2026-05-09
- 親フェーズ: [phase15_implementation_plan.md](phase15_implementation_plan.md)（phase15 完了済み）／ [phase17_implementation_plan.md](phase17_implementation_plan.md)（phase17 完了前提）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | Step 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 を厳守。Step を跨いだ部分実装は禁止。各 Step 末で `mvn test` / Console 側 `phpunit` + `vitest` / Market 側 `phpunit` + `vitest` の該当層が緑であることを完了条件とする |
| 規模感 | Core 2 テーブル新設（`inquiries` / `inquiry_messages`）+ `notification_subscriptions` への `inquiry_alerts` 追加投入、Service 約 6 本（`CreateInquiryService` / `ListInquiryService` / `GetInquiryService` / `ReplyInquiryService` / `UpdateInquiryStatusService` / `GetUnreadInquiryCountService`）、Validator 1 本（`InquiryTargetOwnershipValidator`）、Console 画面 2 種（一覧・詳細）+ ヘッダー Bell、Market 画面 3 種（一覧・詳細・新規作成）、Vue Composable 1 本（`useVisibilityPolling`）|
| TDD | 設計書 §11 の TDD ケースを Step ごとに割り当てて実装。E2E は Step 7 に集約 |
| コーディング規約 | [coding_guidelines.md](../coding_guidelines.md) 厳守（Service にロジック寄せ・config 駆動・1 ファイル 1 ユースケース・ドメイン単位パッケージ） |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` + `application.properties`（Core）+ `application-test.properties`（Core）+ Console `phpunit.xml` をセット更新（規約 4-3 ／ [user memory: env_vars_and_tests] / r4 IMP-4：Market 側 `phpunit.xml` は不存在のため対象外）。設計書 §7 のチェックリストを Step 0 着手前に必ず実行 |
| テスト値 | ハードコードせず `config()` / `@Value` 経由で取得（規約 4-1） |
| メモリ事項 | Core 側 Heap 制限（`-Xmx384m` ／ [user memory: phaseX4_t3micro_recovery]）を意識。問い合わせ件数は無料枠運用上少量想定だが、Console 一覧（50 件 / ページ）・Market 一覧（20 件 / ページ）でページング徹底。スレッド表示はメッセージ全件取得とするが、運用上 1 スレッドあたり数十件規模を想定し許容 |
| 多態参照 | `inquiries.target_id` / `inquiry_messages.sender_id` は **物理 FK を張らない**（多態のため）。整合性は **Service 層**（特に `InquiryTargetOwnershipValidator`）で `target_type` ごとに検証する。DB 側は CHECK 制約と pair NULL ガードで二重防御 |
| 認証 | Console は phase11 JWT（`Authorization: Bearer ...`）／ Market は phase13 `MARKET_SESSION_ID` Cookie + CSRF（`X-CSRF-Token`）。本フェーズで認証層の追加実装はゼロ |
| CloudFront | phase13 §2.2 で `/api/customer/*` Behavior 追加済み。**本フェーズで CloudFront 変更は不要**（[user memory: market_auth_api_routing] と整合）|
| 通知 | phase17 r6 の `BatchAlertNotifier.dispatch('INFO', 'inquiry_alerts', title, body, payloadIdentity, null, null)` 経由（r4 / IMP-3：phase17 実体クラス。実体の `payload_hash` は `SHA-256('inquiry_alerts:'+payloadIdentity)`）。`level=INFO` のため SES 送信はされず `console_notifications` への INSERT のみ。重複抑制（60 分 / `payload_hash` ベース）は phase17 §6.4 で既実装 |
| ベルマーク | 真実の元は `inquiries.status='NEW'` の COUNT（`console_notifications` ではない）。30 秒ポーリングで Console SPA → `/api/console/inquiries/unread-count`。タブ非表示時は `useVisibilityPolling` Composable がポーリング停止 |
| 添付ファイル | **本フェーズスコープ外**（設計書 §範囲）。スキーマに `attachments` 系カラムを持たせない |
| 担当者割当（`assigned_user_id`） | 本フェーズスコープ外。設計書 §14.1 で将来拡張余地のみ予約済み |

### 設計書からの「本フェーズのスコープ外」確認

| 項目 | 取り扱い |
|------|---------|
| `inquiry_attachments` テーブル | スコープ外。本フェーズではスキーマに痕跡を残さない |
| WebSocket / SSE による push 通知 | スコープ外。30 秒ポーリングで完結 |
| 問い合わせ優先度（`priority`） | YAGNI。`status` のみで運用 |
| 自動クローズ（DONE 自動遷移） | スコープ外。phase17 r7 候補 `InquiryArchiveJob` / `InquiryStaleAlertJob` への要請として申し送り（設計書 §13.2） |
| 通知種別の集約（在庫異常等を Bell に集約） | UI 構造のみ確保（r4 / IMP-6：`InquiryBellBadge.vue` + `useVisibilityPolling` Composable で fetcher 差し替え可能）。集約は phase19 / 後続フェーズへ |
| Market 側 未読バッジ | スコープ外。元設計に未記載 |

### 設計書 r3 改訂で本計画に反映する追加変更（I-1 〜 I-14 / RV-1 〜 RV-12 / RV2-1 〜 RV2-5）

| ID | 反映先 Step | 内容 |
|----|----------|------|
| I-1 / I-2 | Step 1 / Step 2 | `target_type` / `target_id` 多態参照と `sender_type` / `sender_id` 多態参照の正式定義。Service 層で整合性検証 |
| I-3 / RV-1 / RV-2 | Step 3 / Step 6 | phase17 `NotificationDispatcher` 統合。`title` / `body` テンプレートを `config('inquiry.notification_templates')` で管理し、`payload_hash` から `inquiry_messages.id` を除外（60 分連投を 1 通に集約） |
| I-4 / RV-8 | Step 4 | ベルマーク 30 秒ポーリング + `useVisibilityPolling` Composable。`useVisibilityPolling(fetcher, intervalMs)` の `fetcher` 引数で URL 差し替え可能（r4 / IMP-6：実装は `InquiryBellBadge.vue`、`App.vue` サイドバー組込） |
| I-5 | Step 1 / Step 3 | `inquiry_messages.is_internal_note` で内部メモを実装。Market API では DB CHECK + DTO 分離の二重防御 |
| I-6 | Step 2 / Step 3 | ステータス遷移ルールを `config('inquiry.allowed_status_transitions')` に enum 定義 |
| I-7 | Step 0 | 環境変数 5 個（subject 上限・message 上限・page-size × 2・bell ポーリング間隔）を 3+ 箇所セット更新 |
| I-8 | Step 4 / Step 5 | operation_logs 規約（`screen_name` / `api_name` / `comment` プレフィックス）を phase14 / phase15 / phase17 と整合 |
| I-9 / RV-9 | Step 3 / Step 5 | Market POST DTO（`MarketCreateInquiryRequest` / `MarketReplyInquiryRequest`）から `is_internal_note` を構造的に除外。Mass Assignment 攻撃対策 |
| I-10 / RV-10 | Step 1 / Step 8 | `docs/database_design/` / `docs/api_design/` / `ops/healthcheck/required_tables.txt` の同期更新 |
| I-11 / I-12 / I-13 | スコープ外確認 | 添付ファイル / WebSocket / `assigned_user_id` / カテゴリマスタ / 物理削除は不採用。`InquiryArchiveJob` / `InquiryStaleAlertJob` は phase17 r7 候補申し送り |
| I-14 | Step 0 | 命名規約（フォルダ / ファイル / 設定キー）を coding_guidelines §2 と整合 |
| RV-3 | スコープ外確認 | `InquiryArchiveJob` / `InquiryStaleAlertJob` は phase17 r7 候補追加要請として保留。本書実装は単独で完結（phase17 §6.2 `NotificationDispatcher` のみ依存）|
| RV-4 | Step 3 / Step 4 | `market_customers.name_last` / `name_first` を Service 層で `display_name` 組み立て |
| RV-5 | Step 2 | `com.example.inquiry.validator.InquiryTargetOwnershipValidator` を新設し `CreateInquiryService` から呼び出し |
| RV-6 | Step 4 | Console 認証は phase11 JWT で確定。記述変更なし |
| RV-7 | Step 5 | CloudFront Behavior 変更は **不要**（phase13 §2.2 既存） |
| RV-11 | Step 2 | `assigned_user_id` 拡張余地として `UpdateInquiryStatusService` / `ReplyInquiryService` の入力 DTO を Java Record / クラスで明示フィールドを持たせる |
| RV2-1 | Step 3 | §2.2.4 投稿後の挙動の古い `payload_hash` 表記は §6.1 に集約。`level=INFO` のため SES 送出なし |
| RV2-3 | スコープ外 | `assigned_user_id` 追加マイグレーションは将来。`information_schema.columns` チェック方式の参考実装は §14.1.2 にメモ |
| RV2-4 | Step 2 | `assigned_user_id` 拡張余地を `ReplyInquiryService` 側にも展開 |

---

## 1. Step 0 — 前提整備

### 1-1. 既存実装との整合性確認（着手前棚卸し）

#### 1-1-1. phase11 〜 phase17 既存資産（設計書 §1.1 と整合）

| 既存資産 | 場所 | 本フェーズでの利用 |
|---------|------|------------------|
| `users` / `roles` | phase11 / [User.java](../../amazia-core/src/main/java/com/example/auth/entity/User.java) | Console 管理者の参照先。`inquiry_messages.sender_type='admin_user'` のとき `sender_id = users.id` |
| `market_customers` | phase13 | Market 顧客の参照先。`inquiries.user_id = market_customers.id` ／ `sender_type='market_customer'` |
| `market_sessions` | phase13 | Market 認証セッション（`MARKET_SESSION_ID` Cookie） |
| `products` | phase8 | `target_type='product'` のとき |
| `sales` | phase14 | `target_type='sales'` のとき。`sales.user_id = market_customers.id` で所有者検証 |
| `deliveries` | phase15 | `target_type='delivery'` のとき。`deliveries → sales.user_id` で所有者検証 |
| `operation_logs` | phase14 | Console 操作の記録先（`screen_name` / `api_name` / `comment` プレフィックス規約に従う） |
| `notification_subscriptions` | phase17 | `subscription_tag = 'inquiry_alerts'` を新規投入 |
| `console_notifications` | phase17 | 通知センター。本フェーズの新規通知は `target_subscription_tag = 'inquiry_alerts'`、`source_job` は NULL |
| `NotificationDispatcher.dispatch(...)` | phase17 §6.2 | 本フェーズの通知発火点から呼び出す |

#### 1-1-2. Step 0 着手前の確定事項

| 論点 | 確定 |
|------|------|
| `inquiries.target_type` 許容値 | `delivery` / `product` / `sales` / NULL（汎用）の 4 種 |
| ステータス遷移ルール | NEW / IN_PROGRESS / DONE 双方向許容（巻き戻し含む）。`config('inquiry.allowed_status_transitions')` で enum 管理 |
| 通知 level | 全イベント `INFO`（SES 送出なし、`console_notifications` のみ INSERT） |
| 通知 `payload_hash` | 新規：`SHA-256('inquiry_created:'+id)` ／ 返信：`SHA-256('inquiry_replied:'+id)`（messages.id 除外）／ ステータス変更：`SHA-256('inquiry_status:'+id+':'+new_status)` |
| Bell ポーリング間隔 | 30 秒（`INQUIRY_BELL_POLLING_INTERVAL_MS=30000`） |
| Bell 件数 API | `GET /api/console/inquiries/unread-count` → `{ count: number }` |
| `is_internal_note` 入口防御 | DB CHECK（`is_internal_note=FALSE OR sender_type='admin_user'`）+ Market 側 DTO クラスから構造的に排除（二重防御） |
| `notification_subscriptions` 自動投入対象 | phase17 r6 §6.2.1 の既定方針に従い `roles.name='admin' AND active_flag=TRUE` の全ユーザに `inquiry_alerts` を `email_enabled=TRUE, in_app_enabled=TRUE` で `INSERT IGNORE` |

> phase17 で確定した自動購読対象ロールが `admin / senior_admin / eternal_advisor` の 3 種 CSV 駆動になっている事実（[phase17 §1-1-3](phase17_implementation_plan.md)）と整合させるため、本フェーズでも `BATCH_NOTIFICATIONS_AUTO_SUBSCRIBE_ROLES` 環境変数を流用してマイグレーション SQL の `WHERE` 条件を組み立てる。**Step 1 着手時に phase17 側の env が `application.properties` に存在することを確認**し、未存在なら phase17 側設定を改めて参照する形で結線する。

### 1-2. Step 0-1: パッケージ構成の確定（Core）

新規 Java パッケージ：

```
com.example.inquiry
├── entity
│   ├── Inquiry                  # inquiries テーブル
│   └── InquiryMessage           # inquiry_messages テーブル
├── repository
│   ├── InquiryRepository
│   └── InquiryMessageRepository
├── validator
│   └── InquiryTargetOwnershipValidator   # RV-5：target_type ごとの所有者検証を 1 ファイルに集約
├── service
│   ├── CreateInquiryService             # Market 顧客の新規作成
│   ├── ListInquiryService               # 一覧取得（Console / Market 両用、フィルタ Service で受け取る）
│   ├── GetInquiryService                # 詳細取得（メッセージ含む）
│   ├── ReplyInquiryService              # 返信投稿（Market / Console 両用、is_internal_note は Console のみ可）
│   ├── UpdateInquiryStatusService       # ステータス変更（Console のみ）
│   ├── GetUnreadInquiryCountService     # ベルマーク用 status='NEW' COUNT
│   └── notification
│       └── InquiryNotificationDispatcher # NotificationDispatcher 呼び出し + payload 組み立て + target_label 解決
├── controller
│   ├── ListInquiryController             # GET /api/console/inquiries
│   ├── GetInquiryController              # GET /api/console/inquiries/{id}
│   ├── ReplyInquiryController            # POST /api/console/inquiries/{id}/messages
│   ├── UpdateInquiryStatusController     # PATCH /api/console/inquiries/{id}/status
│   ├── GetUnreadInquiryCountController   # GET /api/console/inquiries/unread-count
│   ├── MarketListInquiryController       # GET /api/customer/inquiries
│   ├── MarketGetInquiryController        # GET /api/customer/inquiries/{id}
│   ├── MarketCreateInquiryController     # POST /api/customer/inquiries
│   └── MarketReplyInquiryController      # POST /api/customer/inquiries/{id}/messages
└── dto
    ├── InquiryListResponse              # 一覧レスポンス（Console / Market 共通）
    ├── InquiryDetailResponse            # 詳細レスポンス（messages 配列含む）
    ├── InquiryMessageResponse           # 個別メッセージ DTO
    ├── ConsoleReplyInquiryRequest       # is_internal_note フィールドあり
    ├── ConsoleUpdateInquiryStatusRequest # newStatus + reason
    ├── MarketCreateInquiryRequest       # RV-9：is_internal_note を持たない
    ├── MarketReplyInquiryRequest        # RV-9：is_internal_note を持たない
    ├── InquiryListFilter                # status / dateFrom / dateTo / userName / targetType（Service 入力）
    └── InquiryStatusMutationContext     # RV-11 拡張余地：将来 assignedUserId を追加可能な Java Record
```

既存資産は一切修正しない（`User` / `Role` / `Customer`（旧称 MarketCustomer / r4：実体クラス名は `Customer`） / `Sales` / `Delivery` / `Product` Entity 等）。phase17 `BatchAlertNotifier`（r4 / IMP-3：本書 r1 では `NotificationDispatcher` 仮称）も phase17 完成形をそのまま呼び出すのみ。

### 1-3. Step 0-2: パッケージ構成の確定（Console / r4 で実装に整合）

> **r4 実装注記**：`Bell.vue` → `InquiryBellBadge.vue`（IMP-6：サイドバー組込）／ `useVisibilityPolling.ts` → `.js`（IMP-6：TS 未導入）／ `InquiryStatusDropdown.vue` / `InquiryMessageBubble.vue` / `InternalNoteAccordion.vue` の細分化は最小機能優先で **`InquiryDetail.vue` 内に直接記述**（コンポーネント抽出は将来課題）／ Pass-through Service の operation_logs 記録は **削除**（IMP-5：Core 側で記録）。

```
amazia-console/app/Inquiry/
├── Controller/  GetUnreadInquiryCountController.php / ListInquiryController.php /
│                GetInquiryController.php / ReplyInquiryController.php /
│                UpdateInquiryStatusController.php
└── Service/     GetUnreadInquiryCountService.php / ListInquiryService.php /
                 GetInquiryService.php / ReplyInquiryService.php /
                 UpdateInquiryStatusService.php
                 # 各 Service は Pass-through（Core API を HTTP コール）。
                 # r4 / IMP-5：operation_logs は Core 側で記録するため、本層では書き込まない。

amazia-console/config/app/Inquiry.php  # config 駆動値（規約 2-1 補足3）
amazia-console/routes/api/Inquiry.php  # ルート明示読込（規約 2-1 補足4）

amazia-console/resources/vue/src/features/inquiry/
├── pages/
│   ├── InquiryList.vue           # 一覧画面（/inquiries）
│   └── InquiryDetail.vue         # スレッド画面（/inquiries/{id}・ステータス変更/返信/内部メモを内包）
└── api/
    └── inquiryApi.js             # axios ラッパー

amazia-console/resources/vue/src/components/inquiry/
└── InquiryBellBadge.vue          # r4 / IMP-6：サイドバーメニュー組込用バッジ

amazia-console/resources/vue/src/composables/
└── useVisibilityPolling.js       # RV-8 / r4：JS 実装（規約 2-3 Shared 思想）
```

### 1-4. Step 0-3: パッケージ構成の確定（Market / r4 で React 直接 Core に確定）

> **r4 実装注記（IMP-4）**：本書 r1 で計画していた Market Pass-through 層（`amazia-market/app/Inquiry/...`）は **実装環境に整合しない**。`amazia-market/` は React 19 + Vite + MUI の SPA 単独構成（`composer.json` / Laravel `app/` 配下なし）。実装は **React → Core 直接呼び出し**で完結（CloudFront `/api/customer/*` Behavior は phase13 §2.2 で構築済みのため追加変更不要）。`config/app/Inquiry.php` / `routes/api/Inquiry.php` も Market 側には作成しない。

```
amazia-market/src/features/inquiry/
├── pages/
│   ├── MyPageInquiryList.jsx        # /mypage/inquiries
│   ├── MyPageInquiryDetail.jsx      # /mypage/inquiries/{id}
│   └── MyPageInquiryNew.jsx         # /mypage/inquiries/new（r4 / IMP-8：対象 ID は数値入力で簡略化）
└── api/
    └── inquiry.js                   # axios ラッパー（withCredentials + CSRF interceptor）

amazia-market/src/App.jsx            # /mypage/inquiries 系 3 ルートを <ProtectedRoute> 配下で追加
amazia-market/src/features/customer/pages/MyPage.jsx  # 「問い合わせ」ボタン追加（マイページ動線）
```

### 1-5. 環境変数追加チェックリスト（設計書 §7）

Step 1 着手前に以下 5 個の新規環境変数を **4 箇所セット更新**（規約 4-3 ／ [user memory: env_vars_and_tests]）：

| # | 環境変数 | docker-compose.yml | application.properties (Core) | application-test.properties (Core) | phpunit.xml (Console) | phpunit.xml (Market) |
|---|---------|:-:|:-:|:-:|:-:|:-:|
| 1 | `INQUIRY_SUBJECT_MAX_LENGTH` | ✅ | ✅ | ✅ | ✅ | ✅ |
| 2 | `INQUIRY_MESSAGE_MAX_LENGTH` | ✅ | ✅ | ✅ | ✅ | ✅ |
| 3 | `INQUIRY_LIST_PAGE_SIZE_CONSOLE` | ✅ | ✅ | ✅ | ✅ | — |
| 4 | `INQUIRY_LIST_PAGE_SIZE_MARKET` | ✅ | ✅ | ✅ | — | ✅ |
| 5 | `INQUIRY_BELL_POLLING_INTERVAL_MS` | ✅ | — | — | ✅（Vitest 用 import.meta.env）| — |

> Vue 側のポーリング間隔は `import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS` 経由で取得する設計（**r4 / IMP-6**：Vite は `VITE_*` 名前空間を `import.meta.env` に自動マッピングするため、`vite.config.js` の `define` 経由のマッピングは不要。テストは別途 Vitest 導入時に対応）。

### 1-6. Step 0-4: config 駆動値の確定（規約 1-2 / 3-1 / r4 で実装に整合）

#### 1-6-1. Core 側 `application.properties`（r4 / IMP-1：yml ではなく properties）

```properties
amazia.inquiry.statuses=NEW,IN_PROGRESS,DONE
# 形式：旧:新1,新2;旧:新1,新2 ... の単一行 CSV（Service 側で Map<String,List<String>> にパース）
amazia.inquiry.allowed-status-transitions=NEW:IN_PROGRESS,DONE;IN_PROGRESS:NEW,DONE;DONE:NEW,IN_PROGRESS
amazia.inquiry.target-types=delivery,product,sales
amazia.inquiry.subject-max-length=${INQUIRY_SUBJECT_MAX_LENGTH:100}
amazia.inquiry.message-max-length=${INQUIRY_MESSAGE_MAX_LENGTH:4000}
amazia.inquiry.page-size-console=${INQUIRY_LIST_PAGE_SIZE_CONSOLE:50}
amazia.inquiry.page-size-market=${INQUIRY_LIST_PAGE_SIZE_MARKET:20}
amazia.inquiry.notification-tag=inquiry_alerts
# 通知テンプレート（r4 / IMP-2：Spring SpEL 衝突回避のため No. 表記）
amazia.inquiry.notification-templates.created.title=[問い合わせ] 新規 No.{inquiry_id} {subject}
amazia.inquiry.notification-templates.created.body={user_name} さんから新規問い合わせが登録されました。\n対象: {target_label}\n件名: {subject}
amazia.inquiry.notification-templates.replied.title=[問い合わせ] 返信 No.{inquiry_id}
amazia.inquiry.notification-templates.replied.body={user_name} さんが No.{inquiry_id} に返信しました。\n件名: {subject}
amazia.inquiry.notification-templates.status-changed.title=[問い合わせ] ステータス変更 No.{inquiry_id}
amazia.inquiry.notification-templates.status-changed.body=No.{inquiry_id} を {old_status} → {new_status} に変更しました。\n件名: {subject}
# target_label テンプレート
amazia.inquiry.target-labels.delivery=配送 No.{target_id}
amazia.inquiry.target-labels.product=商品 No.{target_id} ({product_name})
amazia.inquiry.target-labels.sales=注文 No.{target_id}
amazia.inquiry.target-labels.generic=（汎用）
# operation_logs.comment プレフィックス（r4 / IMP-5：Core 側で記録するためここに集約）
amazia.inquiry.operation-log-prefixes.admin-reply=[admin_reply]
amazia.inquiry.operation-log-prefixes.customer-reply=[customer_reply]
amazia.inquiry.operation-log-prefixes.status-change=[status_change]
amazia.inquiry.operation-log-prefixes.internal-note=[internal_note]
```

#### 1-6-2. Console `config/app/Inquiry.php`（r4 / IMP-4：Market 側は対象外）

Console PHP 側は同等の構造を `config/app/Inquiry.php` に持たせ、`config/app.php` に明示読込追加：

```php
return [
    // 既存...
    'inquiry' => require __DIR__ . '/app/Inquiry.php',
];
```

`config/app/Inquiry.php` の内容は Core 側 properties と同期させる（r4 / IMP-1）。Pass-through 層では `config('app.inquiry.*')` 形式で参照する（r4：既存 `config('app.delivery.*')` / `config('app.auth.*')` と整合）。本フェーズで参照する **サブセット**：
- `subject_max_length` / `message_max_length`：FormRequest バリデーション
- `target_types`：FormRequest の in:rule
- `statuses`：UpdateInquiryStatus の in:rule
- `operation_log_prefixes`：（**r4 / IMP-5**：本フェーズでは Console 側未使用。Core 側で記録するため。将来 Console 側でも記録する可能性に備えて値だけ持たせる）
- Vue 側で `INQUIRY_BELL_POLLING_INTERVAL_MS` を環境変数から取得（**r4 / IMP-6**：Vite 標準の `import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS` 経由）

### 1-7. Step 0-5: ルート明示読込

#### Console
```php
// amazia-console/routes/api.php
require __DIR__ . '/api/Inquiry.php';
```
```php
// amazia-console/routes/api/Inquiry.php
Route::middleware(['auth.jwt'])->group(function () {
    Route::get('/console/inquiries/unread-count', [GetUnreadInquiryCountController::class, '__invoke']);
    Route::get('/console/inquiries', [ListInquiryController::class, '__invoke']);
    Route::get('/console/inquiries/{id}', [GetInquiryController::class, '__invoke']);
    Route::patch('/console/inquiries/{id}/status', [UpdateInquiryStatusController::class, '__invoke']);
    Route::post('/console/inquiries/{id}/messages', [ReplyInquiryController::class, '__invoke']);
});
```

#### Market（r4 / IMP-4：Laravel ルート不存在）

> **r4 実装注記**：Market は Laravel 不在のため `routes/api/Inquiry.php` は作成しない。React コードから直接 Core の `/api/customer/inquiries/*` を叩く。Core 側で `MarketSessionAuthFilter` + `MarketCsrfFilter` が `MARKET_SESSION_ID` Cookie + `X-CSRF-Token` を検証する（既存 phase13 と同経路）。CSRF の `PROTECTED_PREFIX` は `/api/customer/`（[user memory: market_auth_api_routing] と整合）。

### 1-8. 完了条件（Step 0 / r4 で実装に整合）

- [x] パッケージ構成（Core / Console / Market）が確定（§1-2 / §1-3 / §1-4）
- [x] 環境変数 5 個 × 該当箇所すべてに反映済み（§1-5）
- [x] `application.properties` に `amazia.inquiry.*` 追加済み（§1-6-1 / r4 / IMP-1）
- [x] Console `config/app/Inquiry.php` と `config/app.php` 明示読込が追加済み（§1-6-2 / r4 / IMP-4：Market 側は対象外）
- [x] Console `routes/api/Inquiry.php` の明示読込（§1-7）。**Controller クラスは Step 4 で実装するため、ルート定義は Controller 実装と同タイミングで配置してよい**。Step 0 末では config / env / properties の 3 点のみ完了で OK
- [x] Step 0 時点で Core / Console / Market の既存テストが全緑（環境変数追加が既存テストを壊していないこと）

---

## 2. Step 1 — DB マイグレーション + Entity / Repository

### 2-1. schema.sql 追記（本番 MySQL 向け）

`amazia-core/src/main/resources/schema.sql` 末尾に「フェーズ18: 問い合わせ管理」セクションを追加。重複実行は `spring.sql.init.continue-on-error=true` で許容（[phase14 §D](../design/phase11_20/phase14_shipping.md) と同方針）。

#### 2-1-1. `inquiries`（設計書 §3.1）

```sql
-- ============================================================================
-- フェーズ18 Step 1-1: inquiries（問い合わせ親）
-- ============================================================================
CREATE TABLE IF NOT EXISTS inquiries (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    subject     VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    target_type VARCHAR(20)  NULL,
    target_id   BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    KEY idx_inquiries_status_updated_at      (status, updated_at),
    KEY idx_inquiries_user_id_updated_at     (user_id, updated_at),
    KEY idx_inquiries_target                 (target_type, target_id),
    CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES market_customers(id),
    CONSTRAINT chk_inquiries_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT chk_inquiries_target_type CHECK (target_type IN ('delivery', 'product', 'sales') OR target_type IS NULL),
    CONSTRAINT chk_inquiries_target_pair CHECK (
        (target_type IS NULL     AND target_id IS NULL)
        OR (target_type IS NOT NULL AND target_id IS NOT NULL)
    )
);
```

#### 2-1-2. `inquiry_messages`（設計書 §3.2）

```sql
-- ============================================================================
-- フェーズ18 Step 1-2: inquiry_messages（スレッドメッセージ）
-- ============================================================================
CREATE TABLE IF NOT EXISTS inquiry_messages (
    id               BIGINT  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inquiry_id       BIGINT  NOT NULL,
    sender_type      VARCHAR(20) NOT NULL,
    sender_id        BIGINT  NOT NULL,
    message          TEXT    NOT NULL,
    is_internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       DATETIME NOT NULL,
    KEY idx_inquiry_messages_inquiry_id_created_at (inquiry_id, created_at),
    CONSTRAINT fk_inquiry_messages_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id) ON DELETE CASCADE,
    CONSTRAINT chk_inquiry_messages_sender_type CHECK (sender_type IN ('market_customer', 'admin_user')),
    CONSTRAINT chk_inquiry_messages_internal_note_admin CHECK (is_internal_note = FALSE OR sender_type = 'admin_user')
);
```

#### 2-1-3. `notification_subscriptions` への `inquiry_alerts` 自動投入（設計書 §3.3）

```sql
-- ============================================================================
-- フェーズ18 Step 1-3: 既存 admin/senior_admin/eternal_advisor を inquiry_alerts に自動購読
-- phase17 §1-1-3 の自動購読対象ロール CSV と整合（active_flag=TRUE のみ）
-- ============================================================================
INSERT IGNORE INTO notification_subscriptions
    (user_id, subscription_tag, email_enabled, in_app_enabled, created_at, updated_at)
SELECT u.id, 'inquiry_alerts', TRUE, TRUE, NOW(), NOW()
FROM users u
JOIN roles r ON u.role_id = r.id
WHERE r.name IN ('admin', 'senior_admin', 'eternal_advisor')
  AND u.active_flag = TRUE;
```

> phase17 では env CSV 駆動だが、`schema.sql` 内では `@Value` を使えないため対象ロール名をリテラルで列挙する。phase17 の env を変更した場合は本 SQL も同期が必要（**Step 8 ドキュメント反映で明記**）。

### 2-2. JPA Entity 追加

#### 2-2-1. `Inquiry.java`

```java
@Entity
@Table(name = "inquiries")
public class Inquiry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;                  // market_customers.id

    @Column(nullable = false, length = 100)
    private String subject;

    @Column(nullable = false, length = 20)
    private String status;                // NEW / IN_PROGRESS / DONE

    @Column(name = "target_type", length = 20)
    private String targetType;            // delivery / product / sales / null

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = "NEW";
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // CHECK 制約の H2 反映（schema.sql の MySQL CHECK と二重防御）
    // ※ Hibernate @Check は H2 でも反映される（phase17 で前例あり）
}
```

`@Check` 注釈は H2 ではテーブル定義に反映されないが、設計書 §3.1 の CHECK 制約は schema.sql 側に既に書かれているため、H2 環境では `application-test.properties` の `spring.sql.init.schema-locations=` を空のまま（Entity から `ddl-auto=create-drop` で生成）にすると CHECK 制約がない状態となる。**Service 層のバリデーションで多重防御**することで対応する（DB CHECK は本番 MySQL での最後の砦）。

#### 2-2-2. `InquiryMessage.java`

```java
@Entity
@Table(name = "inquiry_messages")
public class InquiryMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;            // market_customer / admin_user

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_internal_note", nullable = false)
    private Boolean isInternalNote = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.isInternalNote == null) this.isInternalNote = false;
    }
}
```

### 2-3. Repository 追加

#### 2-3-1. `InquiryRepository`

```java
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // ベルマーク件数（§6.3 真実の元）
    long countByStatus(String status);

    // Console 一覧（status / dateFrom / dateTo / userName 部分一致 / target_type）
    @Query("SELECT i FROM Inquiry i " +
           " LEFT JOIN MarketCustomer mc ON mc.id = i.userId " +  // RV-4：name_last + name_first
           "WHERE (:status IS NULL OR i.status = :status) " +
           "  AND (:targetType IS NULL OR i.targetType = :targetType) " +
           "  AND (:dateFrom IS NULL OR i.createdAt >= :dateFrom) " +
           "  AND (:dateTo   IS NULL OR i.createdAt <  :dateTo) " +
           "  AND (:userNameLike IS NULL " +
           "    OR LOWER(CONCAT(mc.nameLast, ' ', mc.nameFirst)) LIKE LOWER(CONCAT('%', :userNameLike, '%'))) ")
    Page<Inquiry> searchForConsole(...);

    // Market 一覧（自分のみ強制）
    Page<Inquiry> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
}
```

> JPQL の `MarketCustomer` JOIN は phase13 で `MarketCustomer` Entity が定義されている前提（[user memory: market_auth_api_routing] 整合）。未定義なら native SQL に切り替える判断を Step 1 着手時に実施。

#### 2-3-2. `InquiryMessageRepository`

```java
public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {
    // スレッド表示（時系列順）
    List<InquiryMessage> findByInquiryIdOrderByCreatedAtAsc(Long inquiryId);

    // Market 用：内部メモ除外
    List<InquiryMessage> findByInquiryIdAndIsInternalNoteFalseOrderByCreatedAtAsc(Long inquiryId);
}
```

### 2-4. ドキュメント / ヘルスチェック同期（RV-10）

| 同期対象 | 内容 |
|----------|------|
| `Amazia/docs/database_design/TBL_inquiries.md` | 新設。テーブル定義表 + ER 上の位置 |
| `Amazia/docs/database_design/TBL_inquiry_messages.md` | 新設 |
| `Amazia/docs/database_design/README.md` | ファイル一覧へ追記 |
| `Amazia/docs/database_design/ER_diagram.md` | Mermaid 図に `inquiries` / `inquiry_messages` ノードと `market_customers` / `inquiries` リレーションを追加 |
| `Amazia/ops/healthcheck/required_tables.txt` | `inquiries` / `inquiry_messages` を追記（CD のヘルスチェック対象化／[CLAUDE.md §主要テーブル定数の同期](../../CLAUDE.md) 準拠／[user memory: post_deploy_schema_healthcheck] と整合） |

### 2-5. テスト

| 層 | 内容 |
|----|------|
| Core JUnit | `Inquiry` / `InquiryMessage` の `@PrePersist` / `@PreUpdate` 動作確認 |
| Repository テスト | `InquiryRepository.countByStatus("NEW")` / `searchForConsole(...)` 各フィルタ条件 / `findByUserIdOrderByUpdatedAtDesc` |
| Repository テスト | `InquiryMessageRepository.findByInquiryIdOrderByCreatedAtAsc` / `findByInquiryIdAndIsInternalNoteFalseOrderByCreatedAtAsc` |
| H2 互換 | `application-test.properties` で `schema-locations=` 空のまま、`ddl-auto=create-drop` で全 Entity 生成 |
| MySQL 互換 | Docker `docker compose down -v && docker compose up --build` で schema.sql 全体（既存 + Step 1 追加）が完走 |
| 冪等性 | Core 再起動後も `notification_subscriptions` の `inquiry_alerts` 行数が変わらないこと（`INSERT IGNORE` 検証） |

### 2-6. 完了条件（Step 1）

- [ ] `inquiries` / `inquiry_messages` が H2 / MySQL 双方で生成され、Repository 単体テストが緑
- [ ] `notification_subscriptions` への `inquiry_alerts` 投入が冪等
- [ ] `docs/database_design/TBL_inquiries.md` / `TBL_inquiry_messages.md` 新設、`README.md` / `ER_diagram.md` 更新済み
- [ ] `ops/healthcheck/required_tables.txt` に `inquiries` / `inquiry_messages` 追記済み
- [ ] Core 既存テストが全緑のまま（phase15 / phase17 含む）

---

## 3. Step 2 — Core 共通基盤（Validator / DTO / 例外）

### 3-1. `InquiryTargetOwnershipValidator`（RV-5 / 設計書 §14）

`com.example.inquiry.validator.InquiryTargetOwnershipValidator` を新設。`target_type` ごとの所有者検証ロジックを 1 ファイルに集約。

```java
@Component
public class InquiryTargetOwnershipValidator {

    @Autowired private DeliveryRepository deliveryRepo;
    @Autowired private SalesRepository salesRepo;
    @Autowired private ProductRepository productRepo;

    public void validate(String targetType, Long targetId, Long marketCustomerId) {
        if (targetType == null) {
            if (targetId != null) throw new IllegalArgumentException("target_id must be null when target_type is null");
            return;
        }
        if (targetId == null) throw new IllegalArgumentException("target_id is required when target_type is set");

        switch (targetType) {
            case "delivery" -> validateDelivery(targetId, marketCustomerId);
            case "sales"    -> validateSales(targetId, marketCustomerId);
            case "product"  -> validateProduct(targetId);
            default -> throw new IllegalArgumentException("unknown target_type: " + targetType);
        }
    }

    private void validateDelivery(Long deliveryId, Long marketCustomerId) {
        Delivery d = deliveryRepo.findById(deliveryId)
            .orElseThrow(() -> new EntityNotFoundException("delivery not found: " + deliveryId));
        // deliveries → sales.user_id で所有者検証（phase15 §phase18 への要請事項）
        Sales s = salesRepo.findById(d.getSalesId())
            .orElseThrow(() -> new EntityNotFoundException("sales not found for delivery: " + deliveryId));
        if (!Objects.equals(s.getUserId(), marketCustomerId)) {
            throw new ForbiddenAccessException("delivery does not belong to current customer");
        }
    }

    private void validateSales(Long salesId, Long marketCustomerId) {
        Sales s = salesRepo.findById(salesId)
            .orElseThrow(() -> new EntityNotFoundException("sales not found: " + salesId));
        if (!Objects.equals(s.getUserId(), marketCustomerId)) {
            throw new ForbiddenAccessException("sales does not belong to current customer");
        }
    }

    private void validateProduct(Long productId) {
        Product p = productRepo.findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("product not found: " + productId));
        if (!Boolean.TRUE.equals(p.getIsActive())) {  // phase16 Step 1 で導入された is_active
            throw new IllegalStateException("product is not active: " + productId);
        }
    }
}
```

### 3-2. DTO 群

#### 3-2-1. リクエスト DTO（RV-9 で Market POST DTO から `is_internal_note` を構造的に除外）

```java
// MarketCreateInquiryRequest（RV-9：is_internal_note 持たない）
public record MarketCreateInquiryRequest(
    @NotBlank @Size(max = 100) String subject,
    @NotBlank String message,
    String targetType,
    Long targetId
) {}

// MarketReplyInquiryRequest（RV-9：is_internal_note 持たない）
public record MarketReplyInquiryRequest(
    @NotBlank String message
) {}

// ConsoleReplyInquiryRequest（is_internal_note あり）
public record ConsoleReplyInquiryRequest(
    @NotBlank String message,
    Boolean isInternalNote     // null は false 扱い
) {}

// ConsoleUpdateInquiryStatusRequest
public record ConsoleUpdateInquiryStatusRequest(
    @NotBlank String newStatus,    // NEW / IN_PROGRESS / DONE
    String reason                  // 任意。operation_logs.comment に埋め込む
) {}
```

#### 3-2-2. RV-11 拡張余地：Service 入力 DTO（Java Record / クラスで明示フィールド）

```java
// 将来 assignedUserId を追加可能にする
public record InquiryStatusMutationContext(
    Long inquiryId,
    String newStatus,
    String reason,
    Long actingUserId
    // 将来：Long assignedUserId
) {}

public record ReplyInquiryRequest(
    Long inquiryId,
    String senderType,         // admin_user / market_customer
    Long senderId,
    String message,
    boolean isInternalNote
    // 将来：Long assignedUserId（未割当時の自動セット）
) {}
```

### 3-3. レスポンス DTO

```java
public record InquiryListResponse(
    Long id, Long userId, String userName, String subject, String status,
    String targetType, Long targetId, String targetLabel,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}

public record InquiryDetailResponse(
    Long id, Long userId, String userName, String subject, String status,
    String targetType, Long targetId, String targetLabel,
    LocalDateTime createdAt, LocalDateTime updatedAt,
    List<InquiryMessageResponse> messages
) {}

public record InquiryMessageResponse(
    Long id, String senderType, Long senderId, String senderName,
    String message, boolean isInternalNote, LocalDateTime createdAt
) {}
```

### 3-4. 例外定義

```java
public class IllegalInquiryStatusTransitionException extends RuntimeException { ... }   // 400
public class ForbiddenAccessException extends RuntimeException { ... }                  // 403
// EntityNotFoundException（既存 Spring 標準）→ 404
```

`GlobalExceptionHandler` に上記をマッピング追加（既存ハンドラに 1 〜 2 件追加するだけ）。

### 3-5. テスト

| ID | 内容 |
|----|------|
| OWNV-1 | `target_type=null` + `target_id=null` → OK |
| OWNV-2 | `target_type=delivery` + 自分の sales 配下 delivery → OK |
| OWNV-3 | `target_type=delivery` + **他人の** sales 配下 delivery → `ForbiddenAccessException` |
| OWNV-4 | `target_type=sales` + 他人の sales → `ForbiddenAccessException` |
| OWNV-5 | `target_type=product` + `is_active=true` → OK |
| OWNV-6 | `target_type=product` + `is_active=false` → `IllegalStateException` |
| OWNV-7 | `target_type=delivery` + `target_id=999999`（存在せず）→ `EntityNotFoundException` |
| OWNV-8 | `target_type=unknown_value` → `IllegalArgumentException` |
| DTO-1  | `MarketCreateInquiryRequest` クラスに `isInternalNote` フィールドが**存在しない**（リフレクションで確認） |

### 3-6. 完了条件（Step 2）

- [ ] `InquiryTargetOwnershipValidator` 単体テスト（OWNV-1 〜 OWNV-8）が緑
- [ ] DTO 群がコンパイル通過、`MarketCreateInquiryRequest` / `MarketReplyInquiryRequest` に `isInternalNote` がないことが構造的に保証される
- [ ] `IllegalInquiryStatusTransitionException` / `ForbiddenAccessException` が `GlobalExceptionHandler` で適切な HTTP コードに変換される

---

## 4. Step 3 — Core Service / Controller（CRUD + 通知発火）

### 4-1. `CreateInquiryService`（Market 顧客の新規作成）

```java
@Service
public class CreateInquiryService {

    @Autowired private InquiryRepository inquiryRepo;
    @Autowired private InquiryMessageRepository messageRepo;
    @Autowired private InquiryTargetOwnershipValidator targetValidator;
    @Autowired private InquiryNotificationDispatcher notificationDispatcher;

    @Value("${amazia.inquiry.subject-max-length}")
    private int subjectMaxLength;

    @Value("${amazia.inquiry.message-max-length}")
    private int messageMaxLength;

    @Transactional
    public Long create(MarketCreateInquiryRequest req, Long marketCustomerId) {
        // 1. バリデーション（DB CHECK と二重防御）
        if (req.subject().length() > subjectMaxLength)
            throw new IllegalArgumentException("subject exceeds max length");
        if (req.message().length() > messageMaxLength)
            throw new IllegalArgumentException("message exceeds max length");

        // 2. 所有者検証
        targetValidator.validate(req.targetType(), req.targetId(), marketCustomerId);

        // 3. inquiries INSERT
        Inquiry inquiry = new Inquiry();
        inquiry.setUserId(marketCustomerId);
        inquiry.setSubject(req.subject());
        inquiry.setStatus("NEW");
        inquiry.setTargetType(req.targetType());
        inquiry.setTargetId(req.targetId());
        inquiry = inquiryRepo.save(inquiry);

        // 4. 初回メッセージ INSERT（同一トランザクション）
        InquiryMessage msg = new InquiryMessage();
        msg.setInquiryId(inquiry.getId());
        msg.setSenderType("market_customer");
        msg.setSenderId(marketCustomerId);
        msg.setMessage(req.message());
        msg.setIsInternalNote(false);
        messageRepo.save(msg);

        // 5. 通知発火（同一トランザクション内・dispatcher 側で REQUIRES_NEW 制御）
        notificationDispatcher.dispatchCreated(inquiry);

        return inquiry.getId();
    }
}
```

### 4-2. `ListInquiryService` / `GetInquiryService`

- `ListInquiryService.listForConsole(InquiryListFilter, Pageable)` → `Page<InquiryListResponse>`
- `ListInquiryService.listForMarket(Long marketCustomerId, Pageable)` → `Page<InquiryListResponse>`（自分のみ強制）
- `GetInquiryService.getForConsole(Long id)` → `InquiryDetailResponse`（messages 全件、内部メモ含む）
- `GetInquiryService.getForMarket(Long id, Long marketCustomerId)` → `InquiryDetailResponse`
  - 所有者チェック：`inquiry.userId != marketCustomerId` なら `ForbiddenAccessException`
  - メッセージは `findByInquiryIdAndIsInternalNoteFalseOrderByCreatedAtAsc` を使う

`userName` の組み立てルール（RV-4）：

```java
String displayName(MarketCustomer mc) {
    return mc.getNameLast() + " " + mc.getNameFirst();
}
```

`targetLabel` の組み立てルール（設計書 §6.1）は `InquiryNotificationDispatcher` と同じヘルパー（`InquiryTargetLabelResolver`）に集約し、Service / Dispatcher 双方から呼び出す。

### 4-3. `ReplyInquiryService`（Market / Console 両用）

```java
@Service
public class ReplyInquiryService {

    @Autowired private InquiryRepository inquiryRepo;
    @Autowired private InquiryMessageRepository messageRepo;
    @Autowired private InquiryNotificationDispatcher notificationDispatcher;

    @Transactional
    public Long reply(ReplyInquiryRequest req) {
        Inquiry inquiry = inquiryRepo.findById(req.inquiryId())
            .orElseThrow(() -> new EntityNotFoundException("inquiry not found"));

        // Market 顧客は自分のものだけ
        if ("market_customer".equals(req.senderType())
            && !Objects.equals(inquiry.getUserId(), req.senderId())) {
            throw new ForbiddenAccessException("inquiry does not belong to current customer");
        }

        // is_internal_note は admin のみ（DTO 分離 + ここで再確認）
        boolean isInternalNote = req.isInternalNote();
        if (isInternalNote && !"admin_user".equals(req.senderType())) {
            throw new IllegalArgumentException("only admin can post internal note");
        }

        // メッセージ INSERT
        InquiryMessage msg = new InquiryMessage();
        msg.setInquiryId(req.inquiryId());
        msg.setSenderType(req.senderType());
        msg.setSenderId(req.senderId());
        msg.setMessage(req.message());
        msg.setIsInternalNote(isInternalNote);
        messageRepo.save(msg);

        // inquiries.updated_at 更新（@PreUpdate に依存しないよう明示）
        inquiry.setUpdatedAt(LocalDateTime.now());
        inquiryRepo.save(inquiry);

        // 通知（顧客返信時のみ。管理者返信時は status 不変・通知なし）
        // ※ 管理者返信は内部メモを除き顧客にメール通知する将来案もあるが、本フェーズでは未実装
        if ("market_customer".equals(req.senderType()) && !isInternalNote) {
            notificationDispatcher.dispatchReplied(inquiry);
        }

        return msg.getId();
    }
}
```

### 4-4. `UpdateInquiryStatusService`（Console のみ）

```java
@Service
public class UpdateInquiryStatusService {

    @Autowired private InquiryRepository inquiryRepo;
    @Autowired private InquiryNotificationDispatcher notificationDispatcher;

    @Value("#{${amazia.inquiry.allowed-status-transitions}}")
    private Map<String, List<String>> allowedTransitions;

    @Transactional
    public void update(InquiryStatusMutationContext ctx) {
        Inquiry inquiry = inquiryRepo.findById(ctx.inquiryId())
            .orElseThrow(() -> new EntityNotFoundException("inquiry not found"));

        String oldStatus = inquiry.getStatus();
        String newStatus = ctx.newStatus();

        if (Objects.equals(oldStatus, newStatus)) return;  // 同値遷移は no-op

        List<String> allowed = allowedTransitions.getOrDefault(oldStatus, List.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalInquiryStatusTransitionException(
                "transition not allowed: " + oldStatus + " -> " + newStatus);
        }

        inquiry.setStatus(newStatus);
        inquiry.setUpdatedAt(LocalDateTime.now());
        inquiryRepo.save(inquiry);

        notificationDispatcher.dispatchStatusChanged(inquiry, oldStatus, newStatus);
    }
}
```

### 4-5. `GetUnreadInquiryCountService`（ベルマーク用）

```java
@Service
public class GetUnreadInquiryCountService {
    @Autowired private InquiryRepository inquiryRepo;

    public long count() {
        return inquiryRepo.countByStatus("NEW");  // 設計書 §6.3 真実の元
    }
}
```

### 4-6. `InquiryNotificationDispatcher`（phase17 統合 / r4 で実装に整合）

> **r4 実装注記（IMP-3）**：phase17 の実体クラスは仮称 `NotificationDispatcher` ではなく `com.example.notification.service.BatchAlertNotifier` で、シグネチャは `dispatch(level, subscriptionTag, title, body, payloadIdentity, sourceJob, sourceBatchExecutionId)` と引数分解されている（`NotificationPayload` builder ではない）。`payload_hash` は `BatchAlertNotifier.buildPayloadHash(subscriptionTag, payloadIdentity, sourceJob)` 内で `SHA-256(subscriptionTag + ':' + payloadIdentity)` として算出される。本書では phase17 実装を変えず、phase18 側は `payloadIdentity` のみを組み立てる方針。

実装の正本は [InquiryNotificationDispatcher.java](../../amazia-core/src/main/java/com/example/inquiry/service/notification/InquiryNotificationDispatcher.java)。要旨：

```java
@Component
public class InquiryNotificationDispatcher {

    private static final String LEVEL_INFO = "INFO";

    private final BatchAlertNotifier batchAlertNotifier;        // phase17 実体クラス
    private final InquiryTargetLabelResolver targetLabelResolver;
    private final CustomerRepository customerRepository;        // 実体名は Customer

    @Value("${amazia.inquiry.notification-tag}")  private String tag;   // inquiry_alerts
    @Value("${amazia.inquiry.notification-templates.created.title}")  private String createdTitleTpl;
    @Value("${amazia.inquiry.notification-templates.created.body}")   private String createdBodyTpl;
    // 同様に replied / status-changed のテンプレート

    public void dispatchCreated(Inquiry inquiry) {
        Map<String, String> vars = baseVars(inquiry);
        String title = render(createdTitleTpl, vars);
        String body  = render(createdBodyTpl, vars);
        String payloadIdentity = "inquiry_created:" + inquiry.getId();
        // 実体の payload_hash = SHA-256("inquiry_alerts:inquiry_created:" + id)
        batchAlertNotifier.dispatch(LEVEL_INFO, tag, title, body, payloadIdentity, null, null);
    }

    public void dispatchReplied(Inquiry inquiry) {
        // payloadIdentity = "inquiry_replied:" + inquiry.getId()（messages.id 含めない／RV-2）
        // 実体の payload_hash = SHA-256("inquiry_alerts:inquiry_replied:" + id)
    }

    public void dispatchStatusChanged(Inquiry inquiry, String oldStatus, String newStatus) {
        // payloadIdentity = "inquiry_status:" + inquiry.getId() + ":" + newStatus
        // 実体の payload_hash = SHA-256("inquiry_alerts:inquiry_status:" + id + ":" + new_status)
    }
}
```

`InquiryTargetLabelResolver` は `target_type` ごとの DB 参照（`product` の場合のみ `products.name` を SELECT）を伴うため、Service 層に置く（`com.example.inquiry.service.InquiryTargetLabelResolver`）。

### 4-7. Controller 層（Console / Market 両セット）

#### Console（JWT 認証）

```java
@RestController
@RequestMapping("/api/console/inquiries")
public class GetUnreadInquiryCountController {
    @Autowired private GetUnreadInquiryCountService service;

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> handle() {
        return ResponseEntity.ok(Map.of("count", service.count()));
    }
}

// ListInquiryController / GetInquiryController / ReplyInquiryController / UpdateInquiryStatusController
// すべて 1 ファイル 1 ユースケース（規約 2-2）
```

#### Market（`MARKET_SESSION_ID` Cookie 認証）

```java
@RestController
@RequestMapping("/api/customer/inquiries")
public class MarketCreateInquiryController {
    @Autowired private CreateInquiryService service;

    @PostMapping
    public ResponseEntity<Map<String, Long>> handle(
            @Valid @RequestBody MarketCreateInquiryRequest req,
            @AuthenticationPrincipal MarketCustomerPrincipal principal) {
        Long inquiryId = service.create(req, principal.getCustomerId());
        return ResponseEntity.ok(Map.of("id", inquiryId));
    }
}

// MarketListInquiryController / MarketGetInquiryController / MarketReplyInquiryController
```

`MarketCustomerPrincipal` は phase13 で定義済み（`MARKET_SESSION_ID` Cookie 経由でセッションから `market_customers.id` を取得）。本フェーズでは追加実装しない。

### 4-8. テスト（設計書 §11.1 準拠 / r4 で実装に整合 / payload_hash 表記更新）

> **r4 実装注記（IMP-3）**：本書 r1 のテストケース内 `payload_hash = SHA-256('inquiry_created:'+id)` 等の表記は phase17 `BatchAlertNotifier.buildPayloadHash` の挙動により実体は `SHA-256('inquiry_alerts:inquiry_created:'+id)` （`subscriptionTag` プレフィクス付き）。実装では `InquiryServiceTest.SUP1` 等で `sha256("inquiry_alerts:inquiry_replied:" + id)` を直接計算して照合済み。

#### 正常系
- CRT-1: `CreateInquiryService` で inquiries + 初回 inquiry_messages が同一トランザクションで INSERT
- CRT-2: 通知 dispatch で 1 件 INSERT（実体の `payload_hash = SHA-256('inquiry_alerts:inquiry_created:'+id)`）
- LIST-1: Console 一覧で status / dateFrom / dateTo / userName / targetType フィルタが動作
- LIST-2: Market 一覧で他顧客の問い合わせが含まれない（`WHERE user_id = :sessionUserId` 強制）
- GET-1: Console 詳細でメッセージが時系列順、内部メモ含む
- GET-2: Market 詳細で内部メモが API レスポンスから除外される
- REP-1: `ReplyInquiryService` の Market 顧客返信で実体 `payload_hash = SHA-256('inquiry_alerts:inquiry_replied:'+id)` で通知
- REP-2: 管理者返信は通知発火しない（status 不変・通知なし方針）
- UPD-1: NEW → IN_PROGRESS の遷移が成功し `updated_at` 更新 + 実体 `payload_hash = SHA-256('inquiry_alerts:inquiry_status:'+id+':IN_PROGRESS')`
- UPD-2: DONE → NEW の巻き戻しが許容される
- CNT-1: `GetUnreadInquiryCountService.count()` が `inquiries.status='NEW'` の COUNT を返す
- TPL-1: `console_notifications.title` / `body` が config テンプレート展開済みで NOT NULL INSERT（RV-1）
- SUP-1: 同一 `payload_hash` の 60 分以内連投が `console_notifications.suppressed=TRUE` で抑制される（RV-2 整合 / phase17 §6.4 結線）
- OPLOG-1〜OPLOG-4（**r4 / IMP-5 で追加**）：管理者返信 / 内部メモ / 顧客返信記録なし / ステータス変更 reason 付きの operation_logs 記録検証

#### 異常系（規約 4-2 / r4 で例外名を実装に整合）
- ERR-1: 不正ステータス遷移（例：DONE → DONE 以外で許容されない組合せ）→ `IllegalInquiryStatusTransitionException`（HTTP 400）
- ERR-2: 存在しない `inquiries.id` → `InquiryNotFoundException`（r4：既存 `EntityNotFoundException` との切り分けのため新設 / HTTP 404）
- ERR-3: 他人の inquiry を Market API で取得 → `ForbiddenInquiryAccessException`（r4：既存 `ForbiddenAccessException` との衝突回避のため新設 / HTTP 403）
- ERR-4: `target_type='delivery'` + `target_id=NULL` → Service 層で `InquiryValidationException`（r4：HTTP 400 / DB CHECK と二重防御）
- ERR-5: `target_type='delivery'` + 他顧客の delivery → `InquiryTargetOwnershipValidator` が `ForbiddenInquiryAccessException`
- ERR-6: Market 顧客が `is_internal_note=TRUE` を渡そうとしても、DTO に該当フィールドがないため設定されない（DTO-1 で構造的に保証済み）。仮に Service 層直接呼出で `senderType='market_customer'` + `isInternalNote=true` のケースは Service 層で `InquiryValidationException`
- ERR-7: 件名 / 本文の文字数上限超過 → FormRequest / `@Valid` 標準バリデーション or Service 層 `InquiryValidationException`（`@Value("${amazia.inquiry.subject-max-length}")` 経由）

#### config / 環境変数経由化テスト（規約 4-1）
- CFG-1: `subject-max-length` を `application-test.properties` で `5` に上書き → 6 文字以上で `InquiryValidationException`
- CFG-2: `message-max-length` を上書き → 同様

### 4-9. 完了条件（Step 3 / r4 で実装に整合）

- [x] Service 6 本（Create / List / Get / Reply / UpdateStatus / GetUnreadCount）+ Validator + NotificationDispatcher + TargetLabelResolver の単体テストが緑（実装結果：`InquiryServiceTest` 25/25 緑）
- [x] Controller 9 本（Console 5 + Market 4）コンパイル通過（**r4 注記**：`@WebMvcTest` Slice テストは Step 6 / Step 7 の Service 層 + MockMvc 統合検証で代替。実装結果：`InquiryE2EFlowTest` 3/3 緑）
- [x] 通知発火検証（CRT-2 / REP-1 / UPD-1 / TPL-1 / SUP-1）が緑
- [x] 異常系 7 件すべて緑
- [x] phase15 / phase17 の既存テストが緑のまま（実装結果：phase17 通知関連 13/13 緑）
- [x] **`docs/api_design/Core_API.md` に `/api/console/inquiries/*` 系・`/api/customer/inquiries/*` 系を追記**（RV-10）

---

## 5. Step 4 — Console Pass-through + SPA

### 5-1. Console Pass-through 層（Laravel / r4 で実装に整合）

> **r4 実装注記（IMP-5）**：本書 r1 では Console Pass-through 層で `operation_logs` を直接書き込む案だったが、Console 側に operation_logs テーブルへのアクセス基盤（Model / 接続）が存在せず、既存 phase14 / 15 / 17 では **Core 側 Service が `OperationLogRepository.save(...)` で書き込む**運用が踏襲されている。実装は既存方針に揃え、**Core 側 `ReplyInquiryService` / `UpdateInquiryStatusService` が記録**する。Console Pass-through Service / Controller は Core を呼ぶだけのシン層に保つ。

各 Controller は Core API を HTTP コール → そのままレスポンス転送するだけ（operation_logs 記録は Core 側で完結）：

```php
// app/Inquiry/Service/ReplyInquiryService.php
class ReplyInquiryService {
    private string $baseUrl;
    public function __construct() { $this->baseUrl = config('services.amazia_core.base_url'); }

    public function reply(?string $userId, int $inquiryId, array $payload): Response {
        // Core 呼出のみ（X-User-Id ヘッダで管理者 ID を Pass-through）
        // operation_logs は Core 側で書く（phase14 / 15 / 17 と整合）
        return Http::withHeaders($userId ? ['X-User-Id' => $userId] : [])
            ->post("{$this->baseUrl}/console/inquiries/{$inquiryId}/messages", $payload);
    }
}
```

operation_logs の記録は **Core 側**（参照：[ReplyInquiryService.java](../../amazia-core/src/main/java/com/example/inquiry/service/ReplyInquiryService.java) / [UpdateInquiryStatusService.java](../../amazia-core/src/main/java/com/example/inquiry/service/UpdateInquiryStatusService.java)）：

| Service | operation_logs.action | comment 規約 |
|---------|------------------------|--------------|
| `ReplyInquiryService`（通常返信）| `reply_inquiry` | `[admin_reply] message_id=N` |
| `ReplyInquiryService`（内部メモ）| `add_internal_note` | `[internal_note] message_id=N` |
| `UpdateInquiryStatusService` | `update_inquiry_status` | `[status_change] 旧:NEW → 新:IN_PROGRESS reason='...'` |
| `ReplyInquiryService`（顧客返信）| ─（記録対象外） | ─ |

### 5-2. Console SPA（Vue / r4 で実装に整合）

> **r4 実装注記（IMP-6）**：本書 r1 では「Header.vue に `Bell.vue` を組込」案だったが、Console SPA に独立した `Header.vue` は存在せず、`App.vue` がサイドバー（`a-layout-sider`）でナビゲーションを担う構成だった。実装は **`App.vue` のサイドバーメニュー項目「問い合わせ」に Ant Design Vue の `<a-badge>` バッジを組込**する形にした。コンポーネント名は `Bell.vue` ではなく `InquiryBellBadge.vue`、TS ではなく JS で実装（Console は TS 未導入）。元設計の「30 秒ポーリング」「タブ非表示時停止」「未対応 0 件で非表示」の意図は完全に維持。

#### 5-2-1. `InquiryBellBadge.vue`（サイドバーメニュー組込用バッジ）

実装の正本は [InquiryBellBadge.vue](../../amazia-console/resources/vue/src/components/inquiry/InquiryBellBadge.vue)。要旨：

```vue
<template>
  <a-badge :count="displayCount" :overflow-count="99" :offset="[10, 0]" :show-zero="false">
    <span>問い合わせ</span>
  </a-badge>
</template>

<script setup>
import { computed } from 'vue';
import { useVisibilityPolling } from '../../composables/useVisibilityPolling.js';
import { getUnreadInquiryCount } from '../../features/inquiry/api/inquiryApi.js';

const intervalMs = Number(import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS) || 30000;

const fetcher = async () => (await getUnreadInquiryCount()).data;
const { data } = useVisibilityPolling(fetcher, intervalMs);
const displayCount = computed(() => data.value?.count ?? 0);
</script>
```

- `App.vue` サイドバー（`a-layout-sider`）での組込：
  ```vue
  <a-menu-item key="/inquiries">
    <InquiryBellBadge />
  </a-menu-item>
  ```

#### 5-2-2. `useVisibilityPolling.js`（RV-8 / 規約 2-3 Shared / r4：JS で実装）

実装の正本は [useVisibilityPolling.js](../../amazia-console/resources/vue/src/composables/useVisibilityPolling.js)。要旨：

```js
import { ref, onMounted, onUnmounted } from 'vue';

// fetcher: () => Promise<T> （URL 文字列ではなく fetcher 関数を受ける構造に変更 / r4）
// axios クライアント / interceptor を経由するため、fetch 直接呼出ではなく fetcher を渡す
export function useVisibilityPolling(fetcher, intervalMs) {
  const data = ref(null);
  const error = ref(null);
  let timer = null;

  const fetchOnce = async () => {
    try { data.value = await fetcher(); error.value = null; }
    catch (e) { error.value = e; }
  };

  const start = () => { if (timer) return; fetchOnce(); timer = setInterval(fetchOnce, intervalMs); };
  const stop  = () => { if (timer) { clearInterval(timer); timer = null; } };

  const onVisibilityChange = () => {
    if (document.hidden) stop();
    else { fetchOnce(); start(); }
  };

  onMounted(() => { start(); document.addEventListener('visibilitychange', onVisibilityChange); });
  onUnmounted(() => { stop(); document.removeEventListener('visibilitychange', onVisibilityChange); });

  return { data, error };
}
```

#### 5-2-3. `InquiryList.vue`

- フィルタ：status / dateFrom / dateTo / userName / targetType（設計書 §2.1.2）
- ソート：updated_at DESC / ASC 切替
- ページング：50 件 / ページ
- 一覧の `target_type` 表示は config の `target-labels` ベース

#### 5-2-4. `InquiryDetail.vue`

- 上部：件名 / ユーザー名 / `InquiryStatusDropdown` / 対象（クリックで `/deliveries/{target_id}` 等へ遷移）
- 中央：`InquiryMessageBubble`（顧客左 / 管理者右）の時系列リスト
- 下部：返信 textarea + 「返信送信」ボタン
- 最下部：`InternalNoteAccordion`（折りたたみ式・管理者間共有）

#### 5-2-5. `InquiryStatusDropdown.vue`

- 現在の status から `config('inquiry.allowed_status_transitions')` を参照して許容遷移先のみを option 化
- 同値遷移は表示しない

### 5-3. テスト（設計書 §11.2）

#### バックエンド（Pass-through 層 / PHPUnit / r4 で実装に整合）

**r4 / IMP-5**：本書 r1 では Pass-through 層で operation_logs を記録する案だったが、実装は Core 側で記録する方針に変更（既存 phase14 / 15 / 17 と整合）。Pass-through テスト（`Tests\Feature\Inquiry\InquiryProxyTest`）は **中継挙動・X-User-Id ヘッダ透過・クエリ透過・Console FormRequest バリデーション**のみを検証する：

- PT-CSL-1: `GET /api/console/inquiries/unread-count` が Core を呼出し `{ count: N }` を返す
- PT-CSL-2: `GET /api/console/inquiries` のクエリ（status / targetType / userName / page / size）が Core に透過される
- PT-CSL-3: `GET /api/console/inquiries/{id}` の 404 が透過される
- PT-CSL-4: `POST /api/console/inquiries/{id}/messages`（通常返信）で `X-User-Id` ヘッダが Core に透過される（operation_logs 記録は Core 側 `ReplyInquiryService` で完結）
- PT-CSL-5: 内部メモ投稿の payload（`isInternalNote: true`）が透過される
- PT-CSL-6: `PATCH /api/console/inquiries/{id}/status` が中継される
- PT-CSL-7: 未知ステータス・文字数上限超過は Console FormRequest で 422 を返す
- 全 9 ケース緑（実装結果）

#### SPA（Vue / r4：Vitest 未導入のためスコープ外）

> **r4 実装注記（IMP-7）**：`amazia-console/resources/vue/package.json` に Vitest が登録されておらず、phase18 単独でテストフレームワーク導入を行うのは規模超過。本書 r1 の SPA-CSL-1〜7 は **`npm run build` 緑による構文・依存解決確認**で代替し、UI 動作は Step 6（通知統合検証）/ Step 7（end-to-end）の手動確認で担保する。Vitest 導入は phaseX-N（テスト基盤整備）として別フェーズ申し送り候補。

代替する手動 E2E 確認項目（従来の SPA-CSL-1〜7 相当）：
- ベルマーク（サイドバーメニューの `<a-badge>`）に件数が正しく表示される（バッジ数値・99+）
- 未対応 0 件でバッジ非表示
- 件名クリックでスレッド画面に遷移
- ステータスドロップダウンで許容遷移のみが option として出る
- `target_type='delivery'` の問い合わせから対象配送ページへリンク遷移
- `useVisibilityPolling` のタブ非表示で `clearInterval`、再表示で即時 fetch + setInterval 再開（DevTools の Network タブで確認）
- ポーリング間隔が `import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS` 経由（ハードコード禁止）

### 5-4. 完了条件（Step 4 / r4 で実装に整合）

- [x] Pass-through 層 PHPUnit が緑（PT-CSL-1〜7 相当 / 実装結果：`InquiryProxyTest` 9/9 緑）
- [x] **r4 / IMP-7**：SPA Vitest はスコープ外。`npm run build` 緑で代替（実装結果：ビルド緑）
- [x] **r4 / IMP-6**：`App.vue` サイドバーにベルマーク組込済み、ローカル起動で動作確認
- [x] **`docs/api_design/Console_API.md` に Pass-through 経路を追記**（RV-10）

---

## 6. Step 5 — Market SPA（React 直接 Core / r4 で実装に整合）

> **r4 実装注記（IMP-4）**：本書 r1 では Market Pass-through 層（`amazia-market/app/Inquiry/...`）を計画していたが、`amazia-market/` は React 19 + Vite + MUI の SPA 単独構成（Laravel 不在）。実装は **React → Core 直接呼び出し**で完結（CloudFront `/api/customer/*` Behavior は phase13 §2.2 で構築済みのため追加変更不要）。本節タイトルを「Market Pass-through + SPA」から「Market SPA（React 直接 Core）」に修正。

### 6-1. Market Pass-through 層（不存在 / r4 / IMP-4）

> **削除**：本書 r1 の §6-1 Pass-through Service コード例は **対象不存在のため不要**。実装は Market 側 React コードから Core を直接叩く。

代わりに React 側 axios クライアントが `MARKET_SESSION_ID` Cookie + `X-CSRF-Token` ヘッダで Core を呼び出す（既存 `features/orders/api/salesReturn.js` と同パターン）：

```js
// amazia-market/src/features/inquiry/api/inquiry.js
import axios from 'axios';
import { getCsrfToken } from '../../customer/api/customer';

const client = axios.create({ baseURL: '/api/customer', withCredentials: true });
client.interceptors.request.use((config) => {
  const method = (config.method ?? 'get').toUpperCase();
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    const token = getCsrfToken();
    if (token) {
      config.headers ??= {};
      config.headers['X-CSRF-Token'] = token;
    }
  }
  return config;
});

export const listMyInquiries = (params = {}) => client.get('/inquiries', { params }).then(r => r.data);
export const getMyInquiry = (id) => client.get(`/inquiries/${id}`).then(r => r.data);
export const createInquiry = (payload) => client.post('/inquiries', payload).then(r => r.data);
export const replyMyInquiry = (id, message) => client.post(`/inquiries/${id}/messages`, { message }).then(r => r.data);
```

### 6-2. Market SPA（React / r4 で実装に整合）

> **r4 実装注記（IMP-4）**：拡張子は `.tsx` ではなく `.jsx`（Market は TS 未導入）。

#### 6-2-1. `MyPageInquiryList.jsx`

- ページング 20 件（実装は Core の Page サイズに従う）
- 列：件名 / ステータス / 最終更新
- マイページ（`/mypage`）の「問い合わせ」ボタンから遷移

#### 6-2-2. `MyPageInquiryDetail.jsx`

- 上部：件名 + ステータス（読み取り専用：顧客は変更不可）
- 中央：メッセージ吹き出し時系列（内部メモは API レスポンスから除外済）
- 下部：返信 textarea（status='DONE' のときは非表示）

#### 6-2-3. `MyPageInquiryNew.jsx`（r4 / IMP-8 で簡略化）

> **r4 実装注記（IMP-8）**：本書 r1 で計画していた `TargetTypeSelector.tsx`（target_type 連動の動的セレクタ：deliveries / sales 直近 3 ヶ月プルダウン、商品検索コンポーネント流用）は **最小機能優先で対象 ID の数値入力**に簡略化。理由：(a) 顧客が ID を確認する手段はマイページの注文・配送履歴に既に存在、(b) サーバ側 `InquiryTargetOwnershipValidator` が IDOR を完全に塞ぐため不正 ID は 403 で弾かれる、(c) 動的セレクタの実装コストに比べ UX 上の効果がフェーズ18 段階では限定的。動的セレクタは phase19 / 後続で UI のみ差し替え可能（Service 層は無変更）。

入力 UI（実装結果）：
- 件名 / 本文（フロントバリデーション：100 / 4000 文字。送信ボタンの disabled で表現）
- 対象種別プルダウン：`空（指定なし）` / `sales`（注文について）/ `delivery`（配送について）/ `product`（商品について）
- 対象 ID 入力欄（`type="number"`）：種別を選択した場合のみ表示
- submit 後は `/mypage/inquiries/{id}` へリダイレクト（投稿完了画面ではなくスレッド画面）

### 6-3. テスト（設計書 §11.3 / r4 で実装に整合）

> **r4 実装注記（IMP-4）**：本書 r1 の Pass-through 層テスト（PT-MKT-*）は **対象不存在のため不要**。Market は React 直接 Core で、E2E 経路の検証は **Core 側 MockMvc 統合テスト**（`InquiryE2EFlowTest`）で担う（PT-MKT-1〜7 相当をすべてカバー）。Vitest は React コンポーネント単独テストに集中。

#### Core MockMvc E2E（Step 7 で実装 / `InquiryE2EFlowTest`）
- E2E_Market作成_Console一覧_返信_ステータス変更のフルフローが緑（11 ステップ通し検証）
- E2E_他人のinquiryへMarketアクセスは403（IDOR 拒否 / PT-MKT-7 相当）
- E2E_session無し_Market_API_は401
- 全 3 ケース緑（実装結果）

#### Market SPA（Vitest + React Testing Library / `src/features/inquiry/pages/*.test.jsx`）
- 一覧表示・空状態・エラー表示
- マイページから「問い合わせ」ボタンに遷移できる
- 新規作成画面で件名 / 本文 / 対象種別 / 対象 ID を入力して送信できる
- 対象種別の選択に応じて対象 ID 入力欄が出現する（r4 / IMP-8：プルダウンではなく数値入力）
- 文字数上限超過で送信ボタンが無効化される
- 全 6 ケース緑（実装結果）

### 6-4. 完了条件（Step 5 / r4 で実装に整合）

- [x] **r4 / IMP-4**：Market Pass-through 層は不存在のため対象外。代わりに Core MockMvc E2E（`InquiryE2EFlowTest`）3/3 緑
- [x] SPA Vitest が緑（実装結果：Market 6/6 緑）
- [x] Market マイページから問い合わせ作成 → 一覧 → スレッド表示の flow がローカルで動作（実装結果：`npm run build` 緑、Vitest 緑）
- [x] **`docs/api_design/Market_API.md` に React 側呼び出しを追記**（RV-10）

---

## 7. Step 6 — 通知統合の検証（phase17 結線確認 / r4 で実装に整合）

> **r4 実装注記（IMP-3）**：本書 r1 の `payload_hash` 算出式は phase17 `BatchAlertNotifier.buildPayloadHash` の挙動により実体は `SHA-256('inquiry_alerts:'+payloadIdentity)` 形式（`subscriptionTag` プレフィクス付き）。下表を実装に合わせて修正。

### 7-1. 検証項目（設計書 §11.4 / r4 で実装に整合）

| ID | 検証 | 期待結果（r4 / IMP-3：実体の payload_hash） |
|----|------|----------|
| INT-1 | Market 顧客が新規作成 | `console_notifications` が `target_subscription_tag='inquiry_alerts'`, `level='INFO'`, `payload_hash=SHA-256('inquiry_alerts:inquiry_created:'+id)` で 1 件 INSERT |
| INT-2 | 同一 inquiry に短時間で 2 回返信 | 60 分以内なら 2 件目が `suppressed=TRUE` で抑制（実装結果：`InquiryServiceTest.SUP1` 緑） |
| INT-3 | `notification_subscriptions.subscription_tag='inquiry_alerts'` の admin が購読者解決される | INFO のためメール自体は送られないが、`findBySubscriptionTagAndEmailEnabledTrue('inquiry_alerts')` が動作（実装結果：`InquiryNotificationIntegrationTest.INT_TAG` 緑） |
| INT-4 | Market 顧客が返信 | `console_notifications` が `payload_hash=SHA-256('inquiry_alerts:inquiry_replied:'+id)` で INSERT（実装結果：`InquiryServiceTest.REP1` 緑） |
| INT-5 | Console 管理者がステータス変更 | `console_notifications` が `payload_hash=SHA-256('inquiry_alerts:inquiry_status:'+id+':'+new_status)` で INSERT（実装結果：`InquiryServiceTest.UPD1` / `InquiryNotificationIntegrationTest.INT_status_変更_2回は_new_status_違いで_payload_hash_が異なる` 緑） |
| INT-6 | Bell ポーリングで `inquiries.status='NEW'` の COUNT が増減反映 | 30 秒以内（タブ非表示時はスキップ）。**手動 E2E 検証**項目（automated 化はスコープ外） |

### 7-2. 実施手順（r4 で automated 化 + 手動の切り分け明示）

INT-1 〜 INT-5 は **automated test で緑**（`InquiryServiceTest` / `InquiryNotificationIntegrationTest` / `InquiryE2EFlowTest`）。INT-6 のみ実機 SPA 動作確認が必要。

1. **automated（PC ローカルで実施）**：`mvn -B test -Dtest="InquiryServiceTest,InquiryNotificationIntegrationTest,InquiryE2EFlowTest"` で INT-1〜INT-5 が緑になることを確認
2. **手動 E2E（INT-6 / docker-compose）**：`docker compose up --build` で Core / Console / MySQL を起動 → Market（`amazia-market` で `npm run dev`）でログインして問い合わせ作成 → Console（`amazia-console` の Vue）にログインしてサイドバーの「問い合わせ」バッジが `1` に増えることを確認（30 秒以内）→ Console から返信 → Market 側でリロード → 返信表示確認 → ステータスを NEW → IN_PROGRESS → DONE → NEW と一巡させ動作確認 → タブ非表示時にポーリングが停止することを DevTools の Network タブで確認

### 7-3. 完了条件（Step 6 / r4 で実装に整合）

- [x] INT-1 〜 INT-5 が automated test で緑（実装結果：`InquiryServiceTest` 25/25 + `InquiryNotificationIntegrationTest` 4/4 + `InquiryE2EFlowTest` 3/3）
- [ ] INT-6（Bell ポーリング 30 秒反映 / タブ可視性連動）の手動検証 ⚙ ユーザー側で実施
- [x] phase17 の既存テスト（`BatchAlertNotifierTest` / `ConsoleNotificationRepositoryTest` 等）が緑のまま（実装結果：13/13 緑）
- [x] `console_notifications.title` / `body` が config テンプレート展開済みで NOT NULL 保存されている（`InquiryNotificationIntegrationTest.INT_console_notifications_の_title_と_body_は_NOT_NULL_で展開済み` 緑）

---

## 8. Step 7 — E2E（end-to-end 動作確認）

### 8-1. シナリオ

設計書 §10 Step B 完了条件と同等：

> Market から問い合わせ作成 → Console ベルマーク件数増加 → 管理者が返信 → Market 側で返信表示、の end-to-end が **本番 HTTPS 構成（CloudFront → EC2）**で通る

### 8-2. 手順

1. ローカル `docker-compose.yml`（マルチコンテナ）で完走 → Core / Console / Market 全テスト緑
2. ステージング相当環境（EC2）にデプロイ：
   - schema.sql 反映を確認（`SHOW TABLES LIKE 'inquiries';` / `'inquiry_messages';`）
   - `notification_subscriptions` への `inquiry_alerts` 投入を確認（`SELECT COUNT(*) FROM notification_subscriptions WHERE subscription_tag='inquiry_alerts';`）
   - `ops/healthcheck/required_tables.txt` に新規 2 テーブルが含まれ、phaseX-6 の post-deploy ヘルスチェック（[user memory: post_deploy_schema_healthcheck]）でグリーンになる
3. 本番 HTTPS（CloudFront → EC2 / [user memory: phase11_https_policy]）で end-to-end フロー：
   - Market マイページから新規問い合わせ作成（`target_type='delivery'` を 1 件）
   - Console ヘッダーで Bell バッジが `1` 表示（30 秒以内）
   - Console 一覧で件名クリック → スレッド画面
   - 返信投稿
   - Market 側でリロード → 返信表示
   - Console でステータス NEW → DONE
   - Market 側で「完了」表示確認
4. 失敗時のロールバック手順：phase15 / phase17 と同様、`console_notifications` への INSERT は冪等抑制が効くためロールバック不要。`inquiries` / `inquiry_messages` の DROP は Step 8 のドキュメントに「事故時の手動 DROP 手順」として明記しておく（実際には実行しない）。

### 8-3. 完了条件（Step 7 / r4 で実装に整合）

- [x] **r4 追加**：Core MockMvc E2E（`InquiryE2EFlowTest`）3/3 緑で end-to-end の実装正当性を担保
- [ ] ローカル `docker compose down -v && docker compose up --build` で Core / Console / MySQL 起動完走 ⚙ ユーザー側で実施
- [ ] EC2 デプロイ後 `required_tables.txt` ヘルスチェック緑 ⚙ ユーザー側で実施
- [ ] 本番 HTTPS で end-to-end フロー完走 ⚙ ユーザー側で実施

---

## 9. Step 8 — ドキュメント反映 + 本番デプロイ

### 9-1. ドキュメント同期チェック（RV-10 / [user memory: design_doc_completion_checklist]）

| 同期対象 | 反映内容 |
|----------|----------|
| `docs/database_design/TBL_inquiries.md` | Step 1 で新設 |
| `docs/database_design/TBL_inquiry_messages.md` | Step 1 で新設 |
| `docs/database_design/README.md` | ファイル一覧へ 2 行追記 |
| `docs/database_design/ER_diagram.md` | Mermaid 図に inquiries / inquiry_messages とリレーション追加 |
| `ops/healthcheck/required_tables.txt` | `inquiries` / `inquiry_messages` 追記 |
| `docs/api_design/Core_API.md` | `/api/console/inquiries/*` 系 + `/api/customer/inquiries/*` 系の入出力 DTO・認証要件・例外コード |
| `docs/api_design/Console_API.md` | Pass-through 経路（admin / user 両方が叩ける） |
| `docs/api_design/Market_API.md` | React 側呼び出し API 一覧 |
| `docs/architecture.md` 等 | 該当があれば「`InquiryBellBadge.vue` + `useVisibilityPolling`」の記述追加（r4 / IMP-6 / [user memory: project_phase20_scope] のスコープ外確認：必要に応じて） |

### 9-2. 本番デプロイ

1. EC2 へ Core デプロイ（`mvn package` → `scp` → 再起動）
2. Console / Market デプロイ（CloudFront 配下の S3 / EC2 へ）
3. デプロイ後ヘルスチェック（phaseX-6）：
   - `required_tables.txt` チェッカーが緑
   - `/api/console/inquiries/unread-count` が `{ count: 0 }` を返す（初期は 0 件）
   - `notification_subscriptions` の `inquiry_alerts` 行数 = `(admin + senior_admin + eternal_advisor で active_flag=TRUE) のユーザ数`

### 9-3. 完了条件（Step 8 / フェーズ18 完了の定義）

- [ ] DB 設計書同期：`TBL_inquiries.md` / `TBL_inquiry_messages.md` 新設・`README.md` / `ER_diagram.md` 更新
- [ ] API 設計書同期：`Core_API.md` / `Console_API.md` / `Market_API.md` 更新
- [ ] ヘルスチェック同期：`ops/healthcheck/required_tables.txt` 更新
- [ ] 本番 EC2 で end-to-end フロー完走（Step 7-2 手順 3）
- [ ] phase17 r7 候補申し送り：`InquiryArchiveJob` / `InquiryStaleAlertJob` の追加要請を phase17 §14.1 r7 候補リストに記載（[user memory: feedback_trouble_doc_consolidation] と整合：別フェーズ起こしではなく phase17 r7 への追記で対応）
- [ ] phase15 / phase17 への要請事項が解消されたことを各設計書に追記（phase15 RR-7 解決完了・phase17 §6.2 統合完了）

---

## 10. リスク / 留意点

### 10-1. メモリ事項（[user memory: phaseX4_t3micro_recovery]）

t3.micro + `-Xmx384m` 構成のため：
- `InquiryRepository.searchForConsole` は必ず `Pageable` で発行（全件 List 化禁止）
- `InquiryMessageRepository.findByInquiryIdOrderByCreatedAtAsc` は 1 スレッドあたりメッセージ全件取得だが、運用想定は 1 スレッド数十件規模で許容。**スレッド長が 100 件超になる兆候**が出たら別フェーズで pagination を検討（YAGNI）
- ベルマーク件数 SQL は `idx_inquiries_status_updated_at` で `WHERE status='NEW'` の COUNT が O(log n)

### 10-2. 無料枠完走方針（[user memory: free_tier_first]）

- WebSocket / SSE 不採用（30 秒ポーリング維持）
- `console_notifications` への INSERT は phase17 既存の重複抑制（60 分・`payload_hash`）が効くため、嵐になっても 1 通に集約される
- SES 送出は `level=INFO` で行わない（SES 月 62,000 通の枠を圧迫しない）

### 10-3. phase17 への依存

本フェーズは **phase17 r6 完了が前提**：
- `notification_subscriptions` テーブル存在
- `console_notifications` テーブル存在
- `NotificationDispatcher.dispatch(...)` Bean 存在
- 重複抑制（60 分・`payload_hash`）動作

phase17 が r6 完了済みであることを Step 0 着手前に確認する（既存 phase17 実装計画書のステータス確認）。

### 10-4. phase15 への依存

`inquiries.target_type='delivery'` の所有者検証は `deliveries → sales.user_id` で行う。`Delivery.salesId` / `Sales.userId` が phase15 で確定していることを Step 2 着手前に確認する。

### 10-5. 既存 `Customer` Entity の `name_last` / `name_first` カラム名（RV-4 / r4 で実体に整合）

> **r4 注記**：実体クラス名は `MarketCustomer` ではなく **`Customer`**（パッケージ：`com.example.market.customer.entity.Customer`）。本書 r1 の `MarketCustomer` 表記は仮称。

設計書 r2 で実カラム名を `name_last` / `name_first` に確定済み。`Customer.java` Entity にもこのフィールド名で `nameLast` / `nameFirst` として定義されている（実装結果で確認済み）。Step 1 着手時に Entity を grep で確認：
```bash
grep -E "nameLast|nameFirst|first_name|last_name" amazia-core/src/main/java/com/example/market/customer/entity/Customer.java
```

### 10-6. RV-3 phase17 r7 候補申し送り（r4 で完了）

`InquiryArchiveJob`（DONE 後 N 日でアーカイブ）と `InquiryStaleAlertJob`（NEW のまま N 日経過で WARN 通知）は本フェーズスコープ外。phase17 r7 改訂時に新規 ID で受理してもらう。**本フェーズ実装は単独完結**（phase17 §6.2 `BatchAlertNotifier` のみ依存 / r4 / IMP-3：仮称 `NotificationDispatcher` ではない）。

**r4 / IMP-9：申し送り済**：[phase17_implementation_plan.md §14-2-a](phase17_implementation_plan.md) に r7 候補追加要請として 2 ジョブを記載済（2026-05-09）。

---

## 11. 採用しなかった選択肢（実装観点）

| 候補 | 不採用理由 |
|------|-----------|
| Step 0 → A → B のまま 3 段で実装 | 設計書の段取りそのものは適切だが、実装作業として「DB / Validator / Service / Console / Market」をまとめて 1 ステップにすると差分レビューが大粒度になりすぎる。本書は 8 ステップに分解 |
| `InquiryNotificationDispatcher` を phase17 `BatchAlertNotifier` に直接マージ（r4 / IMP-3 で表記更新） | phase17 の `BatchAlertNotifier` を改修すると phase17 既存テストへの影響範囲が広がる。本フェーズ専用のドメイン dispatcher を 1 層噛ませて phase17 側は「level + tag + title + body + payloadIdentity」を受け取るだけの汎用 API に保つ |
| `InquiryBellBadge.vue` を `App.vue` 直書き（Composable 切り出さず / r4 / IMP-6 で組込先を `App.vue` サイドバーに変更）| RV-8 で明示された通り、phase19 集約 Bell との再利用余地のため `useVisibilityPolling` を Composable 化する |
| `InquiryTargetOwnershipValidator` を Service 内のプライベートメソッドで実装 | RV-5 で「1 ファイルに集約する」方針が明示。`CreateInquiryService` 以外（将来の `UpdateInquiryService` 等）でも再利用したい構造のため、専用 Validator クラスとして切り出す |
| Market 側 DTO を Console と共通化 | RV-9 で `is_internal_note` の構造的除外が明示。**Mass Assignment 攻撃面を Controller 入口で塞ぐ**ため別 DTO クラスを保つ |

---

## 12. レビューコメント対応サマリ（実装計画レイヤー）

設計書 r3 までで RV-1 〜 RV-12 / RV2-1 〜 RV2-5 はすべて反映済み。本実装計画書はそれらを Step 単位に**実装作業**として落とし込んだもの。実装計画書レイヤー独自のレビューはまだ存在しない（初版 / 2026-05-08）。

---

## 改訂履歴

| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 2026-05-08 | 設計書 r3 を実装作業に落とし込み。Step 0 〜 Step 8 の 8 段階で実装計画化。phase17 実装計画書の構造（Step 別の作業 / TDD / 完了条件 / コード例 / 既存実装との整合確認）と同等粒度で記述 |
| r4 | 2026-05-09 | 実装完走後の齟齬を本書に反映（実装済みの内容に合わせる）。設計書 r4 の IMP-1〜IMP-9 と整合。🔴必須：(IMP-1) §1-6-1 / §1-6-2 / §1-7 等の `application.yml` 表記を `application.properties` に修正。Console config 参照は `config('app.inquiry.*')` 形式（既存 phase14 / 15 / 17 と同方針）。(IMP-2) §1-6-1 等の通知テンプレートで `#{inquiry_id}` / `#{target_id}` を `No.{inquiry_id}` / `No.{target_id}` に変更（Spring `@Value` SpEL 衝突回避）。(IMP-3) §4-6 / §4-8 等の `payload_hash` 算出式を、phase17 `BatchAlertNotifier.buildPayloadHash` の実体（`subscriptionTag` プレフィクス付き `SHA-256('inquiry_alerts:'+payloadIdentity)`）に合わせて修正。(IMP-4) §1-4 / §6 「Market Pass-through 層」を **不存在**として削除し、§6 を「Market SPA（React 直接 Core）」に書き換え。CloudFront `/api/customer/*` Behavior は phase13 §2.2 で既存。Market 側 Laravel `phpunit.xml` / `config/app/Inquiry.php` は対象外。(IMP-5) §5-1 で計画していた「Console Pass-through 層での operation_logs 記録」を削除し、Core 側 `ReplyInquiryService` / `UpdateInquiryStatusService` で記録する方針に修正（既存 phase14 / 15 / 17 と同方針）。(IMP-6) §5-2-1 「Header.vue 組込」を「App.vue サイドバーメニュー組込」に修正。`Bell.vue` は `InquiryBellBadge.vue` にリネーム。`useVisibilityPolling` は `.ts` ではなく `.js` で配置（Console は TS 未導入）。(IMP-7) §5-3 SPA-CSL-1〜7 は **Vitest 未導入のためスコープ外**として注記し、`npm run build` 緑で代替。Vitest 導入は phaseX-N 申し送り。(IMP-8) §6-2-3 `TargetTypeSelector` の動的セレクタ案を **数値入力**に簡略化。動的セレクタは phase19 / 後続で拡張可能。(IMP-9) §10-6 / §11 で言及済の phase17 r7 候補（`InquiryArchiveJob` / `InquiryStaleAlertJob`）を [phase17_implementation_plan.md](phase17_implementation_plan.md) §14-2-a に追記済。 |
