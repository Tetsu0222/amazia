# 041: 公開期間判定が秒単位と JST 0:00 の二重基準で食い違い、PRE_ORDER 商品の注文確定が 400

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- 画面: Market 商品詳細 (`/products/9`) → 「予約する」 → Checkout (`?preorder=1`) → 確定
- エンドポイント: `POST /api/customer/orders/confirm`
- レスポンス: 400 Bad Request（`product is not published`）

## 症状
Console で「りんご」を `publish_start = 2026-05-07 17:13:02 / release_date = 2026-07-31 / accept_preorder = true` で登録すると、Market 一覧では `PRE_ORDER`（予約受付中）として表示され、ProductDetail の「予約する」ボタンも活性化される。
ところが Checkout で「予約を確定する」を押すと Core が 400 を返す（`product is not published`）。
ユーザー視点では「予約できる商品として表示されているのに予約できない」という不整合。

## 根本原因
Amazia の公開期間判定が **二重基準** になっていた：

| 関数 | 比較単位 | 用途 |
|------|---------|------|
| `Product#isPublished()` | `LocalDateTime.now()`（秒単位） | `OrderConfirmationService` / `ListProductService` |
| `PreorderStatusService#judge()` | `LocalDate.now(JST)`（日付単位） | C-2 で導入。Market 集約 API / 予約ステータス判定 |

設計書 [phase14_5_preorder_status.md](../design/phase11_20/phase14_5_preorder_status.md) §2-2 は **「JST 0:00 基準」** を明記しているが、phase11 以前から存在する `Product#isPublished()` はそのまま秒単位 `LocalDateTime` 比較を使い続けていた。
C-2 で `PreorderStatusService` を JST 0:00 基準で実装した結果、同じ `publish_start = 2026-05-07 17:13` の商品について：

- `PreorderStatusService`: `today=2026-05-07 == publishStart.toLocalDate()=2026-05-07` → `today.isBefore(...)` は false → **公開済扱い** → PRE_ORDER と判定
- `Product#isPublished()`: `now=2026-05-07 09:30 < 2026-05-07 17:13` → **非公開扱い** → 注文確定で 400

→ Market 表示と注文確定で挙動が分岐し、UX が破綻。

## なぜ CI で検知できなかったか
- `PreorderStatusServiceTest` は固定 Clock + JST 0:00 基準でロジックを網羅していた（13 ケース）
- `OrderConfirmationServiceTest` は publish_start を「過去」または NULL でしかセットしておらず、**「公開日が今日の未来時刻」のケースを境界値として持っていなかった**
- `SkuAggregateControllerTest` も同様で、C-4 で追加した「NOT_PUBLIC 除外」テストは publish_start を未来「日」でセットしていたため `Product#isPublished()` と `PreorderStatusService` の両方で false になり差分が出なかった
- 結果として「同じ日の時刻違い」という現実シナリオがテストの隙間に落ちた

## 修正内容
公開判定を **`PreorderStatusService` に統一**：

1. [`PreorderStatusService.java`](../../amazia-core/src/main/java/com/example/product/service/PreorderStatusService.java) に `isPublished(Product)` を追加（JST 0:00 基準）
2. [`OrderConfirmationService.java`](../../amazia-core/src/main/java/com/example/order/service/OrderConfirmationService.java) を `Product#isPublished()` → `preorderStatusService.isPublished(product)` に切替
3. [`ListProductService.java`](../../amazia-core/src/main/java/com/example/product/service/ListProductService.java) を同様に切替
4. [`Product.java`](../../amazia-core/src/main/java/com/example/product/entity/Product.java) の `isPublished()` メソッドを削除（呼び出し元なし）
5. amazia-core `mvn test` 253/253 グリーン

これで Market 集約・予約ステータス API・注文確定・公開商品一覧 すべてが **JST 0:00 基準** の単一窓口（`PreorderStatusService`）を経由するようになった。

## 再発防止

| 観点 | 対策 |
|------|------|
| 単一の判定窓口 | 「公開」「予約可」など概念ごとに 1 つの Service に判定ロジックを集約。Entity に判定メソッドを置かない（Spring Bean ではないため Clock 注入できず時刻固定テストが書けない） |
| 境界値の取り方 | 「今日の時刻違い」を境界値テストの観点に必ず含める。日付単位と秒単位の混在は同日の時刻違いで初めて顕在化する |
| 設計書の判定基準を実装に明記 | 設計書「JST 0:00 基準」は明文化されていたが、コード側にコメントが不足していた。今回の修正で Service にコメントを追加 |
| 035・039・040 と同型の「画面間 / Service 間契約のテスト不在」 | C-4 までは Market 表示までしかテストしておらず、Market → Checkout → Core までの貫通テストがなかった。フェーズ完了条件に「実機ブラウザでの貫通確認」を残しておく重要性を再確認 |

## 関連
- 027: H2 / 本番 MySQL の DDL 互換問題
- 035: SkuDetail getter とフロント参照の乖離（画面間契約のテスト不在）
- 038: products.price/stock の NOT NULL 残存（H2 / 本番 MySQL のスキーマ乖離）
- 039: Market Checkout の preorder モード未対応（画面間契約のテスト不在）
- 040: SKU 価格未登録の商品が Market に出る（業界標準と Service 層の販売可否概念の欠落）

C-2 〜 C-4 を通じて「予約フロー」を導入する過程で、既存システムの**整合性ホール**が次々に顕在化した。これは設計書を読み込んで二重基準を事前に検知できなかった結果でもある。
