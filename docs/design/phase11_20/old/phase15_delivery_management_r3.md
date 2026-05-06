# フェーズ15：配送管理（改訂版 r3）

## ステータス
🔲 未着手

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 初版日不明（git 履歴未取得） | 初稿（フェーズ15配送管理の基本設計） |
| r1 | 2026-05-06 | レビューコメント R-1 〜 R-12 を反映。phase14_shipping.md との整合、tracking_code の責務分離、入荷管理（inbounds）の DB 設計追加、配送ステータスの統一、operation_logs 反映、異常系テスト追加など。 |
| r2 | 2026-05-06 | 再レビューコメント RR-1 〜 RR-10 を反映。ステータス遷移のスコープ宣言、配送予定日の operation_logs 記録、`sales:deliveries` の多重度確定、`deliveries` 生成タイミング確定、在庫テーブル仕様の明示など。 |
| r3 | 2026-05-06 | 再々レビューコメント RRR-1 〜 RRR-11 を反映。`inventories` を**並行運用**に格下げ（`products.stock` 廃止は phase14 r2 へ切り出し）、注文確定フローのガード条件明示、ダミー倉庫導入による NULL UNIQUE 回避、入荷再計算の優先度ルール、`update_scheduled_date` の reason_code 化、在庫増減ログ（`inventory_movements`）の将来課題化など。 |

## 範囲
- Amazia Console
- Amazia Market
- Amazia Core
- DB設計

## 機能概要
- Amazia Console に配送管理機能（顧客への配送・商品入荷）を追加
- Amazia Core に配送管理テーブルおよび CRUD を実装
- Amazia Market の購入履歴に配送情報を反映
- 将来的な問い合わせ管理（phase18）との連携を見据えた設計とする

## 本フェーズのスコープ外（RR-1 / RRR-1 対応）
以下は本フェーズで扱わない。将来フェーズ（または phase14 r2）で別途定義する。

| 機能 | 理由 / 取り扱い |
|------|----------------|
| 発送前キャンセル（注文取消による配送中止） | phase14 `shipping_statuses` に CANCELED 系ステータスが存在しない。phase14 r2 でマスタ追加が必要なため、本フェーズでは扱わない。 |
| 配達失敗・持ち戻り（DELIVERY_FAILED） | 同上。`shipping_statuses` マスタ拡張が前提。 |
| 再配達（配達日変更・複数回配達） | 上記と同じく phase14 r2 でのマスタ拡張が前提。本フェーズでは「初回配達のみ」を前提とする。 |
| 分割配送（1注文を複数の `deliveries` に分割） | `sales:deliveries = 1:1` を本フェーズの前提とするため範囲外（RR-3 参照）。 |
| ギフト配送（購入者≠配送先） | phase14 の `address.user_id` 制約や受取人モデルの拡張が必要なため、本フェーズでは扱わない。`deliveries.user_id` も持たない（RR-5 対応）。 |
| `products.stock` の廃止と完全移行 | **RRR-1 対応**：本書 r3 では `inventories` を**並行運用**で導入し、`products.stock` 廃止と全社移行は phase14 r2 のスコープに切り出す。 |
| 注文確定時の在庫減算フロー（`sales` 作成と在庫減算の順序・ガード条件） | **RRR-2 対応**：本書では「`deliveries` 生成」の責務のみ持ち、在庫減算フロー本体は phase14 r2 で確定する。本書では協調仕様（インターフェース）のみ定義する。 |

---

# 全体方針（R-1 / R-2 / RRR-1 対応）

## 🔹 phase14 設計との責務分担

phase14_shipping.md でメインに定義済みのテーブル（`sales` / `address` / `shipping_statuses` / `payment_methods`）と本フェーズで新規追加するテーブルの責務を以下のとおり明文化する。

| テーブル | 定義フェーズ | 役割 |
|---------|-------------|------|
| `sales` | phase14 | **注文時点のスナップショット**（住所マスタID・配送ステータスID・配送日希望など、購入確定時の情報） |
| `address` | phase14 | 住所マスタ。`is_active` で論理削除（履歴管理） |
| `shipping_statuses` | phase14 | 配送ステータスマスタ（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED） |
| `payment_methods` | phase14 | 決済方法マスタ |
| `products.stock` | phase14（既存） | **本書 r3 では維持**。`inventories` と並行運用。読み取り正本は当面 `products.stock` のまま（RRR-1 対応） |
| `deliveries` | **phase15（本書）** | **実配送オペレーション**（実際の発送日・配達完了日・配送業者の追跡番号など、配送実体としての情報） |
| `shipping_methods` | **phase15（本書）** | 配送方法マスタ（宅配 / コンビニ受取 / 置き配） |
| `inbounds` | **phase15（本書）** | 商品の入荷（仕入）管理。顧客への配送とは責務を分離 |
| `inventories` | **phase15（本書／並行運用）** | 商品×倉庫の現在在庫。**並行運用フェーズでは書き込み正本だが、読み取り正本は引き続き `products.stock`**（RRR-1 対応） |
| `warehouses` | **phase15（本書）** | 倉庫マスタ。倉庫マスタ未整備期は **ダミー倉庫 1 行**のみ登録（RRR-3 対応） |

### 設計上の原則
- **`sales` は「注文確定」のスナップショット、`deliveries` は「配送実体」**。住所変更や再配達などの配送オペレーション情報は `deliveries` 側で扱う。
- 住所は VARCHAR で再保持せず、`address.id`（phase14）を FK 参照する。
- 配送ステータスは VARCHAR ではなく `shipping_statuses` マスタ（phase14）への FK（`shipping_status_id`）で持つ（規約 3-1「権限・定数・マスタは config／マスタ化」と整合）。

## 🔹 在庫モデルの並行運用方針（RRR-1 対応）

本フェーズは「配送管理＋入荷」が責務であり、在庫データモデルの全社的書き換え（`products.stock` 廃止）は影響範囲が大きいため、本書では**並行運用**で導入する。

| 観点 | 本書 r3（並行運用） | phase14 r2（完全移行・将来） |
|------|-------------------|---------------------------|
| 読み取り正本（販売判定・予約ステータス・購入処理） | **`products.stock`**（既存維持） | `inventories` |
| 書き込み正本（入荷加算） | `inventories.quantity` を加算 | `inventories.quantity` を加算 |
| `products.stock` の更新 | **入荷登録 Service 内で `inventories` と同時に同期更新**（同一トランザクション） | 廃止 |
| 倉庫別在庫 | 当面は単一ダミー倉庫のみ。倉庫マスタ整備後に複数行へ分割 | 倉庫別在庫として正規運用 |

