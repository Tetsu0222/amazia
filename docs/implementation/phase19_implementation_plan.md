# フェーズ19 実装計画（お知らせ機能）

## 概要
- 対象設計書: [phase19_notice_management.md](../design/phase11_20/phase19_notice_management.md)（**r2 / 2026-05-07**）
- 対象範囲: Amazia Core / Amazia Console / Amazia Market / DB 設計
- 段取り: 設計書 §「実装段取り（Step A → C）」をベースに、**Step 0（前提整備）→ Step A（Core スキーマ + API）→ Step B（Console UI）→ Step C（Market UI）→ Step D（フェーズ完了確認・ドキュメント反映）** の 5 段階で実施
- 作成日: 2026-05-08
- 親フェーズ: [phase17_implementation_plan.md](phase17_implementation_plan.md)（phase17 完了済み）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | Step 0 → A → B → C → D を厳守。Step を跨いだ部分実装は禁止。Step A の Core API 契約が確定するまで Step B / C は着手しない（設計書 §実装段取り：契約変更で手戻りが発生するため） |
| 規模感 | Core 3 テーブル新設（`notice_categories` / `notices` / `notice_reads`）+ Service 約 7 本 + Controller 8 本 + Console 5 エンドポイント + Console 画面 2 種（一覧 / 登録編集）+ Market 4 コンポーネント（ヘッダー / 一覧 / モーダル / バッジ） |
| TDD | 設計書「TDD テストケース」セクションに列挙された全項目を Step ごとに割り当てて実装。境界値テスト（公開期間境界・重複既読登録）は該当 Step に集約 |
| コーディング規約 | [coding_guidelines.md](../coding_guidelines.md) 厳守（Service にロジック寄せ・config 駆動・1 ファイル 1 ユースケース・ドメイン単位パッケージ） |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` + `application-test.properties`（Core）+ Console `phpunit.xml`（Console 値が要る場合）+ Market `.env.test`（Market 値が要る場合）をセット更新（規約 4-3） |
| テスト値 | ハードコードせず `config()` / `@Value` / `import.meta.env` 経由で取得（規約 4-1） |
| メモリ事項 | Core 側 Heap 制限（`-Xmx384m`）を意識し、一覧 API は JOIN で 1 クエリにまとめ N+1 を回避（設計書 §技術検討事項）。`is_read` 計算も `LEFT JOIN notice_reads ... IS NULL` で 1 クエリ |
| マイグレーション | 業務テーブルは Core `schema.sql` に冪等構文（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`）で追記（[037](../troubles/037_flyway_misassumed_phase14_tables_missing.md) と同方針）。Console Laravel migrations にお知らせテーブルは追加しない（Console は Core API 越しに操作するのみ） |
| H2 互換 | schema.sql の MySQL 専用構文を持ち込まないことを最優先（test_insights カテゴリ7-2）。テストは `application-test.properties > spring.sql.init.schema-locations=` を空のまま、Entity から ddl-auto=create-drop で生成 |
| operation_logs | `operation_logs` 書き込みは **Core Service が直接行う**（設計書 R19-3 / R19-4）。Console は `X-User-Id` ヘッダで actor の `users.id` を渡すのみ。既存 `RegisterInboundController` と同方式 |
| PK 型 | Core 内テーブル PK / FK は `BIGINT`、`users` / `market_customers` を参照する FK は `BIGINT UNSIGNED`（設計書「PK 型ポリシー（R19-1）」/ 037 起因の型不整合事故防止） |
| DTO 分離 | `NoticeMarketDto` / `NoticeConsoleDto` を別クラスとして分離（R19-11）。`author` フィールドは `NoticeConsoleDto` のクラス定義にのみ存在し、コンパイル時点で Market 側に存在しない |
| 公開期間 | JST タイムゾーン保存・リアルタイム DATETIME 比較。`publish_end` の `23:59:59` 自動付与は **Console 側 FormRequest の責務**（R19-2 / R19-5）。Core はリクエスト値そのままで `now() BETWEEN publish_start AND publish_end` 比較するのみ |

### 設計書からの「本フェーズのスコープ外」確認

| 項目 | 取り扱い |
|------|---------|
| Console 社員（`users.id`）の既読履歴 | スコープ外。既読主体は `market_customers.id` のみ |
| `notice_reads.deleted_flag` による既読解除 | 採用しない（YAGNI／設計書 §`deleted_flag` 廃止）。将来要件発生時に `deleted_at` を追加検討 |
| 通知メール / Push 連携 | スコープ外（設計書 §将来課題） |
| 多言語対応 / 添付ファイル / 重要度の細分化 | スコープ外（設計書 §将来課題） |
| Swiper 等の外部ローテーションライブラリ導入 | 不採用。`setInterval(5000)` で実装（無料枠最優先） |
| サマータイム・海外展開 | スコープ外（phase14 r3 と同方針） |

---

## 1. Step 0 — 前提整備

### 1-1. 既存実装との整合性（2026-05-08 時点の棚卸し方針）

| 既存資産 | 場所 | フェーズ19 での利用方針 |
|---------|------|----------------------|
| `users` Entity / Repository | phase11 既存 | `notices.author_id` の参照先。読み取りのみ。`notices` 側は `BIGINT UNSIGNED` で型を揃える |
| `market_customers` Entity | phase13 既存 | `notice_reads.market_customer_id` の参照先。読み取りのみ。FK 型は `BIGINT UNSIGNED` |
| `operation_logs` 一式 | phase14 既存 | お知らせ作成 / 更新 / 削除で記録。本フェーズで追加する 3 種 action（`create_notice` / `update_notice` / `delete_notice`）を Core Service から記録 |
| `RegisterInboundController` | phase15 既存 | `X-User-Id` ヘッダ経由で actor の `users.id` を受け取る既存パターンの参考実装。お知らせ系 Controller も同方式に揃える |
| Console `auth.jwt` ミドルウェア | phase11 既存 | お知らせ Console API（`/api/admin/notices`）の認可。Service 層で `auth()->user()->id` を取得し `X-User-Id` ヘッダにセットして Core を呼び出す |
| Market 会員セッション認証 | phase13 既存 | `/api/customer/notices/*` 系の認証（既存 `customer` 系ルートと同じセッション方式） |
| `GlobalExceptionHandler`（`@RestControllerAdvice`） | phase11 既存 | 422 のエラー詳細を `errors[].field` / `errors[].message` で返すレスポンス整形を継承（test_insights カテゴリ2 / 021） |

