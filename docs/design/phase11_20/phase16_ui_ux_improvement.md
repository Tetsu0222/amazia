
# フェーズ16：UIデザイン改善

## ステータス
🟡 着手中（Step 1 / Step 1.5 / Step 2 / Step 3 / Step 3.1 / Step 4 / Step 5 / Step 6-1 / Step 6-2 / Step 6-3 / Step 6-4 / Step 6-5 / Step 6-6 / Step 8 / Step 9 / Step 12 / Step 13 実装完了・2026-05-07／Step 7 / Step 10 / Step 11 設計策定・2026-05-07）

## 範囲
- Amazia Console  
- Amazia Market  
- UI/UX デザイン全般

## 機能概要
- Amazia Console と Amazia Market の UI を改善し、ユーザビリティと視認性を向上させる  
- Console は現行の雰囲気を維持しつつ、より洗練された管理画面へ  
- Market は Amazon の UI/UX を参考に、直感的で使いやすい EC サイトとして仕上げる

---

# Step 1：商品有効/無効スイッチ（Console 商品マスタ） ✅ 実装完了（2026-05-07）

### 背景・目的
現状、商品を Market から非表示にするには `publish_start` / `publish_end`（公開期間）を設定する必要がある。  
運用上「期間調整なしで、即時に Market から外したい／戻したい」というユースケースが頻発するため、公開期間とは独立した **手動の有効/無効スイッチ** を追加する。

### 設計方針
- `products` テーブルに **`is_active BOOLEAN NOT NULL DEFAULT TRUE`** を追加する
  - 既存の `status_code`（販売段階：`WAITING` / `RESERVATION` / `ON_SALE`）は **販売段階を表す軸** として残す
  - `is_active` は **Market 露出 ON/OFF を表す直交軸** として扱う
  - 例：`status_code = ON_SALE` かつ `is_active = FALSE` ＝「販売中扱いだが一時的に Market 非表示」
- Market 側の表示判定は **`is_active = TRUE` AND 公開期間条件** の AND 結合とする
  - 既存の `PreorderStatusService#isPublished()` の判定窓口に `is_active` チェックを追加する
- 公開期間（`publish_start` / `publish_end`）は従来通り「期間スケジュール」用途として残す（廃止しない）

### DB 変更
- `amazia-core/src/main/resources/schema.sql` に `ALTER TABLE products ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE` を追記（冪等）
- `Product` エンティティに `isActive` フィールドを追加
- `docs/database_design/TBL_products.md` のカラム表に追記

### API 変更（Core / Console）
- Core `GET/PUT /products/{id}` のレスポンス・リクエスト DTO に `isActive` を追加
- Core `GET /products`（Market 露出用エンドポイント）の WHERE 句に `is_active = TRUE` を追加
- Console `routes/api/Product.php` の Pass-through で `isActive` を透過
- 設計書 `docs/api_design/Core_API.md` / `Console_API.md` / `Market_API.md` を更新

### UI 変更（Console）
- `ProductForm.vue`：商品名のすぐ下、または「ステータス」項目のすぐ近くに **「Market 公開」スイッチ（a-switch）** を追加
  - ON＝有効（`is_active = TRUE`）／OFF＝無効（`is_active = FALSE`）
  - 既定値は ON
- `ProductList.vue`：一覧の各行に有効/無効バッジを表示し、無効商品はグレーアウト表示
- 一覧上部にフィルタ「有効のみ／無効のみ／すべて」を追加（既定：すべて）

### TDD テストケース
- Core
  - `is_active = FALSE` の商品は Market 一覧 API のレスポンスに含まれない
  - `is_active = TRUE` かつ公開期間内なら表示される
  - 公開期間外なら `is_active = TRUE` でも表示されない（AND 条件）
  - PUT で `isActive` を切り替えできる
- Console
  - 商品編集画面でスイッチを OFF にして保存すると永続化される
  - 一覧フィルタが正しく機能する
- Market
  - 無効化された商品は商品詳細 URL を直叩きしても 404 / 一覧から消える

---

## Step 1.5：商品「ステータス」UI の見直し ✅ 実装完了（2026-05-07）

### 背景・目的
Step 1 で `is_active`（Market 露出 ON/OFF）を導入した結果、`status_code`（`WAITING` / `RESERVATION` / `ON_SALE`）は **`release_date` / `preorder_start_date` から自動算出できる軸** と重複していることが運用で明確になった。

- `status_code` は `PreorderStatusService#judge()` で `release_date` と `preorder_start_date` から導出可能
- 手動運用すると「日付は発売後なのに `status_code = RESERVATION` のまま」のような不整合を生む
- ただし将来「販売段階を手動で上書きしたい」要件が再浮上する可能性は残るため、**DB カラム（`products.status_code`）は削除しない**

そこで「Console UI から `status_code` の表示・設定を一旦外し、発売日ベースの自動表示に切り替える」運用に変更する。

### 設計方針
- `products.status_code` カラム・関連 API・`PreorderStatusService` は **そのまま残す**（将来の再導入に備える）
- Console の **入力 UI と表示 UI からだけ** `status_code` を取り除く
- 商品マスタ系画面で消した「ステータス」の代替として、SKU 管理画面に「発売日基準のステータス表示」と日付列を追加する

### UI 変更（Console）

#### 商品マスタ登録画面 / 編集画面（`ProductForm.vue`）
- 「ステータス」`<a-form-item>` および関連 select・`statuses` 取得処理を削除
- 送信ペイロードからは `statusCode` を含めない（既存値は Core 側で保持）

#### 商品マスタ一覧画面（`ProductList.vue`）
- 「ステータス」列を削除（`statusLabel` / `statusColor` / `STATUS_MAP` も削除）
- 「公開状態」「有効/無効」列はそのまま残す

#### 商品一覧（SKU 管理）画面（`SkuList.vue`）
SKU 一覧テーブルの列を以下に変更する：

| 列 | 表示内容 | データソース |
|----|---------|-------------|
| SKUコード | 既存 | `sku.skuCode` |
| 色 | 既存 | `sku.color` |
| サイズ | 既存 | `sku.size` |
| ステータス | **発売日前 / 発売中 の 2 値表示**（バッジ）<br>判定：`release_date` 未設定 or 当日含む過去 → `発売中`、未来 → `発売前` | 選択中の `product.releaseDate` |
| 予約開始日 | `YYYY-MM-DD` 表示（NULL は「公開と同時」） | 選択中の `product.preorderStartDate` |
| 発売日 | `YYYY-MM-DD` 表示（NULL は「未設定」） | 選択中の `product.releaseDate` |
| （操作列） | 既存「選択中／選択」 | 既存ロジック |

- 「ステータス」列の値は **SKU 自身の `sku.status` ではなく、親商品の `release_date` から算出した発売前/発売中 の 2 値**
  - 既存の `sku.status`（`ACTIVE` 等）は SKU 単位の有効/無効軸であり、画面要件「発売日前か後かを表示」にそぐわないため、列のラベルは「ステータス」のまま意味を差し替える
  - SKU 単位の `status` は将来の機能（販売停止など）に備えて DB に残す
- 発売日・予約開始日は商品単位の値のため、同じ商品内では全 SKU 行で同じ値が並ぶことになるが、SKU 一覧のみを見ている運用者が「この SKU は発売前か」を即判断できる方が優先

#### 商品一覧（SKU 集約版）画面（`ProductMarketList.vue`・`/products/market-view`）
Market 公開データ確認用画面の列に以下を追加する。データソースは Core `GET /api/products/market` の `ProductMarketSummary` DTO（既に `releaseDate` / `preorderStartDate` を保有しているため API 変更不要）。

| 追加列 | 表示内容 | データソース |
|--------|---------|-------------|
| ステータス | **発売前 / 発売中 の 2 値表示**（バッジ）<br>判定：`releaseDate` 未設定 or 当日含む過去 → `発売中`、未来 → `発売前` | `record.releaseDate` |
| 予約開始日 | `YYYY-MM-DD` 表示（NULL は「公開と同時」） | `record.preorderStartDate` |
| 発売日 | `YYYY-MM-DD` 表示（NULL は「未設定」） | `record.releaseDate` |

### DB 変更
**なし**（`products.status_code` は残置、`skus.status` も残置）。

### API 変更
**なし**（Core / Console とも `statusCode` を返し続け、フロントが使わないだけ）。

### TDD テストケース
- 表示変更のみのため Vue ユニットテストは追加せず、フェーズ16冒頭の方針通り E2E / 表示確認で担保
- ProductForm の送信時に `statusCode` プロパティが含まれない / 含まれても Core 側既存値が保持される（既存 Update テストで担保済み）

---

# Step 2：予約管理画面と売上の予約除外フィルタ ✅ 実装完了（2026-05-07）

> 実装計画書: [phase16_step2_implementation_plan.md](../../implementation/phase16_step2_implementation_plan.md)

## 2-1. 背景・目的

フェーズ12〜14.5 で予約販売の仕組みは整ったが、Console 側には以下のギャップが残っている。

1. **予約状態の商品を一覧で把握する画面がない**
   - 商品マスタ画面では `release_date` / `preorder_start_date` を個別に確認するしかなく、「いま何が予約受付中か」が一目で分からない。
   - 予約数・予約者・発売日までの残日数といった運用判断材料を集約する場所が必要。