### 並行運用の実装ルール
- 入荷登録 Service は `inbounds` INSERT・`inventories.quantity` 加算・`products.stock` 加算を**同一トランザクション**で実行する。
- 販売・予約ステータス判定・返品時の在庫戻しなど、**`products.stock` を読み書きする既存ロジックは本フェーズでは変更しない**。
- 並行運用が破綻するリスク（`products.stock` と `inventories` の値が乖離）は、移行完了までは入荷経路のみが書き込み元なので、入荷 Service のトランザクション境界が守られていれば不整合は起きない。
- phase14 r2 で完全移行するタイミングで、読み取り側を `inventories` 参照に切り替え、`products.stock` を廃止する。

## 🔹 注文確定フローとの協調（RRR-2 対応）

注文確定時の `deliveries` 生成と在庫減算は同一トランザクションで完結する必要があるが、フロー全体（バリデーション・在庫減算・`sales` INSERT・`deliveries` INSERT）は **phase14 r2 で確定する**ものとし、本書では協調仕様（本書側で必要な前提・出力）のみ定義する。

### 本書側の協調仕様
- `deliveries` の生成は **phase14 r2 が定義する注文確定 Service から呼び出される**。本書はその呼び出しインターフェース（`DeliveryCreationService.createForSales(sales_id)`）と生成内容のみ責任を持つ。
- 通常購入で在庫切れの場合の注文拒否は phase14 r2 側のバリデーション責務。本書では「在庫切れでも注文が成立した = 予約購入（`sales.is_preorder = true`）」と扱う。
- 通常購入の在庫切れ注文を本書側で観測した場合（=phase14 のバリデーション漏れ）は、`deliveries` 生成 Service で例外を投げて拒否する（防御的バリデーション）。

### phase14 r2 が確定すべきフロー（参考・本書で要請）
```
1. validateOrder(sales_request)
   - is_preorder == false かつ stock <= 0 なら拒否
2. begin transaction
3. stock 減算（並行運用期は products.stock 減算が正本／is_preorder=true は減算しない）
4. INSERT sales
5. DeliveryCreationService.createForSales(sales.id)
   → INSERT deliveries (shipping_status_id=PENDING,
                        scheduled_date=DeliveryScheduleService.calculate(),
                        ...)
6. commit
```

## 🔹 tracking_code の責務分離（R-2 対応）

初版で混在していた「配送追跡番号」と「問い合わせ番号」を分離する。

- `tracking_code VARCHAR(100)` … **配送業者の追跡番号**（純粋に配送用途）。`deliveries` テーブルが保持。
- 問い合わせとの紐付けは **`inquiries` 側で `target_type='delivery' / target_id=deliveries.id` を持つ方式**（phase18 と整合）。`deliveries.inquiry_id` は持たない。
  - 問い合わせ対象は配送以外（商品・売上）にも広がるため、対象側 FK を `deliveries` に持たせるとスケールしない。
  - phase18 設計書側で `inquiries.target_type` / `target_id` の追加を要請する（本書末尾「phase18 への要請事項」参照）。

## 🔹 sales と deliveries の多重度（RR-3 対応）

本フェーズでは **`sales : deliveries = 1 : 1`** を前提とする。

- DB レベルで `deliveries.sales_id` に **`UNIQUE` 制約**を設ける。
- 分割配送・複数配送先（ギフト含む）は本フェーズのスコープ外（前掲「スコープ外」表）。
- 将来 1:N に拡張する場合は phase14 r2 / phase15 r3+ で `UNIQUE` 制約を解除し、必要なら `deliveries.shipment_no` 等の番号体系を追加する。

## 🔹 deliveries の生成タイミング（RR-4 対応）

**注文確定と同時に `deliveries` レコードを生成する**（同一トランザクションで `sales` と1:1で同期作成。呼び出しは phase14 r2 が定義する注文確定 Service から）。

| 項目 | 仕様 |
|------|------|
| 生成タイミング | `sales` レコード作成と同一トランザクション（呼び出し元は phase14 r2 が定義） |
| 生成時の `shipping_status_id` | `PENDING` |
| 生成時の `scheduled_date` | `DeliveryScheduleService.calculate(...)` で算出。在庫切れ（予約購入）時は NULL |
| 生成時の `tracking_code` / `shipped_date` / `delivered_date` | NULL |

---

# 機能詳細

---

## 🖥 Amazia Console

### 顧客への配送管理
- 購入商品の配送状況を管理
- 配送ステータス更新（phase14 マスタ：PENDING → SHIPPED → DELIVERED、または RETURN_REQUESTED / RETURNED）
- 配送先情報の確認・変更（住所変更時は phase14 `address` の `is_active` 切替に従う）
- 配送追跡番号（`tracking_code`）の登録・参照
- 配送予定日の手動修正（出荷遅延等）
- 将来的に問い合わせ画面と相互リンク（`inquiries.target_type='delivery'` 経由）

### 商品の入荷管理
- 商品入荷情報を登録（`inbounds` テーブル）
- 入荷日・数量・倉庫情報・仕入先（任意）を管理
- 在庫テーブル（`inventories`）と並行運用の `products.stock` を**同時に**加算（**Service 層で実装**。Model にロジックを書かない／規約 1-1）

### Console 操作の operation_logs 記録（R-8 / RR-2 / RRR-5 対応）
phase14 の方針に倣い、以下の管理操作はすべて `operation_logs` に記録する（5W1H 追跡）。

| action | target_type | target_id | comment 規約 |
|--------|-------------|-----------|-------------|
| `update_shipping_status` | `deliveries` | deliveries.id | 旧ステータス・新ステータス・理由（フリーテキスト） |
| `update_shipping_address` | `deliveries` | deliveries.id | 旧 address_id・新 address_id・理由（phase14 と整合） |
| `update_scheduled_date` | `deliveries` | deliveries.id | **先頭にプレフィックス `[manual]` / `[inbound_recalc]` / `[shipping_delay]` を付与**し、続けて旧予定日・新予定日・自由記述（RRR-5 対応：集計可能化） |
| `register_tracking_code` | `deliveries` | deliveries.id | 登録した追跡番号 |
| `register_inbound` | `inbounds` | inbounds.id | 商品ID・数量・倉庫 |

`update_scheduled_date` の reason プレフィックス値は `config/app/Delivery.php` に enum 定義（`[manual, inbound_recalc, shipping_delay]`）し、Service 層で生成時に固定する。本格的な集計需要が出た段階で `operation_logs` 側にカラム（`reason_code`）を追加する案を phase14 r2 へ要請する（本書末尾「phase14 r2 への要請事項」参照）。

`screen_name` / `api_name` の命名規約は phase14 / phase15 共通で別途定義する（RR-10 / RRR-9：本書末尾「共通命名規約への提案」参照）。