### 1-2. Step 0-1: パッケージ構成の確定（Core）

新規 Java パッケージ：

```
com.example.notice
├── controller
│   ├── CreateNoticeController          POST   /api/notices
│   ├── UpdateNoticeController          PUT    /api/notices/{id}
│   ├── DeleteNoticeController          DELETE /api/notices/{id}
│   ├── GetNoticeController             GET    /api/notices/{id}
│   ├── ListNoticeController            GET    /api/notices
│   ├── MarkAsReadController            POST   /api/customer/notices/{id}/read
│   ├── GetUnreadCountController        GET    /api/customer/notices/unread-count
│   └── GetUnreadHeaderNoticesController GET   /api/customer/notices/unread
├── service
│   ├── CreateNoticeService
│   ├── UpdateNoticeService
│   ├── DeleteNoticeService
│   ├── GetNoticeService
│   ├── ListNoticeService
│   ├── MarkAsReadService
│   └── GetUnreadCountService
├── entity
│   ├── Notice              （@Table(name="notices")）
│   ├── NoticeCategory      （@Table(name="notice_categories")）
│   └── NoticeRead          （@Table(name="notice_reads")）
├── repository
│   ├── NoticeRepository
│   ├── NoticeCategoryRepository
│   └── NoticeReadRepository
├── dto
│   ├── NoticeMarketDto              （Market 用：author 含まず）
│   ├── NoticeConsoleDto             （Console 用：author { id, name } 含む）
│   ├── NoticeCategoryDto
│   ├── CreateNoticeRequest
│   ├── UpdateNoticeRequest
│   └── UnreadCountResponse
└── validator
    └── NoticePeriodValidator        （publish_start <= publish_end の二重防御）
```

`com.example.notice.controller.GetNoticeCategoriesController`（`GET /api/notice-categories`）は分類マスタ取得用に追加。

### 1-3. Step 0-2: パッケージ構成の確定（Console / Laravel）

```
app/Notice/
├── Controller/
│   ├── ListNoticeController          GET    /api/admin/notices
│   ├── GetNoticeController           GET    /api/admin/notices/{id}
│   ├── CreateNoticeController        POST   /api/admin/notices
│   ├── UpdateNoticeController        PUT    /api/admin/notices/{id}
│   └── DeleteNoticeController        DELETE /api/admin/notices/{id}
├── Service/
│   ├── ListNoticeService
│   ├── GetNoticeService
│   ├── CreateNoticeService
│   ├── UpdateNoticeService
│   └── DeleteNoticeService
└── Request/
    ├── StoreNoticeRequest            （新規作成バリデーション + 時分秒補完）
    └── UpdateNoticeRequest           （編集バリデーション）

config/app/Notice.php                 （新規・category_id・本文最大長等の定数）
routes/api/Notice.php                 （新規・api.php に明示 require 追加）

resources/vue/src/features/notice/
├── api/
│   └── notice.js
└── pages/
    ├── NoticeList.vue                （管理者向け一覧 + フィルタ）
    └── NoticeForm.vue                （新規作成・編集の共通フォーム）
```

### 1-4. Step 0-3: パッケージ構成の確定（Market / React）

```
src/features/notice/
├── api/
│   └── notice.ts                     （Market API 集約）
├── components/
│   ├── HeaderNotice.tsx              （ヘッダー自動切替アコーディオン）
│   ├── NoticeListPage.tsx            （一覧画面 + タブフィルタ）
│   ├── NoticeModal.tsx               （本文モーダル + 前後遷移）
│   └── UnreadBadge.tsx               （未読数バッジ）
└── hooks/
    ├── useUnreadCount.ts             （60秒 Polling）
    └── useHeaderNotices.ts           （5秒ローテーション + Polling 結果バッファ）
```

### 1-5. Step 0-4: 設定ファイルの追加項目（Step A 着手前にスケルトン作成）

**Core `application.properties`** に追加：
```properties
# notice categories（schema.sql の INSERT IGNORE と整合）
amazia.notice.categories.important-id=1
amazia.notice.categories.normal-id=2
# ヘッダー用未読取得の上限件数
amazia.notice.header.max-items=10
# 本文最大長（DB CHECK と同値）
amazia.notice.body.max-length=10000
# 件名最大長
amazia.notice.subject.max-length=255
```

**Core `application-test.properties`** にも同じキーをテスト用値で追加（規約 4-3）。

**Console `config/app/Notice.php`** を新設：
```php
return [
    'categories' => [
        'important_id' => env('NOTICE_CATEGORY_IMPORTANT_ID', 1),
        'normal_id'    => env('NOTICE_CATEGORY_NORMAL_ID', 2),
    ],
    'subject_max_length' => 255,
    'body_max_length'    => 10000,
];
```

`config/app.php` に `'notice' => require __DIR__.'/app/Notice.php',` を**明示的に追記**（規約 2-1 補足3）。

**Console `phpunit.xml`** に追加：
```xml
<env name="NOTICE_CATEGORY_IMPORTANT_ID" value="1"/>
<env name="NOTICE_CATEGORY_NORMAL_ID" value="2"/>
```

**Market `.env`** に追加：
```
VITE_NOTICE_UNREAD_POLL_MS=60000
VITE_NOTICE_HEADER_ROTATE_MS=5000
```

**Market `.env.test`** に同等値を追加。

**`docker-compose.yml`** にも同名の環境変数があれば該当サービスに追記（本フェーズでは Core / Console / Market いずれも追加環境変数なしで動作可能だが、`NOTICE_CATEGORY_*_ID` を docker 越しに上書き可能にしたい場合のみセット更新）。

### 1-6. Step 0-5: ルート登録方針

- Console：`routes/api.php` に `require __DIR__.'/api/Notice.php';` を明示追加（規約 2-1 補足4）
- Market：React 側ルーティングに `/notices` と `/notices/:id`（モーダル用にクエリパラメータでもよい）を追加

### 1-7. Step 0 完了条件
- [ ] パッケージ構成が `coding_guidelines.md` 2-1 と整合していることを確認
- [ ] `application.properties` / `application-test.properties` / `config/app/Notice.php` / `phpunit.xml` / `.env.test` のスケルトンが追加され、空状態でも既存テストが緑（phase17 完了時の各テスト件数を維持）
- [ ] DB 設計書 / API 設計書の更新タスクをこの段階で発行（Step A〜D 完了時に都度更新する CLAUDE.md ルール準拠）
- [ ] CSRF の `PROTECTED_PREFIX` が `/api/customer/` を含むことを確認（[reference_market_auth_api_routing.md](../../../.claude/projects/c--Users-root2-OneDrive--------ProjectFullStackRenaissance/memory/reference_market_auth_api_routing.md)）。`/api/customer/notices/*` 系もこの保護下で動く