2. **売上管理画面で予約と通常購入が同居している**
   - `sales.is_preorder` カラムと「区分」表示は phase14 r4 で導入済み（[SalesList.vue:112](../../amazia-console/resources/vue/src/features/sales/pages/SalesList.vue#L112)）だが、フィルタや集計時の除外機構はない。
   - 予約は「売上が立っているが商品はまだ手元にない（発売前）」という性質上、月次の売上分析・在庫回転の集計に混ぜると誤読される。デフォルトで除外し、必要時のみ「見込み表示」として含める運用にしたい。

## 2-2. スコープ

| # | 対応 | 対象 |
|---|------|------|
| A | 予約管理画面（Console）の新設 | Console UI / Core API |
| B | 売上管理画面の一覧フィルタ「予約除外」 | Console UI のみ（クライアント側フィルタ） |
| C | 売上集計（月別 / SKU別 / 決済方法別）の予約除外既定化 | Console UI のみ（クライアント側計算） |
| D | 「見込み表示」トグルによる予約込み集計への切替 | Console UI のみ |

**スコープ外**：
- Market 側の予約商品表示変更（フェーズ16後段の Market UI 改善で扱う）
- 予約注文のキャンセル / 発売日変更フロー（既に phase14_5 で確定済み・本ステップでは触らない）
- ページング / サーバーサイドフィルタ化（売上件数が現状規模では不要・将来課題）

## 2-3. 「予約商品」「予約売上」の定義

| 用語 | 定義 | 判定根拠 |
|------|------|---------|
| 予約状態の商品 | `PreorderStatusService#judge()` が `PRE_ORDER` を返す商品 | `release_date` 未到来かつ `preorder_start_date` 到来済み・`is_active = TRUE`・公開期間内 |
| 予約注文（売上） | `sales.is_preorder = TRUE` のレコード | 注文確定時点で `release_date` 未到来 → phase14 既存ロジックで TRUE 設定 |

「予約商品」は `products` 視点、「予約売上」は `sales` 視点で別軸。Step 2 では両軸を別画面で扱う。

## 2-4. 予約管理画面（Console）

### 2-4-1. 画面位置

- サイドバー：「売上管理」と「返品管理」の間に **「予約管理」** を追加（[App.vue:20-21](../../amazia-console/resources/vue/src/App.vue#L20-L21)）
- ルート：`/preorders`（[router/index.js](../../amazia-console/resources/vue/src/router/index.js)）

### 2-4-2. 表示内容

| 列 | データソース | 備考 |
|----|------------|------|
| 商品ID | `products.id` | テキスト表示のみ（商品マスタへの遷移はさせない） |
| 商品名 | `products.name` | テキスト表示のみ（商品マスタへの遷移はさせない） |
| 予約開始日 | `products.preorder_start_date` | NULL 時は「公開と同時」 |
| 発売日 | `products.release_date` | |
| 発売まで | `release_date - today` の日数 | 当日は「本日発売」、過ぎていれば一覧から除外 |
| 予約受付 | `products.accept_preorder` | バッジ「受付中／停止中」 |
| 予約数（数量合計） | `sales` を `is_preorder = TRUE` AND `sku.product_id = products.id` で SUM(`quantity`) | Core で集計して返却 |
| 予約金額（合計） | 同上を SUM(`amount`) | 円表示 |
| Market 公開 | `products.is_active` | バッジ |

並び順：発売日昇順（直近の発売予定を先頭）。

### 2-4-3. 取得元データ

予約管理画面のデータは **Core 側の新規エンドポイント `GET /api/products/preorders`** から取得する。

理由：
- 既存の `GET /api/products`（商品一覧）は予約数・予約金額の集計を返さない
- 既存の `GET /api/sales` は売上一覧でありステータス情報を持たない
- 商品単位 × 売上集計の JOIN を Service 層に集約することで Console を薄く保つ（規約 1-1）

### 2-4-4. Core API 仕様

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/products/preorders` |
| 認証 | Console JWT（既存ミドルウェア） |
| コントローラー | `ListPreorderProductsController`（新設） |
| サービス | `ListPreorderProductsService`（新設） |

**仕様**
- `is_active = TRUE` かつ `PreorderStatusService#judge() = PRE_ORDER` の商品を抽出
- 各商品について `sales` から `is_preorder = TRUE` のレコードを集計（数量・金額）
- 発売日昇順で返却

**レスポンス例**
```json
[
  {
    "productId": 12,
    "productName": "Tシャツ夏モデル",
    "preorderStartDate": "2026-04-01",
    "releaseDate": "2026-08-01",
    "daysUntilRelease": 86,
    "acceptPreorder": true,
    "isActive": true,
    "preorderQuantity": 47,
    "preorderAmount": 235000
  }
]
```

### 2-4-5. Console Pass-through

| 項目 | 内容 |
|------|------|
| メソッド | GET |
| パス | `/api/preorders`（Console ローカル） |
| 認証 | 要 |
| コントローラー | `App\Preorder\Controller\ListPreorderController`（新設） |
| サービス | `App\Preorder\Service\ListPreorderService`（新設） |

Core の `/api/products/preorders` を Pass-through。ルート定義は新規 `routes/api/Preorder.php` に分離（規約 2-1 補足4）。

### 2-4-6. UI コンポーネント

- 新規ファイル：`amazia-console/resources/vue/src/features/preorder/pages/PreorderList.vue`
- 新規ファイル：`amazia-console/resources/vue/src/features/preorder/api/preorderApi.js`
- ant-design-vue の `a-table` を使用（既存の SalesList.vue と同様のスタイル）

## 2-5. 売上管理画面の改修（Console）

### 2-5-1. 「一覧」タブ

- ヘッダー右側に **フィルタチェックボックス「予約を除外」** を追加（既定：OFF＝全件表示）
- 既存の「区分」列はそのまま残す

### 2-5-2. 「集計」タブ

設計書の以下の指示に従う：
- **売上分析は予約商品を除外する（既定）**
  - 月別売上 / SKU別売上 / 決済方法別売上：`is_preorder = TRUE` の sales を集計対象から除外
- **「見込み表示」ボタン押下で予約も含めた集計に切り替える**
  - トグル：押下後はラベルを「見込み表示中（予約含む）」に変更し、再押下で通常表示に戻る
  - 押下中はカード上部に「※ 予約購入を含む見込み値です」の注意書きを表示
- 既存の「購入区分別売上」カードはトグル状態に関わらず常に通常／予約の両方を表示（区分の意味そのもの）

### 2-5-3. 実装方針（Console UI 内で完結）

- 売上一覧 API（`GET /api/sales`）はサーバー側フィルタを追加せず、Vue の `computed` で `is_preorder` を見て除外／包含を切り替える
- 理由：
  - Core 側の API スキーマを変えずに済み、規約・他画面に副作用を出さない
  - 売上件数規模では現状クライアント側フィルタで十分（将来ページング化時にサーバー側フィルタへ移行する余地）
  - 「見込み表示」トグルが UI 状態として完結し、リクエスト往復が発生しない

## 2-6. DB 変更

**なし**（既存の `products.release_date` / `products.preorder_start_date` / `products.is_active` / `sales.is_preorder` をそのまま利用）。

## 2-7. API 変更まとめ

| 区分 | エンドポイント | 種別 |
|------|---------------|------|
| Core | `GET /api/products/preorders` | 新設 |
| Console | `GET /api/preorders` | 新設（Core Pass-through） |
| Core | `GET /api/sales` | 変更なし |
| Console | `GET /api/sales` | 変更なし |

設計書 `docs/api_design/Core_API.md` / `Console_API.md` に新設エンドポイントを追記する（CLAUDE.md「DB / API 設計書のメンテナンスルール」遵守）。

## 2-8. UI 変更まとめ（Console）

| 画面 | 変更内容 |
|------|---------|
| `App.vue` | サイドバーに「予約管理」メニューを追加 |
| `router/index.js` | `/preorders` ルートを追加 |
| `features/preorder/pages/PreorderList.vue` | 新規作成 |
| `features/preorder/api/preorderApi.js` | 新規作成 |
| `features/sales/pages/SalesList.vue` | 一覧タブに「予約を除外」フィルタ・集計タブに「見込み表示」トグル追加 |

## 2-9. TDD テストケース

### Core
- 予約商品（PRE_ORDER）一覧取得：`is_active = FALSE` の商品は除外される
- 予約商品（PRE_ORDER）一覧取得：`release_date` を過ぎた商品は除外される（PreorderStatus が変わるため）
- 予約商品（PRE_ORDER）一覧取得：予約数量・金額が `sales.is_preorder = TRUE` のみで集計される
- 予約商品（PRE_ORDER）一覧取得：発売日昇順で返却される
- 予約商品が 0 件のとき空配列を返す

### Console（Laravel Feature テスト）
- `GET /api/preorders` が認証なしで 401 を返す
- 認証済みリクエストで Core レスポンスが透過的に返る
- Core が 5xx を返した時のステータス透過

### Console（Vue / vitest）
- SalesList の「予約を除外」フィルタ ON で `is_preorder = TRUE` の行が一覧から消える
- 集計タブの初期状態で月別/SKU別/決済方法別から予約売上が除外されている
- 「見込み表示」トグルで集計が予約込みに切り替わる
- 「見込み表示」中は注意書きが表示される
- 購入区分別売上は常に通常／予約の両方を表示する

---

# Step 3：入荷登録画面の選択UI化と SKU管理画面からの機能移譲 ✅ 実装完了（2026-05-07）

> 実装計画書: [phase16_step3_implementation_plan.md](../../implementation/phase16_step3_implementation_plan.md)

## 3-1. 背景・目的

phase15 で入荷管理画面（`/inbound`）を新設したが、入荷登録画面（`/inbound/create`）は商品 ID と SKU ID を**数値で直接入力**する作りで、運用者が ID を覚えていないと使えなかった。また同フェーズで SKU 管理画面（`/skus`）に在庫管理タブを統合した経緯から、入荷登録機能が「SKU 管理画面の在庫タブ」と「入荷管理画面」の両方に存在しており、どちらが正なのか曖昧だった。

Step 3 では、入荷登録の窓口を「入荷管理画面」に一本化し、UI を ID 直入力から商品マスタ→SKU 連動セレクトに切り替える。

## 3-2. 改善（入荷登録画面 / `InboundCreate.vue`）

### 3-2-1. 商品マスタ→SKU 選択 UI 化

| 項目 | Before | After |
|------|--------|-------|
| 商品の指定 | `<a-input-number>` で商品 ID を直接入力 | `<a-select>` で商品マスタ一覧から選択（show-search で名前検索可） |
| SKU の指定 | `<a-input-number>` で SKU ID を直接入力 | `<a-select>` で「商品を選択するまで disabled」→ 選択後に `getProductSkus(productId)` で読み込み |
| 商品変更時 | — | `selectedSkuId` をリセット |

データソース：
- 商品リスト：`getAdminProducts()`（公開期間外の商品も対象にするため admin 経由）
- SKU リスト：`getProductSkus(productId)`

### 3-2-2. 機械的な注意事項の表示変更

現状の青枠アラート：
> 倉庫はバックエンドが既定値（id=1 'default'）を自動セットします（並行運用期）。

→ 運用フェーズ表現を外し、選択UI化を踏まえたシンプル案内に変更：
> 商品とSKUを選択し、入荷数量と入荷日を入力してください。倉庫は自動でデフォルトが設定されます。

## 3-3. 改善（SKU 管理画面 / `SkuList.vue`）

入荷登録画面ができたため、SKU 管理画面からは入荷登録機能を撤去し、参照系（現在在庫＋入荷履歴）のみ残す。

| 要素 | 操作 |
|------|------|
| `<template #extra>` の「Excel一括入荷」ボタン | **削除**（Excel は入荷管理画面に移譲） |
| 在庫タブの「入荷登録」フォーム | **削除**（`stockForm` / `handleStockReceive` / `receiveSkuStock` import も除去） |
| 在庫タブの「現在在庫」表示 | **残す**（参照用） |
| 在庫タブの入荷履歴テーブル | **残す**（参照用） |
| 在庫タブ冒頭 | 「入荷登録は『入荷管理』画面から行ってください。」の info アラートを追加 |

## 3-4. 改善（入荷管理画面 / `InboundList.vue`）

ヘッダー右側に「Excel一括入荷」ボタンを追加し、SKU 管理画面から移譲された Excel 一括入荷機能の入口とする。

```html
<template #extra>
  <a-space>
    <a-button @click="goImport">Excel一括入荷</a-button>
    <a-button type="primary" @click="goCreate">入荷登録</a-button>
  </a-space>
</template>
```

`goImport` は `/inbound/import` に遷移する。

## 3-5. 新設（`InboundStockImport.vue`）

- 新規ファイル：`amazia-console/resources/vue/src/features/inbound/pages/InboundStockImport.vue`
- 内容：旧 `SkuStockImport.vue` の UI を移植。`@back` 先を `/inbound` に変更。
- 呼び出す API は既存の `importSkuStock(file)`（`POST /api/skus/stocks/import`）をそのまま流用。API 互換性を保つため Console / Core 側のエンドポイントは変更しない。

## 3-6. 撤去

- `amazia-console/resources/vue/src/features/skus/pages/SkuStockImport.vue` を削除
- `router/index.js` から `/skus/stocks/import` ルートと `SkuStockImport` の import を削除
- `SkuStockList.vue` の「Excel一括入荷」ボタンの遷移先を `/skus/stocks/import` → `/inbound/import` に変更（旧画面も新窓口に整合）

## 3-7. DB 変更

**なし**

## 3-8. API 変更

**なし**（`POST /api/inbounds`・`POST /api/skus/stocks/import` をそのまま流用）

## 3-9. UI 変更まとめ（Console）

| 画面 | 変更内容 |
|------|---------|
| `InboundCreate.vue` | 商品/SKU を `<a-select>` 連動式に変更・青枠アラート文言を変更 |
| `InboundList.vue` | ヘッダーに「Excel一括入荷」ボタン追加 |
| `InboundStockImport.vue` | 新規作成（旧 `SkuStockImport.vue` の移植先） |
| `SkuList.vue` | 在庫タブから入荷登録フォームを削除・「Excel一括入荷」ボタン削除・入荷履歴と現在在庫は残す |
| `SkuStockList.vue` | 「Excel一括入荷」ボタンの遷移先を `/inbound/import` に変更 |
| `SkuStockImport.vue` | **削除** |
| `router/index.js` | `/inbound/import` 追加・`/skus/stocks/import` 削除 |

## 3-10. TDD テストケース

- 表示変更と遷移先変更のみのため、本ステップでも Vue ユニットテストは追加せず手動 E2E で担保（フェーズ16冒頭の方針）
- 既存の Laravel feature テスト（`POST /api/skus/stocks/import` 系）には影響なし

---

# Step 3.1：入荷登録の本日付固定（未来日入荷の混入防止） ✅ 実装完了（2026-05-07）

## 3.1-1. 背景・目的

Step 3 で入荷登録画面（`/inbound/create`）の UI を改善したが、`<a-date-picker>` の入荷日欄に未来日を入力しても在庫加算（`product_sku_stocks` / `inventories`）が即時行われていた。`RegisterInboundService` が `inboundedAt` の値を見ずに無条件で在庫加算するため、「発売前の予定数量」と「実在庫」が区別できない状態になっていた。

本登録画面は **当日入荷の即時計上** のみに役割を限定し、未来日（入荷予定）は別画面（既存の Step:X バックログ「入荷予定画面」）で扱う方針とする。

## 3.1-2. 改善内容

### フロントエンド（`InboundCreate.vue`）
- 入荷日 `<a-form-item>` および `<a-date-picker>` を **削除**
- フォーム state から `inboundedAt` を削除し、登録ペイロードからも除外
- 青枠アラートの文言を更新：「入荷日は登録時の本日付で記録されます。」を明記

### Console Pass-through（`RegisterInboundController.php`）
- バリデーションを `'inboundedAt' => 'required|date'` → `'inboundedAt' => 'nullable|date'` に変更
- 既存運用（明示送信）との互換は維持

### Core API（`RegisterInboundRequest.java` / `RegisterInboundService.java`）
- `RegisterInboundRequest.inboundedAt` の `@NotNull` を解除
- `RegisterInboundService#register` で `inboundedAt` が null の場合は `LocalDate.now()` を強制セット

## 3.1-3. DB 変更
**なし**

## 3.1-4. API 変更
**なし**（`POST /api/inbounds` の `inboundedAt` は任意項目化のみ。互換的変更）

## 3.1-5. TDD テストケース
- Core `RegisterInboundServiceTest`：`inboundedAt` 未指定で本日付が自動セットされる
- Core `RegisterInboundControllerTest`：`inboundedAt` 省略で 201 を返し `inboundedAt` が本日付
- Console `InboundProxyTest`：`inboundedAt` 省略でも Core に転送され 201 を返す

## 3.1-6. 申し送り
- 「入荷予定画面」（未来日入荷の管理 UI）は `次タスク.txt` の Step:X として継続バックログ

---

# Step 4：操作履歴画面の表示ラベル和名化（Console 操作履歴） ✅ 実装完了（2026-05-07）

## 4-1. 背景・目的

操作履歴画面（`/operation-logs`）の「操作」「対象」「画面名」「API名」列が、Core で記録された英字キー（`register_inbound` / `inbounds:3` / `console.inbound.register` / `POST /api/inbounds`）をそのまま表示しており、運用者にとって意味が直感的に把握しづらい。

これらのキーは Core 側の固定文字列（各 Service の `private static final String ACTION` / `SCREEN_NAME` / `TARGET_TYPE` / `API_NAME` 定数）として記録されているため、Console UI 側でラベルマップを持って和名表示する。

## 4-2. 設計方針

### 4-2-1. ラベルマップの配置

- **Vue 側の config モジュール**として持つ（PHP `config/app/` 経由ではない）
- 配置先：`amazia-console/resources/vue/src/features/operationLog/config/labels.js`
- 理由：
  - 操作履歴の和名化は **表示専用ロジック**（Core 値はサーバ側で消費しないため Laravel config に置く意味がない）
  - 規約 5（Vue）「コンポーネントに表示ロジック以外を書かない」「API は `src/api/` 配下に集約」と整合
  - Core で新たな action / screen が追加された際、ラベル追加は Vue ファイル 1 箇所で完結する

### 4-2-2. マップ対象の列

| 列 | キー | マップ |
|----|------|--------|
| 操作 | `record.action` | `ACTION_LABELS` |
| 対象 | `record.targetType` | `TARGET_TYPE_LABELS`（`{type}:{id}` の type 部分のみ和名化） |
| 画面名 | `record.screenName` | `SCREEN_NAME_LABELS` |
| API名 | `record.apiName` | `API_NAME_LABELS`（`POST /api/inbounds` 等の固定パターン） |

### 4-2-3. フォールバック

- マップに存在しないキーは **原文をそのまま表示** する（「不明」と置き換えない）
- 理由：Core で新規 action が追加された場合、Vue 側のマップ更新が間に合わなくても画面で意味が読み取れる状態を保つ

### 4-2-4. 検索フィルタ

- 検索ボックス（画面名 / API名 / 操作）は **既存の英字キー入力** を維持する
- Core 側の `OperationLogRepository` は英字キーで部分一致検索する仕様（変更しない）
- プレースホルダ文言・ヒント表示も既存のまま

## 4-3. ラベル定義（初期セット）

Core 側の Service 定数を網羅して以下を定義する：

### action（操作）
| キー | 和名 |
|------|------|
| `register_inbound` | 入荷登録 |
| `update_shipping_status` | 配送ステータス更新 |
| `update_shipping_address` | 配送先住所更新 |
| `update_scheduled_date` | 配送予定日更新 |
| `register_tracking_code` | 追跡番号登録 |
| `approve_sales_return` | 返品承認 |
| `reject_sales_return` | 返品却下 |
| `refund_sales_return` | 返金処理 |

### target_type（対象）
| キー | 和名 |
|------|------|
| `inbounds` | 入荷 |
| `deliveries` | 配送 |
| `sales_return` | 返品 |

### screen_name（画面名）
| キー | 和名 |
|------|------|
| `console.inbound.register` | 入荷登録画面 |
| `console.delivery.update_status` | 配送ステータス更新 |
| `console.delivery.update_address` | 配送先住所更新 |
| `console.delivery.update_scheduled_date` | 配送予定日更新 |
| `console.delivery.register_tracking` | 追跡番号登録 |
| `console.sales_return.approve` | 返品承認画面 |
| `core.batch.inbound_recalc` | 入荷再計算バッチ |

### api_name（API名）
| キー | 和名 |
|------|------|
| `POST /api/inbounds` | 入荷登録 API |
| `PATCH /api/deliveries/:id/status` | 配送ステータス更新 API |
| `PATCH /api/deliveries/:id/address` | 配送先住所更新 API |
| `PATCH /api/deliveries/:id/scheduled-date` | 配送予定日更新 API |
| `PATCH /api/deliveries/:id/tracking-code` | 追跡番号登録 API |
| `POST /api/sales-returns/:id/approve` | 返品承認 API |
| `POST /api/sales-returns/:id/reject` | 返品却下 API |
| `POST /api/sales-returns/:id/refund` | 返金処理 API |

## 4-4. UI 変更（Console）

`features/operationLog/pages/OperationLogList.vue`：
- `import { ACTION_LABELS, TARGET_TYPE_LABELS, SCREEN_NAME_LABELS, API_NAME_LABELS, labelOr } from '../config/labels.js'` を追加
- `<template #bodyCell>` で各列のキーを `labelOr(map, key)` で和名化して表示
- 「対象」列は `targetType` を和名化しつつ `:targetId` を末尾に維持（例：`入荷:3`）

## 4-5. DB 変更
**なし**

## 4-6. API 変更
**なし**（Core / Console とも値を返し続け、フロントが表示時に変換するだけ）

## 4-7. TDD テストケース

表示変更のみのため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保。
- `register_inbound` 行が「入荷登録」と表示される
- `inbounds:3` 行の対象列が「入荷:3」と表示される
- マップに存在しない未知の action は原文のまま表示される（フォールバック動作）

---

# Step 5：SKU 管理画面の起点を「商品プルダウン」から「商品一覧」へ ✅ 実装完了（2026-05-07）

> 実装計画書: [phase16_step5_implementation_plan.md](../../implementation/phase16_step5_implementation_plan.md)

## 5-1. 背景・目的

`/skus`（SKU 管理画面）は画面トップに **商品プルダウン** があり、ここで商品を選ばない限り SKU 一覧が一切表示されない作りだった。商品数が増えると「目的の SKU を編集する」までに余計なクリックが必要で、SKU 中心の運用感が薄かった。

設計書の指示：
> 商品をプルダウン選択肢として表示するのではなく、最初から SKU 一覧を表示させておいてほしい。
> 各商品マスタ押下 ⇒ SKU 展開 ⇒ 選択 ⇒ SKU 登録や編集とすると、アクション数は変わらないが UI は向上すると考える。

これを踏まえ、画面の起点を **「最初から商品一覧が見える」** に変える。プルダウンは廃止し、行展開（expand）で SKU を表示、SKU 行の「選択」ボタンで詳細モーダルを開く構成にする。

## 5-2. 設計方針

| 観点 | 方針 |
|------|------|
| 起点 | 商品プルダウンを廃止し、`getAdminProducts()` の結果を最初からテーブル表示する |
| SKU の表示 | `<a-table>` の `expandable` 機能で商品行を展開すると、その商品の SKU 一覧と「+ SKU を追加」フォームを表示する |
| SKU 詳細管理（価格・在庫・画像） | SKU 行の「選択」ボタンで `<a-modal>` を開き、その中に既存の 3 タブ（価格管理 / 在庫管理 / 画像管理）を配置 |
| 行クリック時の挙動 | 商品行クリック＝展開トグル（`expand-row-by-click`）／SKU 行の「選択」ボタンクリック＝モーダル起動 |
| キャッシュ | 一度展開した商品の SKU は `skuMap[productId]` に保持。再展開時は再フェッチしない（`SkuList` 内で完結する短命キャッシュ） |
| クエリパラメータ | 商品マスタ画面 `ProductList.vue` の「SKU管理」ボタンが `/skus?productId=N` で遷移する仕様を温存。受け側は該当商品行を初期展開する |
| 配色・列構成 | 商品行は商品マスタ画面（`ProductList.vue`）と表記を揃える（公開状態 / 有効・無効 / SKU 数 / 発売バッジ）。SKU 行は Step 1.5 で確定した列（SKUコード / 色 / サイズ / ステータス / 予約開始日 / 発売日 / 操作）をそのまま採用 |
| 無効商品の扱い | 商品行は `is_active = false` でグレーアウト（既存 `.row-inactive` スタイル準用）し、展開・SKU 編集は可能なまま残す |

## 5-3. UI 変更（Console）

### 商品一覧（メイン）

`SkuList.vue` のトップレベル：

```
┌─ SKU管理（/skus） ────────────────────────────────────┐
│ ┌ 商品一覧（最初から表示）─────────────────────────┐ │
│ │ ▶ 1  Tシャツ夏モデル   発売中  公開中  有効  3SKU│ │
│ │ ▼ 2  帽子              発売前  公開中  有効  2SKU│ │
│ │   └ SKU一覧テーブル                              │ │
│ │     ・HAT-RD-M  Red M  発売前  ...  [選択]      │ │
│ │     ・HAT-RD-L  Red L  発売前  ...  [選択]      │ │
│ │   └ [+ SKUを追加] フォーム                       │ │
│ │ ▶ 3  シャツ秋モデル   発売中  非公開  無効  1SKU│ │
│ └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

商品列：

| 列 | 内容 |
|----|------|
| ID | `products.id` |
| 商品名 | `products.name` |
| SKU数 | `products.skuCount`（バッジ） |
| 発売 | `release_date` ベースの「発売中／発売前」（バッジ・Step 1.5 と同ロジック） |
| 公開状態 | `publishStart` / `publishEnd` 判定（`ProductList.vue` の `isPublished` と同ロジック） |
| 有効/無効 | `is_active`（`ProductList.vue` と同じバッジ） |

SKU 列（Step 1.5 確定列をそのまま踏襲）：

| 列 | 内容 |
|----|------|
| SKUコード | `sku.skuCode` |
| 色 | `sku.color` |
| サイズ | `sku.size` |
| ステータス | 親商品の `release_date` から「発売中／発売前」 |
| 予約開始日 | `product.preorderStartDate`（NULL は「公開と同時」） |
| 発売日 | `product.releaseDate`（NULL は「未設定」） |
| 操作 | `[選択]` ボタン（モーダル起動） |

SKU 一覧テーブル直下に **「+ SKU を追加」フォーム**（色・サイズ・登録ボタン）を配置。フォーム state は商品単位に独立（`skuForms[productId]`）で持ち、送信時はその商品行のみ `getProductSkus(productId)` を再フェッチする。

### SKU 詳細モーダル

SKU 行の「選択」ボタンで開く。

- タイトル：`SKU詳細：HAT-RD-L（Red / L）`
- 中身：`<a-tabs>`（価格管理 / 在庫管理 / 画像管理）— **既存 3 タブの UI とロジックをそのままモーダル内に移植**
- 表示制御：`destroy-on-close` を設定し、閉じるたびに DOM ごと破棄。再度別 SKU を選び直しても確実にクリーンな状態で開く
- フッター：`null`（タブ内のフォームが完了アクションを持つため、モーダル独自の OK/キャンセルは置かない）

### 廃止・撤去

- 画面トップの商品プルダウン `<a-select>` および付帯する `<a-form layout="inline">` ヘッダー
- 下部固定の「選択中の SKU：…」`<a-divider>` と `<a-tabs>`（モーダル内へ移植）
- `selected-row` ハイライト用 CSS（行選択の概念がモーダル方式に置き換わるため）

## 5-4. データ取得

| タイミング | 関数 | 備考 |
|------------|------|------|
| 画面 mount | `getAdminProducts()` | 商品全件 |
| 商品行展開（初回） | `getProductSkus(productId)` | 既存 |
| `route.query.productId` 指定 | 同上を内部呼び出し | ProductList の「SKU管理」ボタン互換 |
| SKU モーダル `price` タブ | `getSkuPrices(skuId)` | 既存 |
| SKU モーダル `stock` タブ | `getSkuStock(skuId)` + `getSkuStockHistory(skuId)` | 既存（参照のみ） |
| SKU モーダル `image` タブ | `getSkuImages(skuId)` | 既存 |
| SKU 追加 | `createProductSku(productId, data)` | 既存 |
| 価格登録 | `createSkuPrice(skuId, data)` | 既存 |
| 画像アップロード | `uploadSkuImage(skuId, file)` | 既存 |

タブ単位の遅延ロード（`loadedTabs`）は維持。モーダルを閉じる／別 SKU で開き直すたびにリセット。

## 5-5. DB 変更

**なし**

## 5-6. API 変更

**なし**（Core / Console / Market いずれも既存エンドポイントをそのまま流用）

## 5-7. UI 変更まとめ（Console）

| 画面 | 変更内容 |
|------|---------|
| `features/skus/pages/SkuList.vue` | 商品プルダウン廃止 → 商品一覧テーブル化・SKU 詳細をモーダル化・「+ SKU を追加」を商品行配下に移動 |
| `features/products/pages/ProductList.vue` | 変更なし（`/skus?productId=N` 遷移は受け側で互換維持） |
| `router/index.js` | 変更なし（`/skus` ルートはそのまま） |

## 5-8. TDD テストケース

表示変更のみのため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- `/skus` を直接開いて商品一覧が初期表示される（プルダウンが存在しない）
- 商品行を展開すると SKU 一覧と「+ SKU を追加」フォームが表示される
- 別商品を展開しても、先に展開した商品の SKU は再取得されない（キャッシュ動作）
- SKU 行の「選択」ボタンで詳細モーダルが開き、価格 / 在庫 / 画像の各タブが従来通り動く
- モーダルを閉じて別 SKU を選び直すと、タブ表示が初期化される
- `/skus?productId=2` で開くと id=2 の商品行が初期展開される（ProductList の「SKU管理」ボタン互換）
- `is_active = false` の商品行はグレーアウト表示される

## 5-9. 申し送り（フェーズ17 へ）

本 Step 5 のレビュー時に、価格管理タブの **「適用開始日」に未来日を入れても即時反映される** 実装ギャップが見つかった。フェーズ10 設計書 §5.3 で計画されながら実装されていなかった「未来日価格の自動切替」を、フェーズ17 の日次バッチとして正式に取り込む：

- `phase17_batch_processing.md` r7 改訂で **3.1 ⑥ `ApplyScheduledPricesJob`** を新設
- DB：`product_sku_prices.is_active` カラム追加 + 新規 `product_sku_scheduled_prices` テーブル
- API：`GET / PUT / DELETE /api/skus/{id}/scheduled-price` 新設、既存 `GET /api/skus/{id}/prices` を「`is_active = TRUE` の現行価格 1 件」を返す仕様に統一
- UI：本 Step 5 で導入した SKU 詳細モーダル「価格管理」タブを「現行価格 / 予約変更 / 履歴」3 ブロック構成へ拡張（フェーズ17 Step 6.5 として実装）

本 Step 5 のスコープは「商品プルダウン廃止 → 商品一覧 + 展開 + モーダル化」までに留め、API / DB の変更は伴わない（規約遵守）。

---

# Step 6：Console 検索条件の拡充

設計書方針：商品マスタ／商品一覧（SKU 集約版）／売上管理／予約管理の各画面に、運用者が頻用する検索条件を追加する。Step 6 は子ステップ（6-1〜6-4）に分割し、画面ごとに独立して進める。

## Step 6-1：商品マスタ画面の検索条件 ✅ 実装完了（2026-05-07）

### 6-1-1. 背景・目的

商品マスタ画面（`/products`）は現状、「有効/無効」フィルタしか持たず、商品数が増えると目的の商品を一覧から探しにくい。運用上必要な以下の検索軸を追加する：

- 商品名（部分一致）
- 価格（最低〜最高の帯域検索）
- 在庫有無（在庫あり／在庫なし）

### 6-1-2. 設計方針

| 観点 | 方針 |
|------|------|
| フィルタ実装位置 | **クライアント側（`ProductList.vue` の `computed`）で完結** |
| API / DB 変更 | **なし**（既存 `GET /admin/products` のレスポンスに `name` / `minPrice` / `maxPrice` / `totalStock` が揃っているため、サーバ側変更は不要） |
| 検索 UI 配置 | 既存ヘッダー行の下に `<a-card>` で 1 行のフォーム枠を新設し、その中に inline フォームで 3 条件 + クリアボタンを配置 |
| 既存「有効/無効」フィルタとの関係 | 並列の AND フィルタとして機能（`filteredProducts` で同一 `computed` 内で重ね掛け） |
| 「クリア」操作 | `resetSearch()` で 3 条件のみリセット（有効/無効ラジオは維持） |

理由：
- 規約 5（Vue）に従い表示専用ロジックはコンポーネント内に閉じる
- 件数規模では現状クライアント側フィルタで十分（将来ページング化時にサーバ側へ移行余地）
- Step 2-5-3（売上管理画面の予約除外フィルタ）と同じ判断軸

### 6-1-3. 検索仕様

| 条件 | 入力 UI | 比較ロジック |
|------|--------|-------------|
| 商品名 | `<a-input allow-clear>` | `product.name` を小文字化して `includes()` 部分一致 |
| 価格（最低） | `<a-input-number min=0 step=100>` | `product.maxPrice >= minPrice`（`maxPrice` が null の商品は除外） |
| 価格（最高） | `<a-input-number min=0 step=100>` | `product.minPrice <= maxPrice`（`minPrice` が null の商品は除外） |
| 在庫 `すべて` | `<a-radio-button>` | フィルタなし |
| 在庫 `在庫あり` | 同上 | `product.totalStock > 0` |
| 在庫 `在庫なし` | 同上 | `product.totalStock <= 0`（次タスク.txt 原文「個数<0」は実運用で発生しないため、ゼロ含む「在庫切れ」として扱う） |

価格の帯域は **「商品の SKU 価格幅と検索範囲が重なるか」** で判定する。
- 例：商品 A の SKU 価格幅 1000〜3000 円、検索範囲 2000〜2500 円 → ヒット
- 例：商品 A の SKU 価格幅 1000〜3000 円、検索範囲 4000〜5000 円 → 非ヒット
- 価格未設定（`minPrice == null`）の商品は、価格条件を 1 つでも入れた時点で除外される

### 6-1-4. UI 変更（Console）

`features/products/pages/ProductList.vue`：
- 既存ヘッダー行の直下に `<a-card>` を追加し、`<a-form layout="inline">` で「商品名 / 価格（最低〜最高）/ 在庫（ラジオ）/ クリア」を配置
- `searchForm = { name, minPrice, maxPrice, stockFilter }` を ref で保持
- `filteredProducts` computed を「有効/無効フィルタ + 商品名 + 価格帯 + 在庫」を AND で重ね掛けする形に拡張

### 6-1-5. DB 変更
**なし**

### 6-1-6. API 変更
**なし**（`GET /admin/products` をそのまま流用）

### 6-1-7. TDD テストケース

表示変更のみのため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- 商品名に部分一致するキーワードを入れると一覧が絞られる
- 価格 最低のみ入力で、その額以上の SKU を持つ商品だけ残る
- 価格 最高のみ入力で、その額以下の SKU を持つ商品だけ残る
- 価格 最低・最高を両方入力で、価格幅が範囲と重なる商品だけ残る
- 在庫「在庫あり」で `totalStock > 0` の商品だけ残る
- 在庫「在庫なし」で `totalStock <= 0` の商品だけ残る
- 「有効のみ」+ 検索条件の組み合わせで AND になる
- 「クリア」ボタンで検索 3 条件がリセットされ、有効/無効フィルタは維持される

---

## Step 6-2：商品一覧（SKU 集約版）画面の検索条件 ✅ 実装完了（2026-05-07）

### 6-2-1. 背景・目的

商品一覧（SKU 集約版）画面（`/products/market-view`、`ProductMarketList.vue`）は Market 公開データの確認用画面で、現状は検索条件を一切持たず、商品数が増えるとスクロールでしか目的の商品に辿り着けない。

Step 1.5 でこの画面に「ステータス（発売前 / 発売中）」「予約開始日」「発売日」列が追加され、Step 6-1 で商品マスタ画面に検索条件を導入した流れを踏まえ、Market 公開データ確認用途で頻用する以下の検索軸を追加する：

- 商品名（部分一致）
- 価格（最低〜最高の帯域検索）
- 発売日（最早〜最遅の帯域検索）
- 予約開始日（最早〜最遅の帯域検索）
- 在庫有無（在庫あり／在庫なし）

### 6-2-2. 設計方針

| 観点 | 方針 |
|------|------|
| フィルタ実装位置 | **クライアント側（`ProductMarketList.vue` の `computed`）で完結**（Step 6-1 と同じ判断軸） |
| API / DB 変更 | **なし**（既存 `GET /api/products/market` の `ProductMarketSummary` DTO に `productName` / `minPrice` / `maxPrice` / `totalStock` / `releaseDate` / `preorderStartDate` が揃っているため、サーバ側変更は不要） |
| 検索 UI 配置 | `<a-page-header>` 直下に `<a-card>` で 1 行のフォーム枠を新設し、その中に inline フォームで 5 条件 + クリアボタンを配置 |
| 「クリア」操作 | `resetSearch()` で 5 条件全てをリセット |
| 日付帯域の比較基準 | `releaseDate` / `preorderStartDate` は `YYYY-MM-DD` 文字列のため、`<a-date-picker value-format="YYYY-MM-DD">` の出力と文字列比較で帯域判定する（時刻成分なし） |

理由：
- 規約 5（Vue）に従い表示専用ロジックはコンポーネント内に閉じる
- Step 6-1 と同じ判断軸（件数規模ではクライアント側で十分）
- 日付の文字列比較は `YYYY-MM-DD` 形式が辞書順 = 時系列順となる性質を利用（`Date` 変換不要）

### 6-2-3. 検索仕様

| 条件 | 入力 UI | 比較ロジック |
|------|--------|-------------|
| 商品名 | `<a-input allow-clear>` | `record.productName` を小文字化して `includes()` 部分一致 |
| 価格（最低） | `<a-input-number min=0 step=100>` | `record.maxPrice >= minPrice`（`maxPrice` が null の商品は除外） |
| 価格（最高） | `<a-input-number min=0 step=100>` | `record.minPrice <= maxPrice`（`minPrice` が null の商品は除外） |
| 発売日（最早） | `<a-date-picker value-format="YYYY-MM-DD">` | `record.releaseDate >= releaseDateFrom`（`releaseDate` が null の商品は除外） |
| 発売日（最遅） | 同上 | `record.releaseDate <= releaseDateTo`（同上） |
| 予約開始日（最早） | 同上 | `record.preorderStartDate >= preorderStartDateFrom`（`preorderStartDate` が null の商品は除外） |
| 予約開始日（最遅） | 同上 | `record.preorderStartDate <= preorderStartDateTo`（同上） |
| 在庫 `すべて` | `<a-radio-button>` | フィルタなし |
| 在庫 `在庫あり` | 同上 | `record.totalStock > 0` |
| 在庫 `在庫なし` | 同上 | `record.totalStock <= 0`（Step 6-1 と同じく「ゼロ含む在庫切れ」として扱う） |

価格・発売日・予約開始日の帯域は **「商品の値幅と検索範囲が重なるか／値が範囲内に入るか」** で判定する。
- 価格は SKU 価格幅（`minPrice` 〜 `maxPrice`）と検索範囲のオーバーラップ判定（Step 6-1 と同じ）
- 発売日 / 予約開始日は商品単位の単一値のため、範囲内に入るかの単純判定
- 値未設定（null）の商品は、該当条件を 1 つでも入れた時点で除外される

### 6-2-4. UI 変更（Console）

`features/products/pages/ProductMarketList.vue`：
- `<a-page-header>` 直下に `<a-card>` を追加し、`<a-form layout="inline">` で「商品名 / 価格（最低〜最高）/ 発売日（最早〜最遅）/ 予約開始日（最早〜最遅）/ 在庫（ラジオ）/ クリア」を配置
- `searchForm = { name, minPrice, maxPrice, releaseDateFrom, releaseDateTo, preorderStartDateFrom, preorderStartDateTo, stockFilter }` を ref で保持
- `filteredProducts` computed を新設し、`<a-table :data-source>` を `products` から `filteredProducts` に切り替える

### 6-2-5. DB 変更
**なし**

### 6-2-6. API 変更
**なし**（`GET /api/products/market` をそのまま流用）

### 6-2-7. TDD テストケース

表示変更のみのため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- 商品名に部分一致するキーワードを入れると一覧が絞られる
- 価格 最低・最高で帯域がオーバーラップする商品だけ残る
- 発売日 最早・最遅で `releaseDate` が範囲内の商品だけ残る
- 予約開始日 最早・最遅で `preorderStartDate` が範囲内の商品だけ残る
- 在庫「在庫あり」で `totalStock > 0` の商品だけ残る
- 在庫「在庫なし」で `totalStock <= 0` の商品だけ残る
- 複数条件を組み合わせた場合 AND になる
- 「クリア」ボタンで全検索条件がリセットされる

---

## Step 6-3：売上管理画面の日付帯域検索と集計粒度切替 ✅ 実装完了（2026-05-07）

### 6-3-1. 背景・目的

売上管理画面（`/sales`、`SalesList.vue`）は phase14〜16 Step 2 を経て「予約除外フィルタ」「見込み表示トグル」が揃ったが、**期間で絞る／集計粒度を切り替える** 機能がない：

- 一覧タブは全期間が時系列で並ぶだけで、「先月分だけ見たい」が一覧では難しい
- 集計タブの「月別売上」カードは粒度が月固定で、「日別の動きを見たい」「年別で大局を掴みたい」に応えられない

設計書（次タスク.txt）の指示は「日単位、月単位、年単位でボタン切り替えか検索条件に設定（工数が少ない方で）」。本ステップでは：

- **一覧タブ**：売上日の帯域検索（from-to）を追加
- **集計タブ**：「月別売上」カードを日別／月別／年別の粒度切替対応に拡張（既定：月別）

の 2 軸で対応する。

### 6-3-2. 設計方針

| 観点 | 方針 |
|------|------|
| フィルタ実装位置 | **クライアント側（`SalesList.vue` の `computed`）で完結**（Step 2-5-3 / Step 6-1 / Step 6-2 と同じ判断軸） |
| API / DB 変更 | **なし**（既存 `GET /api/sales` の `salesDate`（`YYYY-MM-DD`）を流用） |
| 一覧タブ UI | 既存の「予約を除外」チェックボックスと同じ行に `<a-date-picker>` 2 つで売上日の帯域を配置 |
| 集計タブ UI | 「月別売上」カードのタイトル右側に `<a-radio-group>` で「日 / 月 / 年」切替。既定は「月」 |
| 粒度切替の影響範囲 | 「月別売上」カードのみに適用。SKU別／決済方法別／購入区分別カードは粒度の概念がないためそのまま（運用上の文脈が違う） |
| 列タイトルの動的化 | 粒度に応じてカードタイトルとカラム名を「日別売上 / 日付」「月別売上 / 年月」「年別売上 / 年」に切替 |

理由：
- 一覧の期間絞り込みは「特定の月だけ見たい」運用要求に直結し、日付帯域検索が UI として最も自然
- 集計の粒度切替は「月別売上」のスキーマと最も親和性が高い（年月キーを `slice(0,4)` / `slice(0,7)` / `slice(0,10)` で切り出すだけ）
- SKU別・決済方法別・購入区分別は「カテゴリ別の累計」を見る指標で、粒度切替を入れるとカードが増えて視認性が落ちる

### 6-3-3. 検索仕様（一覧タブ）

| 条件 | 入力 UI | 比較ロジック |
|------|--------|-------------|
| 売上日（最早） | `<a-date-picker value-format="YYYY-MM-DD">` | `s.salesDate >= salesDateFrom`（`salesDate` が null の行は除外） |
| 売上日（最遅） | 同上 | `s.salesDate <= salesDateTo`（同上） |
| 予約を除外 | 既存 `<a-checkbox>` | `excludePreorderInList = true` で `s.preorder = TRUE` を除外 |

`filteredSalesForList` computed で「日付帯域 + 予約除外」を AND 重ね掛けする。日付は `YYYY-MM-DD` 文字列の辞書順比較（時系列順と一致）。

### 6-3-4. 集計粒度仕様（集計タブ「月別売上」カード）

| 粒度 | キー切り出し | カードタイトル | カラム名 |
|------|------------|--------------|---------|
| 日 | `salesDate.slice(0, 10)` | 日別売上 | 日付 |
| 月（既定） | `salesDate.slice(0, 7)` | 月別売上 | 年月 |
| 年 | `salesDate.slice(0, 4)` | 年別売上 | 年 |

ソート：キーの降順（直近を先頭）。粒度ラジオは `summaryGranularity` ref で保持し、既定は `'month'`。「見込み表示」トグルとは独立に動作する（粒度切替は集計対象集合を変えない）。

### 6-3-5. UI 変更（Console）

`features/sales/pages/SalesList.vue`：
- 一覧タブの予約除外チェックの行に売上日の `<a-date-picker>` 2 つを並べる
- 集計タブの「月別売上」カードヘッダー右側（`extra` スロット）に `<a-radio-group>` で粒度切替
- `filteredSalesForList` を「日付帯域 + 予約除外」の AND に拡張
- `summaryByMonth` を `summaryByGranularity` にリネームし、粒度別キー切り出しに対応
- カードタイトル / カラム title を粒度に応じて差し替え

### 6-3-6. DB 変更
**なし**

### 6-3-7. API 変更
**なし**（`GET /api/sales` をそのまま流用）

### 6-3-8. TDD テストケース

表示変更のみのため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- 一覧タブの売上日 from / to で期間内の行だけ残る
- 一覧タブの予約除外と日付帯域は AND で重ね掛けされる
- 集計タブの粒度ラジオで「日別 / 月別 / 年別」に切り替わる
- 粒度切替時に「見込み表示」トグルの効果（予約含む/除く）が維持される
- 既定状態（粒度 = 月）で従来の「月別売上」と同じ集計結果になる

---

## Step 6-4：予約管理画面の検索条件 ✅ 実装完了（2026-05-07）

### 6-4-1. 背景・目的

予約管理画面（`/preorders`、`PreorderList.vue`）は Step 2 で新設した予約商品の運用画面で、現状は検索条件を一切持たない。発売予定の商品が増えてくると「特定の商品を即座に絞り込みたい」「特定の発売週の予約状況だけ見たい」運用要求が出るため、以下の検索軸を追加する：

- 商品名（部分一致）
- 価格（最低〜最高の帯域検索）
- 発売日（最早〜最遅の帯域検索）
- 予約開始日（最早〜最遅の帯域検索）

### 6-4-2. 設計上の差分（Step 6-1 / 6-2 との違い）

Step 6-1 / 6-2 はすべて「クライアント側 `computed` で完結・API/DB 変更なし」だったが、本ステップでは **Core API レスポンス DTO への項目追加** を伴う：

- 既存 `PreorderProductItem`（[PreorderProductItem.java](../../../amazia-core/src/main/java/com/example/product/dto/PreorderProductItem.java)）には `minPrice` / `maxPrice` が含まれず、UI 単独では価格帯検索ができない
- 商品マスタ画面（Step 6-1）の `ProductAdminSummary` と同じ集計（SKU の `productSkuPrices.price` の min/max）を Service 層に追加することで、表示用の「価格帯」と検索用の判定材料を 1 度の API コールで賄える
- フロント追加実装は商品名／価格帯／発売日／予約開始日の 4 軸帯域フィルタで Step 6-2 と同一構造

### 6-4-3. Core 変更

#### DTO（`PreorderProductItem.java`）
- `Integer minPrice` / `Integer maxPrice` を追加（null 許容：SKU が無い／価格未設定の商品は null）
- 既存項目（productId / productName / preorderStartDate / releaseDate / daysUntilRelease / acceptPreorder / isActive / preorderQuantity / preorderAmount）はそのまま

#### Service（`ListPreorderProductsService.java`）
- 既存の SKU 集合取得（`skuRepository.findByProductIdIn`）に加え、対象 SKU について `productSkuPriceRepository.findBySkuIdIn` で価格を 1 回まとめて取得
- 商品単位で SKU 価格を集計し min / max を算出（パターンは [AdminListProductService.java](../../../amazia-core/src/main/java/com/example/product/service/AdminListProductService.java) と同じ）

### 6-4-4. UI 変更（Console）

`features/preorder/pages/PreorderList.vue`：
- `<a-page-header>` 直下に `<a-card>` で検索フォーム（商品名／価格 from-to／発売日 from-to／予約開始日 from-to／クリア）を配置
- 既存列の右側に「価格帯」列を追加（Step 6-1 の `ProductList.vue` と同表記：未設定／単一値／minPrice 〜 maxPrice）
- `searchForm` ref と `filteredPreorders` computed で AND 重ね掛け
- `<a-table :dataSource>` を `preorders` → `filteredPreorders` に切替

### 6-4-5. 検索仕様

| 条件 | 入力 UI | 比較ロジック |
|------|--------|-------------|
| 商品名 | `<a-input allow-clear>` | `record.productName` を小文字化して `includes()` 部分一致 |
| 価格（最低） | `<a-input-number min=0 step=100>` | `record.maxPrice >= minPrice`（`maxPrice` が null の商品は除外） |
| 価格（最高） | 同上 | `record.minPrice <= maxPrice`（`minPrice` が null の商品は除外） |
| 発売日（最早 / 最遅） | `<a-date-picker value-format="YYYY-MM-DD">` | Step 6-2 と同じ範囲内判定 |
| 予約開始日（最早 / 最遅） | 同上 | 同上 |

価格帯のオーバーラップ判定・日付の文字列辞書順比較は Step 6-2 と同方針。

### 6-4-6. DB 変更
**なし**（既存 `products` / `product_skus` / `product_sku_prices` を集計して返すのみ）

### 6-4-7. API 変更

| 区分 | エンドポイント | 種別 |
|------|---------------|------|
| Core | `GET /api/products/preorders` | レスポンスフィールド追加（`minPrice` / `maxPrice`、互換的変更） |
| Console | `GET /api/preorders` | 変更なし（Pass-through が透過する） |

設計書 `docs/api_design/Core_API.md` の該当エンドポイント・Response Item 表に `minPrice` / `maxPrice` を追記する。

### 6-4-8. TDD テストケース

#### Core
- 既存テストの「レスポンス項目が完全に詰められる」に minPrice / maxPrice の確認を追加
- 新規：複数 SKU の価格を min / max で集計して返すこと
- 新規：SKU の価格が未登録の商品は minPrice / maxPrice が null になること

#### UI
表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保。

- 商品名に部分一致するキーワードで一覧が絞られる
- 価格帯・発売日・予約開始日の各帯域検索が AND で重ね掛けされる
- 「クリア」ボタンで全検索条件がリセットされる
- 価格未設定の商品は価格条件を入れた瞬間に除外される

---

## Step 6-5：配送管理画面の検索条件拡充 ✅ 実装完了（2026-05-07）

### 6-5-1. 背景・目的

配送管理画面（`/delivery`、`DeliveryList.vue`）は phase15 で配送ステータスのみのサーバサイドフィルタを持つが、運用上は以下の検索ニーズが頻出する：

- **追跡番号** の部分一致検索（顧客から「追跡番号 XXX の状況は？」と問い合わせを受けた時の即応）
- 売上 ID・配送方法・配送予定日・発送日・配達完了日の絞り込み

設計書（次タスク.txt）の指示は「配送ステータスに加えて、各列を検索対象とする。特に追跡番号は分かりやすいようにしたい」。本ステップで全 9 列のうち操作列以外の各列に検索対象を広げる。

### 6-5-2. 設計方針

| 観点 | 方針 |
|------|------|
| フィルタ実装位置 | **クライアント側（`DeliveryList.vue` の `computed`）で完結**（Step 6-1 / 6-2 / 6-4 と同方針） |
| 既存サーバサイド `shippingStatusId` フィルタ | **廃止**（クライアント側に統合）。Core API は変更しないが、Console 画面からは `shippingStatusId` クエリを送らない |
| API / DB 変更 | **なし**（既存 `GET /api/deliveries` をそのまま利用） |
| 追跡番号の UI | カード上部に **専用の独立行** で配置し「追跡番号」ラベル＋幅広 input。他の検索条件とは別行にして視認性を確保 |
| その他列 | 配送ステータス・売上 ID・配送方法・配送予定日（帯域）・発送日（帯域）・配達完了日（帯域）を 2 行目の inline フォームに配置 |
| 「クリア」操作 | 全条件をリセット（`reload()` も含む既存 `reset` から差し替え） |

理由：
- 追跡番号の問い合わせ対応は配送管理の主要動線で、専用行で押し出すことで「分かりやすく」を実装的に体現
- 既存サーバサイドフィルタを廃止する判断は、クライアントフィルタとの同居が UX 上「ステータスを外したときだけサーバ往復、ほかはクライアント」という説明しづらい挙動を生むため。データ規模では現状クライアント側で十分（Step 2-5-3 / Step 6-3 と同じ判断軸）

### 6-5-3. 検索仕様

| 条件 | 入力 UI | 比較ロジック |
|------|--------|-------------|
| 追跡番号 | `<a-input allow-clear>`（幅広・専用行） | `record.trackingCode` を小文字化して `includes()` 部分一致 |
| 売上 ID | `<a-input-number>` | `record.salesId === salesId`（厳密一致・`null` 入力で条件無効） |
| 配送方法 | `<a-select allow-clear>` | `record.shippingMethodId === shippingMethodId` |
| 配送ステータス | `<a-select allow-clear>` | `record.shippingStatusId === shippingStatusId` |
| 配送予定日（最早 / 最遅） | `<a-date-picker value-format="YYYY-MM-DD">` | `YYYY-MM-DD` 文字列の辞書順帯域判定（Step 6-2 と同方針）。`scheduledDate` が null の行は条件付与時に除外 |
| 発送日（最早 / 最遅） | 同上 | 同上（`shippedDate` 対応） |
| 配達完了日（最早 / 最遅） | 同上 | 同上（`deliveredDate` 対応） |

複数条件は AND で重ね掛け。

### 6-5-4. UI 変更（Console）

`features/delivery/pages/DeliveryList.vue`：
- 既存の `<a-form layout="inline">` を `<a-card>` 内に変更し、「追跡番号」専用行＋「その他条件」inline 行の 2 段構成
- 既存の「検索」「クリア」ボタンを「クリア」のみに統一（クライアントフィルタなので明示送信ボタン不要）
- `searchForm` ref と `filteredDeliveries` computed を導入し、`<a-table :dataSource>` を `deliveries` → `filteredDeliveries` に切替
- `onMounted` での全件取得は `listDeliveries()`（パラメータなし）に統一

### 6-5-5. DB 変更
**なし**

### 6-5-6. API 変更
**なし**（`GET /api/deliveries` は引き続きクエリパラメータ `shippingStatusId` を受け付けるが、Console 画面からは送らなくなる。サーバ側仕様としては互換維持）

### 6-5-7. TDD テストケース

表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保。

- 追跡番号の部分一致で行が絞り込まれる
- 配送ステータスのプルダウンで該当ステータスのみに絞られる
- 配送予定日 from / to で帯域内の行だけ残る
- 複数条件を組み合わせた場合 AND になる
- 「クリア」ボタンで全条件がリセットされ全件表示に戻る

---

## Step 6-6：入荷管理画面の検索条件拡充と Excel 入荷経路の inbounds 統合 ✅ 実装完了（2026-05-07）

### 6-6-1. 背景・目的

入荷管理画面（`/inbound`、`InboundList.vue`）は phase15 で新設したが、検索条件は `productId` の単一サーバ側フィルタしかなく、運用上は以下の検索ニーズが残っている：

- 商品ID 以外の各列（倉庫・仕入先・数量・入荷日・登録日時）での絞り込み
- **追跡番号** での検索（仕入先からの納品問い合わせ対応）

加えて、phase15 / Step 3 までの実装で「Excel 一括入荷」が **`/inbounds` を経由せず `/skus/{id}/stocks/receive` を直接呼ぶ仕様** となっており、Excel 経由で入った在庫が **入荷管理画面に表示されない**（=履歴も追跡番号も残らない）構造ギャップが見つかった。

次タスク.txt の指示：「商品IDに加えて、各列を検索対象とする。追跡番号を追加。エクセルに追跡番号を載せて、エクセル入荷の場合はその追跡番号を登録、表示するようにしたい。本来、DBカラム追加はスコープ外だが、本件は16対象とする。」

本ステップで **DB カラム追加を含むフェーズ16の例外スコープ** として、追跡番号の永続化と Excel 入荷経路の inbounds 統合を併せて実施する。

### 6-6-2. 設計方針

| 観点 | 方針 |
|------|------|
| DB | `inbounds` に `tracking_code VARCHAR(255) NULL` を追加（手動登録時は null） |
| Excel 入荷経路の統合 | `ImportProductSkuStockService` を **`POST /api/inbounds` 経由** に切り替え。SKU コード → SKU ID 解決後、`{productId, skuId, quantity, trackingCode}` を Core に渡す。これにより Excel 入荷も入荷管理画面の一覧 / 検索対象になる |
| Core API 変更 | `RegisterInboundRequest` / `InboundResponse` に `trackingCode`（任意項目）を追加 |
| 既存手動入荷フロー | UI（`InboundCreate.vue`）はそのまま：手動登録時の追跡番号は受け取らない（Excel のみで使う運用要求のため）。Core は受け取れば保存する仕様（API としては手動からも送れる） |
| 検索 | クライアント側 `computed` で完結（Step 6-5 と同方針）。サーバ側 `productId` フィルタは廃止し全件取得 |
| 追跡番号 UI 配置 | Step 6-5 の配送管理画面に倣い、検索カード上部に「追跡番号」専用行を独立配置 |
| Excel ヘッダー | 既存 `sku_code` / `quantity` に **任意列 `tracking_code`** を追加。空欄なら null として登録（既存 Excel ファイルとの後方互換維持） |

### 6-6-3. DB 変更

`inbounds` テーブル：

| 追加カラム | 型 | NULL | デフォルト | 備考 |
|-----------|-----|------|-----------|------|
| tracking_code | VARCHAR(255) | NULL | NULL | 配送追跡番号。Excel 入荷で取り込む。手動入荷では null |

`schema.sql` には `ALTER TABLE inbounds ADD COLUMN IF NOT EXISTS tracking_code VARCHAR(255)` を冪等で追記する（CLAUDE.md「マイグレーション方式の前提」遵守）。

### 6-6-4. Core 変更

#### Entity（`Inbound.java`）
- `private String trackingCode;` を追加（getter / setter）

#### DTO
- `InboundResponse`：`trackingCode` を追加
- `RegisterInboundRequest`：`trackingCode` を追加（任意項目・バリデーションなし）

#### Service（`RegisterInboundService.java`）
- `request.getTrackingCode()` を `inbound.setTrackingCode()` に詰める
- 操作ログのコメントに含めるかは情報量が増えすぎるため見送り（DB 検索すれば十分）

### 6-6-5. Console 変更

#### `ImportProductSkuStockService.php`
従来は SKU コード → SKU ID 解決後に `/skus/{id}/stocks/receive` を呼んでいたが、Step 6-6 では：

1. SKU コード → SKU ID 解決（`GET /skus/by-code/{code}`）
2. SKU ID → product_id 解決（既存 SKU レスポンスに含まれる）
3. **`POST /inbounds`** に `{productId, skuId, quantity, trackingCode}` を送る

行ごとの inbound レコードが立つため、Excel 入荷も入荷管理画面で確認・検索できる。

Excel ヘッダーの追加列：

| 列 | 必須/任意 | 内容 |
|----|----------|------|
| sku_code | 必須 | 既存 |
| quantity | 必須 | 既存 |
| tracking_code | **任意（追加）** | 配送追跡番号。空欄なら null |

`validateRow` は tracking_code を任意扱いで、長さ 255 字超のみエラーとする。

### 6-6-6. UI 変更（Console）

`features/inbound/pages/InboundList.vue`：
- 既存 `<a-form>` を `<a-card>` に変更し、追跡番号専用行 + inline 行の 2 段構成（Step 6-5 と同方針）
- 検索条件：追跡番号（部分一致）／商品ID（完全一致）／倉庫ID（完全一致）／仕入先ID（完全一致）／入荷数量（最低・最高）／入荷日（帯域）
- `<a-table>` に「追跡番号」列を「入荷数量」と「入荷日」の間に挿入
- サーバ側 `productId` クエリは廃止し、`onMounted` で全件取得 → クライアントフィルタ

`InboundCreate.vue`：**変更なし**（手動入荷では追跡番号を入力させない方針）

### 6-6-7. API 変更まとめ

| 区分 | エンドポイント | 種別 |
|------|---------------|------|
| Core | `POST /api/inbounds` | リクエスト項目追加（`trackingCode`、互換的変更） |
| Core | `GET /api/inbounds` | レスポンス項目追加（`trackingCode`、互換的変更）。`productId` クエリは仕様維持 |
| Console | `POST /api/inbounds` | リクエスト・レスポンス透過（Pass-through） |
| Console | `POST /api/skus/stocks/import` | 内部実装が `POST /skus/.../receive` → `POST /inbounds` に切替（互換的・公開仕様は同じ） |

設計書 `docs/api_design/Core_API.md` と `docs/database_design/TBL_inbounds.md` を更新する。`required_tables.txt` は inbounds が既存登録済みなので変更不要（カラム追加のみ）。

### 6-6-8. TDD テストケース

#### Core
- `Inbound` Entity の永続化で `trackingCode` が往復する
- `RegisterInboundService` が `trackingCode` 付きリクエストで保存できる（既存サービステストに 1 ケース追加）
- `RegisterInboundService` が `trackingCode` 未指定でも従来通り保存できる（互換性確認）
- `ListInboundService` のレスポンス DTO に `trackingCode` が含まれる（既存テストに assertion 追加）

#### Console（Laravel Feature）
- `ImportProductSkuStockService` の Excel 取り込みで `tracking_code` 列がある行が `/inbounds` に POST される（Mock Http で検証）
- `tracking_code` 列が空 / 列自体がない既存 Excel でも動作する（後方互換）

#### UI
表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保。

### 6-6-9. 申し送り

- Excel 入荷経路を `/inbounds` に切り替えたことで、行単位で `inbounds.id` が発行される。これにより操作ログ・FIFO 再計算・inventories 同期が **Excel 入荷でも正しく走る** ようになる（従来は欠落していた）
- 旧 `/skus/{id}/stocks/receive` API は他フローの利用が残るため廃止しない（SKU 管理画面の在庫タブ参照系・テスト・将来用途）

---

## Step 7：商品マスタ画面の UI 視認性・操作性改善 🟡 設計策定（2026-05-07）

### 7-1. 背景・目的

商品マスタ画面（`/products`、`ProductList.vue`）は Step 1 / 1.5 / 6-1 で機能を積み増してきた結果、画面要素が増え、以下の **視認性・操作性のひずみ** がスクリーンショット観察で確認できた。本ステップでは DB / API には手を入れず、`ProductList.vue` 単体の見た目と挙動の整理に閉じる。

観察された主な課題：

| # | 課題 | 影響 |
|---|------|------|
| A | `<a-page-header>` の `sub-title="Amazia Console"` がサイドバーのアプリ名と重複している | タイトル横にノイズが出て、画面名（"商品マスタ"）の視認性が落ちる |
| B | 「一括削除（0件）」が常に表示され、未選択時もグレーボタンとして主要操作列を圧迫する | 主要 CTA（新規登録）への視線誘導を阻害 |
| C | 上段の「すべて／有効のみ／無効のみ」（`activeFilter`）と検索カード内の「在庫 すべて／在庫あり／在庫なし」（`stockFilter`）が **同じ pill ラジオ表現** で並走している | 「フィルタ群が二か所に分かれている」状態で、運用者がどちらでどの軸を絞るか毎回探すコストが発生 |
| D | テーブル列「公開状態」と「有効/無効」が並んでいるが、Step 1 で導入した直交軸の関係が UI 上で読み取りづらい | `is_active = FALSE` かつ `公開期間内` のとき「●公開中＋無効」という一見矛盾した表示になり、初見の運用者が混乱する |
| E | ヘッダーラベル「有効/無効」が列幅 90px で **"効" だけ折り返している** | 文字単位で改行され、見出しとして体裁が崩れる |
| F | 「価格帯」「合計在庫」が左寄せ表示で、桁の比較がしづらい | 数値列の右寄せ原則から外れている |
| G | 「+」（行展開）アイコンと行チェックボックスが左端に並び、どちらをクリックすると何が起きるかが直感的でない | 現状は `expand-row-by-click` で行クリックでも展開するが、視覚的手がかりが弱い |
| H | 「操作」列の "編集 / SKU管理 / 削除" が等価ボタンで横並び | SKU管理は遷移先が明確でも、視覚的には編集・削除と同列の重みに見え、誤操作リスクは中庸 |
| I | テーブル下のページネーションに **総件数表示がなく**、現在何件中の何ページを見ているか判別できない | 件数が増えたとき「あと何ページあるか」「フィルタ後の件数はいくつか」が分からない |
| J | 検索カード内で「商品名」「価格」「在庫」が `inline` で横一列にあり、ウィンドウ幅が狭いと折り返し時に **`〜` セパレータと "円" 単位が分断される** | 中間幅の表示で価格レンジが読みにくい |

### 7-2. スコープ

| 観点 | 方針 |
|------|------|
| 対象画面 | `features/products/pages/ProductList.vue` 単体 |
| 対象外 | `ProductForm.vue`（編集画面）／`SkuList.vue`／その他画面（次タスクで個別判断） |
| DB 変更 | **なし** |
| API 変更 | **なし** |
| バックエンド変更 | **なし**（既存 `GET /admin/products` のレスポンスをそのまま使う） |

### 7-3. 改善方針

#### 7-3-1. ヘッダー整理（課題 A・B）

- `<a-page-header>` の `sub-title` を削除（`title="商品マスタ"` のみに）
- 既存ヘッダー右側の総件数表示として、フィルタ後件数 / 全体件数のサブテキストを `<a-page-header>` 配下の `description` slot に表示
  - 例：`全 3 件中 3 件を表示` / フィルタ適用時は `全 3 件中 1 件を表示（フィルタ適用中）`
- 「一括削除」ボタンは **`v-if="selectedRowKeys.length > 0"`** に変更し、選択がないときは DOM ごと出さない
  - これにより未選択時のヘッダー左側はラジオフィルタのみとなり、主要 CTA「+ 新規登録」への視線誘導が回復する

#### 7-3-2. フィルタの一本化（課題 C）

「有効/無効」ラジオを **検索カード内に集約** し、ヘッダー行から取り除く。

- 検索カードのレイアウトを `<a-card>` 内 2 段構成に変更：
  - 1 段目（基本検索）：商品名／価格（最低〜最高）／在庫
  - 2 段目（状態フィルタ）：有効/無効（すべて／有効のみ／無効のみ）／公開状態（すべて／公開中のみ／非公開のみ）／クリア
- 「公開状態」フィルタは新設：既存 `isPublished()` ロジックを `filteredProducts` に取り込み、`publishStatus = 'all' | 'published' | 'unpublished'` で AND 結合する
  - これは課題 D の解決にも寄与する（運用者が "公開中で無効" を意図的に絞り込めるようになる）
- 「クリア」ボタンの動作は **全 5 条件をリセット**（現行は 3 条件のみリセットだが、フィルタ統合に合わせて全部戻す挙動に変更）
  - Step 6-1-7 の「クリアで有効/無効は維持」仕様は本ステップで上書きする旨、Step 6-1 の備考に追記する

#### 7-3-3. テーブル列の見直し（課題 D・E・F）

| 列 | 変更点 |
|----|-------|
| ID | 変更なし |
| 商品名 | 変更なし |
| SKU数 | 変更なし |
| 価格帯 | `align: 'right'` を付与し右寄せ |
| 合計在庫 | `align: 'right'` を付与し右寄せ。値が 0 のときは赤系の小バッジ（`<a-tag color="red">在庫なし</a-tag>`）で表示 |
| 状態 | **「公開状態」「有効/無効」を 1 列に統合**。タイトル「状態」、内容は `<a-space>` で「●公開中／●非公開」バッジ＋「有効／無効」タグの 2 行縦並び。列幅 110px |
| 操作 | 課題 H の対応として、`<a-button-group>` で「編集」を primary、「SKU管理」を default、「削除」を danger アウトラインに揃え、間隔と密度を整える |

「状態」列の縦 2 行表示は、Step 1 で導入した直交 2 軸（公開期間 / `is_active`）を **見た目でも 2 軸として表現する** ためのもの。1 列に統合することで列数も減り、ヘッダー折り返し問題（課題 E）も解消する。

#### 7-3-4. 行展開アイコンと選択チェックボックス（課題 G）

- `expand-row-by-click` を **削除**（行クリックでの展開は副作用が大きい）
- `<a-table>` の `expandIcon` カスタムレンダリングで、`+` / `−` のボタンを `<a-button type="text" size="small">` に統一して視認性を上げる
- 行選択チェックボックスとの間に 4px のスペーサを置き、誤クリックを防ぐ

#### 7-3-5. ページネーション（課題 I）

- `<a-table>` の `:pagination` を明示的に設定：
  ```js
  pagination: {
    pageSize: 20,
    showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
    showSizeChanger: true,
    pageSizeOptions: ['20', '50', '100'],
  }
  ```
- 件数表示は `filteredProducts.length` に対して効くため、フィルタ適用中の件数推移も追える

#### 7-3-6. 検索カードのレスポンシブ（課題 J）

- 価格の「最低〜最高〜円」は単一の `<a-form-item label="価格">` 内に内包されているが、ウィンドウ幅で `<a-input-number>` × 2 が折り返しても **セパレータ `〜` と単位 `円` が同じ行内に残る** ように `<a-input-group compact>` でラップする
- 検索カード全体を `<a-form layout="inline">` から `<a-form layout="vertical" class="search-form-grid">` に変更し、`display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));` で列を自動折り返し

### 7-4. UI 変更まとめ（Console）

`features/products/pages/ProductList.vue`：

- `<a-page-header>` の `sub-title` 削除、`description` slot に件数表示
- ヘッダー行から「すべて／有効のみ／無効のみ」ラジオを削除
- 「一括削除」ボタンを `v-if="selectedRowKeys.length > 0"` 化
- 検索カードを 2 段構成に変更（基本検索 + 状態フィルタ）
- 「公開状態」フィルタを新設し `filteredProducts` に AND 結合
- `columns` 配列：
  - 「価格帯」「合計在庫」に `align: 'right'`
  - 「公開状態」「有効/無効」を「状態」1 列に統合
- `expand-row-by-click` 削除、`expandIcon` カスタム
- `pagination` に `showTotal` / `showSizeChanger` 追加
- 価格入力を `<a-input-group compact>` でラップ

### 7-5. DB 変更
**なし**

### 7-6. API 変更
**なし**

### 7-7. TDD テストケース

表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- ヘッダーから "Amazia Console" のサブタイトルが消えている
- 商品を 1 件もチェックしていない状態で「一括削除」ボタンが画面に存在しない
- 1 件以上チェックすると「一括削除（N件）」が現れる
- 「有効/無効」ラジオが検索カード内にあり、ヘッダー行には存在しない
- 「公開状態」フィルタを「公開中のみ」にすると `publishEnd` が過去の商品が消える
- 「公開状態」フィルタを「非公開のみ」にすると公開期間内の商品が消える
- クリアボタン押下で 5 条件すべてが既定値に戻る
- 「価格帯」「合計在庫」列の数値が右寄せで表示される
- 「状態」列に「●公開中／●非公開」と「有効／無効」が縦 2 行で表示される
- 行クリックでは展開せず、左端の `+` ボタンクリック時のみ展開する
- ページネーションに「N-M / 全 K 件」が表示される
- ページサイズを 50 / 100 に切り替えできる
- ウィンドウ幅 768px 程度でも価格の `〜` と `円` が分断されない

### 7-8. 申し送り

- 同様の UI 観点（ヘッダー重複・フィルタ二重化・状態列の統合・数値右寄せ・件数表示・展開アイコン）は他の一覧画面（`SkuList.vue` / `ProductMarketList.vue` / `InboundList.vue` / `DeliveryList.vue` 等）にも横展開余地がある。本ステップでは商品マスタに閉じ、横展開要否は次タスクで判断する。
- Step 6-1-7 の「クリアで有効/無効は維持」仕様は Step 7-3-2 で上書きされる。Step 6-1 の説明節にその旨の追記を入れる（実装時併せて対応）。

---

## Step 8：SKU管理画面の UI 視認性・操作性改善 ✅ 実装完了（2026-05-07）

### 8-1. 背景・目的

SKU管理画面（`/skus`、`SkuList.vue`）は Step 1.5 / Step 3 で SKU 一覧構造と発売前後表示を整えたが、商品マスタ画面（Step 7 対象）と並べて見たときに以下の **視認性・操作性のひずみ** がスクリーンショット観察と実装読解で確認できた。本ステップでは Step 7 と同様、DB / API には手を入れず `SkuList.vue` 単体に閉じる。

観察された主な課題：

| # | 課題 | 影響 |
|---|------|------|
| A | `<a-page-header>` の `sub-title="Amazia Console"` がサイドバーのアプリ名と重複（Step 7 課題 A と同根） | タイトル横にノイズが出て、画面名（"SKU管理"）の視認性が落ちる |
| B | 画面上部に **検索フォームが一切ない** | 商品数が増えると目的の親商品を探すのにスクロールするしかない（商品マスタ画面とのギャップが大きい） |
| C | 親テーブルのヘッダー行と本文行の **間に過大な余白** が見えるレイアウトで、初見では「商品が読み込めていないのか」と誤認しやすい | スクリーンショット上でヘッダー（ID / 商品名…）と最初の行（2 / テスト）の間に空白帯が大きく出ている |
| D | 親テーブルに「発売 / 公開状態 / 有効/無効」の 3 列が右側に並ぶが、Step 7 課題 D と同根の「公開状態」「有効/無効」の意味重複がここでも発生 | 直交軸であることが UI から読み取れない |
| E | 「有効/無効」列幅 90px で **"効" だけ折り返し**（Step 7 課題 E と同根） | 列見出しの体裁が崩れる |
| F | `expand-row-by-click="true"` のため **行のどこをクリックしても展開**する | 行内のテキストを選択しようとしただけで展開し、誤操作の温床（Step 7 課題 G と同根） |
| G | 展開行内で「SKU 一覧テーブル」と「SKU 追加フォーム（色 / サイズ / SKUを追加）」が **境界線なく縦に並ぶ** | どこまでが既存 SKU の表示で、どこからが追加 UI かが視覚的に分かれていない |
| H | SKU 行右端の **青塗りボタンのラベルが「選択」** | 押すと SKU 詳細モーダル（価格 / 在庫 / 画像）が開くが、「選択」では何が起きるか予測できない |
| I | SKU 追加フォームが **色・サイズ必須** のバリデーションを `handleSkuSubmit` に直書き（`if (!form.color || !form.size) return`） | 色やサイズの概念がない商品（食品・書籍など）でも入力を強制される。実 DB スキーマ上は NULL 可だが UI が阻んでいる |
| J | 親テーブルに「SKU数」列があるが、SKU 数 **0 の行を展開すると「SKUが登録されていません」だけが大きく出て、追加フォームへの導線が空白を挟んだ下にある** | SKU 0 件のときに最初にやるべき操作（追加）が画面下部に埋もれる |
| K | SKU詳細モーダルが `width="780px"` 固定 | 1024px クラスのウィンドウ幅で右端が窮屈／タブ内のテーブル + フォームが横スクロールする |
| L | モーダル「在庫管理」タブの `<a-alert message="入荷登録は「入荷管理」画面から行ってください。">` が **テキスト案内のみで遷移ボタンがない** | 運用者が一旦モーダルを閉じてサイドバーから「入荷管理」に飛ぶ必要があり動線が長い |
| M | 親テーブルにページネーション・件数表示・ページサイズ切替がない（既定 10 件ページング） | 全 N 件中いま何件目を見ているか不明（Step 7 課題 I と同根） |

### 8-2. スコープ

| 観点 | 方針 |
|------|------|
| 対象画面 | `features/skus/pages/SkuList.vue` 単体 |
| 対象外 | SKU詳細モーダル内の機能仕様（価格履歴の複数行化など）／在庫管理タブのバックエンド拡張／画像アップロードのサーバ側仕様 |
| DB 変更 | **なし** |
| API 変更 | **なし** |
| バックエンド変更 | **なし** |

### 8-3. 改善方針

#### 8-3-1. ヘッダー整理（課題 A）

- `<a-page-header>` の `sub-title` を削除
- `<a-page-header>` 配下の `description` slot に件数表示（`全 N 件中 M 件を表示`）を出す
- ヘッダー右側に **「全展開／全折りたたみ」ボタン** を追加（既存 SKU を一括確認したいときの操作性向上）

#### 8-3-2. 検索カードの新設（課題 B）

商品マスタ画面（Step 6-1）と同じ判断軸で、`SkuList.vue` にもクライアント側 `computed` ベースの検索カードを新設する。

- `<a-card>` 内 `<a-form layout="inline">` で以下 3 条件 + クリアボタン：
  - **商品名**（部分一致）
  - **発売状態**（すべて／発売中のみ／発売前のみ）：親商品の `releaseDate` 基準（`isReleased()` 流用）
  - **有効/無効**（すべて／有効のみ／無効のみ）
- API / DB 変更なし。`getAdminProducts()` の戻り値を `filteredProducts` computed で絞り込む

商品マスタ画面と同じ並び順・同じ UI コンポーネントで揃え、運用者が画面間を移動しても操作感が変わらないようにする。

#### 8-3-3. 親テーブルの列見直し（課題 C・D・E）

| 列 | 変更点 |
|----|-------|
| ID | 変更なし |
| 商品名 | 変更なし |
| SKU数 | 変更なし（バッジ表示はそのまま） |
| 発売 | 変更なし（発売中／発売前タグ） |
| 状態 | **「公開状態」「有効/無効」を 1 列に統合**（Step 7-3-3 と同方針）。バッジ＋タグの縦 2 行表示。列幅 110px |

「公開状態」「有効/無効」の統合により列数が減り、ヘッダー折り返し（課題 E）が解消する。スクリーンショットで観察された「ヘッダーと最初の行の余白の大きさ」（課題 C）は、`<a-table>` の `:body-style="{ padding: '0' }"` ではなく親 `<div>` の余白が原因の可能性が高いため、`a-page-header` 直下のラッパー余白を `margin-top: 16px` 程度に整える。

#### 8-3-4. 行クリック展開の廃止（課題 F）

- `expand-row-by-click` を **削除**
- `expandIcon` カスタムレンダリングで `+` / `−` を `<a-button type="text" size="small">` に統一（Step 7-3-4 と同方針）

これにより「行内テキストを選択しようとしただけで展開」の事故が無くなる。

#### 8-3-5. 展開行内レイアウトの整理（課題 G・J）

展開行内を以下の 2 ブロックに **明示的に分割**：

```
┌─────────────────────────────────────────────┐
│ 既存 SKU 一覧（a-table size=small）         │
│ - 0 件のときは <a-empty> をコンパクト表示   │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│ ＋ SKU を追加                                │
│ 色 [____] サイズ [____] [SKUを追加]         │
└─────────────────────────────────────────────┘
```

- 2 ブロック目は `<a-card>` `size="small"` で囲み、タイトルに「＋ SKU を追加」を入れる
- SKU 0 件の場合は `<a-empty>` の高さを抑え（`:image-style="{ height: '32px' }"`）、追加カードの目立ち方を上げる
- ブロック間の縦余白を `margin: 12px 0` で統一

#### 8-3-6. SKU 行アクションのラベル変更（課題 H）

- 「選択」ボタンを **「詳細・編集」** に変更（中身：価格・在庫・画像の管理）
- アイコンに `<EditOutlined />` を併記して操作意味を補強
- ボタンの type は `default` に下げ（モーダルを開くだけの導線なので primary は過剰）、アクセントを `<a-typography-link>` 風に整える

#### 8-3-7. SKU 追加フォームのバリデーション緩和（課題 I）

- `handleSkuSubmit` の必須チェックを **「色 OR サイズの少なくとも 1 つ、または商品が "色サイズなし" 設定なら両方空でも可」** に緩和
- 簡易判定として：「色・サイズ両方空のときに `<a-popconfirm>` で『色・サイズなしの SKU を作成しますか？』と確認」を挟む
- 既存商品の運用が「色・サイズ必須」前提のため、空のまま登録できるようにするだけにし、エラー文言は警告 → 確認に格下げ

DB 側（`skus.color` / `skus.size`）は元々 NULL 可。Core API もバリデーションしていない（実装読解により確認）ため、UI のガードを緩めるだけで成立する。

#### 8-3-8. SKU詳細モーダルの幅調整（課題 K）

- `width="780px"` を `:width="modalWidth"` に変更し、`computed` で `Math.min(900, window.innerWidth - 80)` を返す
- またはより簡潔に `width="80%"` + `style="max-width: 900px"` でも可
- モーダル内のテーブルが横スクロールしないよう、価格管理タブのテーブルに `:scroll="{ x: 'max-content' }"` を付与

#### 8-3-9. 在庫管理タブから入荷管理画面へのジャンプ動線（課題 L）

`<a-alert>` を以下に拡張：

```vue
<a-alert
  message="入荷登録は「入荷管理」画面から行ってください。"
  type="info"
  show-icon
  style="margin-bottom: 12px"