---

## 🧠 Amazia Core

### 配送管理テーブル CRUD
- `deliveries` の CRUD を Core に実装。Console / Market 双方から利用。
- 配送ステータス遷移は Service 層で**遷移ルール**を管理（後述「配送ステータス遷移ルール」参照）。
- 配送予定日の自動計算は `DeliveryScheduleService` に集約（後述「配送予定日の計算仕様」）。
- 注文確定時、phase14 r2 が定義する注文確定 Service から `DeliveryCreationService.createForSales(sales_id)` を呼び出して `deliveries` を生成（RRR-2 対応）。

### 入荷テーブル CRUD
- `inbounds` の CRUD を Core に実装。
- 入荷登録時、Service 層で対象商品×倉庫の `inventories.quantity` を加算するとともに、並行運用期は `products.stock` も同時に加算する。`inbounds` レコード追加・`inventories` 加算・`products.stock` 加算は同一トランザクションで実行（RRR-1 対応）。
- 入荷時、対象商品の在庫切れにより `scheduled_date = NULL` だった `deliveries` を再計算する（バッチではなく、入荷登録 Service 内で同期実行）。
  - **再計算の優先度（RRR-4 対応）**：対象商品の `deliveries` のうち `scheduled_date IS NULL` のレコードを **`sales.created_at` 昇順**で並べ、入荷数量分（`inbounds.quantity`）を先入れ先出しで充足。充足できた `deliveries` のみ `scheduled_date` を更新する。
- 再計算で `scheduled_date` を更新した `deliveries` 1件ごとに `update_scheduled_date` を operation_logs に `comment='[inbound_recalc] 旧:NULL → 新:YYYY-MM-DD'` で記録。

### 入力バリデーション（RRR-8 / RRR-10 対応）
- `inbounds.quantity` は `> 0` を Service 層でバリデーション（負数・0 を拒否）。
- `inventories.quantity` の減算は **`SELECT ... FOR UPDATE`（悲観ロック）** で同時実行制御する。本フェーズでは加算のみ責任を持つが、将来販売側が `inventories` を読み取り正本化したときに揺れないよう方針を明記。
- DB 制約として `CHECK (inventories.quantity >= 0)` / `CHECK (inbounds.quantity > 0)` を付与し、テストでバグを早期検出。

---

## 🛒 Amazia Market

### 購入履歴への配送情報反映
- 購入履歴画面に以下を表示
  - 配送ステータス（`shipping_statuses` マスタの表示名）
  - 配送予定日（`scheduled_date`）
  - 配送完了日（`delivered_date`、あれば）
  - 配送方法（`shipping_methods` マスタの表示名）
- 表示元は `deliveries` テーブル（実配送）。注文確定と同時に生成されるため、フォールバックは原則発生しない（RR-4 に基づく）。

---

# DB設計（追加）

## deliveries テーブル（新規：配送実体）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| sales_id | BIGINT | NOT NULL | 売上ID（phase14 `sales.id` への FK／**`UNIQUE` 制約**：RR-3 対応） |
| shipping_address_id | BIGINT | NOT NULL | 配送先住所（phase14 `address.id` への FK／R-1 対応） |
| shipping_method_id | BIGINT | NOT NULL | 配送方法マスタID（`shipping_methods.id`／R-9 対応） |
| shipping_status_id | BIGINT | NOT NULL | 配送ステータスマスタID（phase14 `shipping_statuses.id`／R-1・R-4 対応） |
| tracking_code | VARCHAR(100) | NULL | **配送業者の追跡番号**（R-2 対応：問い合わせ番号とは分離） |
| scheduled_date | DATE | NULL | 配送予定日（在庫切れ等で確定不能なときは NULL／R-6 対応） |
| shipped_date | DATE | NULL | 発送日 |
| delivered_date | DATE | NULL | 配達完了日 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `sales_id` は phase14 `sales.id` を参照（FK）。**`UNIQUE(sales_id)` 制約あり**（RR-3：1注文に1配送）。
- `shipping_address_id` は phase14 `address.id` を参照（FK）。
- `shipping_status_id` は phase14 `shipping_statuses.id` を参照（FK）。
- `shipping_method_id` は本書 `shipping_methods.id` を参照（FK）。
- 配送先ユーザは `sales.user_id` から辿れるため、`deliveries.user_id` は持たない（RR-5：YAGNI で削除。ギフト配送はスコープ外）。
- `sales.shipping_address_id` は **`sales.user_id` が所有する `address` のみ参照可能**であることを Service 層バリデーションで強制する（RRR-7 対応）。

### インデックス方針（R-10 対応）
| インデックス | 用途 |
|-------------|------|
| `uk_deliveries_sales_id` | UNIQUE 制約として機能。sales からの逆引き（購入履歴・売上画面）も高速化 |
| `idx_deliveries_shipping_status_id` | ステータス別の一覧・集計（Console 配送管理画面） |
| `idx_deliveries_tracking_code` | 追跡番号での問合わせ検索 |
| `idx_deliveries_scheduled_date` | 配送予定日でのバッチ・一覧フィルタ |

---

## shipping_methods テーブル（新規：配送方法マスタ／R-9 対応）

| id | name | description |
|----|------|-------------|
| 1 | home_delivery | 宅配 |
| 2 | konbini_pickup | コンビニ受取 |
| 3 | dropoff | 置き配 |

phase14 の決済方法マスタ（`payment_methods`）と同じ方針で集計容易性を担保する。

---

## warehouses テーブル（新規：倉庫マスタ／RRR-3 対応）

倉庫マスタ未整備期の `inventories.warehouse_id NULL` による UNIQUE 一意性破綻（MySQL/PostgreSQL は NULL を区別する仕様で `(product_id, NULL)` が複数行入りうる）を回避するため、**ダミー倉庫を1行登録**しておく。

| id | name | description |
|----|------|-------------|
| 1 | default | 全社単一倉庫（倉庫マスタ整備までの暫定） |

将来 `inbounds.warehouse_id` で複数倉庫を扱う段階で本マスタにレコードを追加する。

---

## inbounds テーブル（新規：商品入荷管理／R-3 対応）