---

## 2. Step A — Core スキーマ + API

### 2-1. schema.sql 追記（`amazia-core/src/main/resources/schema.sql` 末尾）

設計書 §DB 設計（追加）の通り、以下の順序で追記する。

```sql
-- ============================================================================
-- フェーズ19: お知らせ機能（設計書 phase19_notice_management.md r2）
-- ============================================================================

-- 1. notice_categories（分類マスタ）
CREATE TABLE IF NOT EXISTS notice_categories (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(20) NOT NULL,
    label         VARCHAR(50) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_notice_categories_code UNIQUE (code)
);
INSERT IGNORE INTO notice_categories (id, code, label, display_order) VALUES
    (1, 'important', '重要', 1),
    (2, 'normal',    '普通', 2);

-- 2. notices（お知らせ本体）
CREATE TABLE IF NOT EXISTS notices (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject       VARCHAR(255) NOT NULL,
    category_id   BIGINT NOT NULL,
    body          TEXT NOT NULL,
    author_id     BIGINT UNSIGNED NOT NULL,
    publish_start DATETIME NOT NULL,
    publish_end   DATETIME NOT NULL,
    deleted_at    DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notices_category FOREIGN KEY (category_id) REFERENCES notice_categories(id),
    CONSTRAINT fk_notices_author   FOREIGN KEY (author_id)   REFERENCES users(id),
    CONSTRAINT chk_notices_publish_period CHECK (publish_start <= publish_end)
);
CREATE INDEX IF NOT EXISTS idx_notices_publish_period ON notices (publish_start, publish_end);
CREATE INDEX IF NOT EXISTS idx_notices_category_id    ON notices (category_id);
CREATE INDEX IF NOT EXISTS idx_notices_deleted_at     ON notices (deleted_at);
CREATE INDEX IF NOT EXISTS idx_notices_author_id      ON notices (author_id);

-- 3. notice_reads（既読管理）
CREATE TABLE IF NOT EXISTS notice_reads (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    notice_id           BIGINT NOT NULL,
    market_customer_id  BIGINT UNSIGNED NOT NULL,
    read_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notice_reads_notice_customer UNIQUE (notice_id, market_customer_id),
    CONSTRAINT fk_notice_reads_notice   FOREIGN KEY (notice_id)          REFERENCES notices(id),
    CONSTRAINT fk_notice_reads_customer FOREIGN KEY (market_customer_id) REFERENCES market_customers(id)
);
CREATE INDEX IF NOT EXISTS idx_notice_reads_market_customer_id ON notice_reads (market_customer_id);
```

**注意点（test_insights カテゴリ7-2）**：`ON UPDATE CURRENT_TIMESTAMP` は MySQL 専用構文のため、`updated_at` の自動更新は JPA `@PreUpdate` または Service 側で実装する。`CHECK (publish_start <= publish_end)` は H2 / MySQL 双方で通る構文に統一。

### 2-2. JPA Entity / Repository 新規作成

#### Entity
- `Notice`：`@Table(name="notices")`、`@PrePersist` / `@PreUpdate` で `created_at` / `updated_at` を自動セット。`category_id` は `@ManyToOne(fetch=LAZY)` で `NoticeCategory` を参照、`author_id` は `Long`（DB 側 `BIGINT UNSIGNED` だが Java 側は `Long` で受ける／Core 内で署名比較しないため許容）
- `NoticeCategory`：`@Table(name="notice_categories")`。読み取り中心マスタ
- `NoticeRead`：`@Table(name="notice_reads")`、`UNIQUE(notice_id, market_customer_id)` を `@Table(uniqueConstraints=...)` でも宣言

#### Repository
- `NoticeRepository`：
  - `findByIdAndDeletedAtIsNull(Long id)` — Market 用詳細取得
  - `findByIdActiveAtNow(Long id, LocalDateTime now)` — 公開期間内 + 未削除のみ
  - 一覧用にページング対応 `Specification` または専用クエリメソッド
  - 未読数集計用カスタムクエリ（後述 2-4 のクエリを `@Query` で定義）
- `NoticeCategoryRepository`：単純な `findAll(Sort.by("displayOrder"))`
- `NoticeReadRepository`：
  - `existsByNoticeIdAndMarketCustomerId(Long, Long)` — UI 表示の `is_read` 判定補助
  - `INSERT ... ON DUPLICATE KEY UPDATE` 相当はネイティブクエリで実装（後述 2-3-7）

### 2-3. Service / Controller 実装

各 Service / Controller は coding_guidelines 2-2「1ファイル 1ユースケース」に従う。

#### 2-3-1. CreateNoticeService / CreateNoticeController（POST /api/notices）

**Controller**：`@RequestHeader("X-User-Id") Long userId` で actor を受け取る（既存 `RegisterInboundController` と同方式）。

**Service `CreateNoticeService.create(CreateNoticeRequest req, Long userId)`**：
1. **事前チェック**（R19-6）：
   - `category_id` が `notice_categories` に存在するか（不在なら `ResponseStatusException(422, "category not found")`）
   - `userId` が `users` に存在するか（不在なら `ResponseStatusException(422, "actor not found")`）
   - `publish_start <= publish_end` を `NoticePeriodValidator` で再チェック（DB CHECK との二重防御）
2. `INSERT notices`（`author_id = userId`、`deleted_at = NULL`）
3. **同一トランザクション**で `OperationLogService.record(action='create_notice', user_id=userId, target_type='notices', target_id=notice.id, screen_name='console.notice.create', api_name='POST /api/notices', comment="件名：" + subject)` を記録
4. レスポンスは `NoticeConsoleDto`（201 Created）

#### 2-3-2. UpdateNoticeService / UpdateNoticeController（PUT /api/notices/{id}）

- 事前チェックは作成と同様 + 対象 `notices.id` 存在確認（404）+ 論理削除済の場合は 410
- `UPDATE notices` + `OperationLogService.record(action='update_notice', screen_name='console.notice.edit', api_name='PUT /api/notices/:id')`
- レスポンスは `NoticeConsoleDto`（200 OK）

