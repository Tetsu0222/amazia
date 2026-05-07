
# フェーズ16：UIデザイン改善

## ステータス
🟡 着手中（Step 1 / Step 1.5 / Step 2 / Step 3 / Step 3.1 / Step 4 / Step 5 実装完了・2026-05-07）

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