>
  <template #action>
    <a-button size="small" @click="$router.push(`/inbound/create?skuId=${selectedSkuId}&productId=${selectedProductId}`)">
      入荷登録へ
    </a-button>
  </template>
</a-alert>
```

クエリパラメータ `skuId` で遷移先（`InboundCreate.vue`）の SKU を初期選択できるよう、Step 8 の合わせ技として `InboundCreate.vue` の `onMounted` で `route.query.skuId` を読む実装も同フェーズで行う（軽微な追加実装で、UI 改善の枠内）。

#### 8-3-10. 親テーブルのページネーション（課題 M）

`<a-table>` の `:pagination` を Step 7-3-5 と同じ仕様で明示：

```js
pagination: {
  pageSize: 20,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100'],
}
```

### 8-4. UI 変更まとめ（Console）

`features/skus/pages/SkuList.vue`：

- `<a-page-header>` の `sub-title` 削除、`description` slot に件数表示、右側に「全展開／全折りたたみ」ボタン
- 検索カード新設（商品名 / 発売状態 / 有効/無効 / クリア）
- `productColumns`：「公開状態」「有効/無効」を「状態」1 列に統合
- `expand-row-by-click` 削除、`expandIcon` カスタム
- 展開行内を「既存 SKU 一覧」「＋ SKU を追加」の 2 ブロックに分割
- SKU 行の「選択」を「詳細・編集」に改名、`<EditOutlined />` 付与
- SKU 追加の必須バリデーションを緩和（両方空は popconfirm 経由で許容）
- SKU詳細モーダルの幅をレスポンシブ化
- 在庫管理タブの `<a-alert>` に「入荷登録へ」遷移ボタンを追加
- 親テーブルに `showTotal` / `showSizeChanger` 付き pagination を追加

`features/inbound/pages/InboundCreate.vue`（合わせ技）：

- `onMounted` で `route.query.skuId` を読み、初期選択 SKU を設定（追加実装は数行で済む）

### 8-5. DB 変更
**なし**

### 8-6. API 変更
**なし**

### 8-7. TDD テストケース

表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- ヘッダーから "Amazia Console" のサブタイトルが消えている
- 「全展開」を押すと全商品行が同時に展開される／「全折りたたみ」で閉じる
- 検索カードの「商品名」部分一致で親一覧が絞られる
- 検索カードの「発売前のみ」で `releaseDate` 未来の親商品だけ残る
- 検索カードの「有効のみ」で `is_active = TRUE` の親商品だけ残る
- 「クリア」ボタンで検索 3 条件すべてが既定値に戻る
- 親テーブルの「状態」列に「●公開中／●非公開」と「有効／無効」が縦 2 行で表示される
- 行クリックでは展開せず、左端の `+` ボタンクリック時のみ展開する
- 展開行内に「既存 SKU 一覧」「＋ SKU を追加」の 2 ブロックが明示的なカード境界で表示される
- SKU 0 件のときも「＋ SKU を追加」カードがすぐ下に視認できる位置で出る
- SKU 行右端のボタンラベルが「詳細・編集」になっている
- SKU 追加フォームで色・サイズ両方空欄で「SKUを追加」を押すと、popconfirm が出て確認後に登録できる
- SKU詳細モーダルが幅 1280px のウィンドウで右端まで張り付かず適切な幅で開く
- モーダル「在庫管理」タブの「入荷登録へ」ボタンを押すと `/inbound/create?skuId=...&productId=...` に遷移し、`InboundCreate.vue` が当該商品 / SKU を初期選択する
- 親テーブルのページネーションに「N-M / 全 K 件」が表示され、ページサイズを 50 / 100 に切り替えできる

### 8-8. 申し送り

- 「親テーブルの状態列を 1 列に統合」「pagination の `showTotal` 設定」「`expand-row-by-click` 廃止」「`<a-page-header>` の sub-title 廃止」は Step 7 と完全に同方針。**実装時は両画面で利用する `<StatusCell>` `<TableExpandIcon>` 等の小さな共通コンポーネントに切り出す** ことで、横展開対象（`ProductMarketList.vue` / `InboundList.vue` / `DeliveryList.vue` 等）への展開コストを下げる
- SKU 詳細モーダルの「価格管理」タブは現状 `getSkuPrices()` が単一価格しか扱えない構造（`prices.value = data ? [data] : []`）。価格履歴の複数行化はバックエンド改修を伴うため、本ステップのスコープ外（次フェーズ以降で要検討）
- `InboundCreate.vue` の `route.query.skuId` 受け取り実装は Step 8 の動線改善に必要なため同フェーズで実施するが、軽微（数行追加）のため別ステップ化はしない

---

## Step 9：商品一覧（SKU集約版）画面の UI 視認性・操作性改善 ✅ 実装完了（2026-05-07）

### 9-1. 背景・目的

商品一覧（SKU集約版）画面（`/products/market-view`、`ProductMarketList.vue`）は Step 6-2 で 5 軸（実体 7 入力）のクライアント側検索条件を導入したが、結果として **検索カードの密度が画面内で最も高い画面** になり、列レイアウトと検索 UI の両面で以下の課題が露呈した。Step 7 / 8 と同様、DB / API には手を入れず `ProductMarketList.vue` 単体に閉じる。

観察された主な課題：

| # | 課題 | 影響 |
|---|------|------|
| A | 検索カードに `<a-form-item>` が 6 件横並び（商品名 / 価格帯 / 発売日帯 / 予約開始日帯 / 在庫 / クリア）で、`inline` レイアウトのため **2 段折り返し**になっている | 1 軸ずつの位置がウィンドウ幅で動的に変わり、運用者が探しづらい |
| B | 価格帯 `〜 円` / 発売日帯 `〜` / 予約開始日帯 `〜` のセパレータ + 単位 が 3 箇所にあり、**折り返し時に左右が分断** されることがある（Step 7 課題 J と同根） | レンジ条件の意味が読み取りづらい |
| C | テーブル「メイン画像」列がヘッダー幅 100px に対し見出し文字数が 5 文字あり、**「メイン画」「像」で 2 行に折り返し**ている | 見出しの体裁が崩れる |
| D | テーブル「最低価格」「合計在庫」が左寄せ（Step 7 課題 F と同根） | 数値の桁比較ができない |
| E | 検索適用中でもテーブル直前・直後に **件数表示やフィルタ適用バッジがない** | 「全 N 件中 M 件にフィルタ済」が画面から読み取れない |
| F | `expand-row-by-click="true"` で行クリックにより誤展開（Step 7 課題 G / Step 8 課題 F と同根） | 行内テキスト選択ができない |
| G | テーブル行高がメイン画像 64px + 余白で大きく、**1 画面に 3〜4 行しか入らない** | 一覧性が下がり、スクロール量が増える |
| H | ページネーションに件数表示・サイズ切替がない（Step 7 課題 I / Step 8 課題 M と同根） | 全 N 件中の現在地が不明 |
| I | `<a-page-header>` の `sub-title="Marketに公開されているSKU集約データの確認用"` は **本画面では削除しない**（Step 7・8 と異なり意味のある説明文） | サブタイトルの取り扱い方針を Step ごとに明確化する必要がある |
| J | テーブル「ステータス」「予約開始日」「発売日」が画面右側にまとまっているが、**メイン画像と並ぶ「最低価格」「合計在庫」は数値で、その右に並ぶ「ステータス／予約開始日／発売日」は商品状態軸** で、列のグループ意味が混在 | グルーピングのヒント（罫線・背景色など）がなく、列の役割が読み解きづらい |
| K | 「メイン画像」列で `mainImage` が null のとき「画像なし」とグレー文字を出すだけ | プレースホルダー画像にすれば行高が揃い、視覚ノイズが減る |

### 9-2. スコープ

| 観点 | 方針 |
|------|------|
| 対象画面 | `features/products/pages/ProductMarketList.vue` 単体 |
| 対象外 | Market 側公開エンドポイント（`GET /api/products/market`）の DTO 変更／画像ホスティング仕様 |
| DB 変更 | **なし** |
| API 変更 | **なし** |
| バックエンド変更 | **なし** |

### 9-3. 改善方針

#### 9-3-1. ヘッダー整理（課題 E・I）

- `<a-page-header>` の `sub-title` は **維持**（本画面は確認用の補助説明として意味がある）
- `<a-page-header>` 配下の `description` slot に件数表示を追加：
  - 例：`全 3 件中 3 件を表示` / フィルタ適用時は `全 3 件中 1 件を表示（フィルタ適用中）`
- ヘッダー右側に **「全展開／全折りたたみ」ボタン** を追加（Step 8-3-1 と同方針。SKU 確認用画面なので展開操作の頻度が高い）

#### 9-3-2. 検索カードのレイアウト見直し（課題 A・B）

`<a-form layout="inline">` から **`<a-form layout="vertical">` + CSS Grid** に変更（Step 7-3-6 と同方針）：

```vue
<a-form layout="vertical" class="search-form-grid" :model="searchForm">
  <a-form-item label="商品名">...</a-form-item>
  <a-form-item label="価格">
    <a-input-group compact>
      <a-input-number ... /><span>〜</span><a-input-number ... /><span>円</span>
    </a-input-group>
  </a-form-item>
  <a-form-item label="発売日">
    <a-input-group compact>
      <a-date-picker ... /><span>〜</span><a-date-picker ... />
    </a-input-group>
  </a-form-item>
  <a-form-item label="予約開始日">...</a-form-item>
  <a-form-item label="在庫">...</a-form-item>
  <a-form-item>
    <a-button @click="resetSearch">クリア</a-button>
  </a-form-item>