#### 2-3-3. DeleteNoticeService / DeleteNoticeController（DELETE /api/notices/{id}）

- `UPDATE notices SET deleted_at = NOW() WHERE id = :id AND deleted_at IS NULL`
- 影響行数 0 なら既に削除済（410）
- `OperationLogService.record(action='delete_notice', screen_name='console.notice.list', api_name='DELETE /api/notices/:id')`
- 関連する `notice_reads` は **物理削除しない**（参照履歴維持／設計書 TDD「お知らせ削除時、関連する `notice_reads` は維持される」）
- 204 No Content

#### 2-3-4. GetNoticeService / GetNoticeController（GET /api/notices/{id}）

- クエリパラメータ `include_unpublished` / `include_deleted` は **Console JWT のときのみ有効**（設計書 §4 / §5）
- Market アクセス（または `customer` セッションでない場合）は常に「公開期間内 + 未削除」のみ。それ以外は 404
- 認証種別の判定：
  - `X-User-Id` ヘッダ + Console JWT 検証通過 → Console モード（`NoticeConsoleDto` 返却）
  - Market 会員セッション → Market モード（`NoticeMarketDto` 返却 + `is_read` キー付与）
  - 未認証 → Market モード（`NoticeMarketDto` 返却 + `is_read` キー**省略**）

#### 2-3-5. ListNoticeService / ListNoticeController（GET /api/notices）

**重要**：N+1 回避のため `notices LEFT JOIN notice_categories` + `LEFT JOIN notice_reads ON ... AND market_customer_id = :customerId` を 1 クエリで実行する（設計書 §技術検討事項）。

- ページング：`page`（1始まり）/ `per_page`（最大 100、デフォルト 20）
- 並び順：`category_id ASC, publish_start DESC, id DESC`
- フィルタ：`category_id`（任意）
- Market 視点（`include_unpublished` / `include_deleted` 無視）：`now() BETWEEN publish_start AND publish_end AND deleted_at IS NULL`
- Console 視点：パラメータに従い全件参照可能
- レスポンス DTO は呼び出し元（Console / Market / 未認証）で出し分け（**DTO クラスとして別物**：`NoticeMarketDto` / `NoticeConsoleDto`）

#### 2-3-6. GetUnreadCountService / GetUnreadCountController（GET /api/customer/notices/unread-count）

設計書 §6 の擬似 SQL を `@Query(nativeQuery = true)` で実装：

```sql
SELECT
  nc.code AS category_code,
  COUNT(*) AS unread_count
FROM notices n
JOIN notice_categories nc ON nc.id = n.category_id
LEFT JOIN notice_reads nr
  ON nr.notice_id = n.id AND nr.market_customer_id = :customer_id
WHERE n.deleted_at IS NULL
  AND :now BETWEEN n.publish_start AND n.publish_end
  AND nr.id IS NULL
GROUP BY nc.code
```

- レスポンス：`{ "data": { "important": N, "normal": M, "total": N+M } }`
- 集計に未存在の `category_code` は 0 で埋める（クライアント側 UX 安定化）

#### 2-3-7. MarkAsReadService / MarkAsReadController（POST /api/customer/notices/{id}/read）

**Service `MarkAsReadService.markAsRead(Long noticeId, Long marketCustomerId)`**：
1. `notices` を `findByIdActiveAtNow(...)` で取得（公開期間内 + 未削除）
2. 不在なら `ResponseStatusException(404)`
3. ネイティブクエリで `INSERT INTO notice_reads (notice_id, market_customer_id, read_at) VALUES (:notice_id, :customer_id, NOW()) ON DUPLICATE KEY UPDATE read_at = read_at`（H2 互換のため、テスト時は H2 の `MERGE INTO` 構文または `EXISTS` 分岐で代替する）
4. 200 OK（冪等）

**H2 / MySQL 互換**：H2 v2 は `ON DUPLICATE KEY UPDATE` を解釈しないため、Service 層で「`existsByNoticeIdAndMarketCustomerId` → false なら INSERT、true なら何もしない」で実装する案もあり。**実装着手時に H2 v2 の挙動を再確認**し、ネイティブクエリ採用 vs Service 分岐採用を決定（test_insights カテゴリ7-2）。

#### 2-3-8. GetUnreadHeaderNoticesController（GET /api/customer/notices/unread）

- 未読 + 公開期間内 + 未削除 のうち、`category_id ASC → publish_start DESC` で **最大 `amazia.notice.header.max-items` 件**（=10）取得
- レスポンスは `NoticeMarketDto` の配列（`author` 含まず）
- 1 クエリで `notices LEFT JOIN notice_reads WHERE nr.id IS NULL` + `LIMIT 10`

#### 2-3-9. GetNoticeCategoriesController（GET /api/notice-categories）

- 単純な `findAll(Sort.by("displayOrder"))` を返す
- 認証不要（Market / Console 双方から呼ばれる）

### 2-4. DTO 分離（R19-11）

**`NoticeMarketDto`**：
```java
public record NoticeMarketDto(
    Long id,
    String subject,
    NoticeCategoryDto category,
    String body,
    LocalDateTime publishStart,
    LocalDateTime publishEnd,
    LocalDateTime updatedAt,
    Optional<Boolean> isRead    // 会員セッション時のみ Optional.of(...)、それ以外は Optional.empty() でキー自体を省略
) { }
```

`isRead` を `Optional<Boolean>` で表現し、`@JsonInclude(JsonInclude.Include.NON_ABSENT)` を付与してキー自体を省略する（R19-9）。

**`NoticeConsoleDto`**：
```java
public record NoticeConsoleDto(
    Long id,
    String subject,
    NoticeCategoryDto category,
    String body,
    LocalDateTime publishStart,
    LocalDateTime publishEnd,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime deletedAt,    // null 許容
    AuthorDto author,            // { id, name }
    String publishState          // "未公開" / "公開中" / "終了" / "削除済" のいずれか（Service で算出）
) { }
```

**コンパイル時保証**：`NoticeMarketDto` クラス定義に `author` フィールドが**存在しない**ため、Controller が誤って Console DTO に流し込んでも漏洩しない。

### 2-5. application.properties 整理

実装中に追加が必要になる可能性のあるキー：

```properties
amazia.notice.header.max-items=10
amazia.notice.body.max-length=10000
amazia.notice.subject.max-length=255
amazia.notice.categories.important-id=1
amazia.notice.categories.normal-id=2
```

