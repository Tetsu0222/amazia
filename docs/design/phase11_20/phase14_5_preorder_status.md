# フェーズ14.5：予約ステータス判定 API + phase15 連携整理

## ステータス
🟡 進行中（Step C-1 完了 / 2026-05-06）

## 背景：phase14 r4 からの分離
phase14 r4 実装で Step 0 / A / B-1 〜 B-6（B-5 は B-5-1〜B-5-8 に細分化）が完了した時点で、残スコープ（B-7：予約ステータス判定 API、B-8：phase15 r5 要請整理）を本フェーズに **分離** した。

判断の経緯と意図は次の通り：

- 個人開発・短期スパン（4〜5 日）のポートフォリオ性格に対して、phase14 r4 の全範囲を「設計書通り」消化すると過剰スコープになる
- B-7 は **Product Entity の拡張カラム不足**（release_date / preorder_start_date / accept_preorder / accept_backorder 未実装）が判明し、Console 商品登録 UI まで含めると独立フェーズ規模
- B-8 は phase15 自体の改訂（r4 → r5）と一体で扱うのが筋で、phase14 単独完了に必須ではない
- 「設計書通り全部やる」より「動く範囲で線を引いて締める」方がプロジェクト判断として健全

詳細は [operational_insights.md §スコープ撤退の判断ログ](../../ai_context/operational_insights.md) を参照。

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 2026-05-06 | phase14 r4 から B-7 / B-8 を分離する形で新規作成。本書の判定ロジック・Enum 定義・優先順位は phase14 r4 §予約機能 / §技術検討事項 / §🔧追加検討点1 をそのまま継承する |
| r1 | 2026-05-06 | Step C-1 完了。実装計画書 [phase14_5_implementation_plan.md](../../implementation/phase14_5_implementation_plan.md) を新規作成。`products` テーブルに 4 カラム追加（schema.sql / Product Entity / TBL_products.md / ER_diagram.md 反映）。`mvn test` 234/234 グリーン。公開日 NULL は phase14 既存挙動（NULL = 常時公開）に揃える方針を確定 |

---

## 1. 範囲

| 区分 | 内容 |
|------|------|
| Step C | **予約ステータス判定 API**（旧 phase14 B-7） |
| Step D | **phase15 r5 への要請整理**（旧 phase14 B-8） |

phase14 r4 で扱った購入確定 / 返品ワークフロー / 売上管理 / 操作履歴は **phase14 で完了済み** のため本書では再記述しない。

---

## 2. Step C：予約ステータス判定 API

### 2-1. ステータス Enum（phase14 r4 §予約機能 を継承）

| ステータス | 説明 | Market 表示 |
|-----------|------|-------------|
| `NOT_PUBLIC` | 公開前（公開日未到達） | 非表示 |
| `PRE_ORDER_NOT_STARTED` | 公開済・予約開始前 | 「予約開始前」 |
| `PRE_ORDER` | 予約受付中（公開日 ≤ 今日 < 発売日） | 「予約受付中」＋発売日表示 |
| `ON_SALE` | 発売済・在庫あり | 通常販売 |
| `BACK_ORDER` | 発売済・在庫切れ・予約受付可 | 「再入荷予約受付中」 |
| `SOLD_OUT` | 完売（予約不可） | 「完売」（購入ボタン非表示） |

### 2-2. 判定の優先順位（phase14 r4 §🔧追加検討点1 を継承）

上から順に適用：
1. **公開前（公開日 > 今日）** → `NOT_PUBLIC`
2. **予約開始日が設定されており、今日 < 予約開始日** → `PRE_ORDER_NOT_STARTED`
3. **公開済 & 発売日前（公開日 ≤ 今日 < 発売日）** → `PRE_ORDER`
4. **発売日以降（今日 ≥ 発売日）**
   - 在庫あり → `ON_SALE`
   - 在庫なし & `accept_backorder=true` → `BACK_ORDER`
   - 在庫なし & `accept_backorder=false` → `SOLD_OUT`

「在庫」は **`product_sku_stocks.quantity` を SKU SUM で集計**：
```
商品全体の在庫 = SUM(product_sku_stocks.quantity)
              WHERE sku_id IN (SELECT id FROM product_skus WHERE product_id = ?)
```

「今日」「公開日」「発売日」の判定は **JST 0:00 基準**（サマータイム・海外展開はスコープ外）。

### 2-3. Product Entity 拡張（要追加カラム）

phase14 r4 時点で **Product Entity に下記カラムが不足** していたことが本フェーズ分離の最大要因。本書で追加する：

| カラム | 型 | NULL | 既定 | 用途 |
|--------|-----|-----|-----|------|
| `release_date` | DATE | YES | NULL | 発売日。NULL のときは「発売日未設定 = 公開即発売 = `ON_SALE` 起点」とみなす |
| `preorder_start_date` | DATE | YES | NULL | 予約開始日。NULL のときは「予約開始日設定なし = 公開と同時に予約可」 |
| `accept_preorder` | BOOLEAN | NO | FALSE | 予約購入を受け付けるか |
| `accept_backorder` | BOOLEAN | NO | FALSE | 在庫切れ時に予約継続を受け付けるか |