</a-form>

<style>
.search-form-grid :deep(.ant-form) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: 16px;
  row-gap: 8px;
}
</style>
```

- グリッドにより **ウィンドウ幅に応じて自動的に列数が変わる**（4 列 / 3 列 / 2 列 / 1 列）
- `<a-input-group compact>` で帯域条件のセパレータと単位を 1 セットでまとめ、折り返し分断を防ぐ（課題 B）

#### 9-3-3. テーブル列の見直し（課題 C・D・G・J・K）

| 列 | 変更点 |
|----|-------|
| ID | `width: 80` → `width: 70`（商品マスタと揃える） |
| 商品名 | 変更なし |
| メイン画像 | **列見出しを「画像」に短縮**（課題 C 解消）。`width: 80` に縮小し、画像サイズも 64px → 48px に縮小（課題 G 解消）。`mainImage` が null のときはグレー枠の `<a-skeleton-image>` 風プレースホルダーで行高を揃える（課題 K） |
| 最低価格 | `align: 'right'` 付与（課題 D） |
| 合計在庫 | `align: 'right'` 付与。値 0 のときは `<a-tag color="red">在庫なし</a-tag>` を併記（課題 D） |
| ステータス | 変更なし（発売中／発売前タグ） |
| 予約開始日 | 変更なし |
| 発売日 | 変更なし |

行高は画像縮小により実質的に縮まり、1 画面の行数が 3〜4 行 → 6〜7 行になる（課題 G）。

「ステータス／予約開始日／発売日」の **3 列を視覚的にひとまとまり** として認識できるよう、テーブルの `customRow` で右側 3 列の背景に薄い灰色（`#fafafa`）を付ける案もあるが、Ant Design Vue 標準では列単位の背景色設定が複雑になるため、本ステップでは **見出し行の境界線で区切る** 程度に留め、必要なら次フェーズで対応（課題 J）。