`@ConfigurationProperties("amazia.notice")` でまとめて受けるか、個別 `@Value` で参照するかは Step A 着手時に決定（既存の application.properties の流儀に揃える）。

### 2-6. テスト（TDD）

#### 2-6-1. スキーマレベル
- マイグレーション直後、`notice_categories` マスタ 2 件存在（`important` / `normal`）
- `notices.publish_start > publish_end` で INSERT すると CHECK 制約違反（H2 / MySQL 双方）
- `notice_reads` の同一 `(notice_id, market_customer_id)` 重複 INSERT で UNIQUE 違反
- 存在しない `category_id` での `notices` INSERT は FK 違反

#### 2-6-2. Service 層 / Controller 層（設計書 §TDD Core 正常系・異常系）

**正常系（CRUD 系）**：
- 作成・取得・更新・論理削除の各 API が正しく動作
- 公開期間境界値（`publish_start == now()`, `publish_end == now()`）で含まれる
- `2026-05-07T00:00:00+09:00` 〜 `2026-05-07T23:59:59+09:00` の範囲が JST 0:00 / 23:59:59 で取得可能

**正常系（既読・未読）**：
- 既読登録 API が冪等（同じ会員 × 同じ notice を 5 回叩いて `notice_reads` 1 行）
- 未読数集計が「important / normal / total」で正しく分類される
- ヘッダー用未読取得が最大 10 件で打ち切られる（`amazia.notice.header.max-items` の `@Value` 取得をテスト内でアサート）

**正常系（一覧 API の視点切替）**：
- Market 視点で公開期間外 / 論理削除済が返らない
- Console 視点で `include_unpublished=true&include_deleted=true` で全件返る
- 並び順が `category_id ASC, publish_start DESC, id DESC`

**異常系**：
- 公開期間外（前 / 後）のお知らせを Market から取得 → 404
- 論理削除済お知らせへの既読登録 → 404
- 存在しない `notice_id` への既読登録 → 404
- `publish_start > publish_end` の登録 → 422（CHECK + Service 二重防御）
- `subject` が 256 文字以上 → 422
- `body` が 10001 文字以上 → 422
- 存在しない `category_id` → 422（**Service 事前チェック**で 422、`DataIntegrityViolationException` 経路に到達しないことをテストで担保 / R19-6）
- 存在しない `X-User-Id` → 422（Service 事前チェック、`author_id` の FK 違反まで届かない）
- Market 会員セッションで `include_unpublished=true` → 無視される
- Console JWT なしで `POST /api/notices` → 401
- 既読登録時の DB UNIQUE 違反は `ON DUPLICATE KEY UPDATE` または Service 分岐で吸収され例外を投げない
- `@RestControllerAdvice` 経由で 422 のエラー詳細が `errors[].field` / `errors[].message` で返る

**Market 投稿者非表示の検証**：
- Market 一覧 / 詳細 API のレスポンス JSON に **`author_id` / `author` フィールドが含まれない**ことを ObjectMapper でアサート
- Console 一覧 / 詳細 API のレスポンス JSON には `author: { id, name }` が含まれる
- DTO クラスとして `NoticeMarketDto` には `author` フィールドが定義されていない（**コンパイル時保証**：`NoticeMarketDto.class.getDeclaredFields()` を反射的に検証するテストも追加）

**未認証時の `is_read` 省略（R19-9）**：
- Market 未認証アクセスでの一覧 API レスポンス JSON に **`is_read` キー自体が存在しない**（`null` 値ではなく省略）
- Market 会員セッション時のレスポンスには `is_read` キーが必ず存在する
- Console アクセスでの一覧 API レスポンス JSON にも `is_read` キーが存在しない

**operation_logs 記録の検証（R19-3 / R19-4）**：
- `POST /api/notices` 成功時、`operation_logs` に `action='create_notice'` / `user_id=X-User-Id 値` / `target_type='notices'` / `target_id=新規ID` / `api_name='POST /api/notices'` が 1 件追加される
- `PUT /api/notices/:id` 成功時、`action='update_notice'` / `api_name='PUT /api/notices/:id'`（パスパラメータ展開後の値）
- `DELETE /api/notices/:id` 成功時、`action='delete_notice'`
- バリデーションで 422 になった呼び出しでは `operation_logs` に書き込まれない（トランザクション境界の確認）

#### 2-6-3. テスト値の config 経由化（規約 4-1）

- `application-test.properties` に `amazia.notice.categories.important-id=1` / `normal-id=2` / `header.max-items=10` を記述し、テスト内で `@Value` 注入してアサート
- ハードコード（`assertEquals(1, ...)`）は禁止

### 2-7. Step A 完了条件

- [ ] schema.sql 追記が `mvn test`（amazia-core）で全件緑（既存テスト件数 + 新規 30〜40 件想定）
- [ ] `docker compose down -v && docker compose up --build` で本番想定 MySQL に対しても起動成功（test_insights カテゴリ9）
- [ ] DB 設計書 3 ファイル新規作成（`TBL_notice_categories.md` / `TBL_notices.md` / `TBL_notice_reads.md`）
- [ ] `ER_diagram.md` に 3 テーブルとリレーション追加（カーディナリティ R19-12）
- [ ] `database_design/README.md` の Core システム表に 3 行追記
- [ ] `Core_API.md` に「お知らせ API（フェーズ19）」セクションを追加（8 + 1 エンドポイント）
- [ ] **`ops/healthcheck/required_tables.txt`** に `notice_categories` / `notices` / `notice_reads` の 3 行を追加（CD の主要テーブル存在確認用 / 設計書 §主要テーブル定数の同期）

---

## 3. Step B — Console UI

### 3-1. Step B-1: Notice 中継 Service / Controller（Laravel）

各 Service は `Http::baseUrl(config('app.core_url'))` 経由で Core を叩く（既存 phase14 / phase15 と同方式）。

#### 3-1-1. CreateNoticeService（Console 側）

```php
public function create(StoreNoticeRequest $request): array
{
    $userId = auth()->user()->id;
    $payload = $request->validated();
    // publish_start / publish_end の時分秒補完
    $payload['publish_start'] = $this->normalizeStart($payload['publish_start']); // 'YYYY-MM-DD' → 'YYYY-MM-DDT00:00:00+09:00'
    $payload['publish_end']   = $this->normalizeEnd($payload['publish_end']);     // 'YYYY-MM-DD' → 'YYYY-MM-DDT23:59:59+09:00'

    $response = Http::withHeaders(['X-User-Id' => $userId])
        ->baseUrl(config('app.core_url'))
        ->post('/api/notices', $payload);

    return $this->handleResponse($response);
}
```

