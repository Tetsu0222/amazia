# フェーズ16 Step 2 実装計画（予約管理画面と売上の予約除外フィルタ）

## 概要
- 対象設計書: [phase16_ui_ux_improvement.md](../design/phase11_20/phase16_ui_ux_improvement.md) Step 2
- 対象範囲: Amazia Console / Amazia Core（API 1 本新設のみ）
- 段取り: **Step A（Core API 新設）→ B（Console Pass-through）→ C（Console UI：予約管理画面）→ D（Console UI：売上画面改修）→ E（設計書反映と完了確認）** の 5 段階で実施
- 作成日: 2026-05-07
- 親フェーズ: [phase15_implementation_plan.md](phase15_implementation_plan.md)（phase15 完了済み）/ phase16 Step 1（2026-05-07 完了）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | Step A → B → C → D → E を厳守。各 Step 末で `mvn test` / `phpunit` / `vitest` の該当層が緑であることを完了条件とする |
| 規模感 | Core Controller/Service/DTO 各 1 本 + Console Controller/Service 各 1 本 + Vue ページ 1 本 + 既存 SalesList.vue 改修 |
| TDD | 設計書「Step 2 / 2-9 TDD テストケース」を Step ごとに割り当て |
| コーディング規約 | [coding_guidelines.md](../coding_guidelines.md) 厳守（1 ファイル 1 ユースケース・ドメイン単位パッケージ・Service にロジック寄せ） |
| DB 変更 | **なし**（既存カラムのみで成立） |
| 環境変数 | 追加なし（既存の `services.amazia_core.base_url` を流用） |
| メモリ事項 | Core 側 Heap 制限（`-Xmx384m`）を意識し、予約商品ループ内で `sales` を 1 商品ずつ単独 SELECT せず、対象商品 ID 集合で一括 SELECT してメモリ上で集約（後述 Step A-2 参照） |
| API スキーマ互換性 | `GET /api/sales` のスキーマは変更しない。既存 Market 含めた他画面への影響ゼロ |

### 設計書からの「本ステップのスコープ外」確認（再掲）

| 項目 | 取り扱い |
|------|---------|
| Market 側の予約商品表示変更 | フェーズ16後段の Market UI 改善で扱う |
| 予約注文のキャンセル / 発売日変更フロー | phase14_5 で確定済み・本ステップでは触らない |
| ページング / サーバーサイドフィルタ | 将来課題。Step 2 ではクライアント側フィルタで完結 |

---

## 1. Step A — Core: 予約商品一覧 API 新設

### 1-1. パッケージ構成

```
com.example.product
├── controller
│   └── ListPreorderProductsController.java（新設）
├── service
│   └── ListPreorderProductsService.java（新設）
└── dto
    └── PreorderProductItem.java（新設）
```

既存の `com.example.product` 配下に追加（規約 2-1：ドメイン単位）。

### 1-2. ListPreorderProductsService の実装方針

**入力**：なし（全件取得）

**手順**：
1. `productRepository.findAll()` で全商品を取得
2. 各商品について `PreorderStatusService#judge(productId)` を呼び `PRE_ORDER` のものだけ抽出
3. 抽出された商品 ID の集合を作成
4. `productSkuRepository.findByProductIdIn(productIds)` で SKU を一括取得
5. SKU ID の集合で `salesRepository.findByIsPreorderTrueAndSkuIdIn(skuIds)` を実行（**新規メソッドを SalesRepository に追加**）
6. メモリ上で `productId` ごとに数量・金額を集約
7. DTO に詰め、`releaseDate` 昇順でソートして返却

**メモリ配慮**（test_insights カテゴリ7-2 / phase15 §0 と同方針）：
- 商品ループ内で個別に `sales` を SELECT しない（N+1 回避）
- `findByIsPreorderTrueAndSkuIdIn` で 1 回の SELECT に集約

### 1-3. SalesRepository への追記

