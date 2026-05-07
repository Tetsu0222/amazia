
# フェーズ16：UIデザイン改善

## ステータス
🟡 着手中（Step 1 / Step 1.5 / Step 2 / Step 3 実装完了・2026-05-07）

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

# 機能詳細

---

## 🖥 Amazia Console（管理画面）

### UI改善方針
- 現行の雰囲気は維持しつつ、以下の観点で洗練させる  
  - 情報の階層化（見出し・カード・タブの活用）  
  - 一覧画面の視認性向上（行間・色・アイコン）  
  - 操作ボタンの統一（配置・色・サイズ）  
  - フォーム入力のガイド強化（プレースホルダー・バリデーション表示）  
  - レスポンシブ対応の最適化  

### 対象画面例
- 売上管理  
- 商品管理  
- ワークフロー管理  
- 配送管理  
- 操作履歴  

---

## 🛒 Amazia Market（ECサイト）

### UI改善方針
- **Amazon の UI に寄せる**  
  - ヘッダー構成（検索バー・カテゴリ・アカウント・カート）  
  - 商品一覧のカードデザイン  
  - 商品詳細ページのレイアウト（画像・価格・説明・購入ボタン）  
  - レビュー表示のスタイル  
  - カート画面・購入画面の導線  
  - 配送情報の選択 UI（Amazon の「お届け先を選択」風）  

### 改善対象
- TOPページ  
- 商品一覧  
- 商品詳細  
- カート  
- 決済画面  
- 購入履歴  
- 予約商品表示  

---

# 技術検討事項
- UIフレームワークの選定（Bootstrap / Tailwind / Vuetify / Chakra UI など）  
- ダークモード対応の要否  
- コンポーネント化による保守性向上  
- Market の Amazon 風 UI をどこまで再現するか（完全模倣は避ける）  
- スマホ最適化（特に Market はモバイル比率が高い想定）  
- アクセシビリティ（色覚対応・キーボード操作対応）

---

# TDDテストケース  
※UI改善フェーズのため、主に E2E / 表示確認系

## Amazia Console / PHPUnit（または Dusk）
- 一覧画面のレイアウトが崩れず表示される  
- ボタン配置が統一されている  
- フォームのバリデーション表示が正しく動作する  

## Amazia Market / PHPUnit（または Dusk）
- 商品一覧が正しいレイアウトで表示される  
- カートアイコン・検索バーが正常に動作する  
- 商品詳細ページのレイアウトが崩れない  
- モバイル表示で UI が最適化されている  
