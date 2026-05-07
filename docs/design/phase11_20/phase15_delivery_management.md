# フェーズ15：配送管理（改訂版 r5）

## ステータス
✅ 完了（2026-05-07）

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 初版日不明（git 履歴未取得） | 初稿（フェーズ15配送管理の基本設計） |
| r1 | 2026-05-06 | レビューコメント R-1 〜 R-12 を反映。phase14_shipping.md との整合、tracking_code の責務分離、入荷管理（inbounds）の DB 設計追加、配送ステータスの統一、operation_logs 反映、異常系テスト追加など。 |
| r2 | 2026-05-06 | 再レビューコメント RR-1 〜 RR-10 を反映。ステータス遷移のスコープ宣言、配送予定日の operation_logs 記録、`sales:deliveries` の多重度確定、`deliveries` 生成タイミング確定、在庫テーブル仕様の明示など。 |
| r3 | 2026-05-06 | 再々レビューコメント RRR-1 〜 RRR-11 を反映。`inventories` を**並行運用**に格下げ（`products.stock` 廃止は phase14 r2 へ切り出し）、注文確定フローのガード条件明示、ダミー倉庫導入、入荷再計算 FIFO、reason_code プレフィックス、悲観ロックなど。 |
| r4 | 2026-05-06 | 再々々レビューコメント RRRR-1 〜 RRRR-9 を反映。並行運用マイグレーション仕様の追記、販売側 `inventories` 減算フックの追加（整合性担保）、`shipping_method_id` 調達経路の確定、入荷再計算時の在庫読取元を `inventories` に固定、Console 入荷 UI の倉庫選択非表示方針、テストの config 経由化など。 |
| r5 | 2026-05-07 | [phase14_5_preorder_status.md](phase14_5_preorder_status.md) §3-1 から要請された 6 項目を正式に取り込み（P5-1 〜 P5-6）。`shipping_methods` の INSERT IGNORE 投入仕様、`DeliveryCreationService.createForSales` の OrderConfirmationService からのフック呼び出し明示、**出荷時（PENDING→SHIPPED）の予約購入 SKU 在庫減算ロジック**、出荷時在庫不足の「例外+PENDING 維持」挙動、入荷登録 Service の命名整理（`RegisterInboundService` vs 既存 `ReceiveProductSkuStockService`）、配送ステータス CANCELED / DELIVERY_FAILED / RESCHEDULED のスコープ外確認。phase14_5 完了（C-1〜C-4 / 2026-05-07）に伴い本フェーズへ責務移譲が完了。 |
| r5 実装完了 | 2026-05-07 | r5 で確定した全仕様を Step 0 → A → B（B-1〜B-6） → C（C-1〜C-3）→ D → E（E-α）の順で実装。Core 320 件 / Console 102 件 / Market 77 件すべてグリーン。並行運用整合性テスト 7 ケース緑（販売・入荷・返品復元・予約出荷・例外ロールバック）。住所一覧 API 未提供のため Console 配送先住所変更は address_id 直接入力で暫定運用（phase14 r2 / phase18 への申し送り）。都道府県別リードタイムは phaseX-5 として切り出し。 |

## 範囲
- Amazia Console
- Amazia Market
- Amazia Core
- DB設計
- **並行運用整合性のための販売側在庫減算フック**（RRRR-2 / RRRR-6 対応：r4 で追加）

## 機能概要
- Amazia Console に配送管理機能（顧客への配送・商品入荷）を追加
- Amazia Core に配送管理テーブルおよび CRUD を実装
- Amazia Market の購入履歴に配送情報を反映
- 将来的な問い合わせ管理（phase18）との連携を見据えた設計とする
- **`inventories` テーブルを新規導入し、販売・入荷の双方で `products.stock` と同期更新する並行運用を確立する**

## 本フェーズのスコープ外（RR-1 / RRR-1 対応）

| 機能 | 理由 / 取り扱い |
|------|----------------|
| 発送前キャンセル（注文取消による配送中止） | phase14 `shipping_statuses` に CANCELED 系ステータスが存在しない。phase14 r2 でマスタ追加が必要なため、本フェーズでは扱わない。 |
| 配達失敗・持ち戻り（DELIVERY_FAILED） | 同上。`shipping_statuses` マスタ拡張が前提。 |
| 再配達（配達日変更・複数回配達） | 上記と同じく phase14 r2 でのマスタ拡張が前提。本フェーズでは「初回配達のみ」を前提とする。 |
| 分割配送（1注文を複数の `deliveries` に分割） | `sales:deliveries = 1:1` を本フェーズの前提とするため範囲外（RR-3 参照）。 |
| ギフト配送（購入者≠配送先） | phase14 の `address.user_id` 制約や受取人モデルの拡張が必要なため、本フェーズでは扱わない。`deliveries.user_id` も持たない（RR-5 対応）。 |
| `products.stock` の廃止と完全移行 | **RRR-1 対応**：本書では `inventories` を**並行運用**で導入し、`products.stock` 廃止と全社移行は phase14 r2 のスコープに切り出す。 |
| 注文確定時の在庫減算フロー本体（バリデーション・トランザクション境界） | **RRR-2 対応**：本書では「`deliveries` 生成」と「`inventories` への減算同期フック」のみ責務とし、フロー全体は phase14 r2 で確定する。 |

---

# 全体方針（R-1 / R-2 / RRR-1 / RRRR-1〜RRRR-3 対応）

## 🔹 phase14 設計との責務分担

| テーブル | 定義フェーズ | 役割 |
|---------|-------------|------|
| `sales` | phase14 | **注文時点のスナップショット**（住所マスタID・配送ステータスID・配送日希望など、購入確定時の情報） |
| `address` | phase14 | 住所マスタ。`is_active` で論理削除（履歴管理） |
| `shipping_statuses` | phase14 | 配送ステータスマスタ（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED） |
| `payment_methods` | phase14 | 決済方法マスタ |
| `products.stock` | phase14（既存） | **本書では維持**。`inventories` と並行運用。読み取り正本は当面 `products.stock` のまま（RRR-1 対応） |
| `deliveries` | **phase15（本書）** | **実配送オペレーション** |
| `shipping_methods` | **phase15（本書）** | 配送方法マスタ（宅配 / コンビニ受取 / 置き配） |
| `inbounds` | **phase15（本書）** | 商品の入荷（仕入）管理 |
| `inventories` | **phase15（本書／並行運用）** | 商品×倉庫の現在在庫。**入荷・販売の両方で更新する書き込み正本**。読み取り正本は引き続き `products.stock` |
| `warehouses` | **phase15（本書）** | 倉庫マスタ。倉庫マスタ未整備期は **ダミー倉庫 1 行**のみ登録（RRR-3 対応） |

### 設計上の原則
- `sales` はスナップショット、`deliveries` は配送実体。
- 住所は VARCHAR で再保持せず `address.id` を FK 参照。
- 配送ステータスは `shipping_statuses` マスタ FK（規約 3-1 と整合）。

## 🔹 在庫モデルの並行運用方針（RRR-1 / RRRR-1 / RRRR-2 対応）