```java
// amazia-core/src/main/java/com/example/sales/repository/SalesRepository.java
List<Sales> findByIsPreorderTrueAndSkuIdIn(Collection<Long> skuIds);
```

### 1-4. PreorderProductItem DTO

```java
public class PreorderProductItem {
    private final Long productId;
    private final String productName;
    private final LocalDate preorderStartDate;  // null 許容
    private final LocalDate releaseDate;        // null 許容
    private final Long daysUntilRelease;        // releaseDate - today（null 時は null）
    private final boolean acceptPreorder;
    private final boolean isActive;
    private final long preorderQuantity;
    private final long preorderAmount;
    // コンストラクタ・getter
}
```

### 1-5. ListPreorderProductsController

```java
@RestController
@RequestMapping("/api")
public class ListPreorderProductsController {
    private final ListPreorderProductsService service;
    public ListPreorderProductsController(ListPreorderProductsService s) { this.service = s; }

    @GetMapping("/products/preorders")
    public ResponseEntity<List<PreorderProductItem>> list() {
        return ResponseEntity.ok(service.list());
    }
}
```

### 1-6. Step A の TDD（mvn test）

`amazia-core/src/test/java/com/example/product/service/ListPreorderProductsServiceTest.java`（新設）

- `is_active = FALSE` の商品は除外される
- `release_date` を過ぎた商品は PreorderStatus が変わるため除外される（PRE_ORDER 以外は除外）
- 予約数量・金額が `sales.is_preorder = TRUE` のみで集計される（`is_preorder = FALSE` の sales は無視）
- 発売日昇順で返却される
- 予約商品が 0 件のとき空配列

`amazia-core/src/test/java/com/example/product/controller/ListPreorderProductsControllerTest.java`（新設）
- 200 OK と JSON 配列が返る

**完了条件**：`mvn test` 緑

---

## 2. Step B — Console: Pass-through 実装

### 2-1. パッケージ構成

```
amazia-console/app/Preorder/
├── Controller/
│   └── ListPreorderController.php（新設）
└── Service/
    └── ListPreorderService.php（新設）

amazia-console/routes/api/Preorder.php（新設）
```

`Sales` ドメインと並列のドメインとして `Preorder` を新設（規約 2-1：ドメイン単位フォルダ）。

### 2-2. ListPreorderService

`GetSalesService.php` を参考に同形で実装：

```php
class ListPreorderService
{
    private string $baseUrl;
    public function __construct() { $this->baseUrl = config('services.amazia_core.base_url'); }
    public function list(): Response { return Http::get("{$this->baseUrl}/products/preorders"); }
}
```

### 2-3. ListPreorderController

```php
class ListPreorderController extends Controller
{
    public function __construct(private ListPreorderService $service) {}
    public function __invoke()
    {
        $response = $this->service->list();
        return response()->json($response->json(), $response->status());
    }
}
```

### 2-4. routes/api/Preorder.php

```php
<?php
use Illuminate\Support\Facades\Route;
Route::get('/preorders', \App\Preorder\Controller\ListPreorderController::class);
```

### 2-5. routes/api.php への登録

`require __DIR__.'/api/Preorder.php';` を追記（規約 2-1 補足4：明示的な記載必須）。
※既存の `Sales.php` 等の require 配置と同じグループ化方針。

### 2-6. Step B の TDD（phpunit）

`amazia-console/tests/Feature/Preorder/ListPreorderTest.php`（新設）

- 認証なしで 401（既存 `AuthenticateJwt` ミドルウェアの挙動を確認）
- 認証済みリクエストで Core モックレスポンスが透過的に返る
- Core が 500 を返した時にステータス 500 が透過する（規約 4-2 異常系）

`Http::fake()` で Core を差し替え（既存テストと同じ手法）。

**完了条件**：`phpunit` 緑

---

## 3. Step C — Console UI: 予約管理画面新設

### 3-1. ファイル構成

