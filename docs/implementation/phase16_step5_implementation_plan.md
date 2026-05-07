# フェーズ16 Step 5 実装計画（SKU管理画面の起点を「商品プルダウン」から「商品一覧」へ）

## 概要
- 対象設計書: [phase16_ui_ux_improvement.md](../design/phase11_20/phase16_ui_ux_improvement.md) Step 5
- 対象範囲: Amazia Console（Vue UI のみ）
- 段取り: **A（SkuList.vue 全面再構成）→ B（ProductList との導線整合）→ C（設計書本体への Step 5 詳細追記と完了確認）** の 3 段階で実施
- 作成日: 2026-05-07
- 親フェーズ: phase16 Step 4（2026-05-07 完了）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| API / DB 変更 | **なし**。既存 `GET /api/admin/products`・`GET /api/products/{id}/skus`・`POST /api/products/{id}/skus`・`/api/skus/{id}/prices`・`/api/skus/{id}/stocks*`・`/api/skus/{id}/images*` をそのまま使う |
| ルート変更 | **なし**。`/skus` は SKU 管理画面のままで、内部の UI 構造のみ刷新する |
| クエリパラメータ | `route.query.productId` で来た場合は該当商品行を初期展開する（既存挙動を温存・ProductList の「SKU管理」ボタン互換） |
| 環境変数 | 追加なし |
| 既存テスト | Console UI に vitest は無い。Laravel feature テスト・Spring テストは API を触らないため影響なし。手動 E2E で担保（フェーズ16冒頭の方針と整合） |
| 規模感 | Vue ページ 1 ファイル（`SkuList.vue`）の全面改修・他は触らない |
| コーディング規約 | [coding_guidelines.md](../coding_guidelines.md) 5.（フロント）に準拠 — API は `features/*/api/` 経由・コンポーネントに業務ロジックを書かない |

### 設計書からの「本ステップのスコープ外」確認

| 項目 | 取り扱い |
|------|---------|
| Core / Console の SKU 系 API 変更 | スコープ外（API は phase15 / phase16-step1 のまま） |
| 商品マスタ画面（`ProductList.vue`）の構成 | スコープ外（SKU 管理ボタンと expandable は既に存在・本ステップでは導線確認のみ） |
| `SkuStockList.vue`（旧 SKU 在庫一覧画面） | スコープ外（参照画面として残置） |
| 価格・在庫・画像の各管理タブの中身 | スコープ外（モーダル化に伴う「描画器の置き場所」のみ変更し、ロジック・API 呼び出しは既存を流用） |

---

## 1. Step A — SkuList.vue：商品一覧 + SKU 展開 + SKU 詳細モーダルへの再構成

### 1-1. 現状の問題点

[SkuList.vue](../../amazia-console/resources/vue/src/features/skus/pages/SkuList.vue) は

1. 画面トップに **商品プルダウン** があり、選ばないと SKU が一切表示されない
2. プルダウン → SKU 一覧 → SKU 行クリック → 同画面下部のタブパネル（価格 / 在庫 / 画像）という **縦長の 1 画面** で完結

設計書 Step 5 の要望は「最初から SKU 一覧が見える状態にし、商品マスタ押下で SKU を展開、選択で登録/編集に進む」。アクション数は変わらないが、導線が直感的になる。

### 1-2. 新しい画面構成（After）

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
│                                                       │
│ （SKU行で[選択] 押下時にモーダルが開く）             │
└──────────────────────────────────────────────────────┘

モーダル：「SKU詳細：HAT-RD-L（Red / L）」
┌──────────────────────────────────────────────┐
│ [価格管理] [在庫管理] [画像管理]              │
│  …各タブの既存 UI をそのまま移植…             │
│                                              │
│                          [閉じる]            │
└──────────────────────────────────────────────┘
```

### 1-3. テンプレート骨格（疑似コード）

```html
<a-page-header title="SKU管理" sub-title="Amazia Console" />

