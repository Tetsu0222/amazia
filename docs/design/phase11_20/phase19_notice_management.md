
# フェーズ19：お知らせ機能（改訂版 r1）

## ステータス
🔲 未着手

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 改訂日不明 | 初稿（Console / Market の概念設計と DB 2テーブル案） |
| r1 | 2026-05-07 | 実装可能レベルへの全面詳細化。Console/Core/Market の責務分割、API エンドポイント定義、`market_customers` 既読モデルへの修正（`notice_reads.deleted_flag` 廃止 → UNIQUE + 物理 DELETE）、`notice_categories` マスタ化、公開期間判定の JST 0:00 基準、自動切替の Polling / 一覧の N+1 回避、TDD 異常系の追加、設計書反映ルール（DB / API）の整備。 |
| r2 | 2026-05-07 | r1 レビュー指摘 R19-1〜R19-12 を反映。**operation_logs 書き込みは Core Service が直接行い、`X-User-Id` ヘッダで `actor users.id` を受け取る既存パターン（phase14 / phase15）に統一**。`api_name` は Core API パスを記録。「JST 0:00 基準」の表現を「JST タイムゾーン保存・リアルタイム DATETIME 比較」に書き換え、`publish_end` の 23:59:59 自動付与は Console 側 FormRequest の責務として明文化。PK 型ポリシーの注記、未認証時 `is_read` 省略、ローテーション×Polling 競合方針、DTO 分離（`NoticeMarketDto` / `NoticeConsoleDto`）、ER カーディナリティの記載、`required_tables.txt` 検証スクリプトの追加。 |

---

## 範囲
- Amazia Console（Laravel）：お知らせ登録・編集・削除画面、API
- Amazia Market（React）：ヘッダーお知らせ表示、お知らせ一覧画面、本文モーダル、未読数バッジ
- Amazia Core（Spring Boot）：お知らせ CRUD API、公開期間判定、未読数集計、既読登録 API
- DB 設計：`notices` / `notice_reads` / `notice_categories`（マスタ）の新規追加
- 通知・未読管理：`market_customers` 単位の未読／既読を集計バッジ表示

## 前提

- 既読主体は **Market 会員（`market_customers.id`）** に限定する。Console 社員（`users.id`）の既読履歴はスコープ外。
- 投稿者は **Console 社員（`users.id`）**。Market 側 API レスポンスには `author_id` を含めない（既存設計書の「投稿者は Market に表示しない」を API レイヤーで担保）。
- お知らせの分類は「重要」「普通」の2値だが、**将来増やす可能性があるためマスタ化（`notice_categories`）** する。
- 公開期間（`publish_start` / `publish_end`）は **JST タイムゾーンで保存・比較**する。判定は `now() BETWEEN publish_start AND publish_end` のリアルタイム DATETIME 比較。`publish_end` は Console 側 FormRequest が日付入力を受け取った場合に **`23:59:59` を自動付与**してから Core API へ送る（境界値の正規化は Console の責務、Core は受け取った DATETIME 値そのままで比較）。サーバ／DB の `Asia/Tokyo` 設定と整合。サマータイム・海外展開はスコープ外（phase14 r3 と同方針）。
- DB 初期化は `amazia-core/src/main/resources/schema.sql`（`spring.sql.init.mode=always`）方式に従う（CLAUDE.md「マイグレーション方式の前提」）。`db/migration/V*.sql` は名残ファイルで本番では実行されない。
- Console は Laravel マイグレーション方式は採らない（管理画面側に DB を持たないため）。Console は Core API 越しでお知らせを操作する。
- **`operation_logs` の書き込みは Core Service が直接行う**。Console は Core API 呼び出し時に **`X-User-Id` ヘッダで actor の `users.id` を渡す**（既存 `RegisterInboundController` 等と同方式）。Console から Core に「operation_logs を書け」エンドポイントを叩く構成は採らない（R19-3 / R19-4）。

## PK 型ポリシー（R19-1）

新設テーブルの PK / FK 型は以下の方針で確定する：

| ケース | 型 | 例 |
|------|----|-----|
| Core 新規テーブル PK | `BIGINT` | `notices.id` / `notice_categories.id` / `notice_reads.id` |
| 同 Core 内テーブル間の FK | `BIGINT` | `notices.category_id` → `notice_categories.id`、`notice_reads.notice_id` → `notices.id` |
| Laravel 由来 / Market 系テーブルの PK を参照する FK | `BIGINT UNSIGNED` | `notices.author_id` → `users.id`、`notice_reads.market_customer_id` → `market_customers.id` |

`users` / `market_customers` が `BIGINT UNSIGNED`（TBL_users.md / TBL_market_customers.md 参照）であるため、これらを参照する FK は型を合わせる必要がある（037 起因の型不整合事故防止）。Core 内部は `BIGINT` で統一する。

## 既存活用テーブル一覧（カテゴリ3「既存実装の棚卸し」対応）

| テーブル | 用途 | フェーズ |
|---------|------|---------|
| `users` | Console 社員（投稿者 `notices.author_id` の参照先） | phase11 |
| `market_customers` | Market 会員（`notice_reads.market_customer_id` の参照先） | phase13 |
| `operation_logs` | お知らせ登録／編集／削除の監査ログ | phase14 |

新設は **`notices` / `notice_reads` / `notice_categories`** の3テーブルのみ。

---

# 実装段取り（Step A → C）

phase19 は他フェーズとの相互依存が薄いため、Step A → B → C の3段で完結する。

| Step | 対象 | 主な作業 | 完了条件 |
|------|------|---------|---------|
| **Step A** | Core 側スキーマ・API | `notice_categories` / `notices` / `notice_reads` を `schema.sql` に追加。Core API（CRUD・公開中一覧・未読数・既読登録）を実装。`@RestControllerAdvice` のエラーハンドリング込み（test_insights カテゴリ2 / 021） | Core 単体で `/api/notices` 系がテスト緑 |
| **Step B** | Console（Laravel） | お知らせ登録・編集・削除画面、Console 用 API（Core 経由）、`operation_logs` 記録 | Console 画面でお知らせ CRUD ができ、`operation_logs` に記録されている |
| **Step C** | Market（React） | ヘッダーお知らせ（自動切替）、お知らせ一覧、本文モーダル、未読数バッジ、既読 Ajax | Market でログイン会員が一連の閲覧操作を完了でき、未読／既読が DB と一致 |

実装担当者は **必ず Step A → B → C の順序**で進める。Step A の Core API が確定する前に Console / Market を着手すると、契約変更で手戻りが発生する。

---

# 機能詳細

## 🖥 Amazia Console（管理画面）

