# 039: Market Checkout が preorder モード未対応で予約フローでも在庫数バリデーションが効いてしまう

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- 画面: Market 商品詳細 (`/products/:id`) → 「予約する」ボタン → Checkout (`/checkout?...&preorder=1`)
- 影響範囲: フェーズ14.5 C-4 で追加した予約フロー（PRE_ORDER / BACK_ORDER ステータスからの遷移）

## 症状
予約受付中（PRE_ORDER）の商品詳細で「予約する」ボタンを押すと、checkout に `&preorder=1` 付きで遷移するものの、画面表示は通常購入時と全く同じ：
- タイトル: 「ご注文内容の確認」（予約用文言になっていない）
- ボタン: 「注文を確定する」（予約用文言になっていない）
- 在庫 0 の SKU を選んだ場合に「在庫数（0）を超えています。」警告が出てボタンが非活性になる
- 結果: 予約しようとしても確定できない

## 根本原因
[`amazia-market/src/features/checkout/pages/Checkout.jsx`](../../amazia-market/src/features/checkout/pages/Checkout.jsx) は C-4 で予約フロー導線を追加するより前に作られており、以下が漏れていた：

1. URL クエリ `?preorder=1` を読み取っていなかった（常に `preorder: false` で `confirmOrder` を呼んでいた）
2. 在庫数バリデーション (`stockShortage`) が予約モードでも有効だった
3. タイトル / 数量ラベル / ボタン文言の予約モード分岐がなかった

ProductDetail 側 (C-4) では `&preorder=1` を付けて遷移する実装が入っており、Core `OrderConfirmationService` も `request.isPreorder()` を見て在庫チェックと在庫減算をスキップする実装になっていたが、その間にある Market Checkout 画面だけが取り残されていた。

## なぜ CI で検知できなかったか
- C-4 完了時点で Checkout のテスト (`Checkout.test.jsx`) が存在していなかった
- ProductDetail のテスト (`ProductDetail.test.jsx`) は「予約ボタン押下→/checkout 画面が描画される」までしか確認しておらず、その先の Checkout 画面の挙動は対象外
- Core 側 `OrderConfirmationService` の予約フロー単体テスト (`OrderConfirmationServiceTest`) は `preorder=true` でリクエストすれば在庫不足でも 201 が返ることを検証しているが、UI からその `preorder=true` が確実に組み立てられているかは検証対象外
- 結果として「画面間の連携」がテストの隙間に落ちた

035（SkuDetail の getter / フロント JSON フィールド名乖離）と同型の「画面間契約のテスト不在」パターン。

## 修正内容
[`Checkout.jsx`](../../amazia-market/src/features/checkout/pages/Checkout.jsx) に予約モード分岐を追加：

| 観点 | 通常購入 | 予約 |
|------|---------|------|
| クエリ判定 | デフォルト | `?preorder=1` のとき予約モード |
| タイトル | ご注文内容の確認 | ご予約内容の確認 |
| 数量ラベル | 数量 | 予約数 |
| 数量上限 | `selectedSku.stock` | 制限なし |
| 在庫不足警告 | 表示 | 非表示 |
| ボタン文言 | 注文を確定する | 予約を確定する |
| `confirmOrder.preorder` | `false` | `true` |

新規テスト [`Checkout.test.jsx`](../../amazia-market/src/features/checkout/pages/Checkout.test.jsx) で 7 ケースをカバー：
- 通常モード: タイトル / 在庫超過警告とボタン非活性 / `preorder:false` 送信
- 予約モード: タイトル / 在庫切れ SKU でも警告なし・ボタン活性 / 数量を在庫超で入力しても警告なし / `preorder:true` 送信

## 再発防止

| 観点 | 対策 |
|------|------|
| 画面間のデータ受け渡し契約 | URL クエリ・ナビゲーション state を経由する画面遷移は、遷移先の画面でも単体テストで「クエリの解釈」「state の解釈」を検証する。035 と同型のパターン |
| Checkout のテスト整備 | これまで Checkout はテスト未整備だった。今回の追加で通常購入モードもカバーされたため、今後は通常購入の回帰検知も期待できる |
| 設計書の遷移定義の徹底 | 設計書 phase14_5_preorder_status §4-2 / §4-3 で「予約フローの checkout は `&preorder=1`」と明記する文化を維持。実装はそれを文字通り実現するだけにする |
| テスト観点 | `test_insights.md` の「画面間契約テスト」項目（035 起因）に「URL クエリ経由のモード切替」を追記する余地あり。次フェーズの完了条件に含める |