#### 既存データ（phase14 完了時点）への配慮
- 現存 products は `release_date=NULL` / `preorder_start_date=NULL` / `accept_preorder=FALSE` / `accept_backorder=FALSE` を許容
- この既定値で判定すると **公開期間内 + 在庫あり → `ON_SALE`、公開期間内 + 在庫なし → `SOLD_OUT`** になる（phase14 r4 完了時点の挙動と整合）
- `release_date` / `preorder_start_date` / `accept_preorder` / `accept_backorder` を画面から登録できるよう Console 商品 UI を拡張するのは Step C-3

### 2-4. 実装サブステップ

| Sub | 内容 | 主な実装物 |
|-----|------|-----------|
| **C-1** | schema.sql / Product Entity / Repository に 4 カラム追加 | schema.sql の冪等 ALTER、Product.java フィールド追加、test-data.sql 既存行への CURRENT/NULL 補完 |
| **C-2** | `PreorderStatusService` 新設 + JUnit | `com.example.product.service.PreorderStatusService.judge(productId)` が Enum を返す。優先順位ルールを単体テストで網羅（NOT_PUBLIC / PRE_ORDER_NOT_STARTED / PRE_ORDER / ON_SALE / BACK_ORDER / SOLD_OUT の境界値） |
| **C-3** | `GET /api/products/:id/preorder-status` Controller + Console 商品登録 UI 改修 | Core Controller、Console Product UI に release_date / preorder_start_date / accept_preorder / accept_backorder 入力欄追加、Vue 側で 4 カラム送受信 |
| **C-4** | Market `ProductDetail.jsx` / `ProductMarketList` に表示分岐 + Vitest | ステータスに応じてラベル・購入ボタン・予約ボタンを切り替え |

### 2-5. テスト観点（境界値）

- 公開日 = 今日 0:00 ちょうど → `PRE_ORDER` または `ON_SALE`（4 のルートに進む）
- 予約開始日 = 今日 0:00 ちょうど → `PRE_ORDER`
- 発売日 = 今日 0:00 ちょうど → `ON_SALE`（在庫あり）
- 公開日 NULL → 「公開日未設定」を許容するか、phase14 既存挙動（NULL = 常時公開）に揃えるか **着手時に再判断**
- `release_date` NULL → ON_SALE 起点で扱う（2-3 注記参照）

---

## 3. Step D：phase15 r5 への要請整理

### 3-1. phase14 r4 から積み残した phase15 への要請

phase14 r4 設計書に「phase15 で実装予定」「phase15 r5 で対応」と記述された項目を **phase15 r5 改訂時の要件リスト** として整理する。

| 項目 | phase14 r4 内の記述 | phase15 で必要な対応 |
|------|---------------------|---------------------|
| `shipping_methods` マスタ作成 | sales.shipping_method_id の FK は phase15 で shipping_methods 作成後に追加（schema.sql 注記） | phase15 r5 で `shipping_methods` テーブル新設、INSERT IGNORE で home_delivery / konbini_pickup / dropoff を投入 |
| `DeliveryCreationService.createForSales(salesId)` | 注文確定フロー擬似コード Step 7 で「phase15 と協調」 | phase15 r5 で実装し、phase14 r4 OrderConfirmationService の末尾でフックとして呼び出す |
| 出荷時の予約購入 SKU 在庫減算 | 「`is_preorder=true` のとき出荷時減算」（r4 §予約購入の在庫減算タイミング） | phase15 r5 `DeliveryStatusTransitionService` の `PENDING→SHIPPED` 遷移で実装 |
| 出荷時在庫不足の挙動 | 「例外＋PENDING 維持」（S14-1 / r4） | phase15 r5 で同方針を踏襲 |
| `RegisterInboundService` の改名・統合 | 既存 `ReceiveProductSkuStockService` を流用、または phase15 で改名 | phase15 r5 で命名整理 |
| 配送ステータス CANCELED / DELIVERY_FAILED / RESCHEDULED | マスタは投入済み、機能対応は将来 phase21（仮称） | phase15 r5 ではスコープ外確認のみ |

### 3-2. アクション

- phase15 r5 改訂作業に着手する際は本表を入口として要件を取り込む
- phase15 r5 完了時に本書 §3 を「✅ 反映済み」として閉じる

---

## 4. 完了条件

| 区分 | 条件 |
|------|------|
| Step C | 予約ステータス判定 API が `mvn test` / `phpunit` / `vitest` 全層グリーンで動作。Console 商品登録 UI から 4 カラムを登録でき、Market で 6 種類のステータスが表示分岐される |
| Step D | phase15 r5 設計書に本書 §3-1 の要件が取り込まれ、対応有無が表で確認できる |

---

## 5. 参考リンク

- 親フェーズ設計書: [phase14_shipping.md](phase14_shipping.md)（r4 / 2026-05-06 完了）
- phase14 実装計画: [phase14_implementation_plan.md](../../implementation/phase14_implementation_plan.md)
- 関連フェーズ: [phase15_delivery_management.md](phase15_delivery_management.md)（r4 / r5 改訂は本書 §3 と一体で実施）
- スコープ撤退の判断ログ: [operational_insights.md](../../ai_context/operational_insights.md)
- AIコンテキスト（テスト観点）: [test_insights.md](../../ai_context/test_insights.md)
- コーディング規約: [coding_guidelines.md](../../coding_guidelines.md)
- プロジェクトAIコンテキスト: [CLAUDE.md](../../../CLAUDE.md)