### 並行運用の更新ルール（r4 で確定）

| 経路 | `products.stock` | `inventories.quantity` |
|------|------------------|-----------------------|
| 入荷登録（本フェーズ新規） | **加算**（同一トランザクション） | **加算**（同一トランザクション） |
| 販売（注文確定／phase14 既存） | **減算**（既存処理） | **減算**（**本書 r4 でフック追加**／RRRR-2 対応） |
| 返品復元・キャンセル復元（phase14 既存） | **加算**（既存処理） | **加算**（本書 r4 でフック追加） |
| 棚卸補正（将来） | スコープ外 | スコープ外 |

**両系統で同期更新することにより、並行運用期でも `products.stock == SUM(inventories.quantity by product)` の不変条件が常に成立する**。phase14 r2 で完全移行する際は、整合性の取れた `inventories` をそのまま読み取り正本に切り替えるだけで済む。

### 販売側フック（RRRR-2 対応：方針 (A) 採用）
- 販売・返品復元の在庫更新箇所（phase14 既存 Service）に、`inventories` 同期更新の小さなフックを本フェーズで追加する。
- 具体的には、phase14 既存 Service が `products.stock` を更新する直前または直後（同一トランザクション内）で `InventorySyncService.applyDelta(product_id, warehouse_id=1, delta)` を呼び出す。
- フックの実装責務は本書（phase15）にあるが、**呼び出し位置の埋め込みは phase14 既存コードへの最小修正**となる。phase14 r2 で完全移行する際にこのフックは不要となり、`products.stock` 更新箇所と一緒に削除される。

### 並行運用開始時のマイグレーション仕様（RRRR-1 対応：r4 で新規追加）

並行運用が成立するためには、`inventories` テーブル作成時に既存 `products.stock` の値を `inventories` へ複製する初期データ投入が必須。

```sql
-- 1. warehouses テーブル作成 + ダミー倉庫1行
CREATE TABLE warehouses (...);
INSERT INTO warehouses (id, name, description) VALUES (1, 'default', '全社単一倉庫');

-- 2. inventories テーブル作成
CREATE TABLE inventories (...);

-- 3. 既存 products.stock を inventories に複製（並行運用の初期同期）
INSERT INTO inventories (product_id, warehouse_id, quantity, updated_at)
SELECT id, 1, stock, NOW()
FROM products;

-- 4. 以降、入荷・販売 Service の両方で products.stock と inventories を同期更新
```

このマイグレーションは本フェーズの起動マイグレーションとして必ず実施する。テストでも「マイグレーション直後に `inventories.quantity == products.stock`」を検証する（後述テストケース参照）。

### 不変条件
- 任意の時点で `products.stock(product_id) == SUM(inventories.quantity WHERE product_id=...)` が成立する。
- 倉庫マスタが1行のみの間は `SUM` の結果は `inventories.quantity (warehouse_id=1)` と等価。

## 🔹 注文確定フローとの協調（RRR-2 / RRRR-3 対応）

注文確定時の `deliveries` 生成と在庫減算は同一トランザクションで完結する必要があるが、フロー全体は **phase14 r2 で確定する**。本書では協調仕様（インターフェース）と防御的バリデーションのみ責任を持つ。

### 本書側の協調仕様
- `deliveries` の生成は phase14 r2 が定義する注文確定 Service から `DeliveryCreationService.createForSales(...)` を呼び出して行う。
- 通常購入で在庫切れの場合の注文拒否は phase14 r2 側のバリデーション責務。本書では「在庫切れでも注文成立 = 予約購入（`sales.is_preorder=true`）」と扱う。
- 通常購入の在庫切れ注文を本書側で観測した場合は、`deliveries` 生成 Service で例外を投げて拒否する（防御的バリデーション）。

### `DeliveryCreationService.createForSales` のシグネチャ（RRRR-3 対応：r4 で確定）

`sales` テーブルには現状 `shipping_method_id` カラムが存在しない（phase14 r1 設計）ため、`sales_id` のみではシグネチャが不足する。本書 r4 では以下の方針で確定する。

- **長期方針（推奨）**：`sales` テーブルに `shipping_method_id BIGINT NOT NULL` を追加することを **phase14 r2 へ要請**（後述「phase14 r2 への要請事項」参照）。phase14 r2 完了後は `createForSales(sales_id)` で完結。
- **過渡期方針（本フェーズ実装期間中）**：phase14 r2 完了前に本フェーズ実装が先行する場合、シグネチャは `createForSales(sales_id, shipping_method_id)` とし、呼び出し元（注文確定 Service）から `shipping_method_id` を受け取る。Market の注文確定 API リクエストで `shipping_method_id` を受領し、`sales` には保存せず `DeliveryCreationService` 経由で `deliveries` に直接書き込む。

```
// 過渡期シグネチャ
deliveries createForSales(long sales_id, long shipping_method_id)

// 長期シグネチャ（phase14 r2 で sales.shipping_method_id 追加後）
deliveries createForSales(long sales_id)
```

phase14 r2 完了時に呼び出し元と本 Service のリファクタを行い、過渡期シグネチャから長期シグネチャへ移行する。

### OrderConfirmationService からのフック呼び出し（**P5-2** 対応：r5 で明示）

phase14 r4 完了時点（2026-05-06）の `OrderConfirmationService.confirm()` 末尾には phase15 連携用のスタブコメントが残されている：

```java
// 6. phase15 で DeliveryCreationService.createForSales(savedSales.getId()) を呼び出す
//    本フェーズでは sales.shipping_status_id = PENDING のみで完結。
```

phase15 r5 着手時にこのコメントを実コードに置き換える：

```java
// 6. phase15 r5 連携：deliveries 生成
deliveryCreationService.createForSales(savedSales.getId(), request.getShippingMethodId());
```

過渡期は `(sales_id, shipping_method_id)` の二引数で渡し（`sales` テーブルに `shipping_method_id` カラムが追加されるまで）、phase14 r2 で `sales.shipping_method_id` 追加後は `(sales_id)` 単引数に移行する（既述）。
本呼び出しは `OrderConfirmationService.confirm()` の `@Transactional` 配下にあるため、`DeliveryCreationService` の例外発生時は注文確定全体がロールバックされる。phase14_5 完了時点で OrderConfirmationService テスト 8 件すべてグリーン状態が維持されているため、本フックの追加は phase15 r5 実装着手時に同テストへのケース追加（`deliveries` 生成確認）と一体で行う。

### phase14 r2 が確定すべきフロー（参考・本書で要請）
```
1. validateOrder(sales_request)
   - is_preorder == false かつ stock <= 0 なら拒否
2. begin transaction
3. stock 減算
   - products.stock -= order_quantity（既存）
   - InventorySyncService.applyDelta(product_id, 1, -order_quantity)（本書 r4 のフック）
   - is_preorder=true は減算しない
4. INSERT sales
5. DeliveryCreationService.createForSales(sales.id, shipping_method_id)
   → INSERT deliveries (shipping_status_id=PENDING,
                        scheduled_date=DeliveryScheduleService.calculate(...),
                        ...)
6. commit
```

