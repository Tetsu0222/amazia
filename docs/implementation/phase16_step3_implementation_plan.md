# フェーズ16 Step 3 実装計画（入荷登録画面の選択UI化と SKU管理画面からの機能移譲）

## 概要
- 対象設計書: [phase16_ui_ux_improvement.md](../design/phase11_20/phase16_ui_ux_improvement.md) Step 3
- 対象範囲: Amazia Console（Vue UI のみ）
- 段取り: **A（InboundCreate 改修）→ B（InboundList に Excel 一括入荷を統合）→ C（SkuList から入荷フォーム・Excelボタンを除去）→ D（設計書本体への Step 3 詳細追記と完了確認）** の 4 段階で実施
- 作成日: 2026-05-07
- 親フェーズ: phase16 Step 2（2026-05-07 完了）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| API / DB 変更 | **なし**。既存 `GET /api/products`・`GET /api/products/{id}/skus`・`POST /api/inbounds`・`POST /api/skus/stocks/import` をそのまま使う |
| ルート変更 | `/inbound/import` を新設し、既存 `/skus/stocks/import` は `/inbound/import` へリダイレクトor削除（後述 B-3）。`/skus/stocks` は SKU 在庫照会画面として残置（リダイレクト不要） |
| 環境変数 | 追加なし |
| 既存テスト | Console UI には vitest が存在しないため新規追加なし。Laravel feature テストへの影響もなし |
| 規模感 | Vue ページ 3 ファイル改修 + 1 ファイル新設、router/index.js 1 行追加 |
| コーディング規約 | [coding_guidelines.md](../coding_guidelines.md) 5.（フロント）に準拠 — API は `features/*/api/` 経由・コンポーネントに業務ロジックを書かない |

### 設計書からの「本ステップのスコープ外」確認

| 項目 | 取り扱い |
|------|---------|
| Core / Console の入荷 API スキーマ変更 | スコープ外（`POST /api/inbounds` は phase15 のまま） |
| 倉庫マスタ画面 | スコープ外（既定倉庫 id=1 の自動セット運用を継続） |
| `SkuStockList.vue`（旧 SKU在庫管理画面） | フェーズ15時点で `SkuList.vue` の在庫タブに集約済み・参照は残るが本ステップでは触らない |

---

## 1. Step A — InboundCreate.vue：商品マスタ→SKU 選択 UI 化

### 1-1. 現状の問題点

[InboundCreate.vue](../../amazia-console/resources/vue/src/features/inbound/pages/InboundCreate.vue) は商品 ID と SKU ID を**数値で直接入力**する作り。商品 ID を覚えている運用者はいない前提で、選択 UI に置き換える。

### 1-2. 変更内容

| 項目 | Before | After |
|------|--------|-------|
| 商品の指定 | `<a-input-number v-model="form.productId">` | `<a-select>` で商品マスタ一覧から選択（`getAdminProducts()` を流用） |
| SKU の指定 | `<a-input-number v-model="form.skuId">` | `<a-select>` で「商品を選択するまで disabled」→ 選択後に `getProductSkus(productId)` で読み込み |
| 商品変更時の挙動 | — | `selectedSkuId` をリセット |
| 必須バリデーション | 既存どおり全項目チェック | 同左（productId / skuId はセレクトの値で判定） |

API クライアントは `features/products/api/products.js` の `getAdminProducts` と `features/skus/api/skus.js` の `getProductSkus` を import する（既に `SkuList.vue` で動作実績あり）。

### 1-3. 青枠アラート文言の変更

設計書「機械的な注意事項の表示を変更」に対応。現状：

> 倉庫はバックエンドが既定値（id=1 'default'）を自動セットします（並行運用期）。

→ 運用フェーズ表現を外し、選択UI化を踏まえたシンプルな案内に置き換える：

> 商品とSKUを選択し、入荷数量と入荷日を入力してください。倉庫は自動でデフォルトが設定されます。

### 1-4. 既存ロジックで触らない箇所

- `registerInbound(payload)` 呼び出しの payload 構造（`productId` / `skuId` / `quantity` / `inboundedAt` / 任意 `supplierId`）
- エラーハンドリング（`status === 404 / 400 / 422` 分岐）
- 完了後の `/inbound` への戻り

---

## 2. Step B — InboundList.vue：Excel 一括入荷ボタンの追加と画面新設

### 2-1. InboundList.vue の改修

ヘッダー右側の `<template #extra>` に「Excel一括入荷」ボタンを追加する。

```html
<template #extra>
  <a-space>
    <a-button @click="goImport">Excel一括入荷</a-button>
    <a-button type="primary" @click="goCreate">入荷登録</a-button>
  </a-space>
</template>
```

`goImport` は `router.push('/inbound/import')` で新設画面へ遷移。

### 2-2. 新設ページ：InboundStockImport.vue

`amazia-console/resources/vue/src/features/inbound/pages/InboundStockImport.vue` を新設し、既存の [SkuStockImport.vue](../../amazia-console/resources/vue/src/features/skus/pages/SkuStockImport.vue) の内容を移植する。

- 移植元との差分：
  - `@back` を `/inbound` に変更
  - `sub-title` を「Excel ファイルをアップロードして SKU 単位で在庫を加算します」のまま流用
  - `importSkuStock(file)` API 関数はそのまま `features/skus/api/skus.js` の関数を import（API パスは Console `/api/skus/stocks/import` のまま）