```
amazia-console/resources/vue/src/features/preorder/
├── api/
│   └── preorderApi.js（新設）
└── pages/
    └── PreorderList.vue（新設）
```

### 3-2. preorderApi.js

```js
import api from '../../auth/api/authApi.js';
export function listPreorders() { return api.get('/preorders'); }
```

### 3-3. PreorderList.vue

要件（設計書 §2-4-2）：

| 列 | 表示 | 備考 |
|----|------|------|
| 商品ID | text | テキスト表示のみ |
| 商品名 | text | テキスト表示のみ |
| 予約開始日 | text or 「公開と同時」 | NULL 時 |
| 発売日 | text | |
| 発売まで | `${daysUntilRelease}日` または「本日発売」 | |
| 予約受付 | a-tag「受付中」/「停止中」 | accept_preorder |
| 予約数 | text | |
| 予約金額 | `text.toLocaleString()` 円 | |
| Market 公開 | a-tag「公開中」/「非公開」 | is_active |

並び順：API 既に発売日昇順なのでそのまま。

`SalesList.vue` の構造（a-table + a-page-header）を踏襲する。

### 3-4. router/index.js への登録

```js
import PreorderList from '../features/preorder/pages/PreorderList.vue';
// ...
{ path: '/preorders', component: PreorderList, meta: { requiresAuth: true } },
```

`/sales` の直前に挿入（メニュー順と一致させる）。

### 3-5. App.vue メニュー追加

```vue
<a-menu-item key="/sales">売上管理</a-menu-item>
<a-menu-item key="/preorders">予約管理</a-menu-item>   <!-- 新規 -->
<a-menu-item key="/sales-returns">返品管理</a-menu-item>
```

### 3-6. Step C の TDD（vitest）

`amazia-console/resources/vue/src/features/preorder/__tests__/PreorderList.spec.js`（新設）

- API 成功時：データが `a-table` に表示される
- 「発売まで」が `daysUntilRelease` から正しく計算表示される（0 → 「本日発売」）
- API 失敗時：`message.warning` が呼ばれる（既存 SalesList.vue 同様）

**完了条件**：`vitest` 緑、ブラウザで実際にメニューから遷移して空配列でも 200 が表示されること。

---

## 4. Step D — Console UI: 売上管理画面改修

### 4-1. SalesList.vue の改修ポイント

#### 4-1-1. 一覧タブ：予約除外フィルタ
- `<a-tabs>` の `<a-tab-pane key="list">` 内、`<a-table>` の上に `<a-checkbox v-model:checked="excludePreorderInList">予約を除外</a-checkbox>` を追加
- `dataSource` を `filteredSalesForList` という computed に変更し、`excludePreorderInList` ON のときは `s.preorder === false` で絞り込む

#### 4-1-2. 集計タブ：予約除外既定 + 見込み表示トグル

```vue
<a-tab-pane key="summary" tab="集計">
  <div style="margin-bottom: 12px">
    <a-button @click="includePreorderInSummary = !includePreorderInSummary"
              :type="includePreorderInSummary ? 'primary' : 'default'">
      {{ includePreorderInSummary ? '見込み表示中（予約含む）' : '見込み表示' }}
    </a-button>
    <span v-if="includePreorderInSummary" style="margin-left: 12px; color: #faad14">
      ※ 予約購入を含む見込み値です
    </span>
  </div>
  <!-- 既存カード -->
</a-tab-pane>
```

- 既定値：`includePreorderInSummary = ref(false)`
- `summaryByMonth` / `summaryBySku` / `summaryByPayment` の元データを `salesForSummary` という computed に変更
  - `includePreorderInSummary === true` → `sales.value` 全件
  - `includePreorderInSummary === false` → `sales.value.filter(s => !s.preorder)`
- `summaryByPreorder` は元データ全件のまま（区分別表示は常に両方表示）

### 4-1-3. 影響範囲