**重要**：時分秒補完は **Console 側 FormRequest または Service 層**で実施（R19-2）。Core にはすでに正規化済みの DATETIME を送る。

#### 3-1-2. UpdateNoticeService / DeleteNoticeService / ListNoticeService / GetNoticeService

- いずれも `Http::withHeaders(['X-User-Id' => auth()->user()->id])` で Core を呼ぶ薄いラッパー
- Core が返した 404 / 410 / 422 の HTTP ステータスをそのまま Console レスポンスに伝搬
- **Console 自身は `operation_logs` への INSERT を行わない**（R19-3 / R19-4）。書き込みは Core Service の責務

#### 3-1-3. StoreNoticeRequest / UpdateNoticeRequest（FormRequest）

```php
public function rules(): array
{
    return [
        'subject'       => ['required', 'string', 'min:1', 'max:' . config('notice.subject_max_length')],
        'category_id'   => ['required', 'integer', Rule::in([
            config('notice.categories.important_id'),
            config('notice.categories.normal_id'),
        ])],
        'body'          => ['required', 'string', 'min:1', 'max:' . config('notice.body_max_length')],
        'publish_start' => ['required', 'date'],
        'publish_end'   => ['required', 'date', 'after_or_equal:publish_start'],
    ];
}
```

**`prepareForValidation()` または Service 側**で時分秒補完を実施（後者を推奨：FormRequest はバリデーション専念）。

### 3-2. Step B-2: Vue 画面実装

#### `resources/vue/src/features/notice/`

- **`NoticeList.vue`**：管理者向け一覧
  - 表示項目：ID / 件名 / 分類 / 投稿者（`users.name`）/ 公開開始日・終了日 / 更新日時 / 公開状態
  - フィルタ：分類 / 公開状態（公開前 / 公開中 / 公開終了 / 削除済）/ 期間（更新日時）
  - ページング 20 件 / ページ
  - 並び順：更新日時降順
  - 「新規作成」ボタン → `NoticeForm.vue`（モード=create）
  - 「編集」「削除」ボタン → 各 API 呼び出し
- **`NoticeForm.vue`**：新規作成・編集の共通フォーム
  - 入力項目：件名 / 分類（プルダウン、`GET /api/notice-categories` から取得）/ 本文（textarea）/ 公開開始日（DATE picker）/ 公開終了日（DATE picker）
  - バリデーションは FormRequest 側で実施するが、Vue 側でも `max-length` 等を表示時間に反映
  - 投稿者は表示のみ（`auth.user.name`）

#### ルート登録 / メニュー登録

- `router/index.js` に `/notices` `/notices/create` `/notices/:id/edit` を登録
- `App.vue` のサイドメニューに「お知らせ管理」リンク追加
- ルート定義の順序：静的（`/notices/create`）> 動的（`/notices/:id/edit`）（test_insights カテゴリ2）

### 3-3. テスト（PHPUnit / Vitest）

#### 3-3-1. PHPUnit（`Http::fake()` で Core を偽装）

**正常系**：
- お知らせ登録画面のバリデーションが正しく動作（subject / body の長さ・category_id の存在）
- 公開期間の設定が正しく保存される（Core への送信ペイロードを `Http::fake()` で検証）
- 編集画面で既存値が初期表示される
- 論理削除済のお知らせは「公開状態：削除済」で一覧に表示される

**異常系**：
- `publish_start > publish_end` で登録 → 422 + エラー詳細
- 必須項目欠落で 422
- Console 社員未認証で 401
- Core API が 500 を返した場合、Console もエラー応答を返す
- Core API が 422 を返した場合、Console もそのまま 422 で返す（エラー詳細を保持）

**config 駆動の検証**：
- `config('notice.categories.important_id')` が `phpunit.xml` の値と一致
- `config('notice.body_max_length')` が `phpunit.xml` の値（10000）と一致

**Console から Core への呼び出し（R19-3 / R19-4）**：
- POST `/api/admin/notices` が Core に対し `X-User-Id` ヘッダ付きで `POST /api/notices` を呼び出す（`Http::fake()` で送信ヘッダ検証）
- **Console 自身は `operation_logs` への INSERT を行わない**（DB アサーションで `operation_logs` テーブルが空のまま、Core 側に書き込まれることをテストで担保）
- **Console 側 FormRequest / Service が `publish_start='2026-05-07'`（時分秒なし）の入力を `2026-05-07T00:00:00+09:00` に補完**してから Core へ送る
- 同じく `publish_end='2026-05-07'` を `2026-05-07T23:59:59+09:00` に補完する

#### 3-3-2. Vitest（Vue コンポーネント）

- `NoticeList.vue` のフィルタが正しく URL クエリに反映される
- `NoticeForm.vue` の入力 → 送信フローで API が呼ばれる
- 編集モードで既存値が `v-model` に反映される

### 3-4. Step B 完了条件

- [ ] amazia-console `phpunit` 全件緑（既存テスト + 新規 15〜25 件想定）
- [ ] `vitest` で Vue コンポーネントテスト緑
- [ ] Console 画面で実際にお知らせ CRUD が動作することを手動確認（規約：UI 変更時は dev server で実際に試す）
- [ ] `Console_API.md` に「お知らせ API（フェーズ19）」を追加（5 エンドポイント + `X-User-Id` の取り回し明記）
- [ ] `operation_logs_naming.md` §6 採番例に Console 起点 3 行追加（`create_notice` / `update_notice` / `delete_notice`）

---

## 4. Step C — Market UI

### 4-1. Step C-1: API クライアント実装（`src/features/notice/api/notice.ts`）

```typescript
export const noticeApi = {
  fetchUnreadCount: () => http.get('/api/customer/notices/unread-count'),
  fetchUnreadHeader: () => http.get('/api/customer/notices/unread'),
  fetchList: (params) => http.get('/api/notices', { params }),
  fetchDetail: (id) => http.get(`/api/notices/${id}`),
  markAsRead: (id) => http.post(`/api/customer/notices/${id}/read`),
  fetchCategories: () => http.get('/api/notice-categories'),
};
```

**集約原則**（規約 5）：コンポーネントから直接 fetch せず、必ず `noticeApi` 経由。