顧客への配送（`deliveries`）と責務を分離するための独立テーブル。

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | **NOT NULL** | 倉庫ID（`warehouses.id` への FK／RRR-3 対応：DEFAULT 1 でダミー倉庫を指す） |
| supplier_id | BIGINT | NULL | 仕入先ID（将来マスタ化） |
| quantity | INT | NOT NULL | 入荷数量（**`CHECK (quantity > 0)`**／RRR-10 対応） |
| inbounded_at | DATE | NOT NULL | 入荷日 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- 在庫加算は **Service 層で実装**（規約 1-1）。`inbounds` 登録・`inventories.quantity` 加算・`products.stock` 加算は同一トランザクションで実行（並行運用／RRR-1 対応）。
- `warehouse_id` はマスタ整備までは DEFAULT 1（ダミー倉庫）で運用。
- `supplier_id` のマスタ化は本フェーズのスコープ外。マスタ化されるまで NULL 許容。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_inbounds_product_id` | 商品別の入荷履歴取得 |
| `idx_inbounds_inbounded_at` | 期間別集計 |

---

## inventories テーブル（新規：商品×倉庫の現在在庫／RR-6 / RRR-1 / RRR-3 / RRR-8 対応）

r1 で曖昧だった「在庫テーブル」を本書で明示する。倉庫別在庫を将来扱うため、`product_id × warehouse_id` の複合キー構造とする。**並行運用フェーズでは書き込み正本だが、読み取り正本は引き続き `products.stock`**。

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | **NOT NULL** | 倉庫ID（`warehouses.id` への FK／RRR-3 対応：DEFAULT 1） |
| quantity | INT | NOT NULL | 現在在庫数（**`CHECK (quantity >= 0)`**／RRR-8 対応） |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `UNIQUE(product_id, warehouse_id)` 制約。`warehouse_id NOT NULL` のため NULL 重複問題なし（RRR-3 対応）。
- 倉庫マスタが整備されるまでは `warehouse_id = 1`（ダミー倉庫）の単一行で「全社在庫」を表現。倉庫マスタ整備後は `inbounds.warehouse_id` と整合する形で複数行に分割する。
- **`products.stock` は本書 r3 では廃止しない**。本書 r3 では並行運用で導入し、`products.stock` 廃止は phase14 r2 に切り出す（RRR-1 対応）。
- 在庫減算（販売時）は phase14 の購入処理 Service が `products.stock` 側で実施。本書では加算方向（入荷）のみ責任を持ち、`inventories` と `products.stock` の両方に加算する。
- 在庫減算の同時実行制御方針：**`SELECT ... FOR UPDATE`（悲観ロック）** を採用（RRR-8 対応）。販売側が `inventories` を読み取り正本化する時期（phase14 r2）で揺れないよう、本書で先行明記する。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `uk_inventories_product_warehouse` | UNIQUE 制約。商品×倉庫の在庫一意性 |
| `idx_inventories_product_id` | 商品別の現在在庫参照（Market 在庫表示・予約ステータス判定） |

---

# 配送ステータス遷移ルール（R-4 / R-7 / RR-1 対応）

## ステータス（phase14 マスタに統一）
PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED

本フェーズでは **キャンセル / 配達失敗 / 再配達は扱わない**（前掲「スコープ外」表）。`shipping_statuses` マスタ拡張が必要なため、phase14 r2 で別途検討する。

## 遷移可否（Service 層でガード）

| 現在 → 次 | PENDING | SHIPPED | DELIVERED | RETURN_REQUESTED | RETURNED |
|-----------|---------|---------|-----------|------------------|----------|
| PENDING | - | ✅ | ❌ | ❌ | ❌ |
| SHIPPED | ❌ | - | ✅ | ❌ | ❌ |
| DELIVERED | ❌ | ❌ | - | ✅ | ❌ |
| RETURN_REQUESTED | ❌ | ❌ | ❌ | - | ✅ |
| RETURNED | ❌ | ❌ | ❌ | ❌ | - |

- `DELIVERED → PENDING` のような巻き戻しは Service 層で例外を投げて拒否（規約 4-2 異常系）。
- `RETURN_REQUESTED` への遷移は phase14 `sales_return.status = REQUESTED` と連動して発生する。

---

# 配送予定日の計算仕様（R-6 / RR-2 / RR-8 / RRR-5 対応）

| 項目 | 仕様 |
|------|------|
| 計算タイミング | **注文確定時に Core 側で算出**し `deliveries.scheduled_date` を初期値設定。出荷時点で前後する場合・入荷再計算時は更新 API で上書き。 |
| 入力 | (a) 注文日, (b) phase14 `address.prefecture`（地域マスタ代替）, (c) **`products.stock`（並行運用期の読み取り正本／RRR-1）**, (d) `shipping_methods` の標準リードタイム |
| 計算ロジックの所在 | `DeliveryScheduleService.calculate(...)` に集約（規約 1-1） |
| 在庫切れ時 | `scheduled_date = NULL`。Market は「入荷待ち」として表示。入荷時（`inbounds` 登録時）に再計算（**注文日昇順 FIFO**／RRR-4）。 |
| 設定値（リードタイム） | **初期は `config/app/Delivery.php`（PHP）／ `application.yml`（Java）に定数として定義**（規約 3-1）。47都道府県 × 配送方法分のテーブルを config に持つ。**将来、季節要因・キャンペーン等で頻繁な変更需要が出てきた段階でマスタテーブル（`shipping_lead_times`）化する**（RR-8 対応：段階的方針） |
| 変更履歴 | `scheduled_date` の変更（手動修正・入荷再計算・出荷遅延）はすべて `operation_logs.action = update_scheduled_date` で記録。`comment` は `[manual]` / `[inbound_recalc]` / `[shipping_delay]` プレフィックス付き（RR-2 / RRR-5 対応） |

---

# 技術検討事項
- 配送ステータスの遷移ルール（上記表で確定。キャンセル等はスコープ外）
- 配送予定日の自動計算（上記仕様で確定。地域マスタは phase14 `address.prefecture` を流用）
- `tracking_code` の生成方式：**配送業者の発行番号をそのまま登録**（自動採番しない）。手動入力 or 外部 API 連携を想定。
- 入荷管理と在庫管理の連動：Service 層・同一トランザクションで実施（並行運用：`inventories` と `products.stock` の両方を更新）
- 配送情報の Market 反映タイミング：`deliveries` 更新時に同期反映（ポーリング不要。購入履歴 API が `deliveries` を直接 JOIN）
- 将来的な問い合わせ管理画面との統合：`inquiries.target_type='delivery'` 経由（phase18 と協議。下記「phase18 への要請事項」参照）
- 外部配送 API 連携（phase14 と同方針）
- **将来的な `inventory_movements` テーブル（在庫増減ログ）の追加**（RRR-6 対応）。販売・キャンセル・返品復元・棚卸補正の履歴を一元追跡するため、`inbounds` だけでは不足する側面を補う。phase14 r2 と合わせて検討する。
- **`products.stock` 廃止と `inventories` 完全移行**は phase14 r2 のスコープ（本書 r3 では並行運用に留める／RRR-1）

---

# TDDテストケース

## Amazia Core / JUnit

### 正常系
- 注文確定時に `DeliveryCreationService.createForSales(sales_id)` 経由で `deliveries` が `PENDING` で生成される（同一トランザクション／RR-4・RRR-2）
- 配送情報（`deliveries`）が正しく登録・更新・取得できる
- 配送ステータスの更新が正しく反映される（PENDING → SHIPPED → DELIVERED）
- 入荷情報（`inbounds`）登録時に `inventories.quantity` と `products.stock` の**両方**が正しく増加する（並行運用／同一トランザクション／RRR-1）
- 入荷登録により、在庫切れで `scheduled_date = NULL` だった `deliveries` の予定日が **`sales.created_at` 昇順 FIFO** で再計算される（RRR-4）
- 入荷数量が在庫切れ注文数より少ない場合、注文日が古いものから順に充足し、充足できなかった `deliveries.scheduled_date` は NULL のまま保持される
- `tracking_code` が正しく保存・取得できる
- 配送予定日が地域・配送方法・在庫状況を入力に正しく算出される
- 在庫切れ時に `scheduled_date = NULL` で登録される

### 異常系（R-7 / RRR-7 / RRR-8 / RRR-10 対応）
- **不正な配送ステータス遷移を拒否**：DELIVERED → PENDING、SHIPPED → PENDING など
- **存在しない `sales_id` での配送登録を拒否**（FK 違反 / Service 層チェック）
- **同一 `sales_id` での重複登録を拒否**（`UNIQUE(sales_id)` 制約による／RR-3）
- **`sales.shipping_address_id` が `sales.user_id` 所有でない `address` を指す場合に拒否**（Service 層バリデーション／RRR-7）
- **存在しない `product_id` での入荷登録を拒否**
- **`inbounds.quantity <= 0` の入荷登録を拒否**（`CHECK` 制約 + Service バリデーション／RRR-10）
- 入荷登録の在庫加算が失敗した場合、`inbounds` レコード・`products.stock` 更新もロールバックされる（並行運用のトランザクション境界の検証／RRR-1）
- `shipping_status_id` に存在しないマスタ ID を指定した場合の拒否
- `shipping_method_id` に存在しないマスタ ID を指定した場合の拒否
- 通常購入で在庫切れの注文から `DeliveryCreationService.createForSales` が呼ばれた場合、防御的に例外を投げる（phase14 バリデーション漏れ時のセーフティネット／RRR-2）
- `inventories.quantity` が負数になる更新を `CHECK (quantity >= 0)` で拒否（RRR-8）

## Amazia Console / PHPUnit
- 配送管理画面で配送情報が正しく表示される
- 配送ステータス更新時に `operation_logs` にレコードが記録される（action / target_type / target_id / screen_name / api_name）
- 配送先変更時に `operation_logs` にレコードが記録される（旧住所・新住所が comment に含まれる）
- **配送予定日変更時に `operation_logs.action = update_scheduled_date` で記録され、`comment` 先頭が `[manual]` / `[inbound_recalc]` / `[shipping_delay]` のいずれかである**（RR-2 / RRR-5）
- 入荷登録時に `operation_logs` にレコードが記録される
- 不正な遷移を Console から要求した場合、エラーレスポンスが返る

## Amazia Market / PHPUnit
- 購入履歴に配送情報（ステータス・予定日・完了日・方法）が正しく表示される
- 配送ステータスが phase14 マスタの表示名で表示される
- 配送方法（home_delivery / konbini_pickup / dropoff）が正しく表示名で表示される
- 在庫切れ商品の購入履歴で `scheduled_date` が NULL のとき「入荷待ち」と表示される

> 注：`deliveries` は注文確定と同時に生成される（RR-4）ため、r1 にあった「未生成時フォールバック」のテストケースは r2 で削除した。

---

# phase18（問い合わせ管理）への要請事項

R-2 を実現するため、phase18 設計書の `inquiries` テーブルに以下の追加を要請する。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| target_type | VARCHAR(50) | 問い合わせ対象種別（`delivery` / `product` / `sales` ...） |
| target_id | BIGINT | 対象ID（`target_type` と組み合わせて参照） |

- 配送に関する問い合わせ：`target_type='delivery' / target_id=deliveries.id`
- 既に phase18 の `inquiry_messages` がスレッド管理を担うため、`deliveries` 側に `inquiry_id` は持たせない。

## 依存関係解決のプロセス（RR-7 対応）
- phase18 設計書 r1（`inquiries.target_type` / `target_id` 追加版）を **本フェーズ実装着手前に作成・確定する**ことを前提とする。
- ただし phase18 の実装自体は本フェーズと並行・後続でも構わない。
- phase15 実装着手時点で phase18 が未実装の場合：
  - `deliveries` 側には何も持たないため、phase15 単独のテストには影響なし。
  - 配送 ↔ 問い合わせ連携の動作確認（E2E）は phase18 実装完了後に実施する旨を本書に明記する。

---

# phase14 r2 への要請事項（RRR-1 / RRR-2 / RRR-5 / RRR-6 対応）

本書 r3 で並行運用に格下げ・スコープ外とした論点を、phase14 r2 で確定する必要がある。phase14 r2 の作成時に以下を盛り込むよう要請する。

| 項目 | 内容 |
|------|------|
| `products.stock` 廃止と `inventories` 完全移行 | 販売処理・予約ステータス判定（`BACK_ORDER` / `SOLD_OUT`）・返品時在庫戻しを `inventories` 参照に書き換える |
| 注文確定フローの確定 | バリデーション・在庫減算・`sales` INSERT・`DeliveryCreationService.createForSales` 呼び出しの順序とトランザクション境界 |
| `shipping_statuses` マスタ拡張 | キャンセル（CANCELED）・配達失敗（DELIVERY_FAILED）・再配達系ステータスの追加 |
| `operation_logs` のスキーマ拡張 | `update_scheduled_date` の reason 集計需要が高まった段階で `reason_code` カラムを追加（RRR-5） |
| `inventory_movements` テーブル | 在庫増減ログの一元化（販売・キャンセル・返品復元・棚卸補正／RRR-6） |

---

# 共通命名規約への提案（RR-10 / RRR-9 対応）

phase14 で `operation_logs` に `screen_name` / `api_name` が追加されたが、両カラムに入る具体値の命名規約は phase14 / phase15 のいずれにも未定義。テストでアサートする値が揺れるため、以下の命名規約を **共通 ai_context（例：`docs/ai_context/operation_logs_naming.md`）に定義することを提案**する。

| カラム | 規約案 | 例 |
|--------|--------|----|
| `screen_name` | `{App}{Domain}{Action}Page`（PascalCase） | `ConsoleDeliveryListPage` / `ConsoleDeliveryDetailPage` / `ConsoleInboundCreatePage` |
| `api_name` | `{HTTP_METHOD} {path}`（path は実 URL） | `POST /api/console/deliveries/{id}/status` / `PUT /api/console/deliveries/{id}/scheduled_date` |

### Vue コンポーネントパス → `screen_name` のマッピングルール（RRR-9 対応）
- Vue ページコンポーネント `pages/{Domain}/{Action}.vue`（例：`pages/Delivery/List.vue`）から `screen_name` を機械的に生成するルールを定義する：
  - `{App}` … 実行アプリ名（`Console` / `Market`）
  - `{Domain}` … コンポーネントが属するドメイン（`Delivery` / `Inbound` / ...）
  - `{Action}` … コンポーネント基底名（`List` / `Detail` / `Create` / ...）
  - 例：`Console` × `pages/Delivery/List.vue` → `ConsoleDeliveryListPage`
- AI 駆動開発時、コンポーネントから `screen_name` を機械的に推論できるため、テストアサート値のブレを防げる。

本書スコープ外（共通規約）として扱うが、phase14 / phase15 のテスト実装前に確定が望ましい。

---

# レビューコメント対応サマリ（r1 → r2 → r3）

## r1 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| R-1 | 🔴 必須 | `deliveries` を「実配送」、`sales` を「注文スナップショット」と責務分離。住所は `address.id` FK、ステータスは `shipping_statuses.id` FK 化 |
| R-2 | 🔴 必須 | `tracking_code` を配送追跡番号に限定。問い合わせ連携は `inquiries.target_type/target_id` 方式（phase18 へ要請） |
| R-3 | 🔴 必須 | `inbounds` テーブル新規追加。在庫加算は Service 層・同一トランザクション |
| R-4 | 🟡 推奨 | 配送ステータスを phase14 マスタ（PENDING/SHIPPED/DELIVERED/RETURN_REQUESTED/RETURNED）に統一 |
| R-5 | 🟢 任意 | r1 では `deliveries.user_id` の理由を明記したが、r2 で RR-5 を受けて削除（YAGNI） |
| R-6 | 🟡 推奨 | 配送予定日の計算仕様（タイミング・入力・所在・在庫切れ時挙動）を明文化 |
| R-7 | 🟡 推奨 | 異常系テスト（不正遷移・FK 違反・トランザクション境界・マスタ不整合）を追加 |
| R-8 | 🟡 推奨 | `operation_logs` 記録対象（ステータス更新・住所変更・追跡番号登録・入荷登録）を明記 |
| R-9 | 🟢 任意 | `shipping_methods` マスタ化。`deliveries.shipping_method_id` で参照 |
| R-10 | 🟢 任意 | インデックス方針を追記 |
| R-11 | 🟢 任意 | Market 表示は `deliveries` を主に整理（r2 で生成タイミング確定済のため、フォールバックは保険扱い） |
| R-12 | 🟢 任意 | 冒頭の余分な空行を削除 |

## r2 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| RR-1 | 🟡 推奨 | キャンセル・配達失敗・再配達は本フェーズのスコープ外として明示 |
| RR-2 | 🟡 推奨 | 配送予定日変更時に `operation_logs.action = update_scheduled_date` を記録 |
| RR-3 | 🟡 推奨 | `sales : deliveries = 1 : 1` を確定。`UNIQUE(sales_id)` 制約 |
| RR-4 | 🟡 推奨 | `deliveries` は注文確定と同時に生成 |
| RR-5 | 🟢 任意 | `deliveries.user_id` を YAGNI で削除 |
| RR-6 | 🟢 任意 | 在庫テーブルとして `inventories` を明示 |
| RR-7 | 🟢 任意 | phase18 依存解決プロセスを明記 |
| RR-8 | 🟢 任意 | リードタイムは「初期 config 駆動・将来マスタ化」の段階方針 |
| RR-9 | 🟢 任意 | 改訂履歴の初版日付を補完（r3 で「初版日不明」に修正／RRR-11） |
| RR-10 | 🟢 任意 | `screen_name` / `api_name` の命名規約を共通 ai_context に定義する提案 |

## r3 で新規対応（RRR-1 〜 RRR-11）
| ID | 優先度 | 対応 |
|----|--------|------|
| RRR-1 | 🔴 必須 | `inventories` を**並行運用**で導入。`products.stock` 廃止と完全移行は phase14 r2 のスコープに切り出し。入荷時は `inventories` と `products.stock` を同一トランザクションで両方加算 |
| RRR-2 | 🔴 必須 | 注文確定フローは phase14 r2 が確定。本書は協調仕様（`DeliveryCreationService.createForSales` インターフェース・防御的バリデーション）のみ責任を持つ |
| RRR-3 | 🟡 推奨 | `warehouses` マスタを新規追加し、ダミー倉庫1行（id=1）を導入。`inventories.warehouse_id` / `inbounds.warehouse_id` を `NOT NULL DEFAULT 1` に変更し、NULL UNIQUE 一意性破綻を回避 |
| RRR-4 | 🟡 推奨 | 入荷再計算の優先度を **`sales.created_at` 昇順 FIFO** で確定 |
| RRR-5 | 🟡 推奨 | `update_scheduled_date` の `comment` プレフィックスを `[manual]` / `[inbound_recalc]` / `[shipping_delay]` に固定（enum 化）。本格的な集計需要が出た段階で `operation_logs.reason_code` 追加を phase14 r2 へ要請 |
| RRR-6 | 🟡 推奨 | 将来的な `inventory_movements`（在庫増減ログ）テーブル追加を技術検討事項に明記。phase14 r2 への要請事項にも記載 |
| RRR-7 | 🟢 任意 | `sales.shipping_address_id` は `sales.user_id` 所有の `address` のみ参照可能を Service 層バリデーションで強制 |
| RRR-8 | 🟢 任意 | `inventories.quantity` 減算時の同時実行制御方針として悲観ロック（`SELECT ... FOR UPDATE`）を採用。`CHECK (quantity >= 0)` 制約も追加 |
| RRR-9 | 🟢 任意 | 共通命名規約に Vue コンポーネントパス → `screen_name` のマッピングルールを追記提案 |
| RRR-10 | 🟢 任意 | `inbounds.quantity` に `CHECK (quantity > 0)` 制約と Service 層バリデーション |
| RRR-11 | 🟢 任意 | 改訂履歴の初版日付を「初版日不明（git 履歴未取得）」に修正（曖昧表現「下旬」を排除） |

---

# 再々々レビューコメント（r3 に対して）

レビュー日：2026-05-06
レビュー対象：phase15_delivery_management_r3.md
参照：phase14_shipping.md / phase18_inquiry_management.md / docs/coding_guidelines.md
前回までの指摘：R-1 〜 R-12（初版）／ RR-1 〜 RR-10（r1）／ RRR-1 〜 RRR-11（r2）

---

## 総評

RRR-1 〜 RRR-11 はすべて適切に反映されている。特に以下3点は r2 の懸念を解消する形で着地しており、設計品質は十分：

- **RRR-1（在庫モデル並行運用）**：完全移行を phase14 r2 に切り出し、本書では入荷時に `inventories` / `products.stock` の両方を同一トランザクションで加算する明快な方針
- **RRR-2（注文確定フロー協調）**：本書の責務を `DeliveryCreationService.createForSales` インターフェースに絞り、防御的バリデーション（in-bound check）まで残す多層防御
- **RRR-3（ダミー倉庫導入）**：`warehouses(id=1, name='default')` で NULL UNIQUE 問題を DB 制約レベルで根本解決

新設の **「phase14 r2 への要請事項」セクション**（435 行〜）は、本書 r3 で並行運用・スコープ外に格下げした論点を一覧化したチェックリストとして機能しており、phase14 r2 着手時の引き継ぎが明瞭。**設計書としてはこれで実装着手 OK レベル**。

ただし、新規追加された箇所と前回まで残っていた箇所を併せて精査すると、**致命的ではないが詰めておくと安全な論点**がいくつか残っているので、最終確認として下記を提示する。

---

## 🟡 中程度（実装直前に確認推奨）

### RRRR-1. 並行運用中の「`inventories` 初期データ」と整合性がどう保たれるか不明

r3 で `products.stock` を読み取り正本として残し、`inventories` を書き込み正本として並行運用するが、**`inventories` を本フェーズで新規追加した時点での初期値**が未定義。

- `inventories` テーブル作成直後は当然空。`inbounds` 登録があった商品だけ徐々に行が増える
- 既存商品の `products.stock` には実在庫があるはずだが、`inventories.quantity` には何も入っていない
- phase14 r2 で `products.stock` を廃止して `inventories` に切り替える瞬間、**既存の `products.stock` の値が `inventories` に存在しなければ全商品が在庫0扱いになる**

つまり、本書 r3 のマイグレーションで「**全 `products` × デフォルト倉庫の組み合わせで `inventories` 初期行を作成し、`products.stock` の現在値をコピーする**」初期データ投入が必要なはず。これが本書に明記されていない。

**推奨対応：** マイグレーション仕様に1セクション追加。

```
## マイグレーション仕様（並行運用開始時）
1. CREATE TABLE warehouses（ダミー1行 INSERT）
2. CREATE TABLE inventories
3. INSERT INTO inventories(product_id, warehouse_id, quantity)
   SELECT id, 1, stock FROM products
   （既存 products.stock を inventories に複製）