#### 9-3-4. 行クリック展開の廃止（課題 F）

- `expand-row-by-click` を **削除**
- `expandIcon` カスタムレンダリングで `+` / `−` を `<a-button type="text" size="small">` に統一（Step 7-3-4 / Step 8-3-4 と同方針）

#### 9-3-5. ページネーション（課題 H）

`<a-table>` の `:pagination` を Step 7-3-5 / Step 8-3-10 と同じ仕様で明示：

```js
pagination: {
  pageSize: 20,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100'],
}
```

### 9-4. UI 変更まとめ（Console）

`features/products/pages/ProductMarketList.vue`：

- `<a-page-header>` の `description` slot に件数表示、右側に「全展開／全折りたたみ」ボタン
- 検索カードを `<a-form layout="vertical">` + CSS Grid に変更
- 帯域条件（価格 / 発売日 / 予約開始日）を `<a-input-group compact>` でラップ
- `columns`：
  - 「メイン画像」を「画像」に改名、列幅 80px、画像 48px、null プレースホルダー対応
  - 「最低価格」「合計在庫」に `align: 'right'`
  - 「合計在庫」0 件は「在庫なし」タグ併記
- `expand-row-by-click` 削除、`expandIcon` カスタム
- `pagination` に `showTotal` / `showSizeChanger` 追加

### 9-5. DB 変更
**なし**

### 9-6. API 変更
**なし**

### 9-7. TDD テストケース

表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- `<a-page-header>` のサブタイトル「Marketに公開されているSKU集約データの確認用」は維持されている
- `<a-page-header>` の説明欄に「全 N 件中 M 件を表示」が出る／フィルタ適用中は「（フィルタ適用中）」が併記される
- 「全展開」「全折りたたみ」ボタンが機能する
- 検索カードがウィンドウ幅 1920px → 4 列、1280px → 3 列、768px → 2 列で自動的に折り返す
- 価格帯入力で「最低 〜 最高 円」が同一グループ内に保持され、折り返し時も分断されない
- テーブル「画像」列の見出しが 1 行で表示される
- テーブル「最低価格」「合計在庫」の数値が右寄せで表示される
- 「合計在庫」が 0 の行に「在庫なし」赤タグが併記される
- メイン画像が null の行でも行高が他行と揃う
- 行クリックでは展開せず、左端の `+` ボタンクリック時のみ展開する
- ページネーションに「N-M / 全 K 件」が表示され、ページサイズを 50 / 100 に切り替えできる

### 9-8. 申し送り

- Step 7・8 で見送った「`<StatusCell>` `<TableExpandIcon>` 等の小さな共通コンポーネント切り出し」を Step 9 でも見送るが、本ステップを実装する時点で **3 画面分の同パターンが揃う** ため、Step 10 着手前に共通コンポーネント化の要否を再判断する
- 「ステータス／予約開始日／発売日」の列グルーピングの視覚化は、Ant Design Vue の `columns` の `colSpan` トリックや `customRow` で対応可能だが複雑になるため次フェーズ送り

### 9-9. レイアウト追加調整（2026-05-07）

#### 9-9-1. 背景・目的

Step 9 実装完了後、商品マスタ画面と並べて見ると、商品一覧（SKU集約版）画面の検索カードだけが **軸の並びがウィンドウ幅次第で揺れて見える** ことが確認された。

原因は実装方針の差にある：

| 観点 | 商品マスタ（`ProductList.vue`） | SKU集約版（`ProductMarketList.vue`、Step 9 実装版） |
|------|------|------|
| 構造 | **2段の `<div>` に分割**（`search-row--basic` / `search-row--status`） | **1段の `<div class="search-grid">`** に全 6 軸を放り込み |
| 上段Grid | `2fr 1fr`（商品名広め＋価格コンパクト） | — |
| 下段Grid | `auto auto auto 1fr`（軸幅は中身ぴったり、末尾余白でクリアを右寄せ） | — |
| 単段Grid | — | `repeat(auto-fit, minmax(220px, 1fr))` の自動配置 |

