# 043: Market 購入履歴の配送状況が Console の更新を反映しない

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- 画面：Amazia Market 購入履歴（`localhost:5173/orders` / `market.purchase_history.list`）
- API：`GET /api/customer/orders`
- Service：`com.example.order.service.GetMyPurchaseHistoryService`

## 症状
Amazia Console「配送管理」画面で配送ステータスを「配送済（SHIPPED）」に更新した sales が、Market 購入履歴の「配送状況」列では「配送準備中」のまま表示される。配送予定日／発送日／追跡番号など `deliveries` 由来の他カラムは正しく反映されているのに、ステータスチップだけ取り残される現象。

## 根本原因
配送ステータスを保持するカラムが `sales.shipping_status_id` と `deliveries.shipping_status_id` の **2 か所に存在**しており、運用フローで以下のように **書き手と読み手が別テーブルを見ていた**。

- 書き手（Console → Core）：`PATCH /api/deliveries/{id}/status` → `DeliveryStatusTransitionService#transition()` は `deliveries.shipping_status_id` のみを更新する。`sales` 側は触らない。
- 読み手（Market）：`GetMyPurchaseHistoryService#list()` が `s.getShippingStatusId()`（= `sales.shipping_status_id`）を見て `shipping_statuses.code` を引いていた。

`sales.shipping_status_id` は注文確定時に PENDING（=1）で初期化されたあと誰も更新しないため、Console で何度ステータス遷移させても Market 側の表示は永続的に「配送準備中」になる。

設計意図としては deliveries が「配送状態の真実」であり、sales 側の同フィールドはフェーズ15 r5 で deliveries が分離されたあと旧データ互換のために残された残骸（DB 設計書 `TBL_sales.md` / `TBL_deliveries.md` 参照）。読み出し時の参照先を切り替えていなかったのが直接原因。

## なぜ CI で検知できなかったか
`GetMyPurchaseHistoryServiceTest` は **注文確定直後の sales を `service.list()` で取得して PENDING であることを検証**する単体テストしか持たず、Console 側の状態遷移を経由したあとの読み出しを検証していなかった。Console の `DeliveryProxyTest` も Core への HTTP 中継のみ検証する範囲で、Market API への波及を見るクロスサービスのテストが存在しなかった。

`shipping_status_id` を 2 系統に分割した時点で「読み出し時にどちらを正とするか」を契約として明文化していなかったのが構造的原因で、テスト不足はその表層。

## 修正内容
1. **Core: `GetMyPurchaseHistoryService#list` の参照を deliveries 優先に変更**
   - `delivery` が存在する sales は `delivery.getShippingStatusId()` を `shipping_statuses` 引きのキーとし、レスポンス DTO の `shippingStatusCode` に詰める。
   - `delivery` が無い旧 sales（フェーズ15 以前）のみ `sales.getShippingStatusId()` にフォールバック。
2. **API 設計書 `Core_API.md` 更新**
   - `GET /api/customer/orders` の仕様欄に「`shippingStatusCode` は deliveries を真とする／旧 sales のみフォールバック」の旨を追記。

## 再発防止
| 観点 | 対策 |
|------|------|
| 二重ソース問題の再発防止 | `sales.shipping_status_id` は実質「初期値専用」になっているため、将来フェーズで「読まない・書かない」を明文化する（Service 層から参照を全廃するか、カラム自体を廃止）。本トラブルでは読み出し側のみ修正して暫定収束。 |
| クロスサービス回帰テスト | Console の状態遷移 API → Market 購入履歴 API の往復を検証する結合テスト観点を `test_insights.md` に追加（次フェーズで実装）。 |
| 設計書での真実の所在の明示 | `TBL_sales.md` / `TBL_deliveries.md` の shipping_status_id 行に「真の所在は deliveries」を明記する追記が望ましい（本対応スコープ外）。 |