### 4-2. Step C-2: フック実装

#### `useUnreadCount.ts`

```typescript
const POLL_MS = Number(import.meta.env.VITE_NOTICE_UNREAD_POLL_MS); // 60000

export const useUnreadCount = () => {
  const [counts, setCounts] = useState({ important: 0, normal: 0, total: 0 });
  useEffect(() => {
    const fetch = () => noticeApi.fetchUnreadCount().then(r => setCounts(r.data.data));
    fetch();
    const id = setInterval(fetch, POLL_MS);
    return () => clearInterval(id);
  }, []);
  return counts;
};
```

#### `useHeaderNotices.ts`

設計書 §「ヘッダー Polling × ローテーションの競合方針（R19-7）」を実装：

- `currentList` / `nextList` の 2 つの state を保持
- 60 秒 Polling で取得した新リストは **`nextList`** に格納（即時 `currentList` には反映しない）
- 5 秒 `setInterval` のコールバックで `currentList = nextList || currentList; nextList = null;` として境界で差し替え
- **例外**：未読数が 0 になった瞬間は即時に `currentList = []` で非表示化

### 4-3. Step C-3: コンポーネント実装

#### `HeaderNotice.tsx`

- アコーディオン形式（折りたたみ／展開）
- `useHeaderNotices` から最大 10 件取得 + 5秒ローテーション
- 件名表示（`text-overflow: ellipsis`）
- クリック → `/notices` へ遷移
- 未読 0 件 → エリア非表示（または「お知らせはありません」を1件表示）
- アクセシビリティ：`aria-live="polite"` を付与

#### `NoticeListPage.tsx`

- 表示対象：`now() BETWEEN publish_start AND publish_end AND deleted_at IS NULL` のレコードのみ（Core が担保）
- 並び順：`category_id ASC → publish_start DESC`（Core が担保）
- フィルタ：「重要」「普通」「すべて」のタブ式
- 表示項目：件名 / 分類 / 更新日時 / 既読・未読バッジ
- 1ページあたり 20件、ページング
- バッジ表示：
  - 重要：未読件数（赤色 / `UnreadBadge color="red"`）
  - 普通：未読件数（青色 / `UnreadBadge color="blue"`）
  - 未読 0 ならバッジ非表示
- 件名クリック → `NoticeModal` を開く

#### `NoticeModal.tsx`

- モーダル内表示項目：件名 / 分類 / 本文 / 更新日時 / 公開期間
- **投稿者は表示しない**（API レスポンスにそもそも含まれない）
- モーダルが開いた瞬間、**未読なら `noticeApi.markAsRead(id)` を発火**
- 「次のお知らせ」「前のお知らせ」ボタン
  - 一覧画面の `currentList` から現在 ID のインデックス±1 で遷移（API 再呼び出しなし／設計書 §技術検討事項）
  - 端で非活性化
  - 遷移時にも自動既読登録
- アクセシビリティ：`role="dialog"` / `aria-labelledby` を付与

#### 本文の XSS 対策（設計書 §技術検討事項）

- `body` はプレーンテキスト保存。React の自動エスケープに任せる
- 改行のみ `<br>` 変換（`text.split('\n').map(...).join(<br>)` のような実装）
- **`dangerouslySetInnerHTML` 禁止**

#### `UnreadBadge.tsx`

- `count: number` / `color: 'red' | 'blue'` を受け取る単純な表示コンポーネント
- `count === 0` なら `null` を返す

### 4-4. テスト（Vitest + React Testing Library）

#### 4-4-1. 正常系

- ヘッダーのお知らせがアコーディオンで表示される
- ヘッダーで未読が複数ある場合、`setInterval(5000)` で表示が切り替わる（**fake timer で `vi.advanceTimersByTime(5000)` を使う**）
- 未読が 0 件のときヘッダーエリアが非表示（または「お知らせはありません」）
- 一覧画面で「重要 / 普通 / すべて」のフィルタが動作する
- 一覧画面の並び順が「重要 → 普通 → 公開開始日降順」（API レスポンスの順序通り表示）
- 本文モーダルを開いた瞬間に `markAsRead` Ajax が発火する（`msw` 等でモック）
- 既読登録後に未読数バッジが減る（ローカル state 反映 + 60 秒後の Polling で再同期）
- モーダル「次のお知らせ」「前のお知らせ」が一覧の並び順で遷移する
- 端（先頭／末尾）でボタンが非活性化する
- レスポンス JSON に `author` / `author_id` が **含まれていない**ことを E2E（または契約テスト）で検証

#### 4-4-2. 異常系

- 未認証時に `POST /api/customer/notices/:id/read` が 401 を返した場合のフォールバック（ログインモーダル表示等）
- 公開期間外の `notice_id` への既読登録 → 404 + UI でエラートーストを表示せず黙殺
- ネットワークエラー時にヘッダーが「お知らせ取得失敗」を出さず黙殺（ログのみ）
- 本文に HTML タグが含まれた場合に **エスケープされて表示**される（XSS 防御）

#### 4-4-3. ヘッダー Polling × ローテーション競合（R19-7 / fake timer）

- 5秒ローテーション中に Polling で取得した新リストが、**現在の表示サイクル終了まで保留**され、次のサイクルで切り替わる
- 未読数が 0 になった瞬間はローテーション境界を待たず**即時に非表示化**される
- Polling と `setInterval(5000)` のタイミング干渉でレースコンディションが発生しない（fake timer で複数パターン）

#### 4-4-4. config 駆動の検証

- ヘッダー Polling 間隔が `import.meta.env.VITE_NOTICE_UNREAD_POLL_MS`（60000）で読まれる
- ヘッダー自動切替間隔が `import.meta.env.VITE_NOTICE_HEADER_ROTATE_MS`（5000）で読まれる
- ハードコード（`setInterval(fn, 5000)`）は禁止

### 4-5. Step C 完了条件

- [ ] amazia-market `vitest` 全件緑（既存テスト + 新規 15〜25 件想定）
- [ ] Market 画面で実際にログイン会員が一連の閲覧操作を完了でき、未読／既読が DB と一致することを手動確認（規約：UI 変更時は dev server で golden path / edge cases / regression を確認）
- [ ] `Market_API.md` にお知らせ閲覧／既読登録の対応エンドポイント表を追加

---

## 5. Step D — フェーズ完了確認・ドキュメント反映

### 5-1. ドキュメント更新チェックリスト（CLAUDE.md ルール）