商品一覧（SKU集約版）は軸が 6 つ（商品名 / 価格帯 / 発売日帯 / 予約開始日帯 / 在庫 / クリア）と多いため、`auto-fit` の自動配置だとウィンドウ幅次第で 3 列／4 列／5 列に揺れ、帯域条件（価格・発売日・予約開始日）と単軸条件（在庫）が混在して整列が破綻する。

ユーザー要望（2026-05-07）：「商品マスタみたいに綺麗に並べたい」。本節では `<SearchCard>` のような共通コンポーネント化は行わず（Step 11-9 の方針を継承し、画面ごとに軸の並びは独立で持つ）、**SKU集約版に対して 3 段構造の Grid を当てる** ことで視覚的な整列を取り戻す。

#### 9-9-2. スコープ

| 観点 | 方針 |
|------|------|
| 対象 | `amazia-console/resources/vue/src/features/products/pages/ProductMarketList.vue` の検索カード `<style>` 部 + テンプレートの `<div>` 構造 |
| 共通コンポーネント化 | **行わない**（軸が今後の改修で増減しやすい想定のため） |
| 商品マスタ画面 | **手を入れない**（既に 2 段構造で整列しており現状維持） |
| DB 変更 | **なし** |
| API 変更 | **なし** |

#### 9-9-3. 3 段構造の設計

軸の性質で 3 段に分ける：

| 段 | 含む軸 | Grid 仕様 |
|------|------|------|
| 上段 | 商品名（テキスト幅広）／価格（数値帯域） | `grid-template-columns: 2fr 1fr`（商品マスタ上段と完全同形） |
| 中段 | 発売日（日付帯域）／予約開始日（日付帯域） | `grid-template-columns: 1fr 1fr`（帯域 2 つを等幅で並べる） |
| 下段 | 在庫（ラジオ）／クリアボタン | `grid-template-columns: auto 1fr`（在庫は中身ぴったり、末尾 `1fr` の余白でクリアを右寄せ） |

レイアウトイメージ：

```
┌─ 検索カード ──────────────────────────────────────────────┐
│  商品名                            価格                       │
│  [部分一致で検索________________]  [最低]〜[最高]円         │
│                                                              │
│  発売日                       予約開始日                     │
│  [____]〜[____]              [____]〜[____]                  │
│                                                              │
│  在庫                                                  [クリア]│
│  [すべて][在庫あり][在庫なし]                                │
└──────────────────────────────────────────────────────────────┘
```

#### 9-9-4. 実装方針

- テンプレート：単段の `<div class="search-grid">` を **3 段の `<div>` に分割**（上段／中段／下段）。`<a-form layout="vertical">` と `<a-form-item>` のラベル位置はそのまま維持
- CSS：`search-grid` クラスを撤去し、`search-row` ベースクラス + `search-row--basic` / `search-row--dates` / `search-row--status` の 3 行修飾子に置き換える
- 768px 以下の狭い画面では 3 行とも 1 列に潰す（`grid-template-columns: 1fr`）
- 帯域条件の `<a-input-group compact>` ラップ・`range-sep` / `range-unit` の小スタイルは現状維持
- クリアボタンの右寄せ（`justify-self: end`）は下段にだけ適用

#### 9-9-5. UI 変更まとめ（Console）

`features/products/pages/ProductMarketList.vue`：
- `<div class="search-grid">` を `<div class="search-row search-row--basic">` / `<div class="search-row search-row--dates">` / `<div class="search-row search-row--status">` の 3 段に分割
- `<a-form-item label="商品名">` と `label="価格"` を上段に配置
- `<a-form-item label="発売日">` と `label="予約開始日"` を中段に配置
- `<a-form-item label="在庫">` と `label=" "（クリアボタン）` を下段に配置
- `<style>` の `.search-grid` 規則を削除し、`.search-row` / `.search-row--basic` / `.search-row--dates` / `.search-row--status` を追加

#### 9-9-6. DB 変更
**なし**

#### 9-9-7. API 変更
**なし**

#### 9-9-8. TDD テストケース

レイアウト調整のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- ウィンドウ幅 1920px / 1440px / 1200px のいずれでも、検索カード上段に「商品名」「価格」、中段に「発売日」「予約開始日」、下段に「在庫」「クリア」が表示される
- 上段「商品名」入力欄が「価格」入力欄より約 2 倍の幅で表示される
- 中段の「発売日」「予約開始日」が同じ幅で左右に並ぶ
- 下段「クリア」ボタンが右端に表示される
- ウィンドウ幅 768px 以下で 3 段とも各軸が縦 1 列に並ぶ
- 帯域条件（価格・発売日・予約開始日）が `<a-input-group compact>` 内で `〜` セパレータと単位が分断されない

---

## Step 10：売上管理画面の UI 視認性・操作性改善 🟡 設計策定（2026-05-07）

### 10-1. 背景・目的

売上管理画面（`/sales`、`SalesList.vue`）は「一覧」「集計」の 2 タブ構成で Step 2 / Step 6-3 を経て検索条件と集計粒度切替が入ったが、商品マスタ・SKU管理・商品一覧（SKU集約版）画面と並べて見たとき、以下の **視認性・操作性のひずみ** がスクリーンショット観察と実装読解で確認できた。本ステップでは Step 7〜9 と同様、DB / API には手を入れず `SalesList.vue` 単体に閉じる。

観察された主な課題：

| # | 課題 | 影響 |
|---|------|------|
| A | `<a-page-header>` の `sub-title="Amazia Console"` がサイドバーのアプリ名と重複（Step 7・8 課題 A と同根） | タイトル横にノイズが出る |
| B | 一覧タブの検索条件が **「予約を除外」チェック + 売上日帯 + クリア** だけで、商品名 / ユーザ名 / 配送ステータス / 決済方法 / 区分 などの絞り込みがない | 売上が積み上がると目的の行を探しづらい |
| C | 検索行が `<a-card>` でラップされず、`<div>` に直書きで `flex` 配置されている | Step 6-1 以降の他画面（`<a-card>` ラップ）と表現が不統一で、検索エリアの視覚的境界が弱い |
| D | テーブル数値列「数量」「金額（円）」が **左寄せ**（Step 7 課題 F / Step 9 課題 D と同根） | 桁比較しづらい |
| E | ユーザ名列が「W A D A T E T S U Y A」のように **半角空白で分割表示** になっている | データ起因か CSS 起因か実装読解では切り分けできないが、視認性が悪い（見た目では空白挿入されているが `customerName` の元値次第） |
| F | 「配送日」列が全行「—」（NULL）でも常に列幅を取り続ける | NULL 率の高い列が画面横幅を消費する |
| G | 「決済方法」が `credit_card` の **生コード表示**（`SHIPPING_METHOD_LABELS` / `SHIPPING_STATUS_LABELS` のような日本語マップが決済方法には用意されていない） | 運用者向け画面なのに英語識別子のまま |
| H | ページネーションが `pageSize: 50` 固定で、件数表示・サイズ切替なし（Step 7・8・9 課題と同根） | 全 N 件中の現在地が不明 |
| I | 「区分」列が「通常／予約」のプレーンテキスト | `<a-tag>` 化されていないため視覚的識別が弱い |
| J | ヘッダーから件数が分からない（フィルタ適用中の影響範囲が不明） | Step 7・8・9 と同様、件数表示が必要 |
| K | 一覧タブの検索フォーム内「クリア」ボタンが `size="small"` で右端配置 | 他画面（Step 6-1 以降）の通常サイズ「クリア」と統一感がない |
| L | 集計タブの「見込み表示中（予約含む）」トグルが **default ↔ primary でテキストも切り替わる** ボタン | 状態遷移が分かりにくく、初見で「いま含まれているのか除外されているのか」が読み取れない |
| M | 集計タブの 4 カード（粒度別 / SKU別 / 決済方法別 / 区分別）の **数値列がすべて左寄せ** | 集計画面なのに桁比較できない |
| N | テーブル全体が `max-width: 1200px` の親 `<div>` で縛られ、ウィンドウを広げても **横方向に余白が広がるだけで列幅は伸びない** | ワイドモニタで情報密度が下がる |

### 10-2. スコープ

| 観点 | 方針 |
|------|------|
| 対象画面 | `features/sales/pages/SalesList.vue` 単体 |
| 対象外 | `salesApi.js` のレスポンス DTO 変更／集計ロジックのバックエンド移管 |
| DB 変更 | **なし** |
| API 変更 | **なし** |
| バックエンド変更 | **なし**（決済方法ラベルマップは Console フロント内に持つ） |

### 10-3. 改善方針

#### 10-3-1. ヘッダー整理（課題 A・J・N）

- `<a-page-header>` の `sub-title` を削除（Step 7-3-1 / Step 8-3-1 と同方針）
- `<a-page-header>` 配下の `description` slot に件数表示（一覧タブ時のみ。集計タブ時は表示しない）
- 親 `<div>` の `max-width: 1200px` を **削除**（課題 N）。テーブル幅をウィンドウ幅に追従させる
  - 集計タブのカード 4 つは `<a-row :gutter="16">` の中で 2x2 配置されているので、ウィンドウが広がっても破綻しない

#### 10-3-2. 一覧タブの検索カード新設（課題 B・C・K）

`<div>` 直書きから **`<a-card>` ラップ** に変更し、検索条件を以下に拡張：

| 条件 | 入力 UI | 比較ロジック |
|------|--------|-------------|
| 予約を除外 | `<a-checkbox>`（既存） | `s.preorder` を除外 |
| 売上日（最早〜最遅） | `<a-date-picker>` × 2（既存） | 既存ロジック |
| 商品名 | `<a-input allow-clear>`（**新設**） | `s.productName` 部分一致 |
| ユーザ名 | `<a-input allow-clear>`（**新設**） | `s.customerName` 部分一致 |
| 配送ステータス | `<a-select>`（**新設**、`SHIPPING_STATUS_LABELS` から選択肢生成） | `s.shippingStatusCode` 完全一致 |
| 決済方法 | `<a-select>`（**新設**、`PAYMENT_METHOD_LABELS` から選択肢生成）※10-3-3 で定義 | `s.paymentMethodName` 完全一致 |
| 区分 | `<a-radio-group>`（**新設**：すべて／通常のみ／予約のみ） | `s.preorder` 真偽 |
| クリア | `<a-button>`（既存サイズ統一） | 全条件リセット |

レイアウトは `<a-form layout="vertical">` + CSS Grid の **3 段固定構成**（Step 9-9 で SKU 集約版に当てた方針と同形）。`auto-fit` の自動配置だと軸数が多いとウィンドウ幅次第で揺れるため、商品マスタ／SKU 集約版と同じ整列を取る：

| 段 | 含む軸 | Grid 仕様 |
|------|------|------|
| 上段 | 商品名／ユーザ名 | `grid-template-columns: 1fr 1fr` |
| 中段 | 配送ステータス／決済方法／区分 | `grid-template-columns: auto auto auto 1fr` |
| 下段 | 予約除外チェック／売上日帯／クリア | `grid-template-columns: auto auto 1fr`（末尾余白でクリアを右寄せ） |

768px 以下では 3 段とも 1 列に潰す。

`filteredSalesForList` computed に上記 5 条件の AND 結合を追加する。

#### 10-3-3. 決済方法の日本語マップ追加（課題 G）

実装ファイル冒頭に追加：

```js
const PAYMENT_METHOD_LABELS = {
  credit_card: 'クレジットカード',
  convenience: 'コンビニ決済',
  bank_transfer: '銀行振込',
  // 既知の決済方法コードを列挙。未知のコードは生値を出す（fallback）
};
```

`listColumns` の `paymentMethodName` の `customRender` で `PAYMENT_METHOD_LABELS[text] ?? text` で表示。

決済方法のコード体系がバックエンド側で確定していない場合、未マップ値はそのまま生表示にフォールバックする（運用上の既存コードを壊さない）。

#### 10-3-4. テーブル列の見直し（課題 D・F・I）

| 列 | 変更点 |
|----|-------|
| 売上日 | 変更なし |
| 配送日 | **NULL 率が高いため列幅 80px に縮小**（課題 F）。値ありのときのみ太字 |
| ユーザ名 | 表示時に `s.customerName?.replace(/\s+/g, '')` でホワイトスペースを除去（課題 E のデータ起因対策。ただし正当なミドルネーム空白がある場合に備えて、空白除去前後で長さが半分以下になる場合のみ除去する保守的な実装にする） |
| 商品名 | 変更なし |
| 色 | 変更なし |
| サイズ | 変更なし |
| 数量 | `align: 'right'` 付与（課題 D） |
| 金額（円） | `align: 'right'` 付与（課題 D） |
| 配送方法 | 変更なし（既存ラベルマップ） |
| 決済方法 | 日本語ラベル化（10-3-3） |
| 配送ステータス | 変更なし（既存ラベルマップ） |
| 区分 | **`<a-tag>` 化**：通常 → `<a-tag>通常</a-tag>`（無色）／予約 → `<a-tag color="orange">予約</a-tag>`（課題 I） |

#### 10-3-5. ページネーション（課題 H）

一覧タブのテーブル `:pagination` を Step 7-3-5 / Step 8-3-10 / Step 9-3-5 と同じ仕様に変更：

```js
pagination: {
  pageSize: 50,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['50', '100', '200'],
}
```

売上は件数が多い画面なので、既定 50 / 上限 200 にしておく（他画面より粒度を粗く）。

#### 10-3-6. 集計タブの「見込み表示」トグルの改善（課題 L）

`<a-button>` の状態切替から **`<a-switch>` + ラベル** に変更：

```vue
<a-space align="center">
  <a-switch v-model:checked="includePreorderInSummary" />
  <span>予約購入を含む見込み値で表示</span>
  <a-tooltip title="ON にすると予約購入分を集計に含めます。発売前商品の見込み売上を確認したいときに使用します。">
    <InfoCircleOutlined style="color: #999" />
  </a-tooltip>
</a-space>
```

スイッチの ON / OFF で意味が直感的に分かり、ツールチップで運用上の使い所を説明する。

#### 10-3-7. 集計タブの数値列右寄せ（課題 M）

`summaryGranularityColumns` / `summarySkuColumns` / `summaryPaymentColumns` / `summaryPreorderColumns` のすべての数値列（件数 / 数量 / 売上）に `align: 'right'` を付与する。

### 10-4. UI 変更まとめ（Console）

`features/sales/pages/SalesList.vue`：

- 親 `<div>` の `max-width: 1200px` を削除
- `<a-page-header>` の `sub-title` 削除、`description` slot に件数表示（一覧タブ時のみ）
- 一覧タブ：
  - 検索行を `<a-card>` でラップし、`<a-form layout="vertical">` + CSS Grid 化
  - 商品名 / ユーザ名 / 配送ステータス / 決済方法 / 区分 の 5 条件を新設
  - `filteredSalesForList` に 5 条件の AND を追加
  - 「クリア」ボタンサイズを通常に統一、全条件リセットに変更
  - `listColumns`：数量・金額に `align: 'right'`、決済方法に日本語ラベル、区分を `<a-tag>` 化、ユーザ名のホワイトスペース保守的除去、配送日の列幅縮小
  - `pagination` に `showTotal` / `showSizeChanger` 付与（既定 50、選択肢 50/100/200）
- 集計タブ：
  - 「見込み表示」トグルを `<a-switch>` + ラベル + ツールチップに変更
  - 4 カードのテーブルすべての数値列に `align: 'right'`
- スクリプト先頭に `PAYMENT_METHOD_LABELS` を追加

### 10-5. DB 変更
**なし**

### 10-6. API 変更
**なし**（決済方法ラベルは Console フロント内のマップで対応）

### 10-7. TDD テストケース

表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- ヘッダーから "Amazia Console" のサブタイトルが消えている
- 一覧タブで `<a-page-header>` の説明欄に「全 N 件中 M 件を表示」が出る
- 集計タブに切り替えると件数表示は出ない
- 一覧タブ：検索条件「商品名」「ユーザ名」「配送ステータス」「決済方法」「区分」「予約除外」「売上日帯」のすべてが AND で機能する
- 一覧タブ：「クリア」ボタンで全条件が既定値にリセットされる
- 一覧タブ：数量・金額の数値が右寄せで表示される
- 一覧タブ：決済方法が `credit_card` ではなく「クレジットカード」と日本語表示される（未マップ値は生表示にフォールバック）
- 一覧タブ：区分が「予約」のとき橙色のタグで表示される
- 一覧タブ：ページネーションに「N-M / 全 K 件」が表示され、ページサイズ 50 / 100 / 200 を切り替えできる
- 集計タブ：「予約購入を含む見込み値で表示」スイッチが ON のときだけ予約データが集計に含まれる
- 集計タブ：4 カードすべての数値列が右寄せで表示される
- ウィンドウ幅 1920px でテーブルが 1200px に縛られず横いっぱいに広がる

### 10-8. 申し送り

- 売上管理画面の検索条件追加（商品名 / ユーザ名 / 配送ステータス / 決済方法 / 区分）は Step 6-3 で見送られた軸の補完にあたる。Step 6-3 の検索条件が「売上日帯」のみだった経緯（クライアント側 computed の負荷／既定 pageSize 50 でカバー可能）を踏まえ、本ステップではあくまで **クライアント側 computed の範囲** に閉じる
- `PAYMENT_METHOD_LABELS` のマップは Console フロント内の暫定対応。決済方法コード体系が Phase 17 以降で正式化される場合、Core API 側で日本語ラベルを返す方が望ましい（次フェーズ申し送り）
- ユーザ名のホワイトスペース除去（課題 E 対策）は **画面表示時のみの保守的処理** で、データ自体は変更しない。根本原因（Backend / DB のいずれで空白が混入したか）の調査は本ステップのスコープ外とし、別途トラブルドキュメント化するか判断する
- Step 7・8・9・10 で 4 画面分の同パターン（`<a-page-header>` description 件数 / `<a-form layout="vertical">` + Grid 検索 / `expand-row-by-click` 廃止 / `showTotal` ページネーション / 数値右寄せ）が揃ったため、**実装着手前に共通コンポーネント or composable 化の要否を最終判断する**（Step 8-8 / Step 9-8 の継続申し送り）
- 検索カードのレイアウトは Step 9-9 と同じ「3 段固定 Grid」方針を採った（10-3-2）。当初の `auto-fit` Grid 案は軸数が 7 つ（商品名／ユーザ名／配送ステータス／決済方法／区分／予約除外+売上日帯／クリア）に増えたため、SKU 集約版で観察したのと同じ揺れが起きると判断し、商品マスタ・SKU 集約版・売上管理の 3 画面で整列方針を統一した

---

## Step 11：予約管理 / 配送管理 / 入荷管理画面の検索カード共通化と UI 改善 ✅ 実装完了（2026-05-07）

### 11-1. 背景・目的

予約管理（`/preorders`、`PreorderList.vue`）／配送管理（`/delivery`、`DeliveryList.vue`）／入荷管理（`/inbound`、`InboundList.vue`）の 3 画面は、Step 6-4 / 6-5 / 6-6 で検索条件を導入した結果、**いずれも `<a-card>` + `<a-form layout="inline">` で多軸の `<a-form-item>` を横並びにする同型レイアウト** になった。3 画面を並べて見ると以下が明らかになった：

1. **構造が完全に同型**：「カード枠／上段に幅広 1 軸（追跡番号 or 商品名）／下段に多軸 inline／末尾にクリアボタン」というレイアウトが 3 画面で繰り返されている（Preorder は上段なし、Delivery / Inbound は上段に追跡番号）
2. **横並び `inline` の折り返しがウィンドウ幅で不安定**：軸数が多い（Preorder 4 軸 / Delivery 6 軸 / Inbound 5 軸）ため、1280px 程度のウィンドウで `<a-form-item>` の途中で折り返しが起き、ラベルとフィールドの対応が読み取りづらい
3. **帯域条件（`〜` セパレータ）の分断**：Step 7 課題 J / Step 9 課題 B と同じ問題が 3 画面とも発生
4. **同じ「クリア」「リセット」「`searchForm` ref」「`computed filtered*`」のボイラープレートが 3 画面に重複**

ユーザー要望：「検索条件のレイアウトが特に直したい。3 画面で似ているので共通化できないか」を踏まえ、本ステップでは **検索カードの "枠" を共通化する `<SearchCard>` コンポーネントを新設** し、3 画面に同時適用する。中身（`<a-form-item>` の並び）は各画面が slot で書く方針（フィールド定義配列で完全自動化はしない）。

加えて、Step 7・8・9・10 で蓄積した個別画面の改善観点（sub-title 削除、件数表示、数値右寄せ、`showTotal` ページネーション、`max-width` 撤去）を 3 画面にも横展開する。

### 11-2. スコープ

| 観点 | 方針 |
|------|------|
| 対象画面 | `PreorderList.vue` / `DeliveryList.vue` / `InboundList.vue` の 3 画面 |
| 新設コンポーネント | `<SearchCard>`（カード枠 + Grid + クリアボタンのスロットコンテナ） |
| 共通化の深さ | **「枠」だけ共通化**。中身の `<a-form-item>` は各画面が slot で書く（select / radio / 帯域などの自由度を保つため） |
| `searchForm` / `filtered*` / `resetSearch` | **共通化しない**（軸の組み合わせとフィルタ判定が画面ごとに異なるため。`useSearchForm` composable 化も Step 11 では見送り） |
| DB 変更 | **なし** |
| API 変更 | **なし** |
| バックエンド変更 | **なし** |

### 11-3. `<SearchCard>` コンポーネント設計