### お知らせ一覧画面

- 表示項目（管理者向け）
  - ID
  - 件名
  - 分類（重要 / 普通）
  - 投稿者（`users.name`）
  - 公開開始日 / 公開終了日
  - 更新日時
  - 公開状態（公開前 / 公開中 / 公開終了 / 削除済）※サーバ判定で文字列を返す
- 並び順：更新日時 降順
- フィルタ：分類 / 公開状態 / 期間（更新日時）
- 1ページあたり 20件、ページング
- 「新規作成」「編集」「削除」操作

### お知らせ登録／編集画面

入力項目:

| 項目 | 必須 | バリデーション |
|------|------|---------------|
| 件名（subject） | ○ | 1〜255文字 |
| 分類（category_id） | ○ | `notice_categories.id` に存在 |
| 本文（body） | ○ | 1〜10000文字 |
| 公開開始日（publish_start） | ○ | DATETIME。`publish_start <= publish_end` |
| 公開終了日（publish_end） | ○ | DATETIME。`publish_start <= publish_end` |
| 投稿者（author_id） | ― | ログイン中の Console 社員から自動セット |

- バリデーションは Laravel `FormRequest` ＋ `config/app/Notice.php` 駆動（規約 1-2 / 3-1）。
- 投稿者は Market 側に表示しない（API レスポンスに含めない）。
- 削除は **論理削除（後述）**。Console 一覧では「削除済」フラグ付きで一覧表示可能（フィルタで切替）。

### Console から Core への呼び出し

Console は `App\Notice\Service\*` 経由で Core API を呼び出す（規約 1-1）。

| Console 操作 | Core API | 認証 |
|-------------|----------|------|
| 一覧取得 | `GET /api/notices?include_unpublished=true&include_deleted=true` | Console 社員 JWT |
| 詳細取得 | `GET /api/notices/:id` | 同上 |
| 新規作成 | `POST /api/notices` | 同上 |
| 編集 | `PUT /api/notices/:id` | 同上 |
| 削除 | `DELETE /api/notices/:id`（論理削除） | 同上 |

### 操作履歴（operation_logs）

`operation_logs` 記録対象（`docs/ai_context/operation_logs_naming.md` に従う）:

| 操作 | action | screen_name | api_name |
|------|--------|-------------|----------|
| 新規作成 | `create_notice` | `console.notice.create` | `POST /api/notices` |
| 編集 | `update_notice` | `console.notice.edit` | `PUT /api/notices/:id` |
| 削除 | `delete_notice` | `console.notice.list` | `DELETE /api/notices/:id` |

- target_type = `notices` / target_id = `notices.id`
- comment にはプレフィックスなしで「件名：xxx」等の補足を記録

> 命名規約 §6 に Console 起点として上記3行を追記する（phase19 r1 の追加観点）。

---

## 🛒 Amazia Market（ユーザー側）

### ヘッダー：お知らせ自動切替

- 全画面のヘッダーに「お知らせ」エリアを表示。
- アコーディオン形式（折りたたみ／展開）。
- 表示対象：**ログイン会員の未読のうち、公開期間内かつ未削除のお知らせ**を最大10件取得し、5秒ごとに切り替え表示。
- 表示内容：件名（必要に応じて本文の先頭をプレビュー）。文字数オーバーは `text-overflow: ellipsis`。
- クリック → お知らせ一覧画面へ遷移。
- 未読が 0 件の場合：エリア自体は表示せず（または「お知らせはありません」を1件表示）。
- 自動切替の実装方式：**JS の `setInterval(5000)`（外部ライブラリは導入しない／無料枠最優先）**。Swiper 等は将来課題。

### お知らせ一覧画面

- 公開期間内（`publish_start <= now() <= publish_end`）かつ `deleted_at IS NULL` のレコードのみ表示。
- 並び順：分類（重要 → 普通）→ 公開開始日 降順
- 表示項目：件名 / 分類 / 更新日時 / 既読・未読バッジ
- 1ページあたり 20件、ページング
- フィルタ：「重要」「普通」「すべて」のタブ式
- バッジ表示
  - **重要：未読件数（赤色）**
  - **普通：未読件数（青色）**
  - 未読 0 ならバッジ非表示

### お知らせ本文（モーダル）

- 一覧から件名クリック → モーダル展開。
- モーダル内表示項目：件名 / 分類 / 本文 / 更新日時 / 公開期間
- 投稿者（`author_id`）は表示しない。
- モーダルが開いた瞬間、**未読なら既読登録 Ajax を発火**（後述）。
- 「次のお知らせ」「前のお知らせ」ボタン
  - 一覧画面で表示中の並び順に従って遷移
  - 端（先頭／末尾）ではボタンを非活性化
  - 遷移時にも自動既読登録

### 既読登録（Ajax）

- API：`POST /api/customer/notices/:id/read`（Market 認証必須）
- 動作：
  - `notice_reads(notice_id, market_customer_id)` の UNIQUE に対する `INSERT ... ON DUPLICATE KEY UPDATE` で冪等化。
  - 200 を返す（既に既読でもエラーにしない）。
- 公開期間外のお知らせに対する既読登録：404 で拒否（後述「公開期間外のレコード保護」）。
- 削除済お知らせに対する既読登録：404 で拒否。

---

# DB 設計（追加）

## notice_categories テーブル（新規：分類マスタ）

| カラム名 | 論理名 | 型 | NULL | デフォルト | 備考 |
|---------|--------|-----|------|-----------|------|
| id | 分類ID | BIGINT | NOT NULL | AUTO_INCREMENT | PK |
| code | コード | VARCHAR(20) | NOT NULL | - | UNIQUE。`important` / `normal` |
| label | 表示名 | VARCHAR(50) | NOT NULL | - | UI 表示用の日本語名 |
| display_order | 表示順 | INT | NOT NULL | 0 | 一覧ソート用（昇順） |

### 初期データ（schema.sql で INSERT IGNORE）

| id | code | label | display_order |
|----|------|-------|---------------|
| 1 | important | 重要 | 1 |
| 2 | normal | 普通 | 2 |

### 設計上の注意

- マスタ ID は `application.properties > amazia.notice.categories.*-id` および Console `config('notice.categories.*_id')` と整合させる（規約 3-1 / 4-1）。
- レコードは Console 画面からの登録対象ではない。スキーマ起動時のシード投入のみ。
- 将来分類追加時はマスタへ INSERT し、各システムの config を更新するだけで対応可能。

---

## notices テーブル（新規：お知らせ）