- **API 変更なし**（クライアント側 computed のみで完結）
- 既存の「区分」列・「購入区分別売上」カードはそのまま残す

### 4-2. Step D の TDD（vitest）

`amazia-console/resources/vue/src/features/sales/__tests__/SalesList.spec.js`（新設または既存追記）

- 一覧タブ「予約を除外」ON で `preorder = true` の行が消える
- 集計タブ初期状態で月別/SKU別/決済方法別から予約売上が除外されている
- 「見込み表示」トグル ON で集計が予約込みに切り替わる
- 「見込み表示」中は注意書き「※ 予約購入を含む見込み値です」が表示される
- 購入区分別売上は `includePreorderInSummary` の状態に関わらず常に通常／予約両方を表示

**完了条件**：`vitest` 緑、ブラウザで実画面確認

---

## 5. Step E — 設計書反映と完了確認

### 5-1. API 設計書の更新

| ファイル | 追記内容 |
|---------|---------|
| `docs/api_design/Core_API.md` | §「Console 管理 API 群」の売上一覧の前後に「予約商品一覧」セクションを追加（GET /api/products/preorders） |
| `docs/api_design/Console_API.md` | §「売上・在庫 API」の直前または直後に「予約管理 API」セクションを追加（GET /api/preorders） |

### 5-2. DB 設計書

**変更なし**（既存カラムを利用）。`TBL_products.md` / `TBL_sales.md` には新規カラムなし。

### 5-3. 完了条件チェックリスト

- [ ] `mvn test`（Core）が緑
- [ ] `phpunit`（Console）が緑
- [ ] `vitest`（Console フロント）が緑
- [ ] `docker compose up` で Console を起動し、`/preorders` が表示される
- [ ] 売上画面の「予約を除外」「見込み表示」トグルが動作する
- [ ] `docs/api_design/Core_API.md` / `Console_API.md` に新設 API が反映済み
- [ ] 設計書 `phase16_ui_ux_improvement.md` の Step 2 ステータスを「✅ 実装完了（YYYY-MM-DD）」に更新

### 5-4. CLAUDE.md ルール遵守確認

- DB / API 設計書の同フェーズ内更新（CLAUDE.md「DB / API 設計書のメンテナンスルール」）
- テスト内のホスト名ハードコード禁止 → `config('services.amazia_core.base_url')` を `Http::fake()` でモック

---

## 6. 想定リスクと対策

| リスク | 対策 |
|-------|------|
| Core 側で全商品ループ × `PreorderStatusService#judge()` を回すとパフォーマンス劣化 | 商品件数が現状規模（数十〜数百）では許容範囲。将来 1000 件超になったら専用クエリに置き換える（Step 2 のスコープ外として記録） |
| `salesRepository.findByIsPreorderTrueAndSkuIdIn` 戻り値が大量で OOM | `-Xmx384m` 制約下で要注意。本フェーズ完了時のデータ規模を確認し、必要なら数量・金額を SUM するクエリ（`@Query` で集計）に切り替える判断材料を Step A 完了時にメモる |
| Vue 側で `sales.value` 全件をクライアント保持しているため大規模化で遅延 | 売上件数が増えた段階でサーバーサイドフィルタ + ページングへ移行。本ステップではクライアント側で完結（設計書 §2-2 スコープ外） |
| 「見込み表示」トグル状態がリロードで失われる | UX 上問題なし（既定 OFF が安全側）。永続化は要件外 |

---

## 7. 参考リンク

- [phase16_ui_ux_improvement.md（設計書）](../design/phase11_20/phase16_ui_ux_improvement.md)
- [phase14_5_preorder_status.md（PreorderStatus 仕様）](../design/phase11_20/phase14_5_preorder_status.md)
- [coding_guidelines.md](../coding_guidelines.md)
- [test_insights.md](../ai_context/test_insights.md)
- [operational_insights.md](../ai_context/operational_insights.md)