4. 以降、入荷登録 Service が両方更新
```

これがないと並行運用が成立しない。実装時に必ず気づく論点だが、設計書段階で明記しておきたい。

### RRRR-2. 並行運用期の「在庫減算は phase14 既存処理 = `products.stock` のみ更新」だと `inventories` がずれる

r3 の方針：
- 入荷加算：`inventories` と `products.stock` の**両方**を更新（書き込み正本は `inventories`、`products.stock` は同期更新）
- 在庫減算：phase14 既存処理が `products.stock` のみ更新（本書では責務外）

この場合、**減算は片方のみ・加算は両方**となり、時間が経つと `inventories.quantity > products.stock` に乖離が広がる。

例：
- 初期：`products.stock=10` / `inventories.quantity=10`
- 販売（5個減）：`products.stock=5` / `inventories.quantity=10` ← 既存処理が `inventories` を知らないので減らない
- 入荷（3個加）：`products.stock=8` / `inventories.quantity=13`
- ……乖離が累積

phase14 r2 が完全移行する時点で、`products.stock` から再コピーする必要が出る（毎回マイグレーション必要に）。

**推奨対応：** 以下のいずれか。

- (A) **販売側の在庫減算 Service にも本書のスコープで小さくフックを入れ**、`products.stock` 減算と同時に `inventories.quantity` も減算する。phase14 r2 で完全移行するときの差し戻し作業が不要になる
- (B) `inventories` を「真の正本」ではなく「入荷の累積記録」として位置付け、phase14 r2 で完全移行するときに改めて `products.stock` から再構築する設計だと割り切る。本書の `inventories.quantity` は**入荷総量から販売総量を引いていない値**になるため、混乱を避けるため `inventories.received_total` 等の別意味のカラム名にする

(A) の方が将来コストが低い。phase14 既存処理への影響も「販売 Service 内に1行 SQL を足す」程度で済む。本書のスコープがやや膨らむが、整合性のリスクを取るより安全。

私としては **(A) 推奨**。本書 r3 のスコープは「配送管理＋入荷」だが、整合性担保のため販売側にもごく小さなフック（在庫減算時に `inventories` も同時減算）を入れることは、並行運用の前提として現実的。

### RRRR-3. `DeliveryCreationService.createForSales` の引数仕様が `sales_id` のみで足りるか

r3 の協調仕様：
```
DeliveryCreationService.createForSales(sales_id)
```

このシグネチャだと `deliveries` 生成時に必要な情報をすべて `sales` から SELECT で引く前提。実際に必要な情報：

- `sales.user_id` → 配送先住所のオーナー判定に使う
- `sales.shipping_address_id` → `deliveries.shipping_address_id` にコピー
- `sales.product_id` → `DeliveryScheduleService.calculate()` の在庫判定に使う
- `sales` には `shipping_method_id` がない（phase14 r1 の `sales` テーブル設計を確認すると、配送方法のカラムが存在しない）

**`sales` テーブルに `shipping_method_id` が存在しないと `deliveries.shipping_method_id` の値を決められない**。phase14 の `sales` テーブル設計（phase14_shipping.md 198 行目以降）を見ると、`payment_method_id` はあるが `shipping_method_id` は無い。

**推奨対応：** いずれか。

- (A) `sales` テーブルに `shipping_method_id` を追加することを phase14 r2 へ要請（本書「phase14 r2 への要請事項」に追記）
- (B) `DeliveryCreationService.createForSales(sales_id, shipping_method_id)` で呼び出し元から渡す
- (C) Market の注文確定 API リクエストで `shipping_method_id` を受け取り、`sales` には保存せず直接 `deliveries` に書く

(A) が責務分担としてきれいだが phase14 r2 の作業が増える。(B)/(C) は本書だけで完結する。実装着手前に決定を。

### RRRR-4. 入荷再計算時の `DeliveryScheduleService.calculate` 入力の在庫値に時間ずれがある

RRR-4 の対応で「`sales.created_at` 昇順 FIFO で `scheduled_date` を再計算」となったが、再計算ロジックを擬似コードで考えると：

```
on inbound:
  inventories.quantity += inbound.quantity  // 加算
  candidates = SELECT * FROM deliveries
               WHERE product_id = inbound.product_id
                 AND scheduled_date IS NULL
               ORDER BY sales.created_at ASC
  for each d in candidates:
    if inventory enough:
      d.scheduled_date = DeliveryScheduleService.calculate(...)
      inventory -= 1（消費）