- [ ] `docs/database_design/TBL_notice_categories.md` 新規作成
- [ ] `docs/database_design/TBL_notices.md` 新規作成
- [ ] `docs/database_design/TBL_notice_reads.md` 新規作成
- [ ] `docs/database_design/README.md` の Core システム表に 3 行追記
- [ ] `docs/database_design/ER_diagram.md` の Mermaid 図に 3 テーブルとリレーション追加（R19-12 のカーディナリティを忘れずに）
- [ ] `docs/api_design/Core_API.md` に「お知らせ API（フェーズ19）」セクション追加（8 + 1 エンドポイント）
- [ ] `docs/api_design/Console_API.md` に「お知らせ API（フェーズ19）」追加（5 エンドポイント + `X-User-Id` 取り回し）
- [ ] `docs/api_design/Market_API.md` にお知らせ閲覧／既読登録の対応エンドポイント表追加
- [ ] `docs/ai_context/operation_logs_naming.md` §6 採番例に Console 起点 3 行追加（`create_notice` / `update_notice` / `delete_notice`）
- [ ] `ops/healthcheck/required_tables.txt` に `notice_categories` / `notices` / `notice_reads` の 3 行追加
- [ ] 設計書 `phase19_notice_management.md` のステータスを「✅ 完了」に更新

### 5-2. 主要テーブル定数の同期検証（R19-10）

- [ ] `ops/healthcheck/check_required_tables_consistency.sh` を新設または既存スクリプトに追加
  - `docs/database_design/README.md` の Core テーブル列挙と `required_tables.txt` の差分検出
  - 差分がある場合 exit 1
- [ ] phase19 のテストケースに **「`required_tables.txt` に `notice_categories` / `notices` / `notice_reads` の 3 行が存在する」** ことを CI で検証するアサーションを追加（[ListRequiredTablesTest.java](../../amazia-core/src/test/java/) 等の既存テストパターンに合流）
- [ ] CD パイプラインの「主要テーブル存在確認」ステップが、デプロイ後の本番 MySQL に対して 3 テーブル存在をアサートできること

### 5-3. 全体テスト実行

- [ ] `cd Amazia/amazia-core && mvn test` 全件緑
- [ ] `cd Amazia/amazia-console && composer test` 全件緑
- [ ] `cd Amazia/amazia-console && npm run test:vitest` 全件緑（Vue 側）
- [ ] `cd Amazia/amazia-market && npm run test` 全件緑
- [ ] `docker compose down -v && docker compose up --build` で Core / Console / Market 連携起動成功

### 5-4. 手動 E2E（推奨）

設計書 §機能詳細 通りのユーザーフロー：

1. Console 社員ログイン → お知らせ新規作成（公開開始 = 当日、公開終了 = 当日 + 7日）
2. Market 会員ログイン → ヘッダーに新規お知らせが表示される（5秒ローテーション動作確認）
3. ヘッダークリック → お知らせ一覧画面へ遷移
4. 一覧で「重要」フィルタ → 重要のみ表示
5. 件名クリック → モーダル展開 → 既読登録 Ajax 発火確認（Network パネル）
6. モーダル「次のお知らせ」→ 自動既読登録
7. モーダルを閉じて再度同じお知らせを開く → 既読バッジ表示
8. Console から該当お知らせを論理削除 → Market 一覧から消える（既読履歴 `notice_reads` は維持されている）

### 5-5. Step D 完了条件

- [ ] 上記すべてのチェックリストが ✅
- [ ] 設計書 §「DB / API 設計書のメンテナンス（CLAUDE.md ルール）」のすべての項目が消化されている
- [ ] 親フェーズ（phase17）以降のテストカウントが減っていない（リグレッションなし）
- [ ] 設計書 r2 のレビュー指摘 R19-1〜R19-12 がすべて実装に反映されている

---

## 6. リスクと留意点

| リスク | 対策 |
|------|------|
| `ON DUPLICATE KEY UPDATE` の H2 / MySQL 互換差 | Step A-2-3-7 で実装方式（ネイティブクエリ vs Service 分岐）を確定。テストは H2 で動かす前提なので、Service 分岐方式が安全策 |
| 一覧 API の N+1 退化 | `LEFT JOIN notice_reads` を 1 クエリで書く。Hibernate の `@EntityGraph` ではなく明示的な JPQL or ネイティブクエリ |
| `ON UPDATE CURRENT_TIMESTAMP` の H2 非対応 | `@PreUpdate` で Java 側から `updated_at = LocalDateTime.now()` をセット |
| 既存 Console 認証ミドルウェアと `X-User-Id` の整合 | 既存 `RegisterInboundService` の Console ラッパー実装を参考に、`auth()->user()->id` を `X-User-Id` ヘッダにセット |
| Market `is_read` の Optional 表現 | `@JsonInclude(JsonInclude.Include.NON_ABSENT)` + `Optional<Boolean>` で実装。`null` ではなくキー自体を省略させる点に注意（テスト 2-6-2 で担保） |
| 公開期間判定の TZ ズレ | サーバ／DB の `Asia/Tokyo` 設定が前提（phase14 r3 と同方針）。海外展開・サマータイムはスコープ外であり、本フェーズで対応しない |
| `notice_reads` の物理削除と参照履歴維持 | 設計書通り、お知らせ論理削除時も `notice_reads` は残す。CASCADE DELETE は設定しない |
| ヘッダー Polling × ローテーションのレース | R19-7 の「次のローテーション境界で反映」方式を fake timer で TDD（Step C-4-4-3） |
| schema.sql の MySQL 専用構文混入 | Step A 着手前に test_insights カテゴリ7-2 を再確認し、`ON UPDATE CURRENT_TIMESTAMP` / `INDEX (...)` インライン宣言を持ち込まないこと |

---

## 7. 参照

- 設計書：[phase19_notice_management.md](../design/phase11_20/phase19_notice_management.md)（r2 / 2026-05-07）
- コーディング規約：[coding_guidelines.md](../coding_guidelines.md)
- 命名規約：[operation_logs_naming.md](../ai_context/operation_logs_naming.md)
- テスト知見：[test_insights.md](../ai_context/test_insights.md)
- 既存実装計画：[phase15_implementation_plan.md](phase15_implementation_plan.md)（同規模感の参考）
- 既存 `X-User-Id` パターン参考：[RegisterInboundController.java](../../amazia-core/src/main/java/com/example/inbound/controller/RegisterInboundController.java)