## 🔹 tracking_code の責務分離（R-2 対応）

- `tracking_code VARCHAR(100)` … 配送業者の追跡番号（純粋に配送用途）。`deliveries` テーブルが保持。
- 問い合わせとの紐付けは `inquiries` 側で `target_type='delivery' / target_id=deliveries.id` を持つ方式（phase18 と整合）。`deliveries.inquiry_id` は持たない。

## 🔹 sales と deliveries の多重度（RR-3 対応）

`sales : deliveries = 1 : 1` を前提とする。`deliveries.sales_id` に `UNIQUE` 制約を設ける。分割配送・ギフト配送はスコープ外。

## 🔹 deliveries の生成タイミング（RR-4 対応）

注文確定と同時に `deliveries` レコードを生成する（同一トランザクション）。

| 項目 | 仕様 |
|------|------|
| 生成タイミング | `sales` レコード作成と同一トランザクション |
| 生成時の `shipping_status_id` | `PENDING` |
| 生成時の `scheduled_date` | `DeliveryScheduleService.calculate(...)` で算出。在庫切れ（予約購入）時は NULL |
| 生成時の `tracking_code` / `shipped_date` / `delivered_date` | NULL |

---

# 機能詳細

---

## 🖥 Amazia Console

### 顧客への配送管理
- 購入商品の配送状況を管理
- 配送ステータス更新（PENDING → SHIPPED → DELIVERED、または RETURN_REQUESTED / RETURNED）
- 配送先情報の確認・変更（住所変更時は `address` の `is_active` 切替）
- 配送追跡番号（`tracking_code`）の登録・参照
- 配送予定日の手動修正（出荷遅延等）
- 将来的に問い合わせ画面と相互リンク（`inquiries.target_type='delivery'` 経由）

### 商品の入荷管理
- 商品入荷情報を登録（`inbounds` テーブル）
- 入荷日・数量・仕入先（任意）を管理
- **倉庫情報の入力欄は本フェーズでは UI に表示しない**（RRRR-5 対応：r4 で確定）。バックエンドが `warehouse_id = 1`（ダミー倉庫）を自動セットする。`warehouses` のレコード数が2行以上になった時点で UI に倉庫選択フィールドを追加する（将来フェーズ）。
- `inventories` と `products.stock` を**同時に加算**（**Service 層で実装**。Model にロジックを書かない／規約 1-1）。

### Console 操作の operation_logs 記録（R-8 / RR-2 / RRR-5 対応）

| action | target_type | target_id | comment 規約 |
|--------|-------------|-----------|-------------|
| `update_shipping_status` | `deliveries` | deliveries.id | 旧ステータス・新ステータス・理由（フリーテキスト） |
| `update_shipping_address` | `deliveries` | deliveries.id | 旧 address_id・新 address_id・理由（phase14 と整合） |
| `update_scheduled_date` | `deliveries` | deliveries.id | 先頭にプレフィックス `[manual]` / `[inbound_recalc]` / `[shipping_delay]` を付与し、続けて旧予定日・新予定日・自由記述（RRR-5 対応：集計可能化） |
| `register_tracking_code` | `deliveries` | deliveries.id | 登録した追跡番号 |
| `register_inbound` | `inbounds` | inbounds.id | 商品ID・数量・倉庫 |

`update_scheduled_date` の reason プレフィックス値は `config/app/Delivery.php` に enum 定義（`[manual, inbound_recalc, shipping_delay]`）し、Service 層で生成時に固定する。本格的な集計需要が出た段階で `operation_logs.reason_code` カラム追加を phase14 r2 へ要請。

`screen_name` / `api_name` の命名規約は phase14 / phase15 共通で別途定義する（共通 ai_context への提案／本書末尾参照）。

---

## 🧠 Amazia Core

### 配送管理テーブル CRUD
- `deliveries` の CRUD を Core に実装。Console / Market 双方から利用。
- 配送ステータス遷移は Service 層で遷移ルールを管理（後述）。
- 配送予定日の自動計算は `DeliveryScheduleService` に集約（後述）。
- 注文確定時、phase14 r2 が定義する注文確定 Service から `DeliveryCreationService.createForSales(sales_id, shipping_method_id)`（過渡期）または `createForSales(sales_id)`（移行後）を呼び出して `deliveries` を生成。

### 入荷テーブル CRUD
- `inbounds` の CRUD を Core に実装。
- 入荷登録時、Service 層で `inbounds` INSERT・`inventories.quantity` 加算・`products.stock` 加算を**同一トランザクション**で実行（並行運用／RRR-1）。
- 入荷時、対象商品の在庫切れにより `scheduled_date = NULL` だった `deliveries` を再計算する（後述）。

### 入荷登録 Service の命名整理（**P5-5** 対応：r5 で確定）

phase14_5_preorder_status.md §3-1 で「`RegisterInboundService` への改名・統合」が要請された。本フェーズ r5 で以下のとおり整理する：

| Service | 用途 | 命名方針 |
|---------|------|---------|
| `RegisterInboundService`（**新設**） | `inbounds` テーブルへの入荷ヘッダ登録 + `inventories` / `products.stock` 加算 + `deliveries.scheduled_date` 再計算フック | 本フェーズで新規作成 |
| `ReceiveProductSkuStockService`（既存・phase10） | SKU 単位の在庫直接入庫（旧来の Console 一括入荷 UI が呼んでいる） | **存置**。命名通り「SKU 在庫の受入」用途で残し、`RegisterInboundService` 内部から再利用する形に整理 |

`RegisterInboundService` の責務（規約 1-1：Service にロジック寄せ）：
1. `inbounds` レコード INSERT（warehouse / supplier / quantity / received_date）
2. `ReceiveProductSkuStockService` を呼び出して `product_sku_stocks` の対応 SKU を加算（既存ロジック流用）
3. `inventories.quantity` 加算（並行運用）
4. `DeliveryRescheduleService.recalculateForProduct(productId)` で在庫切れ `deliveries` の `scheduled_date` を再計算（FIFO／RRR-4）

これにより「入荷ヘッダ管理（inbounds）」と「SKU 在庫増減」の責務分離が明確になり、既存テスト（`ReceiveProductSkuStockController/Service` 経由）のリグレッションも回避できる。

### 入荷再計算ロジック（RRR-4 / RRRR-4 対応）

```
on inbound(product_id, quantity):
  begin transaction
    inventories.quantity += quantity （対象 product_id × warehouse_id=1、FOR UPDATE）
    products.stock += quantity      （並行運用同期）

    candidates = SELECT d.*
                 FROM deliveries d JOIN sales s ON d.sales_id=s.id
                 WHERE s.product_id = :product_id
                   AND d.scheduled_date IS NULL
                 ORDER BY s.created_at ASC
                 FOR UPDATE

    available = inventories.quantity   ← inventories から最新値を取得（RRRR-4）

    for each d in candidates:
      if available >= 1:
        d.scheduled_date = DeliveryScheduleService.calculate(
            sales_id=d.sales_id,
            stock_available=available    ← ループローカル変数で消費を反映
        )
        UPDATE deliveries SET scheduled_date=... WHERE id=d.id
        operation_logs を「[inbound_recalc] 旧:NULL → 新:YYYY-MM-DD」で記録
        available -= 1
      else:
        break  ← 残りはまだ在庫不足
  commit
```