```

ここで気になるのが、`DeliveryScheduleService.calculate` の入力に並行運用期は **`products.stock`** を使う（350 行目）と書かれている点。再計算ロジック内では「いま入荷してすぐ使う在庫」を扱うので、`products.stock` を読んでも、その値はループ中に更新されないと正しい判定ができない。

**推奨対応：** 入荷再計算時の `calculate` 呼び出しでは、**読み取り元を `inventories` の最新値（`FOR UPDATE` 取得）に切り替える**か、**Service 内のローカル変数で在庫充足量をシミュレートする**かのいずれかを明記。並行運用期だからといって読み取り元を `products.stock` に固定すると再計算が正しく動かない。

### RRRR-5. ダミー倉庫前提でも `inbounds.warehouse_id` を Console UI でユーザーに入力させるかが未定義

r3 で `inbounds.warehouse_id NOT NULL DEFAULT 1` となったが、Console の入荷登録 UI 仕様（71-74 行目「入荷日・数量・倉庫情報・仕入先」）では「倉庫情報」を入力対象としている。

- 倉庫マスタが `warehouses(id=1, name='default')` の1行のみの状態で、UI に倉庫選択フィールドを出すか
- 当面はバックエンドが DEFAULT 1 で勝手に埋める（UI には倉庫フィールドを出さない）か

明示されていない。実装時の UI 設計が揺れる。

**推奨対応：** 「倉庫マスタが1行のみの間は、Console 入荷登録 UI に倉庫選択フィールドを表示しない（バックエンドが DEFAULT 1 を自動セット）。`warehouses` のレコード数が2行以上になった時点でフィールドを追加する」を本書に1行明記。

---

## 🟢 軽微（任意）

### RRRR-6. 「販売 Service への小さなフック」の有無で本書のスコープ判断が変わる

RRRR-2 の (A) を採用するなら、本書のスコープ表記（「配送管理＋入荷」）に「並行運用のための販売側在庫同期」を追記する必要がある。RRRR-2 の判断と連動して整理推奨。

### RRRR-7. テストケースに「並行運用の整合性確認」が不足

並行運用がメインの方針となった以上、`inventories` と `products.stock` の値が**どのタイミングでも一致する**ことを確認するテストが必要。

**推奨追加テスト：**
- 入荷登録後、`inventories.quantity == products.stock`（同一商品・同一倉庫）
- 入荷登録の途中で例外が発生した場合、`inventories` も `products.stock` も両方ロールバックされる
- マイグレーション直後（`inventories` 初期化直後）、`inventories.quantity == products.stock`

RRRR-1 のマイグレーション仕様と合わせて Core / JUnit テストに追加するのが望ましい。

### RRRR-8. `update_scheduled_date` の `comment` プレフィックス値を config 化したのに、テストはハードコード

165 行目で「reason プレフィックス値は `config/app/Delivery.php` に enum 定義」とした一方、テストは `[inbound_recalc]` を文字列としてハードコードしている記述（400 行目）。規約 4-1「テスト内で URL や設定値をハードコードせず、`config()` / `@Value` 経由で取得する」と整合させるため、テストも config 経由でアサート値を取得すべき。

**推奨対応：** TDD テストケースの注釈に「`config('delivery.scheduled_date_reasons.inbound_recalc')` 等で取得した値とアサート」と明記。

### RRRR-9. `inventory_movements` の将来課題は本書スコープ外であることを明記しているが、命名衝突に注意

「将来 `inventory_movements` テーブル追加」と本書で言及したが、すでにオープンソース ERP（Odoo / ERPNext 等）に同名テーブルが存在する。設計時に他システムからの参照例を引きやすい反面、独自仕様が混ざると参照先と紛らわしい。

**推奨対応：** 軽微なので命名は phase14 r2 で決める形でよい。コメントとして本書末尾の要請事項に「命名は他 ERP の慣習との混同を避けるよう確認」と1行残せると親切。

---

## まとめ：r4 が必要かどうかの判定

| 優先度 | 項目 | r4 必要性 |
|--------|------|----------|
| 🟡 推奨 | RRRR-1（マイグレーション仕様＝初期データ投入） | **明記推奨**（実装で確実にぶつかる） |
| 🟡 推奨 | RRRR-2（販売側 `inventories` 減算フック） | **判断必要**（しないなら整合性リスクを許容する旨を明記） |
| 🟡 推奨 | RRRR-3（`shipping_method_id` の所在） | **明記推奨**（phase14 sales に追加 / 引数で渡す / 直接 `deliveries` のいずれか） |
| 🟡 推奨 | RRRR-4（再計算時の在庫読み取り元） | **明記推奨**（並行運用期も再計算は `inventories` を見る） |
| 🟡 推奨 | RRRR-5（Console 入荷 UI の倉庫フィールド） | **明記推奨**（軽微だが UI 仕様の前提として有用） |
| 🟢 任意 | RRRR-6 〜 RRRR-9 | 実装と並行で詰めてOK |

---

## 結論

r3 の方針は概ね妥当で、特に並行運用への切り出し判断は phase14 への波及リスクを最小化できている。これで実装フェーズに進んで大きな手戻りはないと判断できる。

ただし **RRRR-1（マイグレーション初期データ）** と **RRRR-2（販売側フック有無）** は並行運用の整合性に直結するため、**実装着手前に方針を明文化する**ことを強く推奨する。これらは r4 として軽量な追記で済むはず。

**RRRR-3（`shipping_method_id` の所在）** は本書スコープを越えるが、`DeliveryCreationService.createForSales(sales_id)` だけでは値を決められないため、引数追加 or phase14 r2 への要請に切り出す決定が必要。

**最低限：** 以下3点を r4 で追記すれば実装着手レベルとして完成。

1. マイグレーション仕様（`inventories` 初期データを `products.stock` から複製）
2. 販売側 `inventories` 減算フックの方針（やる / やらない）
3. `shipping_method_id` の調達方法（sales 拡張 / 引数追加 / 直接 `deliveries` 書き込み）

それ以外（RRRR-4 〜 RRRR-9）は実装と並行で詰めても致命的ではない。