| カラム名 | 論理名 | 型 | NULL | デフォルト | 備考 |
|---------|--------|-----|------|-----------|------|
| id | お知らせID | BIGINT | NOT NULL | AUTO_INCREMENT | PK |
| subject | 件名 | VARCHAR(255) | NOT NULL | - | 1〜255文字 |
| category_id | 分類ID | BIGINT | NOT NULL | - | FK → `notice_categories.id` |
| body | 本文 | TEXT | NOT NULL | - | 1〜10000文字 |
| author_id | 投稿者 | BIGINT UNSIGNED | NOT NULL | - | FK → `users.id`。Market 表示は禁止 |
| publish_start | 公開開始日時 | DATETIME | NOT NULL | - | JST 保存。Console 側 FormRequest が日付入力時は `00:00:00` を自動付与 |
| publish_end | 公開終了日時 | DATETIME | NOT NULL | - | JST 保存。Console 側 FormRequest が日付入力時は `23:59:59` を自動付与。`publish_start <= publish_end`（CHECK 制約） |
| deleted_at | 論理削除日時 | DATETIME | NULL | NULL | NULL なら有効レコード |
| created_at | 作成日時 | DATETIME | NOT NULL | CURRENT_TIMESTAMP | |
| updated_at | 更新日時 | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | |

### 制約

- `CHECK (publish_start <= publish_end)`
- FK：`category_id` → `notice_categories.id`、`author_id` → `users.id`

### インデックス

| インデックス名 | 種別 | カラム | 用途 |
|---------------|------|--------|------|
| PRIMARY | PRIMARY KEY | id | |
| idx_notices_publish_period | INDEX | (publish_start, publish_end) | 公開期間判定 |
| idx_notices_category_id | INDEX | category_id | 分類フィルタ |
| idx_notices_deleted_at | INDEX | deleted_at | 論理削除絞り込み |
| idx_notices_author_id | INDEX | author_id | 投稿者検索（Console 用） |

### 設計上の注意

- 投稿者 `author_id` は **Market API レスポンスに絶対に含めない**（API DTO レイヤーで除外）。Console API では返す。
- 論理削除採用理由：Console から「過去のお知らせ」を参照／復元できる運用を将来的に持たせるため。物理削除は採らない。
- `body` は最大 10000 文字（VARCHAR ではなく `TEXT` 型）。HTML 直挿入は禁止し、Market 側はプレーンテキスト表示＋改行→`<br>` 変換のみ。XSS 防止（OWASP A03）。

---

## notice_reads テーブル（新規：既読管理）

| カラム名 | 論理名 | 型 | NULL | デフォルト | 備考 |
|---------|--------|-----|------|-----------|------|
| id | ID | BIGINT | NOT NULL | AUTO_INCREMENT | PK |
| notice_id | お知らせID | BIGINT | NOT NULL | - | FK → `notices.id` |
| market_customer_id | 会員ID | BIGINT UNSIGNED | NOT NULL | - | FK → `market_customers.id` |
| read_at | 既読日時 | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 既読登録時刻 |
| created_at | 作成日時 | DATETIME | NOT NULL | CURRENT_TIMESTAMP | |

### 制約

- `UNIQUE (notice_id, market_customer_id)` ：同一会員が同一お知らせを複数回既読登録できない。
- FK：`notice_id` → `notices.id`、`market_customer_id` → `market_customers.id`

### インデックス

| インデックス名 | 種別 | カラム | 用途 |
|---------------|------|--------|------|
| PRIMARY | PRIMARY KEY | id | |
| uk_notice_reads_notice_customer | UNIQUE | (notice_id, market_customer_id) | 重複登録防止 + 未読数集計 |
| idx_notice_reads_market_customer_id | INDEX | market_customer_id | 会員別未読数の集計 |

### 設計判断：`deleted_flag` 廃止

初版設計の `deleted_flag TINYINT`（論理削除で「未読扱い」）は採用しない。理由:

- 「未読 → 既読 → 再度未読」というユースケースが要件にない（設計書本文の「レコードが論理削除されていれば未読扱い」は実運用で発火しない）。
- `deleted_flag` を導入すると `UNIQUE (notice_id, market_customer_id)` が機能せず、既読登録 Ajax の冪等性を Service 層で別ロジックで担保する必要がある。
- 将来「既読を解除する」要件が現れた場合のみ、`deleted_at DATETIME NULL` を追加し UNIQUE を `(notice_id, market_customer_id, deleted_at)` 含む生成列に切り替える。**それまでは導入しない（YAGNI）**。

### `market_customer_id` の型整合（037 起因）

- `market_customers.id` は **BIGINT UNSIGNED**（TBL_market_customers.md 参照）。
- `notice_reads.market_customer_id` も **BIGINT UNSIGNED** で型を揃える。FK 制約を有効化するため。
- Core 側 Entity の Java 型は `Long`（型不整合の混在に注意）。

---

# Core API 仕様（フェーズ19）

ベースURL：`/api`

## 1. お知らせ作成（Console 用）

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/notices` |
| 認証 | Console 社員 JWT（Console 側で検証） + `X-User-Id` ヘッダ（Core 側が actor 識別に使用） |
| Controller | `notice/controller/CreateNoticeController` |

**リクエストヘッダ**

| ヘッダ | 必須 | 説明 |
|--------|------|------|
| X-User-Id | ○ | Console で認証された社員の `users.id`。Core はこの値を `operation_logs.user_id` に記録（既存 `RegisterInboundController` と同方式 / R19-3） |

**リクエストボディ**

| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| subject | string | ○ | 1〜255文字 |
| category_id | long | ○ | `notice_categories.id` に存在 |
| body | string | ○ | 1〜10000文字 |
| publish_start | datetime | ○ | ISO8601。Console 側で `00:00:00` 補完済み |
| publish_end | datetime | ○ | ISO8601。Console 側で `23:59:59` 補完済み。`publish_start <= publish_end` |

> `author_id` はリクエストボディに含めない。Core は `X-User-Id` の値をそのまま `notices.author_id` に保存する（投稿者なりすまし防止）。

**動作**：作成 SUCCESS 時、Core は `notices` INSERT と同一トランザクションで `operation_logs(action='create_notice', user_id=X-User-Id, target_type='notices', target_id=新ID, screen_name='console.notice.create', api_name='POST /api/notices')` を記録する。

**レスポンス（201）**：作成された notice（後述「Console 用レスポンス DTO」）

**エラー**：400（バリデーション） / 401（未認証＝`X-User-Id` 欠落 or 不正） / 422（`publish_start > publish_end` など）

---

## 2. お知らせ更新（Console 用）

| 項目 | 内容 |
|------|------|
| メソッド | PUT |
| パス | `/api/notices/:id` |
| 認証 | Console 社員 JWT + `X-User-Id` ヘッダ |
| Controller | `notice/controller/UpdateNoticeController` |

**リクエスト・レスポンスは作成 API と同様**。

**動作**：UPDATE と同一トランザクションで `operation_logs(action='update_notice', user_id=X-User-Id, target_type='notices', target_id=id, screen_name='console.notice.edit', api_name='PUT /api/notices/:id')` を記録。

**エラー**：404（未存在） / 410（論理削除済）/ 422（バリデーション） / 401（`X-User-Id` 欠落）

---

## 3. お知らせ削除（Console 用：論理削除）

| 項目 | 内容 |
|------|------|
| メソッド | DELETE |
| パス | `/api/notices/:id` |
| 認証 | Console 社員 JWT + `X-User-Id` ヘッダ |
| Controller | `notice/controller/DeleteNoticeController` |

**動作**：`UPDATE notices SET deleted_at = NOW() WHERE id = :id AND deleted_at IS NULL` と同一トランザクションで `operation_logs(action='delete_notice', user_id=X-User-Id, target_type='notices', target_id=id, screen_name='console.notice.list', api_name='DELETE /api/notices/:id')` を記録。

**エラー**：404（未存在） / 410（既に削除済）/ 401（`X-User-Id` 欠落）

---

## 4. お知らせ一覧取得（Console 用 + Market 用 共通エンドポイント）

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/notices` |
| 認証 | Console 社員 JWT または Market 会員セッション |
| Controller | `notice/controller/ListNoticeController` |