<!-- 商品一覧（プルダウン廃止） -->
<a-table
  :columns="productColumns"
  :data-source="products"
  :loading="productsLoading"
  row-key="id"
  :expand-row-by-click="true"
  :expanded-row-keys="expandedRowKeys"
  :row-class-name="(r) => r.isActive ? '' : 'row-inactive'"
  @expand="onProductExpand"
>
  <template #expandedRowRender="{ record }">
    <!-- SKU一覧（その商品の SKU を表示） -->
    <a-spin v-if="skuLoadingMap[record.id]" />
    <template v-else>
      <a-table
        :columns="skuColumns"
        :data-source="skuMap[record.id] || []"
        row-key="id"
        size="small"
        :pagination="false"
      >
        <template #bodyCell="{ column, record: sku }">
          <template v-if="column.key === 'releaseStatus'">
            <a-tag :color="isReleased(record) ? 'green' : 'blue'">
              {{ isReleased(record) ? '発売中' : '発売前' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'preorderStartDate'">
            {{ record.preorderStartDate ?? '公開と同時' }}
          </template>
          <template v-if="column.key === 'releaseDate'">
            {{ record.releaseDate ?? '未設定' }}
          </template>
          <template v-if="column.key === 'action'">
            <a-button size="small" type="primary" @click.stop="openSkuModal(record, sku)">
              選択
            </a-button>
          </template>
        </template>
      </a-table>

      <!-- SKU 追加フォーム（その商品の配下に） -->
      <a-form layout="inline" @finish="handleSkuSubmit(record.id)" ...>
        <a-form-item label="色"><a-input ...></a-form-item>
        <a-form-item label="サイズ"><a-input ...></a-form-item>
        <a-form-item><a-button html-type="submit">SKUを追加</a-button></a-form-item>
      </a-form>
    </template>
  </template>

  <!-- 商品行のセル -->
  <template #bodyCell="{ column, record }">
    <template v-if="column.key === 'releaseStatus'">…</template>
    <template v-if="column.key === 'published'">…</template>
    <template v-if="column.key === 'active'">…</template>
    <template v-if="column.key === 'skuCount'">…</template>
  </template>
</a-table>

<!-- SKU詳細モーダル -->
<a-modal
  v-model:open="skuModalOpen"
  :title="`SKU詳細：${selectedSkuLabel}`"
  width="780px"
  :footer="null"
  @cancel="closeSkuModal"
>
  <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
    <a-tab-pane key="price" tab="価格管理"> …既存UIそのまま… </a-tab-pane>
    <a-tab-pane key="stock" tab="在庫管理"> …既存UIそのまま… </a-tab-pane>
    <a-tab-pane key="image" tab="画像管理"> …既存UIそのまま… </a-tab-pane>
  </a-tabs>
</a-modal>
```

### 1-4. データ取得ロジック（規約 5：API は features/*/api/ 経由・コンポーネントに業務ロジック書かない）

| 取得タイミング | 呼び出し関数 | 呼び方 |
|---------------|-------------|--------|
| 画面 mount 時 | `getAdminProducts()` | 商品全件を一覧表示（プルダウン廃止に伴いインライン展開を前提とするため、既存と同じ admin 一覧を流用） |
| 商品行展開時 | `getProductSkus(productId)` | 既存。展開済み商品はキャッシュ（`skuMap`）から再利用 |
| SKU モーダル開く時（タブ切替時） | `getSkuPrices` / `getSkuStock` / `getSkuStockHistory` / `getSkuImages` | 既存。`loadedTabs` でタブ単位の遅延ロードを継続 |
| SKU 追加 | `createProductSku(productId, data)` | 既存。送信後はその商品行の SKU 配列のみ再フェッチ（全商品を fetch しない） |
| 価格登録 / 画像アップロード | `createSkuPrice` / `uploadSkuImage` | 既存。モーダル内のフォームから呼ぶ |

### 1-5. クエリパラメータ互換（ProductList.vue → /skus?productId=N）

[ProductList.vue:113](../../amazia-console/resources/vue/src/features/products/pages/ProductList.vue#L113) は `/skus?productId=N` で SKU 管理に遷移する仕様。これを壊さないよう、

- `route.query.productId` がある場合：画面マウント後に該当商品の行を `expandedRowKeys` に push し、`onProductExpand` 同等の SKU 取得処理を発火する
- 該当商品が見つからない場合は何もしない（プルダウン版でも同等のフォールバック）

### 1-6. 旧実装からの撤去

| 旧構造 | 撤去/変更 |
|-------|----------|
| 画面トップの `<a-form layout="inline">` 商品プルダウン | **削除** |
| `selectedProductId` / `onProductChange` / `selectedProduct` | **削除**（プルダウン専用の状態） |
| `selectedSkuId` / `selectedSkuLabel` / `selected-row` クラス | **削除**（モーダルに置き換わるため強調表示は不要） |
| 下部固定の SKU 詳細パネル `<a-divider>選択中のSKU…</a-divider>` 〜 `<a-tabs>` | **モーダル内に移植** |
| `clearSkuDetail` / `loadedTabs` | **モーダル開閉時に呼ぶ形へ移行**（タブ単位の遅延ロード方針はそのまま） |
| `<style>` の `.selected-row` | **削除**（行選択ハイライト不要） |

### 1-7. 状態定義（After）

```js
// 商品一覧と SKU 展開キャッシュ
const products = ref([]);
const productsLoading = ref(false);
const expandedRowKeys = ref([]);
const skuMap = ref({});             // productId -> sku[]
const skuLoadingMap = ref({});      // productId -> bool

// 商品ごとの SKU 追加フォーム（簡素：開いた商品行だけ用 1 セット保持）
const skuForms = ref({});           // productId -> { color, size }
const skuSubmittingMap = ref({});

// SKU モーダル
const skuModalOpen = ref(false);
const selectedSkuId = ref(null);
const selectedProductForModal = ref(null);
const selectedSkuLabel = ref('');
const activeTab = ref('price');
const loadedTabs = ref(new Set());

// モーダル内の各タブ state（既存の prices / currentStock / stockHistory / images / 各 form を流用）
```

### 1-8. 表示済み列の踏襲

Step 1.5 で確定した SKU 列「SKUコード / 色 / サイズ / ステータス（発売前後）/ 予約開始日 / 発売日 / 操作（選択）」をそのまま採用する。`isReleased` 判定（`release_date` 未設定 or 当日含む過去 → 発売中）も既存と同じロジックを移植する。

商品列は [ProductList.vue:159-168](../../amazia-console/resources/vue/src/features/products/pages/ProductList.vue#L159-L168) と表記を揃える：

| 列 | キー | 内容 |
|----|------|------|
| ID | `id` | テキスト |
| 商品名 | `name` | テキスト |
| SKU数 | `skuCount` | バッジ表示 |
| 公開状態 | `published` | `publishStart`/`publishEnd` 判定（既存 `isPublished` ロジック） |
| 有効/無効 | `active` | `is_active` バッジ |
| 発売 | `releaseStatus` | 商品の `release_date` から「発売中/発売前」 |

「価格帯 / 合計在庫」は SKU 管理画面では商品単位の集計値を出さず（SKU 展開すれば見える）、列を絞って画面密度を下げる。

---

## 2. Step B — ProductList との導線整合

### 2-1. ProductList.vue「SKU管理」ボタンの維持

[ProductList.vue:113](../../amazia-console/resources/vue/src/features/products/pages/ProductList.vue#L113) の `$router.push('/skus?productId=' + record.id)` は **そのまま維持**。Step 5 の SkuList は受け側で `route.query.productId` を読み、初期展開する（1-5 参照）。

### 2-2. サイドメニュー（App.vue）

`/skus` のメニュー項目はそのまま（プルダウンだけ消えて、起点が一覧表示に変わる）。**変更なし**。

### 2-3. SkuStockList / SkuPriceList / SkuImageList 等の旧画面

- `SkuStockList.vue`（`/skus/stocks`）：phase15 で参照画面化済。本ステップでは触らない
- `SkuPriceList.vue` / `SkuImageList.vue`：もし router 登録があれば残置（参照系）。本ステップではいずれも触らない

---

## 3. Step C — 設計書反映と完了確認

### 3-1. 設計書本体の更新

[phase16_ui_ux_improvement.md](../design/phase11_20/phase16_ui_ux_improvement.md) に Step 5 セクションを **新設**（次タスク.txt の Step5 内容を整形して追記）。記載項目：

- 背景・目的（プルダウン起点の不便さ → 一覧+展開へ）
- 設計方針（商品一覧 + SKU expandable + SKU 詳細モーダル）
- UI 変更まとめ（SkuList.vue のみ）
- DB / API 変更：なし
- TDD テストケース：表示変更のみのため Vue ユニットテストは追加せず E2E で担保（Step 1.5 / Step 3 / Step 4 と同じ方針）
- ステータス行：`✅ 実装完了（2026-05-07）`

冒頭ステータス節（[phase16_ui_ux_improvement.md:5](../design/phase11_20/phase16_ui_ux_improvement.md#L5)）にも `Step 5` を追記する。

### 3-2. API / DB 設計書

- 変更なし（`docs/api_design/`・`docs/database_design/` 触らず）

### 3-3. 動作確認（手動 E2E）

Console を起動して以下を確認：

1. `/skus` を直接開いて、最初から商品一覧が表示される（プルダウンが無い）
2. 商品行の `▶` を押して SKU 一覧と「SKUを追加」フォームが展開される
3. 別商品を展開しても、先に展開した商品の SKU は再取得されない（キャッシュ動作）
4. SKU 行の「選択」ボタンでモーダルが開き、価格 / 在庫 / 画像タブの各機能が従来通り動く（価格登録・画像アップロード・在庫履歴閲覧）
5. モーダルを閉じて別 SKU を選び直すと、タブ表示が初期化される
6. `/skus?productId=2` で来ると id=2 の商品行が初期展開される（ProductList の「SKU管理」ボタン互換）
7. 無効化された商品（`isActive = false`）はグレーアウト表示される

### 3-4. CLAUDE.md 「フェーズ完了の定義」チェック

- [x] DB 変更なし → `docs/database_design/` 更新不要・`required_tables.txt` 更新不要
- [x] API 変更なし → `docs/api_design/` 更新不要
- [x] 環境変数追加なし → `docker-compose.yml` / `phpunit.xml` 更新不要
- [x] 設計書本体（phase16_ui_ux_improvement.md）に Step 5 詳細を追記

---

## 4. 想定リスクと回避策

| リスク | 回避策 |
|--------|-------|
| 商品数が増えると初期表示で `getAdminProducts` が遅くなる | 既存の `ProductList.vue` がすでに同じ呼び出しを行っており、運用規模では許容範囲。将来ページング化が必要になれば商品マスタ画面と同時に対応 |
| SKU 展開を多数開いた状態でメモリが増える | `skuMap` は商品 ID 単位のキャッシュ。明示的な閉じ動作で破棄せず、画面遷移時に Vue がコンポーネント丸ごと破棄する標準動作に任せる |
| SKU 追加フォームが「選択中の商品」依存だったが、各商品行に並ぶ形に変わる | `skuForms[productId]` で商品単位に独立したフォーム state を持つ。送信時は該当 productId のみ再フェッチ |
| モーダル化により価格/在庫/画像の遅延ロード（loadedTabs）の挙動が変わる | モーダルを開いた瞬間にリセット、開いている間はタブ初回表示で fetch、閉じたらリセット。動作は従来と同等 |
| `/skus?productId=N` で来た時の初期展開が動かない | onMounted 内で products fetch 完了後に `route.query.productId` を Number 化して `expandedRowKeys` に push し、`onProductExpand(true, record)` を内部呼び出し |

---

## 5. 完了条件

- 設計書本体（phase16_ui_ux_improvement.md）に Step 5 のセクションが追加され、ステータスが「実装完了（2026-05-07）」になっている
- 上記 3-3 の手動 E2E が通る
- `/skus` トップに商品プルダウンが残っておらず、商品一覧が初期表示されている
- ProductList の「SKU管理」ボタンからの遷移が壊れていない