- **読み取り元**：再計算時の在庫値は **`inventories` の最新値（`FOR UPDATE` 取得）**を使用する（RRRR-4 対応）。並行運用期でも `DeliveryScheduleService.calculate` の在庫入力は `inventories` 側に切り替える（再計算 Service 内のみ。注文確定時の初回計算は `products.stock` を見る既存方針を維持）。
- **ループ中の在庫消費反映**：DB の `inventories.quantity` は再計算ループ中に毎レコード書き戻すと競合制御が複雑になるため、**Service 内のローカル変数 `available`** で消費量をトラッキングし、ループ確定後に `inventories` への反映は不要（`scheduled_date` を埋めただけで、まだ実際には販売減算されていないため）。`inventories.quantity` 自体は入荷の +quantity のままで、`scheduled_date` 確定済みの `deliveries` が将来販売減算されるときに減る。

### 入力バリデーション（RRR-8 / RRR-10 対応）
- `inbounds.quantity` は `> 0` を Service 層でバリデーション（負数・0 を拒否）。
- `inventories.quantity` の減算は `SELECT ... FOR UPDATE`（悲観ロック）で同時実行制御。
- DB 制約として `CHECK (inventories.quantity >= 0)` / `CHECK (inbounds.quantity > 0)` を付与。

### 販売側フック：InventorySyncService（RRRR-2 対応：r4 で新規追加）

phase14 既存の販売 Service・返品復元 Service が `products.stock` を更新する処理に、`inventories` 同期のための薄いサービスを呼び出すフックを埋め込む。本書のスコープでフックの実装と既存 Service への呼び出し追加を行う。

```
class InventorySyncService:
    /**
     * products.stock の増減と同じ delta を inventories.quantity に反映する。
     * 並行運用期のみ存在。phase14 r2 で完全移行されたら削除予定。
     *
     * @param product_id   対象商品ID
     * @param warehouse_id 倉庫ID（並行運用期は常に 1）
     * @param delta        増減量（販売は負数、返品復元・キャンセル復元は正数、入荷は正数）
     */
    applyDelta(product_id, warehouse_id=1, delta)
        SELECT ... FOR UPDATE  ← 悲観ロック
        UPDATE inventories SET quantity = quantity + :delta
                              WHERE product_id=... AND warehouse_id=...
        （CHECK 制約で quantity >= 0 が違反された場合は例外）
```

呼び出し位置（phase14 既存コードへの最小修正）：
- 販売処理 Service：`products.stock -= n` の直後で `InventorySyncService.applyDelta(product_id, 1, -n)`
- 返品復元 Service：`products.stock += n` の直後で `InventorySyncService.applyDelta(product_id, 1, +n)`
- 入荷 Service（本書）：`products.stock += n` の直後で `InventorySyncService.applyDelta(product_id, 1, +n)`

phase14 r2 で `products.stock` 廃止と読み取り正本切替が行われた段階で、このフック（`InventorySyncService` および呼び出し箇所）はすべて削除する。

---

## 🛒 Amazia Market

### 購入履歴への配送情報反映
- 購入履歴画面に配送ステータス・配送予定日・配送完了日・配送方法を表示。
- 表示元は `deliveries` テーブル（実配送）。注文確定と同時に生成されるため、フォールバックは原則発生しない（RR-4 に基づく）。

### 注文確定 API リクエスト（RRRR-3 対応：過渡期）
- phase14 r2 が `sales.shipping_method_id` を追加するまでの過渡期は、Market の注文確定 API リクエストで `shipping_method_id` をクライアントから受領し、注文確定 Service が `DeliveryCreationService.createForSales(sales_id, shipping_method_id)` に渡す。
- phase14 r2 完了後は `sales.shipping_method_id` に保存され、`createForSales(sales_id)` のみで完結する。

---

# DB設計（追加）

## deliveries テーブル（新規：配送実体）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| sales_id | BIGINT | NOT NULL | 売上ID（phase14 `sales.id` への FK／**`UNIQUE` 制約**：RR-3 対応） |
| shipping_address_id | BIGINT | NOT NULL | 配送先住所（phase14 `address.id` への FK／R-1 対応） |
| shipping_method_id | BIGINT | NOT NULL | 配送方法マスタID（`shipping_methods.id`／R-9 対応） |
| shipping_status_id | BIGINT | NOT NULL | 配送ステータスマスタID（phase14 `shipping_statuses.id`） |
| tracking_code | VARCHAR(100) | NULL | 配送業者の追跡番号（R-2 対応） |
| scheduled_date | DATE | NULL | 配送予定日（在庫切れ等で確定不能なときは NULL） |
| shipped_date | DATE | NULL | 発送日 |
| delivered_date | DATE | NULL | 配達完了日 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `sales_id` は phase14 `sales.id` を参照（FK）。**`UNIQUE(sales_id)` 制約あり**（RR-3）。
- `shipping_address_id` は phase14 `address.id` を参照（FK）。
- `shipping_status_id` は phase14 `shipping_statuses.id` を参照（FK）。
- `shipping_method_id` は本書 `shipping_methods.id` を参照（FK）。
- 配送先ユーザは `sales.user_id` から辿れるため、`deliveries.user_id` は持たない（RR-5）。
- `sales.shipping_address_id` は `sales.user_id` が所有する `address` のみ参照可能を Service 層バリデーションで強制（RRR-7）。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `uk_deliveries_sales_id` | UNIQUE 制約として機能 |
| `idx_deliveries_shipping_status_id` | ステータス別の一覧・集計 |
| `idx_deliveries_tracking_code` | 追跡番号での問合わせ検索 |
| `idx_deliveries_scheduled_date` | 配送予定日でのバッチ・一覧フィルタ |

---

## shipping_methods テーブル（新規：配送方法マスタ／R-9 / **P5-1** 対応）

| id | name | description |
|----|------|-------------|
| 1 | home_delivery | 宅配 |
| 2 | konbini_pickup | コンビニ受取 |
| 3 | dropoff | 置き配 |

### マイグレーション仕様（r5 で確定）

`amazia-core/src/main/resources/schema.sql` 末尾に以下を追記し、`spring.sql.init.continue-on-error=true` の冪等運用に従う：

```sql
-- フェーズ15 r5: shipping_methods マスタ
CREATE TABLE IF NOT EXISTS shipping_methods (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) NULL
);

INSERT IGNORE INTO shipping_methods (id, name, description) VALUES
    (1, 'home_delivery', '宅配'),
    (2, 'konbini_pickup', 'コンビニ受取'),
    (3, 'dropoff', '置き配');
```