**クエリパラメータ**

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| include_unpublished | bool | false | 公開期間外も含むか（Console 専用） |
| include_deleted | bool | false | 論理削除済を含むか（Console 専用） |
| category_id | long | - | 分類フィルタ |
| page | int | 1 | ページ番号 |
| per_page | int | 20 | ページサイズ（最大 100） |

- `include_unpublished` / `include_deleted` は **Console 社員 JWT のときのみ有効**。Market 会員セッションでこれらが指定されても無視（常に `false` 扱い）。
- Market では `now() BETWEEN publish_start AND publish_end AND deleted_at IS NULL` のみ返す。
- 並び順：`category_id` 昇順（重要 → 普通）→ `publish_start` 降順 → `id` 降順。

**レスポンス例（Market 用 DTO）**
```json
{
  "data": [
    {
      "id": 1,
      "subject": "メンテナンスのお知らせ",
      "category": { "id": 1, "code": "important", "label": "重要" },
      "body": "...",
      "publish_start": "2026-05-07T00:00:00+09:00",
      "publish_end": "2026-05-14T23:59:59+09:00",
      "updated_at": "2026-05-07T10:00:00+09:00",
      "is_read": false
    }
  ],
  "meta": { "page": 1, "per_page": 20, "total": 35 }
}
```

- `is_read` は **Market 会員セッション時のみ返す**。Console アクセス時・Market 未認証アクセス時は **`is_read` キー自体をレスポンスから完全に省略**する（R19-9）。クライアント側は `'is_read' in item` で存在チェックする。
- Console 用 DTO のみ `author` フィールドを含める：`{ "id": 1, "name": "山田太郎" }`。**Market では絶対に含めない**。

### DTO 分離（R19-11）

Service 層で **`NoticeMarketDto`（Market 用）と `NoticeConsoleDto`（Console 用）を別クラスとして分離**する。Controller で if 分岐して同一 DTO のフィールドを出し分ける構成は採らない（漏洩事故防止）。

| DTO | フィールド | 利用 API |
|-----|-----------|---------|
| `NoticeMarketDto` | id / subject / category / body / publish_start / publish_end / updated_at / (is_read：会員時のみ) | 4・5・8 の Market 視点呼び出し |
| `NoticeConsoleDto` | id / subject / category / body / publish_start / publish_end / created_at / updated_at / deleted_at / author / publish_state | 4・5 の Console 視点呼び出し |

`author` フィールドの型は `NoticeConsoleDto` 側にのみ存在し、`NoticeMarketDto` のクラス定義にコンパイル時点で存在しない。

---

## 5. お知らせ詳細取得

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/notices/:id` |
| 認証 | Console 社員 JWT または Market 会員セッション |
| Controller | `notice/controller/GetNoticeController` |

- Market では公開期間内かつ未削除のみ取得可能。それ以外は 404。
- Console は `?include_unpublished=true&include_deleted=true` で全件参照可能。

---

## 6. 未読数取得（Market 専用）

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/notices/unread-count` |
| 認証 | Market 会員セッション |
| Controller | `notice/controller/GetUnreadCountController` |

**レスポンス例**
```json
{
  "data": {
    "important": 2,
    "normal": 5,
    "total": 7
  }
}
```

**集計クエリ（擬似 SQL）**
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
GROUP BY nc.code;
```

- `:now` は **現在の JST 日時（DATETIME）**。境界値の整合は `publish_start` / `publish_end` 値そのもので表現する（Console 側で `00:00:00` / `23:59:59` を自動付与済み）。Core では `now() BETWEEN publish_start AND publish_end` でそのまま比較する（R19-2 / R19-5）。
- インデックスは `(publish_start, publish_end)` と `notice_reads(notice_id, market_customer_id)` の UNIQUE で十分。

---

## 7. 既読登録（Market 専用）

| 項目 | 内容 |
|------|------|
| メソッド | POST |
| パス | `/api/customer/notices/:id/read` |
| 認証 | Market 会員セッション |
| Controller | `notice/controller/MarkAsReadController` |

**動作（擬似コード）**
```
function markAsRead(notice_id, market_customer_id):
    notice = findActivePublished(notice_id)   // 公開期間内 AND deleted_at IS NULL
    if notice is null:
        throw 404 NotFound
    INSERT INTO notice_reads (notice_id, market_customer_id, read_at)
    VALUES (:notice_id, :customer_id, NOW())
    ON DUPLICATE KEY UPDATE read_at = read_at  -- 冪等
    return 200
```

- 公開期間外・削除済・存在しない notice_id → **404**（既読登録できない）。
- 同一会員が同一 notice を複数回叩いても 200（冪等）。

---

## 8. ヘッダー用：未読お知らせ取得（Market 専用）

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/customer/notices/unread` |
| 認証 | Market 会員セッション |
| Controller | `notice/controller/GetUnreadHeaderNoticesController` |

- ヘッダー自動切替用。最大 **10件**。
- 並び順：`category_id` 昇順 → `publish_start` 降順
- レスポンスは一覧 API と同じ DTO（`author` 含まず）。

---

# Console API 仕様（フェーズ19）

Console は Core API の薄いラッパー（規約 1-1：Controller→Service→Core API 呼び出し）。

