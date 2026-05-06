# 035: Market 購入ボタンで sku_id=undefined になり「対象の商品が見つかりません」

## ステータス
✅ 解決済（2026-05-06）

## 発症箇所
- 画面: Amazia Market `ProductDetail`
- URL: `http://localhost:5173/checkout?sku_id=undefined&quantity=1`
- 関連 API: `GET /api/products/:id/market`（Core）

## 症状
ProductDetail 画面で色・サイズを選択し「購入する」ボタンを押すと、Checkout 画面で「対象の商品が見つかりません。」とエラー表示される。
URL を見ると `sku_id=undefined` となっており、SKU ID が URL に正しく載っていない。

## 根本原因
Core 側 `SkuDetail` DTO の getter 名と Market 側で参照していたフィールド名が不一致だった。

| 場所 | 定義／参照 |
|------|----------|
| Core `com.example.sku.dto.SkuDetail` | フィールド名 `skuId`、getter `getSkuId()` → Jackson シリアライズ後の JSON フィールド名は **`skuId`** |
| Market `ProductDetail.jsx` | `selectedSku.id` を参照 → 実際の JSON には `id` が無いため **`undefined`** |
| Market `Checkout.jsx` | 同様に `s.id` / `selectedSku.id` を参照（3 箇所） |

フェーズ14 Step B-2 で購入導線を新設した際、Core の SkuDetail DTO を確認せずに `id` を仮定して書いたことが直接原因。

## なぜ CI で検知できなかったか
- ProductDetail.jsx / Checkout.jsx の Vitest テストでは、テストデータを `{ id: 101, color: ..., ... }` で書いており、**実際の Core API レスポンス形式（`skuId`）と乖離したテストデータ**になっていた。
- そのためテスト上は購入ボタン押下→ `/checkout?sku_id=101` で遷移成功と判定されていたが、実際の Core 経由では `undefined` になっていた。
- API レスポンスのフィールド名と Market テストデータの整合性チェック（コントラクトテスト）が無かった。

## 修正内容
1. **Market 側のフィールド名を Core の実 JSON に合わせる**
   - `ProductDetail.jsx`: `selectedSku.id` → `selectedSku.skuId`
   - `Checkout.jsx`: 3 箇所 `s.id` / `selectedSku.id` → `skuId`
2. **テストデータを Core API 実 JSON 形式に合わせる**
   - `ProductDetail.test.jsx`: `id: 101` → `skuId: 101`
3. Vitest 49/49 グリーン確認

## 再発防止
| 観点 | 対策 |
|------|------|
| API レスポンス契約の確認 | 新規画面でフロントから API レスポンスを参照する際は、**まず Core 側の DTO（getter 名）を直接読んで JSON フィールド名を確定**してからフロントを書く。getter 命名から JSON フィールド名を外挿（推測）しない。 |
| テストデータの正本化 | フロント側ユニットテストのモックデータは、Core 側の実 DTO クラスに対応した JSON 形式に揃える。可能なら Core 側に「DTO のサンプル JSON を出力するテスト」を置き、それを Market 側のテストデータの参照元にする。 |
| 命名統一の検討（将来課題） | `SkuDetail.skuId` / `SkuDetail.skuCode` のように `sku` プレフィックスが冗長。Entity 内では `id` / `code` の方が自然だが、レスポンスの一意性の観点で skuId にしている可能性あり。レビュー時に「フィールド名は単純な `id` か `skuId` か」を方針として明記すると、フロントとの認識ズレが起きにくい。 |
| 体感バグの早期発見 | ローカル開発時、新画面実装後は **必ずブラウザで手動 E2E**（ボタンクリック → URL バー確認）を 1 周する。本件は手動で URL バーを見れば即座に `undefined` が分かった。 |

## 参考
- 本件で参照した Core ファイル: [SkuDetail.java](../../amazia-core/src/main/java/com/example/sku/dto/SkuDetail.java)
- 修正対象 Market ファイル: [ProductDetail.jsx](../../amazia-market/src/features/products/pages/ProductDetail.jsx) / [Checkout.jsx](../../amazia-market/src/features/checkout/pages/Checkout.jsx)

---

## 追記（2026-05-06）: 連鎖して発見した DTO 構造の乖離

`sku_id` を正しく URL に乗せる修正を入れた直後に、ブラウザで再度購入導線を試したところ **「対象の商品が見つかりません」** の表示が再発した。URL は `/checkout?sku_id=1&quantity=1` と正常で、コンソール警告も 4xx/5xx も出ていない状態だったため別バグと判明。

### 連鎖バグの根本原因
035 本編は「getter 名と JSON フィールド名の不一致」だったが、追記分は **「リスト API レスポンス DTO に期待していたフィールドが、そもそも DTO 定義として存在しない」** ケース。

| 期待 | 実際の DTO（`com.example.sku.dto.ProductMarketSummary`） |
|------|----------------------------------------------------|
| `p.skus[].skuId` で SKU を逆引きできる | フィールドは `productId / productName / description / minPrice / totalStock / mainImage` のみ。**`skus` は存在しない** |
| `target.product.id` でネスト参照できる | レスポンスは `target.productId` のフラット構造 |

旧 `Checkout.jsx` は `getMarketProducts()` 全件取得 → `list.find(p => p.skus?.some(s => s.skuId === skuId))` で SKU から逆引きする実装だったが、`p.skus` が常に `undefined` のため `find` が必ず `undefined` を返し「対象の商品が見つかりません」が必ず出る状態だった。

### 連鎖して気付かなかった理由
- 035 本編の修正で `sku_id=undefined` が `sku_id=1` に直り、**最初の症状は確かに直った**ように見えた
- しかし「全件取得して SKU 逆引き」という旧実装の根本的な破綻には Core DTO を直接読まないと気付けなかった
- 035 で「Core DTO を直接読む」教訓を抽出していたのに、Checkout.jsx 側の **リスト API レスポンス DTO（ProductMarketSummary）** までは確認していなかった

### 連鎖修正内容
- 「全件取得 → SKU 逆引き」をやめ、**ProductDetail から `product_id` も URL に渡す**設計に変更
  - `ProductDetail.jsx`: `/checkout?sku_id=...` → `/checkout?product_id=${id}&sku_id=...`
  - `Checkout.jsx`: `useEffect` 全面書き換え。`getMarketProduct(productId)` 1 回だけ呼んで対象 SKU を抽出
- N+1 問題（リスト取得→詳細取得の 2 回コール）も解消
- Vitest 49/49 グリーン確認

### 追加の再発防止観点
| 観点 | 対策 |
|------|------|
| **リスト API DTO の構造確認** | 詳細 API（`getMarketProduct`）の DTO だけでなく、**リスト API（`getMarketProducts` の `ProductMarketSummary`）** の DTO も実装着手前に確認する。リスト系は「軽量サマリ」のためフィールドが絞られていることが多く、詳細系と構造が大きく異なる。 |
| **「全件取得して逆引き」アンチパターン** | フロントで API を全件取得して JS でフィルタする実装は、レスポンス DTO の構造前提に依存しやすく壊れやすい。**識別子は URL や state で渡し、API 呼び出しは 1 回で完結させる**設計を優先する。 |
| **症状が似ていても根本原因は別の可能性** | 「対象の商品が見つかりません」が同じメッセージで再発したが、原因は **URL の sku_id（035）→ DTO 構造（追記分）** と全く異なっていた。1 回目の修正で症状が消えたかに見えても、別レイヤーで同じ症状を引き起こす機構が温存されている可能性を疑う。 |