`INSERT IGNORE` により再実行しても重複エラーにならず、ID 値も `config('amazia.delivery.shipping-methods.*')` で参照する規約 4-1 に整合する。
phase14 r4 までは Market 注文確定 API のリクエストでクライアントが `shipping_method_id` を直接渡しており、Core 側に shipping_methods マスタ実体は無かった。本マイグレーションで実体化し、FK 制約も有効化する。

---

## warehouses テーブル（新規：倉庫マスタ／RRR-3 対応）

| id | name | description |
|----|------|-------------|
| 1 | default | 全社単一倉庫（倉庫マスタ整備までの暫定） |

倉庫マスタ未整備期は本ダミー1行のみ。`inventories.warehouse_id` / `inbounds.warehouse_id` の `NOT NULL DEFAULT 1` で NULL UNIQUE 一意性破綻を回避。

---

## inbounds テーブル（新規：商品入荷管理／R-3 対応）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | NOT NULL | 倉庫ID（`warehouses.id` への FK／DEFAULT 1） |
| supplier_id | BIGINT | NULL | 仕入先ID（将来マスタ化） |
| quantity | INT | NOT NULL | 入荷数量（**`CHECK (quantity > 0)`**／RRR-10） |
| inbounded_at | DATE | NOT NULL | 入荷日 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- 在庫加算は Service 層で実装（規約 1-1）。`inbounds` 登録・`inventories.quantity` 加算・`products.stock` 加算は同一トランザクションで実行（並行運用）。
- `warehouse_id` はマスタ整備までは DEFAULT 1（ダミー倉庫）で運用。
- `supplier_id` のマスタ化は本フェーズのスコープ外。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_inbounds_product_id` | 商品別の入荷履歴取得 |
| `idx_inbounds_inbounded_at` | 期間別集計 |

---

## inventories テーブル（新規：商品×倉庫の現在在庫／RR-6 / RRR-1 / RRR-3 / RRR-8 対応）

並行運用フェーズでは書き込み正本（入荷・販売・返品復元の全経路から同期更新）。読み取り正本は引き続き `products.stock`。

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | NOT NULL | 倉庫ID（`warehouses.id` への FK／DEFAULT 1） |
| quantity | INT | NOT NULL | 現在在庫数（**`CHECK (quantity >= 0)`**／RRR-8） |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `UNIQUE(product_id, warehouse_id)` 制約。`warehouse_id NOT NULL` のため NULL 重複問題なし（RRR-3）。
- 倉庫マスタが整備されるまでは `warehouse_id = 1`（ダミー倉庫）の単一行で「全社在庫」を表現。
- **販売・返品復元・入荷のすべての経路から `InventorySyncService` 経由で同期更新**（RRRR-2）。
- 在庫減算の同時実行制御方針：`SELECT ... FOR UPDATE`（悲観ロック）。
- **不変条件**：任意時点で `products.stock(product_id) == SUM(inventories.quantity WHERE product_id=...)`。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `uk_inventories_product_warehouse` | UNIQUE 制約。商品×倉庫の在庫一意性 |
| `idx_inventories_product_id` | 商品別の現在在庫参照 |

### マイグレーション仕様（RRRR-1 対応：r4 で新規明記）
本フェーズの起動マイグレーションで以下を実施する。

```sql
-- 1. warehouses 作成 + ダミー1行
CREATE TABLE warehouses (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL
);
INSERT INTO warehouses (id, name, description) VALUES (1, 'default', '全社単一倉庫');