| メソッド | パス | 説明 | 認証 | Core 呼び出し |
|---------|------|------|------|---------------|
| GET | `/api/admin/notices` | 一覧取得（`include_unpublished=true&include_deleted=true` で Core を呼ぶ） | Console 社員 | `GET /api/notices?include_unpublished=true&include_deleted=true` |
| POST | `/api/admin/notices` | 新規作成 | Console 社員 | `POST /api/notices`（`X-User-Id` 付与） |
| GET | `/api/admin/notices/:id` | 詳細取得 | Console 社員 | `GET /api/notices/:id?include_unpublished=true&include_deleted=true` |
| PUT | `/api/admin/notices/:id` | 編集 | Console 社員 | `PUT /api/notices/:id`（`X-User-Id` 付与） |
| DELETE | `/api/admin/notices/:id` | 論理削除 | Console 社員 | `DELETE /api/notices/:id`（`X-User-Id` 付与） |
| GET | `/api/notice-categories` | 分類マスタ取得 | 不要 | `GET /api/notice-categories` |

- バリデーションは `App\Notice\Request\StoreNoticeRequest` / `UpdateNoticeRequest` の FormRequest。Core に送る前に **`publish_start` / `publish_end` の時分秒補完**（日付のみ入力なら `00:00:00` / `23:59:59` を付与）を Service 層で実施。
- Core 呼び出し時、Service 層で **`X-User-Id` ヘッダに `auth()->user()->id` をセット**して送信する（既存 `App\Inbound\Service\RegisterInboundService` と同方式）。
- ルートは `routes/api/Notice.php` を新設し、`routes/api.php` で `require __DIR__.'/api/Notice.php';` で明示的に読み込む（規約 補足4）。
- config は `config/app/Notice.php` を新設し、`config/app.php` から `'notice' => require __DIR__.'/app/Notice.php',` で読み込む（規約 補足3）。
- **Console は `operation_logs` への直接 INSERT は行わない**。書き込みは Core 側 Service（`CreateNoticeService` / `UpdateNoticeService` / `DeleteNoticeService`）の責務（R19-3 / R19-4）。

---

# Market API 呼び出し（フェーズ19）

| 操作 | API | 認証 |
|------|------|------|
| ヘッダー用未読取得 | `GET /api/customer/notices/unread` | Market 会員 |
| 一覧取得 | `GET /api/notices?category_id=...` | Market 会員 or 不要（公開期間内のみ） |
| 詳細取得 | `GET /api/notices/:id` | 同上 |
| 既読登録 | `POST /api/customer/notices/:id/read` | Market 会員 |
| 未読数取得 | `GET /api/customer/notices/unread-count` | Market 会員 |

- React 側は `src/api/notice.ts` に集約（規約 5「フロントエンド」）。
- ヘッダー自動切替の Polling 間隔：**60秒（未読数）**。これと別にローカル `setInterval(5000)` で表示順をローテーションする。

### ヘッダー Polling × ローテーションの競合方針（R19-7）

`useUnreadCount`（60秒 Polling）と `useHeaderNotices` のローテーション（5秒）は別ロジックであるため、Polling で未読リストが入れ替わったタイミングで「現在表示中のお知らせが消える」ケースが発生し得る。

採用方針：**Polling 結果は次のローテーション境界（5秒サイクルの次の切替時）で反映する**。

実装：Polling で取得した新しい未読リストは React state の「次回適用バッファ」に格納し、現在の `setInterval(5000)` のコールバックで `currentList = nextList; nextList = null;` として切替時に差し替える。これにより：

- 表示中のお知らせが視認可能な時間（5秒）の途中で消えない
- バッファ切替のタイミングで「次のお知らせ」がいきなり古いリストの 2 番目から始まらず、新リストの先頭から表示される

例外：未読数が **0 件**になった瞬間は即時にヘッダーを非表示化する（古い未読を表示し続けるのは UX 上不自然）。

---

# フォルダ構成（実装ガイド）

## Core（Spring Boot）
```
/main/java/com/example/amazia/notice
  /controller
    CreateNoticeController.java
    UpdateNoticeController.java
    DeleteNoticeController.java
    GetNoticeController.java
    ListNoticeController.java
    MarkAsReadController.java
    GetUnreadCountController.java
    GetUnreadHeaderNoticesController.java
  /service
    CreateNoticeService.java
    UpdateNoticeService.java
    DeleteNoticeService.java
    GetNoticeService.java
    ListNoticeService.java
    MarkAsReadService.java
    GetUnreadCountService.java
  /entity
    Notice.java
    NoticeCategory.java
    NoticeRead.java
  /repository
    NoticeRepository.java
    NoticeCategoryRepository.java
    NoticeReadRepository.java
  /validator
    NoticePeriodValidator.java
```

## Console（Laravel）
```
/app/Notice
  /Controller
    ListNoticeController.php
    CreateNoticeController.php
    UpdateNoticeController.php
    DeleteNoticeController.php
    GetNoticeController.php
  /Service
    ListNoticeService.php
    CreateNoticeService.php
    UpdateNoticeService.php
    DeleteNoticeService.php
    GetNoticeService.php
  /Request
    StoreNoticeRequest.php
    UpdateNoticeRequest.php
/config/app/Notice.php
/routes/api/Notice.php
/resources/vue/features/Notice
  /api
    notice.js
  /pages
    NoticeList.vue
    NoticeForm.vue
```

## Market（React）
```
/src/features/notice
  /api
    notice.ts
  /components
    HeaderNotice.tsx
    NoticeListPage.tsx
    NoticeModal.tsx
    UnreadBadge.tsx
  /hooks
    useUnreadCount.ts
    useHeaderNotices.ts
```

---

# 技術検討事項