#### 11-3-1. 配置

`amazia-console/resources/vue/src/components/SearchCard.vue` に新設（`features/` 配下のいずれか 1 つに偏らせない）。

#### 11-3-2. インターフェース

```vue
<!-- props -->
- :wide-field-label?: string   // 幅広 1 軸の見出し（例: "追跡番号"）
- :wide-field-placeholder?: string

<!-- emits -->
- @clear         // クリアボタン押下
- @update:wide-field-value  // 幅広 1 軸が指定されているときの v-model

<!-- slots -->
- default        // メインの <a-form-item> 群（Grid で並ぶ）
- wide-field     // 幅広 1 軸を <a-input> 以外で表現したいとき用（任意）
- extra          // クリアボタン以外の追加ボタン（任意）
```

#### 11-3-3. テンプレート骨子

```vue
<template>
  <a-card size="small" class="search-card" :body-style="{ padding: '12px 16px' }">
    <!-- 上段：幅広 1 軸（任意） -->
    <div v-if="wideFieldLabel || $slots['wide-field']" class="search-card__wide-row">
      <span class="search-card__wide-label">{{ wideFieldLabel }}</span>
      <slot name="wide-field">
        <a-input
          :value="wideFieldValue"
          :placeholder="wideFieldPlaceholder ?? '部分一致で検索'"
          allow-clear
          @update:value="$emit('update:wideFieldValue', $event)"
        />
      </slot>
    </div>

    <!-- 下段：メイン Grid -->
    <div class="search-card__grid">
      <slot />
      <div class="search-card__actions">
        <slot name="extra" />
        <a-button @click="$emit('clear')">クリア</a-button>
      </div>
    </div>
  </a-card>
</template>

<style scoped>
.search-card { margin-bottom: 16px; }
.search-card__wide-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.search-card__wide-label {
  font-weight: 500;
  min-width: 80px;
  flex-shrink: 0;
}
.search-card__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: 16px;
  row-gap: 8px;
  align-items: end;
}
.search-card__actions {
  display: flex;
  gap: 8px;
  align-items: end;
  justify-self: end;
}
:deep(.ant-form-item) {
  margin-bottom: 0;
}
</style>
```

#### 11-3-4. Grid の自動列数

| ウィンドウ幅 | 列数（目安） |
|------|------|
| ~480px | 1 列 |
| ~720px | 2 列 |
| ~960px | 3 列 |
| ~1200px | 4 列 |
| ~1440px | 5 列 |
| 1920px+ | 6 列〜 |

`minmax(220px, 1fr)` により列数は CSS Grid 側が自動計算。各画面は軸数に関わらずレイアウトが破綻しない。

#### 11-3-5. 帯域条件の書き方ガイド

各画面の `<a-form-item>` 内で帯域条件を表現するときは、**`<a-input-group compact>` でラップして `〜` セパレータと単位を 1 セットに保つ**（Step 7-3-6 / Step 9-3-2 と同方針）：

```vue
<a-form-item label="価格">
  <a-input-group compact>
    <a-input-number ... placeholder="最低" />
    <span class="range-sep">〜</span>
    <a-input-number ... placeholder="最高" />
    <span class="range-unit">円</span>
  </a-input-group>
</a-form-item>
```

`.range-sep` / `.range-unit` の小スタイルは `<SearchCard>` コンポーネントで `:deep()` で当てて統一する。

### 11-4. 各画面の改善方針

#### 11-4-1. 予約管理画面（`PreorderList.vue`）

観察された主な課題：

| # | 課題 | 影響 |
|---|------|------|
| A | `sub-title="Amazia Console"` 重複（Step 7・8・10 課題 A と同根） | タイトル横にノイズ |
| B | `<a-form layout="inline">` で 4 軸が密集、特に帯域条件 3 つ（価格 / 発売日 / 予約開始日）の `〜` 分断（Step 9 課題 B と同根） | 折り返し時にレンジが読み取りづらい |
| C | 親 `<div>` の `max-width: 1200px` でワイドモニタで余白が広がるだけ（Step 10 課題 N と同根） | 情報密度が下がる |
| D | テーブル「予約数」「予約金額（円）」が左寄せ（Step 7 課題 F / Step 10 課題 D と同根） | 桁比較しづらい |
| E | ページネーション `pageSize: 50` 固定で件数表示・サイズ切替なし | 件数規模の把握ができない |
| F | 件数表示なし（フィルタ適用中の影響範囲が不明） | 他画面と同根 |
| G | 「予約受付」「Market 公開」が `<a-tag>` で表現されているが、意味が直交（受付状態 / 公開状態）するのに視覚的グルーピングがない | 列の役割が読み解きづらい（Step 9 課題 J と同根） |

改善内容：

- `<a-page-header>` の `sub-title` 削除、`description` slot に件数表示
- 親 `<div>` の `max-width: 1200px` 削除
- 検索カードを **`<SearchCard>` に置換**（幅広 1 軸は使わず、4 軸すべてを default slot 内に並べる）
- 価格 / 発売日 / 予約開始日 の帯域条件を `<a-input-group compact>` でラップ
- `columns`：「予約数」「予約金額（円）」に `align: 'right'`
- `pagination` を `showTotal` / `showSizeChanger` 付きに変更（既定 50、選択肢 50/100/200）

#### 11-4-2. 配送管理画面（`DeliveryList.vue`）

観察された主な課題：

| # | 課題 | 影響 |
|---|------|------|
| A | `sub-title="Amazia Console"` 重複 | タイトル横にノイズ |
| B | 検索カードの上段「追跡番号」幅広 1 軸 + 下段 inline 6 軸の構造が ad-hoc（手書き flex + 手書き inline） | レイアウトが手で作り込まれていて統一感がない |
| C | 帯域条件 3 つ（配送予定日 / 発送日 / 配達完了日）の `〜` 分断 | 折り返し時にレンジが読み取りづらい |
| D | 親 `<div>` の `max-width: 1400px` でワイドモニタで余白が広がるだけ | 情報密度が下がる |
| E | データ 0 件のとき `No data` が英語表示（`<a-table>` の `:locale`未設定） | 日本語 UI なのに空状態だけ英語 |
| F | テーブル「ID」「売上ID」が左寄せ数値 | 桁比較しづらい |
| G | ページネーション固定（既定 50、サイズ切替なし） | 件数規模の把握ができない |
| H | 件数表示なし | 他画面と同根 |

改善内容：

- `<a-page-header>` の `sub-title` 削除、`description` slot に件数表示
- 親 `<div>` の `max-width: 1400px` 削除
- 検索カードを **`<SearchCard wide-field-label="追跡番号" v-model:wide-field-value="searchForm.trackingCode">` に置換**。下段の 6 軸は default slot 内に並べる
- 配送予定日 / 発送日 / 配達完了日 の帯域条件を `<a-input-group compact>` でラップ
- `<a-table>` に `:locale="{ emptyText: '該当データがありません' }"` を付与
- `columns`：「ID」「売上ID」に `align: 'right'`
- `pagination` を `showTotal` / `showSizeChanger` 付きに変更

#### 11-4-3. 入荷管理画面（`InboundList.vue`）

観察された主な課題：

| # | 課題 | 影響 |
|---|------|------|
| A | `sub-title="Amazia Console"` 重複 | タイトル横にノイズ |
| B | 検索カードの上段「追跡番号」幅広 1 軸 + 下段 inline 5 軸の構造が ad-hoc（Delivery と同じ手書き構造） | レイアウトが手で作り込まれていて統一感がない |
| C | 帯域条件 2 つ（入荷数量 / 入荷日）の `〜` 分断 | 折り返し時にレンジが読み取りづらい |
| D | 親 `<div>` の `max-width: 1300px` でワイドモニタで余白が広がるだけ | 情報密度が下がる |
| E | データ 0 件のとき `No data` 英語表示 | 日本語 UI なのに空状態だけ英語 |
| F | テーブル「ID」「商品ID」「倉庫ID」「入荷数量」が左寄せ | 桁比較しづらい |
| G | ページネーション固定 | 件数規模の把握ができない |
| H | 件数表示なし | 他画面と同根 |
| I | ヘッダー右の「Excel一括入荷」「入荷登録」ボタンは現状維持で良い（主要 CTA として機能している） | — |

改善内容：

- `<a-page-header>` の `sub-title` 削除、`description` slot に件数表示（`#extra` のボタン群はそのまま維持）
- 親 `<div>` の `max-width: 1300px` 削除
- 検索カードを **`<SearchCard wide-field-label="追跡番号" v-model:wide-field-value="searchForm.trackingCode">` に置換**
- 入荷数量 / 入荷日 の帯域条件を `<a-input-group compact>` でラップ
- `<a-table>` に `:locale="{ emptyText: '該当データがありません' }"` を付与
- `columns`：「ID」「商品ID」「倉庫ID」「入荷数量」に `align: 'right'`
- `pagination` を `showTotal` / `showSizeChanger` 付きに変更

### 11-5. UI 変更まとめ（Console）

新規ファイル：

- `amazia-console/resources/vue/src/components/SearchCard.vue` を新設

`features/preorder/pages/PreorderList.vue`：
- `<a-page-header>` の `sub-title` 削除、`description` に件数
- 親 `<div>` の `max-width` 削除
- 検索カードを `<SearchCard>` に置換
- 帯域条件を `<a-input-group compact>` ラップ
- 「予約数」「予約金額（円）」を `align: 'right'`
- ページネーションに `showTotal` / `showSizeChanger`

`features/delivery/pages/DeliveryList.vue`：
- `<a-page-header>` の `sub-title` 削除、`description` に件数
- 親 `<div>` の `max-width` 削除
- 検索カードを `<SearchCard wide-field-label="追跡番号">` に置換
- 帯域条件 3 つを `<a-input-group compact>` ラップ
- `<a-table>` に `:locale` で空状態日本語化
- 数値列を `align: 'right'`
- ページネーションに `showTotal` / `showSizeChanger`

`features/inbound/pages/InboundList.vue`：
- `<a-page-header>` の `sub-title` 削除、`description` に件数（`#extra` ボタンは維持）
- 親 `<div>` の `max-width` 削除
- 検索カードを `<SearchCard wide-field-label="追跡番号">` に置換
- 帯域条件 2 つを `<a-input-group compact>` ラップ
- `<a-table>` に `:locale` で空状態日本語化
- 数値列を `align: 'right'`
- ページネーションに `showTotal` / `showSizeChanger`

### 11-6. DB 変更
**なし**

### 11-7. API 変更
**なし**

### 11-8. TDD テストケース

表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

#### 共通

- `<SearchCard>` のグリッドがウィンドウ幅 1920px → 6 列前後、1280px → 4〜5 列、768px → 2 列で自動的に折り返す
- 「クリア」ボタン押下で `@clear` が emit され、各画面の `resetSearch()` が走り全条件が既定値に戻る
- 帯域条件（価格 / 入荷数量 / 各種日付）が `<a-input-group compact>` 内に保たれ、折り返し時も `〜` と単位が分断されない

#### 予約管理（PreorderList）

- ヘッダーから "Amazia Console" のサブタイトルが消えている
- ヘッダーに「全 N 件中 M 件を表示」が出る／フィルタ適用中は「（フィルタ適用中）」が併記される
- 「予約数」「予約金額（円）」の数値が右寄せで表示される
- ページネーションに「N-M / 全 K 件」が表示され、ページサイズを 50 / 100 / 200 に切り替えできる
- ウィンドウ幅 1920px でテーブルが 1200px に縛られず横いっぱいに広がる

#### 配送管理（DeliveryList）

- ヘッダーから "Amazia Console" のサブタイトルが消えている
- ヘッダーに件数表示が出る
- 検索カード上段に「追跡番号」幅広入力が表示される
- データ 0 件のとき「該当データがありません」と日本語表示される
- 「ID」「売上ID」の数値が右寄せで表示される
- ページネーションに「N-M / 全 K 件」が表示され、ページサイズ切替できる
- ウィンドウ幅 1920px でテーブルが 1400px に縛られず横いっぱいに広がる

#### 入荷管理（InboundList）

- ヘッダーから "Amazia Console" のサブタイトルが消えている
- ヘッダー右側に「Excel一括入荷」「入荷登録」ボタンが維持されている
- ヘッダーに件数表示が出る
- 検索カード上段に「追跡番号」幅広入力が表示される
- データ 0 件のとき「該当データがありません」と日本語表示される
- 「ID」「商品ID」「倉庫ID」「入荷数量」の数値が右寄せで表示される
- ページネーションに「N-M / 全 K 件」が表示され、ページサイズ切替できる

### 11-9. 申し送り

- **共通化の深さは「枠だけ」に留めた**。`searchForm` ref / `computed filtered*` / `resetSearch()` のボイラープレートは画面ごとに残るが、軸の組み合わせとフィルタ判定が画面ごとに異なるため、フィールド定義配列で完全自動化する設計（`<SearchForm :fields="...">`）は本ステップでは採用しない
- `useSearchForm` composable（フィールド定義から `searchForm` ref / `resetSearch` / `filteredFn` を組み立てる）への発展余地はあるが、現時点で 3 画面すべてに型安全に当てはめるには軸タイプが多く（テキスト / 数値完全一致 / 数値帯域 / 日付帯域 / select / radio）、composable 化のメリットがコード削減量に見合わない
- 将来 4 画面目以降が同型レイアウトを必要としたタイミング（次フェーズ以降）で、composable 化を再判断する
- 商品マスタ画面（Step 7）／商品一覧 SKU 集約版画面（Step 9 + 9-9）／売上管理画面（Step 10）も検索カードを持つが、本ステップでは **既に Step 7 / 9 で `<a-form layout="vertical">` + 個別 Grid CSS の方針を採った** ため、`<SearchCard>` への置換は行わない（既存方針を上書きせず、3 画面の整合性を優先）。なお、SKU 集約版画面は Step 9-9 で 2 段構造（商品マスタと同形）から 3 段構造へ整列を取り直しているが、これも個別 Grid CSS の範囲内の調整であり、本ステップの方針と矛盾しない。次フェーズで全画面横展開のタイミングで一括置換する余地は残す
- `<a-table>` の `:locale="{ emptyText: '該当データがありません' }"` は Delivery / Inbound 以外の画面（一覧系全般）にも適用余地あり。本ステップでは 3 画面に閉じ、横展開は次フェーズ以降で判断する

---

## Step 12：返品管理画面の UI 視認性・操作性改善 ✅ 実装完了（2026-05-07）

### 12-1. 背景・目的

返品管理画面（`/sales-returns`、`SalesReturnList.vue`）は、Step 11 で導入する `<SearchCard>` を活用すべき検索カードを持ちながら、現状は `<a-form layout="inline">` 直書きで「状態」セレクトと「再読込」ボタンしかない最小構成。スクリーンショット観察で **列幅不足による文字折り返しが顕著**（申請日 2 行 / 顧客名 5 行）で、レイアウト面の課題が他画面より目立つ。本ステップでは Step 7〜11 で蓄積した改善観点を返品管理画面にも適用し、Step 11 の `<SearchCard>` を実装後の最初のクライアントとして利用する。

観察された主な課題：

| # | 課題 | 影響 |
|---|------|------|
| A | `<a-page-header>` の `sub-title="Amazia Console"` がサイドバーのアプリ名と重複（Step 7・8・10・11 課題 A と同根） | タイトル横にノイズ |
| B | 検索行が `<a-form layout="inline">` 直書きで `<a-card>` ラップなし | Step 11 で他 3 画面が `<SearchCard>` に揃うため、本画面だけ表現が浮く |
| C | 検索条件が「状態」セレクト 1 軸のみで、運用上必要な軸（顧客名 / 商品名 / 申請日帯 / 売上ID 完全一致 / 理由 部分一致）が無い | 返品件数が増えると目的の申請が探しにくい |
| D | 検索フォーム内に「再読込」ボタンが配置されている | 検索条件のクリアではなく API 再取得操作のため、配置場所として違和感（他画面はクリアボタンが定位置） |
| E | スクリーンショットで「申請日 2026-05-06 13:21」が **2 行に折り返し**、「顧客 WADA TETSUYA」が **5 行に縦折り** している | 列幅不足。顧客名は Step 10 課題 E のホワイトスペース問題が原因 |
| F | テーブル「数量」「売上ID」が左寄せ数値（Step 7 課題 F / Step 10 課題 D / Step 11 と同根） | 桁比較しづらい |
| G | 「申請日」「承認日」の整形が `customRender` で `T → 空白` `slice(0,16)` の生整形。日付と時刻が同セルにあり、列幅 80px 程度では確実に折り返す | 日時列の視認性が悪い |
| H | 「理由」列が `ellipsis: true, width: 200` で省略表示。`title` 属性ホバーでしか全文確認できない | ロングテキストの確認動線が弱い |
| I | 「操作」列が状態に応じて 0〜2 ボタン or `—` で **行ごとに高さ・幅が変わる** | 行高がバラついて視認性が下がる |
| J | 親 `<div>` の `max-width: 1200px` でワイドモニタの余白が広がるだけ（Step 10 課題 N / Step 11 と同根） | 情報密度が下がる |
| K | ページネーション `pageSize: 50` 固定で件数表示・サイズ切替なし | 件数規模の把握ができない |
| L | 件数表示なし | 他画面と同根 |
| M | データ 0 件のとき `<a-table>` の `:locale` 未設定で `No data` 英語表示（Step 11 と同根） | 日本語 UI なのに空状態だけ英語 |
| N | Modal 確認ダイアログのタイトル「承認しますか？」と内容「売上ID X（…）の返品申請を承認します」が冗長 | 操作確認の文言過剰 |

### 12-2. スコープ

| 観点 | 方針 |
|------|------|
| 対象画面 | `features/salesReturn/pages/SalesReturnList.vue` 単体 |
| 利用コンポーネント | Step 11 で新設する `<SearchCard>` を本画面でも採用 |
| DB 変更 | **なし** |
| API 変更 | **なし** |
| バックエンド変更 | **なし** |
| 前提 | Step 11 が先行実装されていること（`<SearchCard>` が存在すること）。Step 11 と Step 12 が同時実装される場合は本ステップが Step 11 の成果物を消費する関係にする |

### 12-3. 改善方針

#### 12-3-1. ヘッダー整理（課題 A・J・L）

- `<a-page-header>` の `sub-title` を削除（Step 7-3-1 / Step 8-3-1 / Step 10-3-1 / Step 11-4 と同方針）
- `<a-page-header>` 配下の `description` slot に件数表示
  - 例：`全 N 件中 M 件を表示` / フィルタ適用時は `全 N 件中 M 件を表示（フィルタ適用中）`
- ヘッダー右側 `#extra` slot に **「再読込」ボタンを移動**（課題 D の解消）。検索カード内ではなくヘッダー右に置くことで、API 再取得操作とフィルタ操作が明確に分離される
- 親 `<div>` の `max-width: 1200px` を削除

#### 12-3-2. 検索カードの拡充（課題 B・C・D）

`<a-form layout="inline">` 直書きを **`<SearchCard>`（Step 11 新設）に置換** し、検索条件を以下に拡張：

| 条件 | 入力 UI | 比較ロジック |
|------|--------|-------------|
| 状態 | `<a-select allow-clear>`（既存） | `r.status` 完全一致 |
| 顧客名 | `<a-input allow-clear>`（**新設**） | `r.customerName` 部分一致（保守的ホワイトスペース除去後の値で比較） |
| 商品名 | `<a-input allow-clear>`（**新設**） | `r.productName` 部分一致 |
| 売上ID | `<a-input-number min=1>`（**新設**） | `r.salesId` 完全一致 |
| 申請日（最早〜最遅） | `<a-date-picker>` × 2（**新設**、`<a-input-group compact>` ラップ） | `r.createdAt` の日付部分が範囲内 |
| 理由 | `<a-input allow-clear>`（**新設**） | `r.reason` 部分一致 |

`filteredList` computed を以下のように 6 条件 AND に拡張：

```js
const filteredList = computed(() => {
  return list.value.filter(r => {
    if (statusFilter.value && r.status !== statusFilter.value) return false;
    if (customerKeyword && !(normalizeName(r.customerName) || '').includes(customerKeyword)) return false;
    if (productKeyword && !(r.productName || '').toLowerCase().includes(productKeyword)) return false;
    if (searchForm.salesId != null && r.salesId !== searchForm.salesId) return false;
    if (searchForm.createdAtFrom && r.createdAt?.slice(0, 10) < searchForm.createdAtFrom) return false;
    if (searchForm.createdAtTo   && r.createdAt?.slice(0, 10) > searchForm.createdAtTo)   return false;
    if (reasonKeyword && !(r.reason || '').toLowerCase().includes(reasonKeyword)) return false;
    return true;
  });
});
```

`<SearchCard @clear="resetSearch">` の `clear` イベントで全 6 条件をリセット。

#### 12-3-3. テーブル列の見直し（課題 E・F・G・H・I）