- 同じ API を呼ぶだけのコピーなので、「2画面が同じ仕様で並行する」状態は作らない。Step C で `SkuStockImport.vue` 側のルートとボタン入口を撤去し、`InboundStockImport.vue` を唯一の窓口とする。

### 2-3. ルート追加

[router/index.js](../../amazia-console/resources/vue/src/router/index.js) に追加：

```js
import InboundStockImport from '../features/inbound/pages/InboundStockImport.vue';
// ...
{ path: '/inbound/import', component: InboundStockImport, meta: { requiresAuth: true } },
```

`/skus/stocks/import` は Step C で削除する。

---

## 3. Step C — SkuList.vue：入荷登録機能の撤去

### 3-1. テンプレート変更

| 要素 | 操作 |
|------|------|
| `<template #extra>` の「Excel一括入荷」ボタン | **削除**（Excel は入荷管理画面に移譲） |
| `<a-tab-pane key="stock">` の「入荷登録」フォーム | **削除**（`stockForm` / `stockReceiving` / `handleStockReceive` も削除） |
| `<a-tab-pane key="stock">` の「現在在庫」表示 | **残す**（参照用） |
| `<a-tab-pane key="stock">` の入荷履歴テーブル | **残す**（参照用） |

設計時のユーザー判断：「現在在庫＋履歴も残す」（登録機能だけ削除し、参照は SKU 管理画面でも可）

### 3-2. script 変更

- import から `receiveSkuStock` を除去
- `stockForm` / `stockFormRef` / `stockReceiving` / `stockRules` / `handleStockReceive` の宣言と利用を全削除
- `fetchStockHistory` / `fetchStock` は残置（参照用）

### 3-3. SkuStockList / SkuStockImport の扱い

- [SkuStockList.vue](../../amazia-console/resources/vue/src/features/skus/pages/SkuStockList.vue)：旧画面・現状ルート `/skus/stocks` で残置。設計書 Step 3 のスコープ外（フェーズ15で SkuList に統合済み・参照のみ）。**本ステップでは触らない**。
- [SkuStockImport.vue](../../amazia-console/resources/vue/src/features/skus/pages/SkuStockImport.vue)：Step B で `InboundStockImport.vue` に置換。本ファイル本体と `/skus/stocks/import` ルートを**削除**する（重複保持しない）。
  - `SkuStockList.vue` 側の「Excel一括入荷」ボタンの遷移先 `/skus/stocks/import` も `/inbound/import` に書き換える（旧画面も新窓口に整合させる）。

---

## 4. Step D — 設計書反映と完了確認

### 4-1. 設計書本体の更新

[phase16_ui_ux_improvement.md](../design/phase11_20/phase16_ui_ux_improvement.md) Step 3 セクションに以下を追記：

- 「入荷登録画面の選択UI化」「青枠アラート文言の変更」「Excel 一括入荷の入荷管理画面への統合」「SKU管理画面からの入荷フォーム/Excelボタンの撤去」の 4 項目について、設計方針・対象ファイル・スコープ外項目を明記
- ステータス行を「Step 3 実装完了（2026-05-07）」に更新

### 4-2. API / DB 設計書

- 変更なし（`docs/api_design/Console_API.md` 触らず・`docs/database_design/` 触らず）

### 4-3. 動作確認

- **手動 E2E**：Console を起動して以下を確認
  1. `/inbound/create` で商品セレクトを開き、SKU セレクトが連動して切り替わる
  2. 商品・SKU・数量・入荷日を選んで送信 → `/inbound` に戻り一覧に出る
  3. `/inbound` の「Excel一括入荷」ボタンから `/inbound/import` に遷移し、Excel アップロード画面が表示される
  4. `/skus` の在庫タブに「入荷登録」フォームが**ない**こと、「Excel一括入荷」ボタンが**ない**こと、現在在庫と履歴は表示されることを確認
- **既存テスト**：触ったコードは Vue UI のみ。Laravel feature テスト・Spring テストは無関係のため実行省略

### 4-4. CLAUDE.md 「フェーズ完了の定義」チェック

- [x] DB 変更なし → `docs/database_design/` 更新不要
- [x] API 変更なし → `docs/api_design/` 更新不要
- [x] 環境変数追加なし → `docker-compose.yml` / `phpunit.xml` 更新不要
- [x] 設計書本体（phase16_ui_ux_improvement.md）に Step 3 詳細を追記

---

## 5. 想定リスクと回避策

| リスク | 回避策 |
|--------|-------|
| `/skus/stocks/import` への直リンクが他画面から残っていて 404 になる | 撤去前に grep で全参照箇所を確認し、`SkuStockList.vue` の遷移先も同時に書き換える（Step C-3） |
| 商品数が多い場合に `<a-select>` のドロップダウンが重くなる | `getAdminProducts` は既存画面（SkuList / SkuStockList）と同じ呼び出し。同じ規模で動いているため許容範囲とみなす |
| Excel 一括入荷の API パス（`/api/skus/stocks/import`）と画面導線（`/inbound/import`）の不一致 | Console の URL（画面）と API パスは独立。API パスは phase15 互換のため変更しない（Pass-through も既存） |

---

## 6. 完了条件

- 設計書本体の Step 3 ステータスが「実装完了（2026-05-07）」になっていること
- 上記 4-3 の手動 E2E が通ること
- 不要となった `SkuStockImport.vue` と `/skus/stocks/import` ルートが削除されていること