- **ヘッダー自動切替の実装方式**：`setInterval(5000)` を採用（Swiper 等の外部ライブラリは導入しない／無料枠最優先）。アクセシビリティのため `aria-live="polite"` を付与。
- **未読管理のパフォーマンス**：会員数が増えた場合、`notice_reads` の `(notice_id, market_customer_id)` UNIQUE インデックスで `LEFT JOIN ... WHERE nr.id IS NULL` が効率的に動作する。1万会員 × 1000お知らせ程度までは問題ない想定。それ以上は将来課題（マテリアライズドビュー等）。
- **公開期間判定はリアルタイム**：phase14 r3 と同じく **JST 0:00 基準**でリアルタイム判定。バッチ処理は導入しない。
- **モーダル前後遷移**：一覧画面で取得したリストを React state に保持し、現在 ID のインデックス±1 で遷移。API 再呼び出しは行わない（N+1 回避）。
- **重要・普通の分類拡張**：`notice_categories` マスタ化により、分類追加は INSERT のみで対応可。`config('notice.categories')` も追記。
- **アクセシビリティ**：ヘッダー自動切替は `aria-live="polite"`、モーダルは `role="dialog"` / `aria-labelledby` を付与。スクリーンリーダー対応。
- **XSS 対策**：本文はプレーンテキスト保存・表示。改行のみ `<br>` 変換。HTML タグは React の自動エスケープに任せる（`dangerouslySetInnerHTML` 禁止）。
- **N+1 対策**：一覧 API は `notices` ↔ `notice_categories` を JOIN で1クエリにまとめる。`is_read` 計算は `LEFT JOIN notice_reads` の存在判定で1クエリ。
- **将来課題**：
  - 既読解除（`deleted_at` カラムの導入）
  - お知らせ通知のメール／Push 連携
  - 多言語対応（`notice_translations` テーブル）
  - 添付ファイル対応
  - 重要度のさらに細かい分類（`urgent` / `info` 等）

---

# TDD テストケース

## Amazia Core / JUnit

### 正常系
- お知らせ CRUD（登録・取得・更新・論理削除）が正しく動作する
- 公開期間内のお知らせのみ Market 用エンドポイントから取得できる
- 公開期間境界値（`publish_start == now()`, `publish_end == now()`）で含まれる
- JST 0:00 境界（`publish_start = '2026-05-07T00:00:00+09:00'`）で 2026-05-07 00:00:00 JST に取得可能
- 未読会員に対してのみ未読カウントが返る
- 既読登録 API が冪等に動作する（同じ会員が同じ notice を 5 回叩いて `notice_reads` レコードは 1 行）
- 未読数集計が「重要 / 普通 / total」で正しく分類される
- 一覧 API で公開期間外・論理削除済が **Market 視点では返らず**、**Console 視点では `include_unpublished=true&include_deleted=true` で返る**
- ヘッダー用未読取得が最大 10 件で打ち切られる
- お知らせ削除時、関連する `notice_reads` は維持される（参照履歴として残す）

### 異常系
- 公開期間外（公開前 / 公開終了後）のお知らせを Market から取得 → 404
- 論理削除済のお知らせに対する既読登録 → 404
- 存在しない notice_id への既読登録 → 404
- `publish_start > publish_end` の登録 → 422（DB CHECK 制約 + Service バリデーション 二重防御）
- `subject` が 256 文字以上 → 422
- `body` が 10001 文字以上 → 422
- 存在しない `category_id` → 422（**Service 事前チェックで先に検出**して `ResponseStatusException(422, "category not found")` を投げる。Repository から `DataIntegrityViolationException` が観測される経路には到達しないことをテストで担保 / R19-6）
- 不正な `X-User-Id`（DB に存在しない `users.id`）→ 422（Service 事前チェック。`notices.author_id` の FK 違反まで届かない）
- Market 会員セッションで `include_unpublished=true` を指定しても無視される（公開期間内のみ返る）
- Console 社員 JWT がないのに `POST /api/notices` を叩いた場合 → 401
- 既読登録時の DB UNIQUE 違反は ON DUPLICATE KEY UPDATE で吸収され例外を投げない
- `@RestControllerAdvice`（GlobalExceptionHandler）により 422 のエラー詳細が `errors[].field` / `errors[].message` で返る（test_insights カテゴリ2 / 021）

### Market 投稿者非表示の検証
- Market 一覧 / 詳細 API のレスポンス JSON に **`author_id` / `author` フィールドが含まれない**ことを確認
- Console 一覧 / 詳細 API のレスポンス JSON には `author: { id, name }` が含まれることを確認
- DTO クラスとして `NoticeMarketDto` には `author` フィールドが定義されていない（コンパイル時保証）

### 未認証時の `is_read` 省略（R19-9）
- Market 未認証アクセスでの一覧 API レスポンス JSON に **`is_read` キー自体が存在しない**（`null` 値ではなく省略）
- Market 会員セッション時のレスポンスには `is_read` キーが必ず存在する
- Console アクセスでの一覧 API レスポンス JSON にも `is_read` キーが存在しない

### テスト値の config 経由化（規約 4-1）
- `notice_categories` のマスタ ID（important_id / normal_id）を `application-test.properties > amazia.notice.categories.*-id` から取得
- ヘッダー用未読取得の上限件数（10件）を `application-test.properties > amazia.notice.header.max-items` から取得
- ヘッダー Polling 間隔（60秒）を Market 側 `.env.test` の `VITE_NOTICE_UNREAD_POLL_MS` から取得

---

## Amazia Console / PHPUnit

### 正常系
- お知らせ登録画面のバリデーションが正しく動作する（subject / body の長さ・category_id の存在）
- 公開期間の設定が正しく保存される
- 編集画面で既存値が初期表示される
- 論理削除されたお知らせは「公開状態：削除済」で一覧に表示される
- 投稿者は **Console 一覧では `users.name` で表示**、Console 詳細にも表示される

### 異常系
- `publish_start > publish_end` で登録 → 422 + エラー詳細
- 必須項目欠落で 422
- Console 社員未認証で 401
- Core API が 500 を返した場合の Console 側エラーハンドリング
- Core API が 422 を返した場合に Console 側もそのまま 422 で返す（エラー詳細を保持）

### config 駆動の検証
- `config('notice.categories.important_id')` が phpunit.xml の値と一致
- `config('notice.body_max_length')` が phpunit.xml の値（10000）と一致

### Console から Core への呼び出し（R19-3 / R19-4）
- POST `/api/admin/notices` が Core に対し `X-User-Id` ヘッダ付きで `POST /api/notices` を呼び出す（Mock で検証）
- Console 自身は `operation_logs` への INSERT を行わない（DB アサーション）
- Console 側 FormRequest が `publish_start='2026-05-07'`（時分秒なし）の入力を `2026-05-07T00:00:00+09:00` に補完してから Core へ送る
- 同じく `publish_end='2026-05-07'` の入力を `2026-05-07T23:59:59+09:00` に補完する

---

## Amazia Market / PHPUnit + React Testing Library

### 正常系
- ヘッダーのお知らせがアコーディオンで表示される
- ヘッダーで未読が複数ある場合、`setInterval(5000)` で表示が切り替わる（タイマーモック）
- 未読が 0 件のときヘッダーエリアが非表示（または「お知らせはありません」）
- 一覧画面で「重要 / 普通 / すべて」のフィルタが動作する
- 一覧画面の並び順が「重要 → 普通 → 公開開始日降順」
- 本文モーダルを開いた瞬間に既読登録 Ajax が発火する
- 既読登録後に未読数バッジが減る（ローカル state 反映 + 60 秒後の Polling で再同期）
- モーダル「次のお知らせ」「前のお知らせ」が一覧の並び順で遷移する
- 端（先頭／末尾）でボタンが非活性化する
- レスポンス JSON に `author` / `author_id` が **含まれていない**ことを E2E で検証

