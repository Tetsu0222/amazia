# 040: SKU 価格未登録の商品が Market 一覧に出てしまい注文時に 400 を返す

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- 画面: Market 商品詳細 (`/products/:id`) → 「予約する」ボタン → Checkout (`/checkout?...&preorder=1`) → 確定
- エンドポイント: Console `POST /api/customer/orders/confirm` → Core `POST /api/customer/orders/confirm`
- レスポンス: 400 Bad Request（`sku price not registered`）

## 症状
Console で「りんご」のように **SKU 価格を登録しないまま** 商品を作成すると、フェーズ14.5 C-4 の改修により Market 一覧に表示されるようになっていた（C-4 で「在庫 0 でも SOLD_OUT として表示」「予約 / 在庫切れ予約も表示」を有効化したため）。
ユーザーがその商品の予約ボタンを押して Checkout 画面まで進めても、Core `OrderConfirmationService` が「SKU 価格未登録」を検出して 400 を返し、確定できない。

## 根本原因
本来 EC では **「SKU・価格・在庫が揃って初めて出品可能」** が業界標準（Amazon / 楽天 / Yahoo! / ZOZO 等）。
Amazia の現状は：

1. Console 商品登録 UI に「公開するには SKU 価格を登録すべし」というワークフローがない（商品登録と SKU 価格登録が完全に分離）
2. 設計書（phase10）で「価格・在庫は SKU 側で管理」と決めたが、**商品の販売可否判定**を Service 層に組み込んでいなかった
3. C-4 までの Market 集約 API は「在庫 0 商品の除外」を販売可否のプロキシとして使っていたが、C-4 でその除外を撤廃したため SKU 価格未登録の商品まで出るようになった
4. Core `OrderConfirmationService.java:119` は価格未登録で 400 を返す = Service 層では正しく弾いているが、UI までその情報が伝わらず**注文画面まで進ませてから初めて拒否**するため UX が悪い

C-4 の副作用として顕在化したが、根本は phase10〜14 を通じて「販売可否」概念が抜け落ちていたこと。

## なぜ CI で検知できなかったか
- 既存の `SkuAggregateControllerTest` の `setUp` は SKU 価格を必ず登録していた（=幸せパスのみ検証）
- 「価格未登録 SKU だけの商品」を Market から除外するというサーバ側の振る舞いは、設計書にも明記されておらずテスト観点として上がっていなかった
- フロント側 (`ProductList.test.jsx` / `ProductDetail.test.jsx`) も `minPrice: 1000` 等を渡して描画確認するのみで、「Console 経由で価格未登録のまま登録された商品」のシナリオは想定外
- 035・039 と同じく「画面間 / Service 間の契約に対するテストの欠落」

## 修正内容
[`ListProductMarketService`](../../amazia-core/src/main/java/com/example/sku/service/ListProductMarketService.java) を改修：

| API | 変更前 | 変更後 |
|-----|-------|-------|
| `GET /api/products/market` | 公開期間内 + SKU あり + 在庫 > 0 | C-4 で在庫 0 も表示 → さらに **`minPrice == null` を除外** |
| `GET /api/products/{id}/market` | 商品が存在すれば 200 | **SKU 0 件 / 全 SKU で価格未登録 のとき 404** |

新規テスト [`SkuAggregateControllerTest`](../../amazia-core/src/test/java/com/example/sku/SkuAggregateControllerTest.java) で 2 ケース追加：
- `価格未登録の商品は一覧から除外されること`
- `価格未登録の商品は詳細でも404が返ること`

UI 側の防御も既に実装済み：
- ProductList: `minPrice == null` のとき価格行ごと非表示（フェーズ14.5 後半で対応済）
- ProductDetail: `selectedSku.price == null` のとき ¥ 表示を非表示
- Checkout: `totalAmount == null` のとき「価格未定」表示

これで「**サーバ側で除外** + **UI 側でも未定表示**」の二重防御。

## 業界標準との比較

| サイト | SKU 未登録 / 価格未定の扱い |
|--------|-----------------------------|
| Amazon | 商品ページは出すが「現在お取り扱いできません」表示で購入不可。価格未定の予約は最低保証価格を必ず提示 |
| 楽天 / Yahoo! | 価格・在庫は出品時必須（バリデーションで弾く） |
| ZOZOTOWN | 予約商品も価格は確定提示が原則 |
| Apple ストア | 「coming soon」枠は価格非表示 OK だがカートには入らない |

→ 本対応は **「価格未定なら告知も出さない」** スタンス。Amazia はまだ「告知ページ」の概念がないため最も保守的な選択を取った。

## 再発防止

| 観点 | 対策 |
|------|------|
| 「販売可否」概念の Service 層への組み込み | 今回 `ListProductMarketService` で `minPrice != null` を販売可否のプロキシとして利用。将来「販売準備中」告知を別 UI で出したいなら、明示的な販売可否フラグ（例: `Product.salesReadiness`）を導入する |
| Console 側の出品ワークフロー強化 | 今回スコープ外。別フェーズで「商品登録 → SKU 価格登録 → 公開」を 1 つのウィザードにまとめるか、`statusCode='ON_SALE'` への遷移条件に SKU 価格登録を要求する仕組みを設計 |
| テスト観点 | `test_insights.md` に「Service 層の販売可否ロジック」「Console 出品 → Market 表示 までの End-to-End 契約」観点を追記する余地。次フェーズの完了条件で検討 |
| 価格未定で予約だけ受け付ける機能 | 業界標準では「最低保証価格の提示」が必要。Amazia で要件として上がった時点で別途設計（amount NULL 許容 + 出荷時確定価格反映 など。スキーマ変更を伴う） |

## 別フェーズに送る要件

本対応では **「Market 露出を防ぐ」** のみを実装した。以下は別フェーズで扱う：

1. Console 商品登録 UI で「公開するなら SKU 価格を登録しろ」ヘルプ / バリデーション
2. 「販売準備中」告知ページ（Apple ストア型）
3. 価格未定でも予約だけ受け付ける機能（`amount` NULL 許容 + phase15 確定価格反映）