| 列 | 変更点 |
|----|-------|
| 申請日 | **列幅 140px** に拡張、表示は **2 行構成**（1 行目に `YYYY-MM-DD`、2 行目に小さく `HH:mm`）。`customRender` で `<span class="datetime-block">` 内に分けて表示 |
| 顧客 | **ホワイトスペースの保守的除去**（Step 10-3-4 と同ロジック）。列幅 120px |
| 商品名 | 変更なし |
| 色 | 変更なし |
| サイズ | 変更なし |
| 数量 | `align: 'right'` 付与（課題 F） |
| 理由 | **列幅 240px に拡張** + `ellipsis: { showTitle: false }` に変更し、**ホバーで `<a-tooltip>` 全文表示**（課題 H 改善）。テーブル外で展開行が必要な場合は次フェーズ送り |
| 状態 | 変更なし（既存タグ） |
| 承認日 | 「申請日」と同じ 2 行構成、列幅 140px |
| 売上ID | `align: 'right'` 付与（課題 F） |
| 操作 | **列幅 180px 固定**、操作不要状態（REJECTED / REFUNDED）の `—` プレースホルダーを削除して空セルにする（課題 I）。`<a-space :size="4">` で密度を上げる |

申請日・承認日の 2 行構成例：

```vue
<template v-else-if="column.key === 'createdAt'">
  <div class="datetime-block">
    <div>{{ record.createdAt?.slice(0, 10) ?? '—' }}</div>
    <div class="datetime-time">{{ record.createdAt?.slice(11, 16) ?? '' }}</div>
  </div>
</template>
```

```css
.datetime-block { line-height: 1.3; }
.datetime-time  { color: #999; font-size: 12px; }
```

#### 12-3-4. ページネーション（課題 K）

`<a-table>` の `:pagination` を Step 7-3-5 / Step 8-3-10 / Step 9-3-5 / Step 10-3-5 / Step 11 と同じ仕様で明示：

```js
pagination: {
  pageSize: 50,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['50', '100', '200'],
}
```

#### 12-3-5. 空状態の日本語化（課題 M）

`<a-table>` に `:locale="{ emptyText: '該当する返品申請がありません' }"` を付与（Step 11-4-2 / 11-4-3 と同方針。文言は画面の意味に合わせる）。

#### 12-3-6. Modal 確認ダイアログの簡潔化（課題 N）

```js
Modal.confirm({
  title: `${def.label}しますか？`,
  content: `売上ID ${record.salesId}：${record.productName}（${record.color} / ${record.size}）`,
  okText: def.label,
  cancelText: 'キャンセル',
  onOk: () => runAction(record, action),
});
```

タイトルで操作意図を、内容で対象を端的に伝える形に整える。「の返品申請を{label}します」の繰り返しを削る。

### 12-4. UI 変更まとめ（Console）

`features/salesReturn/pages/SalesReturnList.vue`：

- 親 `<div>` の `max-width: 1200px` を削除
- `<a-page-header>` の `sub-title` 削除、`description` slot に件数表示、`#extra` slot に「再読込」ボタンを移動
- 検索フォームを `<SearchCard @clear="resetSearch">` に置換し、6 条件（状態 / 顧客名 / 商品名 / 売上ID / 申請日帯 / 理由）に拡張
- 申請日帯は `<a-input-group compact>` でラップ
- `searchForm` ref に `customerKeyword` / `productKeyword` / `salesId` / `createdAtFrom` / `createdAtTo` / `reasonKeyword` を追加
- `filteredList` computed を 6 条件 AND に拡張
- `columns`：
  - 申請日・承認日を 2 行構成、列幅 140px
  - 顧客はホワイトスペース保守的除去、列幅 120px
  - 数量・売上IDに `align: 'right'`
  - 理由は列幅 240px + `ellipsis: { showTitle: false }` + `<a-tooltip>` 全文表示
  - 操作は列幅 180px 固定、`—` プレースホルダー削除
- `<a-table>` に `:locale="{ emptyText: '該当する返品申請がありません' }"`
- `pagination` に `showTotal` / `showSizeChanger` 付与
- `confirmAction` の Modal 文言を簡潔化

### 12-5. DB 変更
**なし**

### 12-6. API 変更
**なし**

### 12-7. TDD テストケース

表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

- ヘッダーから "Amazia Console" のサブタイトルが消えている
- ヘッダー右側に「再読込」ボタンが移動し、検索カード内には存在しない
- ヘッダーに「全 N 件中 M 件を表示」が出る／フィルタ適用中は「（フィルタ適用中）」が併記される
- 検索カードが `<SearchCard>` の枠で表示される（Step 11 と同じ枠）
- 検索条件「状態」「顧客名」「商品名」「売上ID」「申請日帯」「理由」のすべてが AND で機能する
- 「クリア」ボタンで全 6 条件が既定値にリセットされる
- 申請日帯入力で `〜` セパレータが折り返し時に分断されない
- テーブル「申請日」「承認日」が `YYYY-MM-DD` + `HH:mm` の 2 行構成で表示され、列幅 140px で折り返さない
- テーブル「顧客」のユーザ名が空白除去後に表示される（5 行折り返しが解消する）
- テーブル「数量」「売上ID」の数値が右寄せで表示される
- テーブル「理由」がホバー時にツールチップで全文表示される
- テーブル「操作」列で REJECTED / REFUNDED 行が空セルになり `—` が表示されない
- データ 0 件のとき「該当する返品申請がありません」と日本語表示される
- ページネーションに「N-M / 全 K 件」が表示され、ページサイズを 50 / 100 / 200 に切り替えできる
- ウィンドウ幅 1920px でテーブルが 1200px に縛られず横いっぱいに広がる
- 「承認」「却下」「返金完了」ボタン押下時の Modal 文言が簡潔（タイトルと商品情報のみ）

### 12-8. 申し送り

- 本ステップは Step 11 の `<SearchCard>` を **実装後最初に消費する画面** となる。Step 11 と同時実装する場合、`<SearchCard>` の API（`wide-field-label` 等）の汎用性を返品管理画面の要件で再検証する機会になる。本画面は「幅広 1 軸なし／状態 select + 5 軸の混合」構成で、`wide-field-label` を使わない default slot のみのパターンになる
- 「申請日／承認日の 2 行構成（日付＋時刻）」は他の日時列を持つ画面（売上管理 / 操作履歴 等）にも横展開余地あり。次フェーズで `<DateTimeCell>` 共通コンポーネント化の要否を判断する
- 顧客名のホワイトスペース除去は Step 10-3-4 と同根。**Step 10 と Step 12 で同じ `normalizeName()` ヘルパを `utils/` に切り出して共有する** ことで、根本原因（Backend / DB のいずれで空白が混入したか）の調査が完了するまでの暫定対応を一元化する
- 操作列の状態別ボタン（承認 / 却下 / 返金完了）は **状態遷移マシンの UI 表現** であり、行ごとに表示が変わる仕様は維持する。`—` プレースホルダーを削除した結果、空セルが目立つ場合は、列見出しを「操作」→「次の操作」など意味を変える案もあるが、本ステップでは見送り（次フェーズ判断）

---

## Step 13：配送管理画面の `<SearchCard>` Grid レイアウト是正と仕上げ ✅ 実装完了（2026-05-07）

### 13-1. 背景・目的

Step 11 で導入した `<SearchCard>`（`grid-template-columns: repeat(auto-fit, minmax(220px, 1fr))`）を配送管理画面（`/delivery`、`DeliveryList.vue`）に適用した結果、**帯域条件（`<a-input-group compact>` で 130px × 2 + `〜` ＝ 約 280px のセル幅を要求する）と単一軸（売上ID / 配送方法 / 配送ステータス：220px で十分なセル）が同じ `auto-fit` グリッドに混在することで、レイアウトが破綻する** 現象がスクリーンショット観察で明らかになった。

具体的な観察事実（Step 11 適用後の現状）：

- 上段に「追跡番号」幅広 1 軸（OK）
- 中段：左から「売上ID／配送方法／配送ステータス／（空白セル）／配送予定日／（空白セル）／発送日」が並び、**「配送予定日」「発送日」の右側に大きな空白セル** ができている（Grid `auto-fit` が広いセルを 1 行に詰めきれず、空セルが発生）
- 「配送予定日」「発送日」の `〜` の後ろの「最遅」入力欄が **改行されて 2 行に分かれて表示**（`<a-input-group compact>` 内で 130px × 2 がセル幅を超えて折り返している）
- 下段：「配達完了日」だけが独立行に降り、その隣に「クリア」ボタンが **中央寄り** に置かれている（Grid 末尾セルが `justify-self: end` でも、Grid の自動配置では予測できない位置に出る）

これらは Step 11 の設計仮定「全セルを `minmax(220px, 1fr)` で均等扱いする」が、**帯域条件のセル幅要求（実質 280px+）を吸収しきれない** ことに起因する根本問題である。本ステップでは：

1. `<SearchCard>` を **小さく拡張**して「帯域軸 = 2 列分」の表現を可能にする
2. 配送管理画面で帯域 3 軸を 2 列スパンに切り替える
3. 同時に観察された配送管理画面固有の他課題も仕上げる

### 13-2. スコープ

| 観点 | 方針 |
|------|------|
| 拡張対象 | `components/SearchCard.vue`（Step 11 で新設したコンポーネント）に **帯域軸を 2 列スパンさせる仕組み** を追加 |
| 対象画面 | `features/delivery/pages/DeliveryList.vue` |
| 横展開 | 同じ Grid 問題を抱える他画面（予約管理 / 入荷管理 / 売上管理 / 返品管理）への横展開は **次タスクで判断**（Step 13 では配送管理に閉じる）。ただし `<SearchCard>` の拡張自体は他画面でも利用可能な状態にしておく |
| DB 変更 | **なし** |
| API 変更 | **なし** |
| バックエンド変更 | **なし** |

### 13-3. `<SearchCard>` の拡張

#### 13-3-1. 帯域軸の表現方法

`<SearchCard>` の Grid レイアウトに **`.search-card__grid > .span-2` セレクタ** を追加し、`grid-column: span 2` を当てる。各画面は帯域軸の `<a-form-item>` を `<div class="span-2">` でラップするか、`class="span-2"` を直接付与する：

```vue
<!-- DeliveryList.vue 側 -->
<a-form-item label="配送予定日" class="span-2">
  <a-input-group compact>
    <a-date-picker ... style="width: calc(50% - 12px)" />
    <span class="range-sep">〜</span>
    <a-date-picker ... style="width: calc(50% - 12px)" />
  </a-input-group>
</a-form-item>
```

`<SearchCard>` 側 CSS：

```css
.search-card__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: 16px;
  row-gap: 8px;
  align-items: end;
}

/* 帯域軸など、2 列分使いたいセル */
.search-card__grid :deep(.span-2),
.search-card__grid > .span-2 {
  grid-column: span 2;
}

/* 1 列に折り返したとき（minmax 効果で grid が 1 列になったとき）は span 解除 */
@media (max-width: 480px) {
  .search-card__grid :deep(.span-2),
  .search-card__grid > .span-2 {
    grid-column: span 1;
  }
}
```

#### 13-3-2. 帯域軸の内部レイアウト

`<a-input-group compact>` 内の `<a-date-picker>` を **固定 130px から `width: calc(50% - 12px)` に変更**し、セル幅に応じて伸縮させる。これにより 2 列スパン（実質 456px 程度）の中で「最早 / 最遅」が均等に並び、「最遅」の改行が解消される。

#### 13-3-3. `wide` prop の追加（任意）

各画面で `class="span-2"` を `<a-form-item>` に直書きする代わりに、`<SearchCard>` の中で識別する仕組みを **検討したが、本ステップでは採用しない**。理由：

- 各画面が「どの軸を 2 列にするか」を直接指示する方が明示的
- `<a-form-item>` をラップするカスタムコンポーネントを増やすと、Ant Design Vue のフォーム検証フックが機能しなくなる懸念
- `class="span-2"` 1 行で済むため、抽象化しすぎない方が読みやすい

### 13-4. 配送管理画面の改善方針

スクリーンショット観察と実装読解で確認された課題：

| # | 課題 | 影響 |
|---|------|------|
| A | Grid `auto-fit` 配下で「配送予定日」「発送日」セルの右側に **空白セル** ができている | 軸ラベルとフィールドの対応が読み取りづらい |
| B | 帯域条件の `<a-input-group compact>` 内で「最遅」入力が **2 行に折り返している**（130px × 2 + `〜` がセル幅を超える） | レンジ条件の意味が読み取れない |
| C | 「配達完了日」が他の帯域軸と縦に揃わず独立行に降りている | 同種軸が縦にバラけて視認性が悪い |
| D | 「クリア」ボタンが Grid 末尾セルとして中央付近に来ている（`justify-self: end` でも親セル内の右寄せに留まり、グリッド全体の右端には来ない） | 操作の発見性が悪い |
| E | 「追跡番号」幅広 1 軸の入力エリアと中段（売上ID 行）の間に大きな余白 | 視覚的にスカスカ |
| F | テーブル「ID」「売上ID」のヘッダー文字が `align: 'right'` で右端に張り付き、見出しの体裁がアンバランス | 見出し文字の右寄せはセル全体ではなく、ラベル部分の右寄せ |
| G | 「追跡番号」列で値あり (`111111`) と無し (`—`) の列幅が同じで、データの濃淡を活用できていない | 一覧性が下がる |
| H | 「操作」列の「詳細」が `type="link"` の青文字テキストリンク | 一覧画面の操作列としてはボタン形状が望ましい |
| I | 「配送ステータス」select の選択肢が「配送準備中（PENDING）」のように日本語+英語コード併記。テーブルセルも同形式で重複 | 冗長、視覚ノイズ |
| J | `<a-page-header>` の `description` slot 件数表示が **font-size: 13px / rgba(0,0,0,.45)** で薄く小さく、気づきにくい | フィルタ適用中バッジが視認されない |

### 13-5. 改善方針

#### 13-5-1. 検索カードの帯域軸を 2 列スパン化（課題 A・B・C・D・E）

- 「配送予定日」「発送日」「配達完了日」の `<a-form-item>` に **`class="span-2"`** を付与
- 各帯域条件内の `<a-date-picker>` の `style="width: 130px"` を **`style="flex: 1; min-width: 0"`** に変更（`<a-input-group compact>` 内で flex 配分させる）
- 「クリア」ボタン用のセルを `<SearchCard>` 内で **明示的に右端に配置**：`<SearchCard>` 側の `.search-card__actions` に `grid-column: -2 / -1`（最後のグリッドラインに固定）+ 帯域条件セルが続く場合のフォールバックを追加
  - 実装案：`.search-card__actions { justify-self: end; align-self: end; grid-column: 1 / -1; }`（クリアボタンを **常に独立行の右端** に置く）→ 軸数に関わらず操作位置が固定される

更新後の Grid 配置イメージ（1920px 幅・列数 6 のとき）：

```
| 売上ID | 配送方法 | 配送ステータス | (配送予定日 wide                ) |
| (発送日 wide                ) | (配達完了日 wide                ) |
|                                                  クリア(独立行・右端) |
```

1280px 幅（列数 4）のとき：

```
| 売上ID | 配送方法 | 配送ステータス | (配送予定日 wide        ) |
| (発送日 wide        ) | (配達完了日 wide        ) |
|                                       クリア(独立行・右端) |
```

#### 13-5-2. テーブル列の調整（課題 F・G・H・I）

| 列 | 変更点 |
|----|-------|
| ID | `align: 'right'` を維持しつつ、ヘッダー側だけ **`titleAlign: 'left'`** で左寄せ（Ant Design Vue の `align` はセルとヘッダー双方に効くため、`title` 自体に `<span style="text-align: left; display: block">` で個別指定）|
| 売上ID | 同上 |
| 配送方法 | 変更なし |
| 配送ステータス | `customRender` で **「配送準備中（PENDING）」→「配送準備中」** に英語コード併記を削除（課題 I）。SHIPPING_STATUS_LABEL 内の値から `（XXX）` を取り除く |
| 配送予定日 | 変更なし |
| 発送日 | 変更なし |
| 配達完了日 | 変更なし |
| 追跡番号 | **列幅 140px に縮小**、値が NULL の `—` は `style="color: #ccc"` で薄く表示（課題 G） |
| 操作 | 「詳細」を `type="link"` から **`type="default" size="small"`** のボタンに変更し、`size="small"` でコンパクトに（課題 H）。列幅 80px |

`SHIPPING_STATUS_LABEL` / `statusOptions` の英語コード併記の扱いは以下に整理：

```js
const SHIPPING_STATUS_LABEL = {
  1: '配送準備中',
  2: '配送済',
  3: '配送完了',
  4: '返品申請中',
  5: '返品完了',
};

// statusOptions も同形式に揃える（既に英語併記なし）
```

英語コード（PENDING / SHIPPED 等）は **開発者向け表示**として有用な側面もあるため、`title` 属性のホバーで `<a-tooltip title="PENDING">` で開発者だけが確認できる形にする案もある。本ステップでは取り急ぎ削除のみ。

#### 13-5-3. 件数表示の視認性（課題 J）

`<a-page-header>` の `description` slot 内の `<span>` を以下に変更：

```vue
<template #description>
  <a-space :size="8">
    <span style="color: rgba(0, 0, 0, 0.65); font-size: 14px">{{ countLabel }}</span>
    <a-tag v-if="isFilterApplied" color="blue">フィルタ適用中</a-tag>
  </a-space>
</template>
```

- 件数文字を **font-size 13px → 14px、色を 45% → 65%** に強化
- 「（フィルタ適用中）」の括弧テキストを `<a-tag color="blue">` に置き換え、視覚的に目立たせる

### 13-6. UI 変更まとめ（Console）

`components/SearchCard.vue`：

- `.search-card__grid` 内に `.span-2` セレクタ追加（`grid-column: span 2`、480px 以下では解除）
- `.search-card__actions` を **独立行の右端固定**（`grid-column: 1 / -1; justify-self: end`）に変更
- 上記変更が他画面（PreorderList / InboundList / SalesReturnList）の既存レイアウトに **影響しないか実装時に確認**：
  - PreorderList：帯域 3 軸（価格 / 発売日 / 予約開始日）に `class="span-2"` を追加すると整列が改善する想定。本ステップでは触らないが、Step 13 実装時に同時適用するか、別タスクで判断

`features/delivery/pages/DeliveryList.vue`：

- 「配送予定日」「発送日」「配達完了日」の `<a-form-item>` に `class="span-2"` 付与
- 帯域条件内 `<a-date-picker>` の `style` を `flex: 1; min-width: 0` に変更
- `SHIPPING_STATUS_LABEL` から英語コード併記を削除
- `columns`：
  - ID / 売上ID のヘッダー文字を `<span>` で個別左寄せ
  - 追跡番号の列幅 140px、NULL `—` のグレー化
  - 「詳細」を `type="default" size="small"` のボタンに、列幅 80px
- `<a-page-header>` の `description` slot：font-size 14px / color 65%、`<a-tag>` で「フィルタ適用中」明示

### 13-7. DB 変更
**なし**

### 13-8. API 変更
**なし**

### 13-9. TDD テストケース

表示変更のため、フェーズ16冒頭の方針通り Vue ユニットテストは追加せず手動 E2E で担保する。

#### `<SearchCard>` 拡張

- `<a-form-item class="span-2">` を持つ画面で、当該セルが **2 列分の幅** を占める
- 「クリア」ボタンが **常にグリッド末尾の独立行・右端** に配置される（軸数に関わらず固定）
- 480px 以下のウィンドウ幅では `span-2` が解除されて 1 列扱いになる

#### 配送管理画面

- 「配送予定日」「発送日」「配達完了日」の `〜` セパレータが折り返し時に分断されない（最早 / 最遅が同じ行に並ぶ）
- 1920px 幅で 6 列グリッド、1280px 幅で 4 列グリッドに自動的に折り返す
- 「クリア」ボタンが画面下部右端に配置される
- テーブル「配送ステータス」列に英語コード「（PENDING）」等が表示されない
- 「追跡番号」列が幅 140px に縮小され、NULL の行は `—` がグレーで薄く表示される
- 「操作」列の「詳細」がボタン形状（テキストリンクではない）で表示される
- ヘッダー件数表示が読みやすい大きさ・濃さで表示される
- フィルタ適用中は青い `<a-tag>`「フィルタ適用中」が表示される

### 13-10. 申し送り

- 本ステップは **Step 11 の `<SearchCard>` 設計に対する後続改善**。Step 11 完了時点で 4 画面（予約管理 / 配送管理 / 入荷管理 / 返品管理）に展開した結果、配送管理画面で最も顕著にレイアウト破綻が現れたが、**他 3 画面（PreorderList / InboundList / SalesReturnList）も帯域軸を持つ** ため、同じ問題が潜在している
- 配送管理画面以外への `class="span-2"` 適用は **本ステップでは実施しない**。理由：
  - 各画面の帯域軸の数とウィンドウ幅で「2 列スパン」の効果が異なるため、画面ごとに目視確認が必要
  - Step 13 のスコープを配送管理画面に絞り、`<SearchCard>` 拡張の挙動を 1 画面で検証してから他画面に横展開する方が安全
- 他画面への横展開は **Step 14 以降または別フェーズ**で判断する。実装時に他画面のスクリーンショットを撮り直し、現状のレイアウトが許容範囲か再評価する
- 英語コード併記の削除（`SHIPPING_STATUS_LABEL`）は **配送管理画面に閉じる**。同じラベルマップを使う他箇所（売上管理画面の `SHIPPING_STATUS_LABELS`）には影響しない（別 const として独立しているため）。コード体系の一元化は次フェーズ送り
- `<a-page-header>` の `description` slot で `<a-tag>` を「フィルタ適用中」表示に使う構成は、他画面の件数表示にも横展開余地あり。Step 7・10 で同じ表現を採用しているなら統一する判断は次タスクで