### 異常系
- 未認証時に `POST /api/customer/notices/:id/read` が 401 を返した場合のフォールバック
- 公開期間外の notice_id に対する既読登録 Ajax → 404 + UI でエラートーストを表示せず黙殺
- ネットワークエラー時にヘッダーが「お知らせ取得失敗」を出さず黙殺（ログのみ）
- 本文に HTML タグが含まれた場合に **エスケープされて表示**される（XSS 防御）

### ヘッダー Polling × ローテーション競合（R19-7）
- 5秒ローテーション中に Polling で取得した新リストが、**現在の表示サイクル終了まで保留され、次のサイクルで切り替わる**
- 未読数が 0 になった瞬間はローテーション境界を待たず即時に非表示化される
- Polling と setInterval(5000) のタイミング干渉でレースコンディションが発生しない（fake timer でテスト）

### config 駆動の検証
- ヘッダー Polling 間隔が `import.meta.env.VITE_NOTICE_UNREAD_POLL_MS` で読まれる
- ヘッダー自動切替間隔が `import.meta.env.VITE_NOTICE_HEADER_ROTATE_MS`（5000）で読まれる

---

# DB / API 設計書のメンテナンス（CLAUDE.md ルール）

## 新規追加するテーブル定義書（`docs/database_design/`）

| ファイル名 | テーブル | 所属 |
|-----------|---------|------|
| `TBL_notice_categories.md` | notice_categories | Core |
| `TBL_notices.md` | notices | Core |
| `TBL_notice_reads.md` | notice_reads | Core |

`README.md` に「Core システム（お知らせ）※フェーズ19追加」のセクションを追加し、上記3行を追記する。`ER_diagram.md` の Mermaid 図にも `notices` / `notice_categories` / `notice_reads` のリレーションを追加。

### ER カーディナリティ（R19-12）

| 関係 | カーディナリティ | 説明 |
|------|----------------|------|
| `notice_categories` ↔ `notices` | 1:N | 1分類が複数のお知らせを持つ |
| `users` ↔ `notices` | 1:N | 1社員が複数のお知らせを投稿（`notices.author_id`） |
| `notices` ↔ `notice_reads` ↔ `market_customers` | M:N（`notice_reads` で解消） | 同一会員が同一お知らせを既読登録するのは1回限り（UNIQUE 制約） |

## 主要テーブル定数の同期（CLAUDE.md「主要テーブル定数の同期」ルール／phaseX-6 / 044 起因）

CD の「主要テーブル存在確認」ステップが参照する `ops/healthcheck/required_tables.txt` に、本フェーズで追加する Core テーブルを **同フェーズ内で必ず追記**する。漏らすと、本来検知すべき DDL 失敗（`continue-on-error` で潰されたもの）がデプロイ後の自動チェックをすり抜ける。

| 対象テーブル | 所属 | 追記要否 |
|-------------|------|---------|
| `notice_categories` | Core | ○ |
| `notices` | Core | ○ |
| `notice_reads` | Core | ○ |

PR レビュー時、`TBL_notice_*.md` の追加と `required_tables.txt` への追記が両方含まれているかを確認する。

### 検証スクリプト（R19-10）

PR チェック層で以下を自動検証する（`ops/healthcheck/check_required_tables_consistency.sh` 等を新設、または既存スクリプトに追加）：

```sh
# 設計書 README.md と required_tables.txt が同期しているか
# Core システム由来テーブルが README.md に列挙されているのに required_tables.txt に存在しない場合 exit 1
diff <(grep -E '^\| TBL_.*\.md \|' docs/database_design/README.md \
       | grep -E 'Core' \
       | sed -E 's/^\| \[(TBL_[^]]+)\.md\].*/\1/' \
       | sed 's/TBL_//' \
       | sort) \
     <(sort ops/healthcheck/required_tables.txt) \
     || { echo 'required_tables.txt と README.md(Core) の差分あり'; exit 1; }
```

phase19 のテストケースに **「`required_tables.txt` に `notice_categories` / `notices` / `notice_reads` の 3 行が存在する」** ことを CI で検証するアサーションを追加する。

## API 設計書の更新

- `docs/api_design/Core_API.md` に「お知らせ API（フェーズ19）」セクションを追加（前述 Core API 仕様 1〜8）。
- `docs/api_design/Console_API.md` に「お知らせ API（フェーズ19）」を追加。
- `docs/api_design/Market_API.md` に Market 側の呼び出し対応表を追加。

## operation_logs 命名規約の更新

`docs/ai_context/operation_logs_naming.md` §6「採番例」の Console 起点表に以下を追記：

| 操作 | action | screen_name | api_name |
|------|--------|-------------|----------|
| お知らせ作成 | `create_notice` | `console.notice.create` | `POST /api/notices` |
| お知らせ更新 | `update_notice` | `console.notice.edit` | `PUT /api/notices/:id` |
| お知らせ削除 | `delete_notice` | `console.notice.list` | `DELETE /api/notices/:id` |

---

# レビューコメント対応サマリ（初版 → r1）