-- 2. inventories 作成
CREATE TABLE inventories (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL DEFAULT 1,
  quantity INT NOT NULL CHECK (quantity >= 0),
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_inventories_product_warehouse (product_id, warehouse_id),
  KEY idx_inventories_product_id (product_id),
  CONSTRAINT fk_inventories_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_inventories_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

-- 3. 既存 products.stock を inventories に複製（並行運用初期同期）
INSERT INTO inventories (product_id, warehouse_id, quantity, updated_at)
SELECT id, 1, stock, NOW()
FROM products;
```

これを実施しない状態で並行運用を開始すると、既存商品の `inventories` が空のため、販売減算フックが `quantity < 0` で `CHECK` 違反を起こす。phase14 r2 の完全移行時にも、この複製が完了していれば `products.stock` 廃止を安全に行える。

---

# 配送ステータス遷移ルール（R-4 / R-7 / RR-1 対応）

## ステータス（phase14 マスタに統一）
PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED

本フェーズではキャンセル / 配達失敗 / 再配達は扱わない（スコープ外）。

## 遷移可否（Service 層でガード）

| 現在 → 次 | PENDING | SHIPPED | DELIVERED | RETURN_REQUESTED | RETURNED |
|-----------|---------|---------|-----------|------------------|----------|
| PENDING | - | ✅ | ❌ | ❌ | ❌ |
| SHIPPED | ❌ | - | ✅ | ❌ | ❌ |
| DELIVERED | ❌ | ❌ | - | ✅ | ❌ |
| RETURN_REQUESTED | ❌ | ❌ | ❌ | - | ✅ |
| RETURNED | ❌ | ❌ | ❌ | ❌ | - |

巻き戻しは Service 層で例外を投げて拒否（規約 4-2 異常系）。`RETURN_REQUESTED` への遷移は phase14 `sales_return.status = REQUESTED` と連動。

---

# 出荷時の在庫処理（**P5-3 / P5-4** 対応：r5 で新規追加）

## 背景

phase14 r4 注文確定フローでは「`is_preorder=true` の sales は注文確定時に在庫減算しない」ことが確定している（[phase14_5_preorder_status.md](phase14_5_preorder_status.md) §3-1）。
予約購入 SKU の在庫減算をどのタイミングで行うかが phase14 / phase14_5 で未確定だったが、本フェーズ r5 で **配送ステータス `PENDING → SHIPPED` 遷移時** に確定する。

## 遷移時の責務

`DeliveryStatusTransitionService.transition(deliveryId, nextStatusId)` の `PENDING → SHIPPED` 遷移内で、対象 `sales` の `is_preorder` を見て分岐：

| sales.is_preorder | PENDING→SHIPPED 時の在庫処理 |
|------------------|------------------------------|
| `false`（通常購入） | **何もしない**。在庫減算は注文確定時に既に実施済み（phase14 r4） |
| `true`（予約購入） | **本遷移時に減算**。`product_sku_stocks.quantity -= sales.quantity` を `@Version` 楽観ロックで更新。`product_sku_stock_transactions` に `type='sale_preorder_shipment'` / `reference_type='sales'` / `reference_id=sales.id` で記録 |

並行運用期は `inventories.quantity` 側も `InventorySyncService.applyDelta(product_id, warehouse_id=1, -quantity)` で同期減算（RRRR-2 と同じフック）。

## 出荷時在庫不足の挙動（P5-4）

予約購入 SKU の `PENDING → SHIPPED` 遷移時に **在庫不足** を検出した場合（出荷直前まで入荷が間に合わなかったケース）：

```
if (sales.is_preorder && stock.quantity < sales.quantity) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "preorder shipment blocked: insufficient stock");
}
```

- `deliveries.shipping_status_id` は **`PENDING` のまま維持**（SHIPPED に進めない）
- トランザクションはロールバック（在庫を一切触らない）
- Console 側にエラー表示し、入荷を促す（`scheduled_date` の更新は管理者操作）
- `operation_logs.action = 'shipping_blocked_insufficient_stock'` で記録（comment に sales_id / 不足数）

これにより「予約注文を受けたが入荷未達のまま無理に SHIPPED に遷移して帳簿が壊れる」ケースを防ぐ。phase14_5_preorder_status.md §3-1「出荷時在庫不足の挙動 → 例外＋PENDING 維持」の方針通り。

## 設計書 §3-1 との整合

| 項目 | 内容 | r5 反映先 |
|------|------|---------|
| 出荷時の予約購入 SKU 在庫減算（P5-3） | `is_preorder=true` のときのみ `PENDING→SHIPPED` 遷移で減算 | 本セクション「遷移時の責務」 |
| 出荷時在庫不足の挙動（P5-4） | 例外＋PENDING 維持 | 本セクション「出荷時在庫不足の挙動」 |

---

# 配送予定日の計算仕様（R-6 / RR-2 / RR-8 / RRR-5 / RRRR-4 対応）

| 項目 | 仕様 |
|------|------|
| 計算タイミング | 注文確定時に Core 側で算出し `deliveries.scheduled_date` を初期値設定。出荷時点で前後する場合・入荷再計算時は更新 API で上書き。 |
| 入力（注文確定時の初回計算） | (a) 注文日, (b) `address.prefecture`, (c) **`products.stock`（並行運用期の読み取り正本）**, (d) `shipping_methods` の標準リードタイム |
| 入力（入荷再計算時） | 上記のうち (c) を **`inventories.quantity` の最新値（`FOR UPDATE` 取得）+ Service 内ローカル変数で消費トラッキング**に切り替え（RRRR-4 対応） |
| 計算ロジックの所在 | `DeliveryScheduleService.calculate(...)` に集約（規約 1-1） |
| 在庫切れ時 | `scheduled_date = NULL`。Market は「入荷待ち」と表示。入荷時に再計算（注文日昇順 FIFO／RRR-4） |
| 設定値（リードタイム） | 初期は `config/app/Delivery.php` / `application.yml` に定数定義（規約 3-1）。**都道府県別リードタイムのマスタ化は [phaseX-5](../phaseX/phaseX-5_prefecture_based_lead_time.md) に切り出し済**（RR-8） |
| 変更履歴 | `scheduled_date` の変更はすべて `operation_logs.action = update_scheduled_date` で記録。`comment` は `[manual]` / `[inbound_recalc]` / `[shipping_delay]` プレフィックス付き（RR-2 / RRR-5） |

---

# 技術検討事項
- 配送ステータス遷移ルール（上記表で確定。キャンセル等はスコープ外）
- 配送予定日の自動計算（注文確定時は `products.stock`、入荷再計算時は `inventories` 最新値を使用／RRRR-4）
- `tracking_code` の生成方式：配送業者発行番号をそのまま登録（自動採番しない）。手動入力 or 外部 API 連携を想定。
- 入荷管理と在庫管理の連動：Service 層・同一トランザクション（並行運用：`inventories` と `products.stock` を両方更新）
- **販売・返品復元の `inventories` 同期フック**（`InventorySyncService.applyDelta`／RRRR-2）
- 配送情報の Market 反映タイミング：`deliveries` 更新時に同期反映
- 将来的な問い合わせ管理画面との統合：`inquiries.target_type='delivery'` 経由（phase18 と協議）
- 外部配送 API 連携（phase14 と同方針）
- 将来的な `inventory_movements` テーブル（在庫増減ログ）の追加（RRR-6）。命名は他 ERP（Odoo / ERPNext 等）の `inventory_movements` 慣習との混同を避けるよう phase14 r2 で確認（RRRR-9）。
- `products.stock` 廃止と `inventories` 完全移行は phase14 r2 のスコープ（本書では並行運用に留める／RRR-1）

---

# TDDテストケース

## Amazia Core / JUnit

### 正常系
- 注文確定時に `DeliveryCreationService.createForSales(sales_id, shipping_method_id)` 経由で `deliveries` が `PENDING` で生成される（同一トランザクション／RR-4・RRR-2）
- 配送情報（`deliveries`）が正しく登録・更新・取得できる
- 配送ステータスの更新が正しく反映される（PENDING → SHIPPED → DELIVERED）
- 入荷情報（`inbounds`）登録時に `inventories.quantity` と `products.stock` の**両方**が正しく増加する（並行運用／同一トランザクション／RRR-1）
- 販売処理時に `InventorySyncService.applyDelta` 経由で `inventories.quantity` が減算され、`products.stock` と一致が保たれる（RRRR-2）
- 返品復元時に `inventories.quantity` と `products.stock` の両方が正しく加算される（RRRR-2）
- 入荷登録により、在庫切れで `scheduled_date = NULL` だった `deliveries` の予定日が `sales.created_at` 昇順 FIFO で再計算される（RRR-4）
- 入荷再計算時、`DeliveryScheduleService.calculate` の在庫入力は `inventories` 最新値を参照する（RRRR-4）
- 入荷数量が在庫切れ注文数より少ない場合、注文日が古いものから順に充足し、充足できなかった `deliveries.scheduled_date` は NULL のまま保持される
- `tracking_code` が正しく保存・取得できる
- 配送予定日が地域・配送方法・在庫状況を入力に正しく算出される
- 在庫切れ時に `scheduled_date = NULL` で登録される

### 並行運用整合性テスト（RRRR-7 対応：r4 で新規追加）
- マイグレーション直後（`inventories` 初期化直後）、すべての商品で `inventories.quantity == products.stock`
- 入荷登録1件ごとに、対象商品の `inventories.quantity == products.stock` の不変条件が維持される
- 販売（注文確定）1件ごとに、対象商品の `inventories.quantity == products.stock` の不変条件が維持される
- 返品復元1件ごとに、対象商品の `inventories.quantity == products.stock` の不変条件が維持される
- 入荷登録の途中で例外が発生した場合、`inbounds` レコード・`inventories` 加算・`products.stock` 加算がすべてロールバックされる（トランザクション境界）

### 異常系（R-7 / RRR-7 / RRR-8 / RRR-10 対応）
- 不正な配送ステータス遷移を拒否：DELIVERED → PENDING、SHIPPED → PENDING など
- 存在しない `sales_id` での配送登録を拒否（FK 違反 / Service 層チェック）
- 同一 `sales_id` での重複登録を拒否（`UNIQUE(sales_id)` 制約／RR-3）
- `sales.shipping_address_id` が `sales.user_id` 所有でない `address` を指す場合に拒否（RRR-7）
- 存在しない `product_id` での入荷登録を拒否
- `inbounds.quantity <= 0` の入荷登録を拒否（`CHECK` 制約 + Service バリデーション／RRR-10）
- 入荷登録の在庫加算が失敗した場合、`inbounds` レコード・`products.stock` 更新もロールバック
- `shipping_status_id` / `shipping_method_id` に存在しないマスタ ID を指定した場合の拒否
- 通常購入で在庫切れの注文から `DeliveryCreationService.createForSales` が呼ばれた場合、防御的に例外を投げる（RRR-2）
- `inventories.quantity` が負数になる更新を `CHECK (quantity >= 0)` で拒否（RRR-8）
- マイグレーション未実施で並行運用を開始した場合（`inventories` 行が存在しない商品の販売減算フックが走った場合）、明示的なエラーで停止（RRRR-1 のチェック）

### テスト値の config 経由化（RRRR-8 対応：r4 で明記）
規約 4-1「テスト内で URL や設定値をハードコードせず `config()` / `@Value` 経由で取得する」と整合させるため、本書のテストでは以下の値を `config()` / `@Value` 経由でアサート値を取得する：

- `update_scheduled_date` の reason プレフィックス（`config('delivery.scheduled_date_reasons.inbound_recalc')` 等）
- `shipping_methods` のマスタ ID（`config('delivery.shipping_methods.home_delivery_id')` 等）
- 並行運用のダミー倉庫 ID（`config('delivery.default_warehouse_id')`）
- リードタイム定数

## Amazia Console / PHPUnit
- 配送管理画面で配送情報が正しく表示される
- 配送ステータス更新時に `operation_logs` にレコードが記録される
- 配送先変更時に `operation_logs` にレコードが記録される
- 配送予定日変更時に `operation_logs.action = update_scheduled_date` で記録され、`comment` 先頭が `[manual]` / `[inbound_recalc]` / `[shipping_delay]` のいずれか（RR-2 / RRR-5）
- 入荷登録時に `operation_logs` にレコードが記録される
- **入荷登録 UI に倉庫選択フィールドが表示されないこと**（RRRR-5：r4 で追加）
- **入荷登録時、リクエストに倉庫情報が無くてもバックエンドが `warehouse_id=1` を自動セットすること**（RRRR-5）
- 不正な遷移を Console から要求した場合、エラーレスポンスが返る

## Amazia Market / PHPUnit
- 購入履歴に配送情報（ステータス・予定日・完了日・方法）が正しく表示される
- 配送ステータスが phase14 マスタの表示名で表示される
- 配送方法（home_delivery / konbini_pickup / dropoff）が正しく表示名で表示される
- 在庫切れ商品の購入履歴で `scheduled_date` が NULL のとき「入荷待ち」と表示される
- 注文確定 API リクエストで `shipping_method_id` を受領し、`deliveries.shipping_method_id` に正しく保存される（過渡期仕様／RRRR-3）

> 注：`deliveries` は注文確定と同時に生成される（RR-4）ため、r1 にあった「未生成時フォールバック」のテストケースは r2 で削除した。

---

# phase18（問い合わせ管理）への要請事項

R-2 を実現するため、phase18 設計書の `inquiries` テーブルに以下の追加を要請する。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| target_type | VARCHAR(50) | 問い合わせ対象種別（`delivery` / `product` / `sales` ...） |
| target_id | BIGINT | 対象ID（`target_type` と組み合わせて参照） |

- 配送に関する問い合わせ：`target_type='delivery' / target_id=deliveries.id`
- phase18 の `inquiry_messages` がスレッド管理を担うため、`deliveries` 側に `inquiry_id` は持たせない。

## 依存関係解決のプロセス（RR-7 対応）
- phase18 設計書 r1（`inquiries.target_type` / `target_id` 追加版）を本フェーズ実装着手前に作成・確定。
- phase18 の実装自体は本フェーズと並行・後続でも構わない。
- phase15 実装着手時点で phase18 が未実装の場合、phase15 単独テストには影響なし。E2E は phase18 実装完了後。

---

# phase14_5 から取り込んだ要請事項（r5 で正式反映）

[phase14_5_preorder_status.md](phase14_5_preorder_status.md) §3-1 で phase15 r5 への要請として整理された 6 項目を、本フェーズ r5 で取り込んだ反映状況：

| # | 要請項目（phase14_5 §3-1） | r5 反映先セクション | ステータス |
|---|---------------------------|---------------------|-----------|
| P5-1 | `shipping_methods` マスタ作成（INSERT IGNORE で home_delivery / konbini_pickup / dropoff） | [shipping_methods テーブル §マイグレーション仕様](#shipping_methods-テーブル新規配送方法マスタr-9--p5-1-対応) | ✅ 反映済み |
| P5-2 | `DeliveryCreationService.createForSales(salesId)` を OrderConfirmationService から呼び出し | [DeliveryCreationService.createForSales § OrderConfirmationService からのフック呼び出し](#orderconfirmationservice-からのフック呼び出しp5-2-対応r5-で明示) | ✅ 反映済み |
| P5-3 | 出荷時の予約購入 SKU 在庫減算（`is_preorder=true` のときのみ `PENDING→SHIPPED` 遷移で減算） | [出荷時の在庫処理 §遷移時の責務](#出荷時の在庫処理p5-3--p5-4-対応r5-で新規追加) | ✅ 反映済み |
| P5-4 | 出荷時在庫不足の挙動（例外＋PENDING 維持） | [出荷時の在庫処理 §出荷時在庫不足の挙動](#出荷時の在庫処理p5-3--p5-4-対応r5-で新規追加) | ✅ 反映済み |
| P5-5 | `RegisterInboundService` への改名・統合（既存 `ReceiveProductSkuStockService` を流用） | [入荷登録 Service の命名整理](#入荷登録-service-の命名整理p5-5-対応r5-で確定) | ✅ 反映済み |
| P5-6 | 配送ステータス CANCELED / DELIVERY_FAILED / RESCHEDULED は r5 ではスコープ外確認のみ | [本フェーズのスコープ外](#本フェーズのスコープ外rr-1--rrr-1-対応) / [phase14 r2 への要請事項](#phase14-r2-への要請事項rrr-1--rrr-2--rrr-5--rrr-6--rrrr-3-対応) | ✅ スコープ外確認 |

**phase14_5_preorder_status.md §3 は本 r5 の取り込みをもって `✅ 反映済み` として閉じる**。

---

# phase14 r2 への要請事項（RRR-1 / RRR-2 / RRR-5 / RRR-6 / RRRR-3 対応）

本書で並行運用・スコープ外・過渡期仕様とした論点を、phase14 r2 で確定する必要がある。

| 項目 | 内容 |
|------|------|
| `products.stock` 廃止と `inventories` 完全移行 | 販売処理・予約ステータス判定（`BACK_ORDER` / `SOLD_OUT`）・返品時在庫戻しを `inventories` 参照に書き換え。`InventorySyncService` フックを削除。 |
| 注文確定フローの確定 | バリデーション・在庫減算・`sales` INSERT・`DeliveryCreationService.createForSales` 呼び出しの順序とトランザクション境界 |
| **`sales.shipping_method_id` カラム追加（RRRR-3）** | `DeliveryCreationService.createForSales` の長期シグネチャ移行のため、`sales` テーブルに `shipping_method_id BIGINT NOT NULL`（FK to `shipping_methods.id`）を追加 |
| `shipping_statuses` マスタ拡張 | キャンセル（CANCELED）・配達失敗（DELIVERY_FAILED）・再配達系ステータスの追加 |
| `operation_logs` のスキーマ拡張 | `update_scheduled_date` の reason 集計需要が高まった段階で `reason_code` カラムを追加（RRR-5） |
| `inventory_movements` テーブル | 在庫増減ログの一元化（販売・キャンセル・返品復元・棚卸補正／RRR-6）。命名は他 ERP（Odoo / ERPNext）の慣習との混同を避けるよう確認（RRRR-9） |

---

# 共通命名規約への提案（RR-10 / RRR-9 対応）

phase14 で `operation_logs` に `screen_name` / `api_name` が追加されたが、両カラムに入る具体値の命名規約は phase14 / phase15 のいずれにも未定義。共通 ai_context（`docs/ai_context/operation_logs_naming.md`）に定義する提案：

| カラム | 規約案 | 例 |
|--------|--------|----|
| `screen_name` | `{App}{Domain}{Action}Page`（PascalCase） | `ConsoleDeliveryListPage` / `ConsoleInboundCreatePage` |
| `api_name` | `{HTTP_METHOD} {path}`（path は実 URL） | `POST /api/console/deliveries/{id}/status` |

### Vue コンポーネントパス → `screen_name` のマッピングルール（RRR-9 対応）
- Vue ページコンポーネント `pages/{Domain}/{Action}.vue` から `screen_name` を機械的に生成。
- 例：`Console` × `pages/Delivery/List.vue` → `ConsoleDeliveryListPage`

---

# レビューコメント対応サマリ（r1 → r2 → r3 → r4）

## r1 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| R-1 | 🔴 必須 | `deliveries` を「実配送」、`sales` を「注文スナップショット」と責務分離 |
| R-2 | 🔴 必須 | `tracking_code` を配送追跡番号に限定 |
| R-3 | 🔴 必須 | `inbounds` テーブル新規追加 |
| R-4 | 🟡 推奨 | 配送ステータスを phase14 マスタに統一 |
| R-5 | 🟢 任意 | r1 では `deliveries.user_id` の理由を明記、r2 で削除 |
| R-6 | 🟡 推奨 | 配送予定日の計算仕様を明文化 |
| R-7 | 🟡 推奨 | 異常系テストを追加 |
| R-8 | 🟡 推奨 | `operation_logs` 記録対象を明記 |
| R-9 | 🟢 任意 | `shipping_methods` マスタ化 |
| R-10 | 🟢 任意 | インデックス方針を追記 |
| R-11 | 🟢 任意 | Market 表示は `deliveries` を主に整理 |
| R-12 | 🟢 任意 | 冒頭の余分な空行を削除 |

## r2 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| RR-1 | 🟡 推奨 | キャンセル等を本フェーズスコープ外に |
| RR-2 | 🟡 推奨 | 配送予定日変更を `operation_logs` に記録 |
| RR-3 | 🟡 推奨 | `sales:deliveries=1:1` 確定 |
| RR-4 | 🟡 推奨 | `deliveries` は注文確定と同時に生成 |
| RR-5 | 🟢 任意 | `deliveries.user_id` を YAGNI で削除 |
| RR-6 | 🟢 任意 | `inventories` を明示 |
| RR-7 | 🟢 任意 | phase18 依存解決プロセス明記 |
| RR-8 | 🟢 任意 | リードタイムは段階方針 |
| RR-9 | 🟢 任意 | 改訂履歴の初版日付補完 |
| RR-10 | 🟢 任意 | 命名規約を共通 ai_context に提案 |

## r3 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| RRR-1 | 🔴 必須 | `inventories` 並行運用導入 |
| RRR-2 | 🔴 必須 | 注文確定フロー協調仕様 |
| RRR-3 | 🟡 推奨 | `warehouses` ダミー倉庫導入 |
| RRR-4 | 🟡 推奨 | 入荷再計算の FIFO 優先度 |
| RRR-5 | 🟡 推奨 | reason プレフィックス enum 化 |
| RRR-6 | 🟡 推奨 | `inventory_movements` 将来課題化 |
| RRR-7 | 🟢 任意 | shipping_address のオーナー検証 |
| RRR-8 | 🟢 任意 | 悲観ロック + `CHECK` 制約 |
| RRR-9 | 🟢 任意 | Vue → screen_name マッピング |
| RRR-10 | 🟢 任意 | `inbounds.quantity > 0` |
| RRR-11 | 🟢 任意 | 改訂履歴の初版日付を「不明」に修正 |

## r4 で新規対応（RRRR-1 〜 RRRR-9）
| ID | 優先度 | 対応 |
|----|--------|------|
| RRRR-1 | 🟡 推奨 | **並行運用開始時のマイグレーション仕様を新規明記**。`warehouses` 作成 → `inventories` 作成 → 既存 `products.stock` を `inventories` に複製する初期データ投入 SQL を提示 |
| RRRR-2 | 🟡 推奨 | **販売側 `inventories` 減算フック（`InventorySyncService.applyDelta`）を本書スコープに追加**（方針 A 採用）。販売・返品復元の在庫更新箇所に同期フックを埋め込み、不変条件 `products.stock == SUM(inventories.quantity)` を維持。phase14 r2 完了時にフックは削除 |
| RRRR-3 | 🟡 推奨 | **`DeliveryCreationService.createForSales` のシグネチャを過渡期 `(sales_id, shipping_method_id)` / 移行後 `(sales_id)` の二段階で確定**。`sales.shipping_method_id` 追加を phase14 r2 へ要請 |
| RRRR-4 | 🟡 推奨 | **入荷再計算時の `DeliveryScheduleService.calculate` の在庫入力を `inventories` 最新値（`FOR UPDATE`）+ Service 内ローカル変数で消費トラッキングする方式に確定**。注文確定時の初回計算は `products.stock` のままで揺れない |
| RRRR-5 | 🟡 推奨 | **倉庫マスタが1行のみの間は Console 入荷登録 UI に倉庫選択フィールドを表示しない**（バックエンドが `DEFAULT 1` を自動セット）。`warehouses` のレコード数が2行以上になった時点で UI に追加 |
| RRRR-6 | 🟢 任意 | 範囲セクションに「並行運用整合性のための販売側在庫減算フック」を追記（RRRR-2 と連動） |
| RRRR-7 | 🟢 任意 | 並行運用整合性テスト群を新規追加（マイグレーション直後の一致・各経路後の不変条件検証・例外時のロールバック） |
| RRRR-8 | 🟢 任意 | テスト内のアサート値を `config()` / `@Value` 経由で取得する旨を明記（規約 4-1 整合） |
| RRRR-9 | 🟢 任意 | `inventory_movements` の命名は他 ERP の慣習との混同を避けるよう phase14 r2 で確認する旨を要請事項に追記 |