| ID | 観点 | 対応 |
|----|------|------|
| P19-1 | Console / Core / Market の責務分割が未定義 | 「実装段取り Step A → C」と「フォルダ構成」セクションを新設し、各システムの責務を明文化 |
| P19-2 | API エンドポイントが未定義 | 「Core API 仕様」「Console API 仕様」「Market API 呼び出し」を新設（8 + 6 + 5 エンドポイント） |
| P19-3 | 既読主体（社員 or 会員）が不明 | **`market_customers.id` のみを既読主体に確定**。Console 既読は将来課題 |
| P19-4 | `notice_reads.deleted_flag` 廃止検討 | 廃止し、`UNIQUE (notice_id, market_customer_id)` + `INSERT ... ON DUPLICATE KEY UPDATE` で冪等化 |
| P19-5 | `category` の文字列カラム化 | `notice_categories` マスタ化。初期データ（重要 / 普通）と config 連携を定義 |
| P19-6 | 公開期間判定の TZ・境界値未定義 | **JST 0:00 基準**（phase14 r3 と整合）を明記。境界値テストケースも追加 |
| P19-7 | 自動切替・モーダル遷移の実装方式 | `setInterval(5000)` 採用、外部ライブラリは入れない。モーダル遷移は一覧 state 内で完結（N+1 回避） |
| P19-8 | 投稿者の Market 非表示の API 担保 | Market 用 DTO に `author_id` / `author` を含めない実装ルールと E2E テストを明記 |
| P19-9 | XSS 対策が未定義 | `body` はプレーンテキスト保存・表示、改行のみ `<br>` 変換、`dangerouslySetInnerHTML` 禁止 |
| P19-10 | 論理削除の方針 | `notices.deleted_at DATETIME NULL` で統一。`notice_reads` 側は物理 DELETE は採らず参照履歴を維持 |
| P19-11 | 未読数集計のパフォーマンス | UNIQUE インデックス + LEFT JOIN ... IS NULL の集計クエリを擬似 SQL で明示 |
| P19-12 | TDD テストケースが正常系のみ | 異常系（公開期間外既読登録 / バリデーション / 認証 / XSS / N+1 / Polling 失敗）を追加 |
| P19-13 | DB / API 設計書の更新ルール（CLAUDE.md） | 「DB / API 設計書のメンテナンス」セクションを新設し、追加対象ファイルを列挙 |
| P19-14 | operation_logs 命名規約の追記 | §6 採番例に Console 起点 3 行追加（create_notice / update_notice / delete_notice） |
| P19-15 | `market_customer_id` の型整合 | BIGINT UNSIGNED で揃える（037 起因の型不整合トラブル対策） |
| P19-16 | テスト値の config 経由化 | `application-test.properties` / `phpunit.xml` / `.env.test` の値経由のテスト規約を明記（規約 4-1） |
| P19-17 | 主要テーブル定数の同期（phaseX-6 / 044 起因） | `ops/healthcheck/required_tables.txt` に `notice_categories` / `notices` / `notice_reads` を追記する運用を明記。CD の「主要テーブル存在確認」のすり抜け防止 |

## r2 で対応（R19-1 〜 R19-12）

| ID | 優先度 | 対応 |
|----|--------|------|
| R19-1 | 🔴 必須 | **PK 型ポリシーを「前提」セクションに明記**：Core 新規テーブル PK / 同 Core 内 FK は `BIGINT`、`users` / `market_customers` 等 Laravel 由来テーブルへの FK は `BIGINT UNSIGNED` で揃える（037 起因の型不整合事故防止） |
| R19-2 | 🔴 必須 | **`publish_end` の「23:59:59 基準」表記を撤廃**。「Console 側 FormRequest が日付入力時に `23:59:59` を自動付与する正規化」と表現し、Core はリアルタイム DATETIME 比較のみを行うことを明示。`notices` カラム備考欄も書き換え |
| R19-3 | 🔴 必須 | **`api_name` 値は Core API パスに統一**。phase14 / phase15 の既存採番例（`POST /api/inbounds` 等）と整合。Console から呼び出されたか直叩きかは `screen_name` で識別する設計を踏襲 |
| R19-4 | 🔴 必須 | **`operation_logs` 書き込みは Core Service が直接行う**。Console から「operation_logs を書け」エンドポイントは設けない。actor の `users.id` は **`X-User-Id` ヘッダ**で Core に渡す（既存 `RegisterInboundController` と同方式）。Console API 仕様の各エンドポイントに「`X-User-Id` 付与」を明記。`author_id` はリクエストボディから外し、Core が `X-User-Id` の値を `notices.author_id` に保存（なりすまし防止） |
| R19-5 | 🔴 必須 | **「JST 0:00 基準」表現の修正**。前提セクションを「JST タイムゾーン保存・リアルタイム DATETIME 比較」に書き換え、API §6 の `:now` 説明を「現在の JST 日時」と明示し phase14 r3 とは異なるニュアンスであることを記述 |
| R19-6 | 🟡 望ましい | FK 違反（`DataIntegrityViolationException`）が観測される経路に到達しないよう、**Service 層の事前チェックで先に 422 を返す**ことを TDD で担保。`category_id` / `X-User-Id`（→ `author_id`）の双方を対象化 |
| R19-7 | 🟡 望ましい | **ヘッダー Polling × ローテーション競合方針**を新設：Polling 結果は次のローテーション境界（5秒サイクル切替時）で反映。例外は未読数 0 の即時非表示。fake timer で TDD |
| R19-8 | （R19-9 へ統合） | — |
| R19-9 | 🟡 望ましい | **未認証時の `is_read` 省略**：Market 未認証 / Console アクセス時はキー自体を省略（`null` ではない）。会員セッション時のみ `is_read` キーを返す。TDD で「`'is_read' in item` が false」を検証 |
| R19-10 | 🟡 望ましい | **`required_tables.txt` 同期の検証スクリプト**を新設（`ops/healthcheck/check_required_tables_consistency.sh`）。`docs/database_design/README.md` の Core テーブル列挙と差分検出。phase19 のテストケースに 3 行存在のアサーションを追加 |
| R19-11 | 🟢 補足 | **DTO 分離**：`NoticeMarketDto` / `NoticeConsoleDto` を別クラスとして分離。`author` フィールドは `NoticeConsoleDto` のクラス定義にのみ存在し、Market 側にはコンパイル時点で存在しない（Controller の if 分岐による漏洩リスクを排除） |
| R19-12 | 🟢 補足 | **ER カーディナリティを設計書本文に併記**：`notice_categories` ↔ `notices`（1:N）、`users` ↔ `notices`（1:N）、`notices` ↔ `notice_reads` ↔ `market_customers`（M:N、`notice_reads` で解消）。ER 図反映時のミス防止 |

## 既存設計書／ファイルへの波及（r2）

phase19 r2 の確定事項により、以下の関連ファイルにも軽微な追記が必要：

| ファイル | 追記内容 |
|---------|---------|
| `docs/ai_context/operation_logs_naming.md` §6 採番例（Console 起点表） | `create_notice` / `update_notice` / `delete_notice` の 3 行を追加（既に r1 の §「operation_logs 命名規約の更新」で予告済み） |
| `docs/api_design/Console_API.md` | お知らせ API（5エンドポイント）追加。`X-User-Id` の取り回しを明記 |
| `docs/api_design/Core_API.md` | お知らせ API（8エンドポイント）追加。`X-User-Id` ヘッダ仕様を明記 |
| `docs/api_design/Market_API.md` | お知らせ閲覧／既読登録の対応エンドポイント表を追加 |
| `ops/healthcheck/required_tables.txt` | `notice_categories` / `notices` / `notice_reads` を 3 行追加 |
| `ops/healthcheck/check_required_tables_consistency.sh`（新設） | README.md と required_tables.txt の同期検証スクリプト（R19-10） |
