# フェーズ15：配送管理（改訂版 r2）

## ステータス
🔲 未着手

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 2026-04-下旬 | 初稿（フェーズ15配送管理の基本設計） |
| r1 | 2026-05-06 | レビューコメント R-1 〜 R-12 を反映。phase14_shipping.md との整合、tracking_code の責務分離、入荷管理（inbounds）の DB 設計追加、配送ステータスの統一、operation_logs 反映、異常系テスト追加など。 |
| r2 | 2026-05-06 | 再レビューコメント RR-1 〜 RR-10 を反映。ステータス遷移のスコープ宣言、配送予定日の operation_logs 記録、`sales:deliveries` の多重度確定、`deliveries` 生成タイミング確定、在庫テーブル仕様の明示など。 |

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

## 本フェーズのスコープ外（RR-1 対応）
以下は本フェーズで扱わない。将来フェーズ（または phase14 r2）で別途定義する。

| 機能 | 理由 / 取り扱い |
|------|----------------|
| 発送前キャンセル（注文取消による配送中止） | phase14 `shipping_statuses` に CANCELED 系ステータスが存在しない。phase14 r2 でマスタ追加が必要なため、本フェーズでは扱わない。 |
| 配達失敗・持ち戻り（DELIVERY_FAILED） | 同上。`shipping_statuses` マスタ拡張が前提。 |
| 再配達（配達日変更・複数回配達） | 上記と同じく phase14 r2 でのマスタ拡張が前提。本フェーズでは「初回配達のみ」を前提とする。 |
| 分割配送（1注文を複数の `deliveries` に分割） | `sales:deliveries = 1:1` を本フェーズの前提とするため範囲外（RR-3 参照）。 |
| ギフト配送（購入者≠配送先） | phase14 の `address.user_id` 制約や受取人モデルの拡張が必要なため、本フェーズでは扱わない。`deliveries.user_id` も持たない（RR-5 対応）。 |

---

# 全体方針（R-1 / R-2 対応）

## 🔹 phase14 設計との責務分担

phase14_shipping.md でメインに定義済みのテーブル（`sales` / `address` / `shipping_statuses` / `payment_methods`）と本フェーズで新規追加するテーブルの責務を以下のとおり明文化する。

| テーブル | 定義フェーズ | 役割 |
|---------|-------------|------|
| `sales` | phase14 | **注文時点のスナップショット**（住所マスタID・配送ステータスID・配送日希望など、購入確定時の情報） |
| `address` | phase14 | 住所マスタ。`is_active` で論理削除（履歴管理） |
| `shipping_statuses` | phase14 | 配送ステータスマスタ（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED） |
| `payment_methods` | phase14 | 決済方法マスタ |
| `deliveries` | **phase15（本書）** | **実配送オペレーション**（実際の発送日・配達完了日・配送業者の追跡番号など、配送実体としての情報） |
| `shipping_methods` | **phase15（本書）** | 配送方法マスタ（宅配 / コンビニ受取 / 置き配） |
| `inbounds` | **phase15（本書）** | 商品の入荷（仕入）管理。顧客への配送とは責務を分離 |
| `inventories` | **phase15（本書）** | **商品×倉庫の現在在庫**（RR-6 対応：在庫テーブルを明示） |

### 設計上の原則
- **`sales` は「注文確定」のスナップショット、`deliveries` は「配送実体」**。住所変更や再配達などの配送オペレーション情報は `deliveries` 側で扱う。
- 住所は VARCHAR で再保持せず、`address.id`（phase14）を FK 参照する。
- 配送ステータスは VARCHAR ではなく `shipping_statuses` マスタ（phase14）への FK（`shipping_status_id`）で持つ（規約 3-1「権限・定数・マスタは config／マスタ化」と整合）。

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
- 将来 1:N に拡張する場合は phase14 r2 / phase15 r3 で `UNIQUE` 制約を解除し、必要なら `deliveries.shipment_no` 等の番号体系を追加する。

## 🔹 deliveries の生成タイミング（RR-4 対応）

**注文確定と同時に `deliveries` レコードを生成する**（同一トランザクションで `sales` と1:1で同期作成）。

| 項目 | 仕様 |
|------|------|
| 生成タイミング | `sales` レコード作成と同一トランザクション |
| 生成時の `shipping_status_id` | `PENDING` |
| 生成時の `scheduled_date` | `DeliveryScheduleService.calculate(...)` で算出。在庫切れ時は NULL |
| 生成時の `tracking_code` / `shipped_date` / `delivered_date` | NULL |

**Market のフォールバック仕様（r1 で定義）について**：上記方針に従えば `deliveries` 未生成状態は理論上発生しないため、フォールバックロジックは「保険として残す」位置付けとし、Market のテストケースから「未生成時フォールバック」は外す（不要）。

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
- 在庫テーブル（`inventories`）と連動（**Service 層で在庫数を増加**。Model にロジックを書かない／規約 1-1）

### Console 操作の operation_logs 記録（R-8 / RR-2 対応）
phase14 の方針に倣い、以下の管理操作はすべて `operation_logs` に記録する（5W1H 追跡）。

| action | target_type | target_id | comment 例 |
|--------|-------------|-----------|-----------|
| `update_shipping_status` | `deliveries` | deliveries.id | 旧ステータス・新ステータス・理由 |
| `update_shipping_address` | `deliveries` | deliveries.id | 旧 address_id・新 address_id・理由（phase14 と整合） |
| `update_scheduled_date` | `deliveries` | deliveries.id | **旧予定日・新予定日・変更理由（手動 / 入荷再計算 / 出荷遅延）／RR-2 対応** |
| `register_tracking_code` | `deliveries` | deliveries.id | 登録した追跡番号 |
| `register_inbound` | `inbounds` | inbounds.id | 商品ID・数量・倉庫 |

`screen_name` / `api_name` の命名規約は phase14 / phase15 共通で別途定義する（RR-10：本書末尾「共通命名規約への提案」参照）。

---

## 🧠 Amazia Core

### 配送管理テーブル CRUD
- `deliveries` の CRUD を Core に実装。Console / Market 双方から利用。
- 配送ステータス遷移は Service 層で**遷移ルール**を管理（後述「配送ステータス遷移ルール」参照）。
- 配送予定日の自動計算は `DeliveryScheduleService` に集約（後述「配送予定日の計算仕様」）。
- 注文確定時、`sales` と同一トランザクションで `deliveries` を生成（RR-4）。

### 入荷テーブル CRUD
- `inbounds` の CRUD を Core に実装。
- 入荷登録時、Service 層で対象商品×倉庫の `inventories.quantity` を加算。`inbounds` レコード追加と在庫加算は同一トランザクションで実行。
- 入荷時、対象商品の在庫切れにより `scheduled_date = NULL` だった `deliveries` を再計算する（バッチではなく、入荷登録 Service 内で同期実行）。再計算時は `update_scheduled_date` を operation_logs に `comment='入荷再計算'` で記録。

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

## inbounds テーブル（新規：商品入荷管理／R-3 対応）

顧客への配送（`deliveries`）と責務を分離するための独立テーブル。

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | NULL | 倉庫ID（倉庫マスタ未整備の場合 NULL 許容。将来マスタ化） |
| supplier_id | BIGINT | NULL | 仕入先ID（将来マスタ化） |
| quantity | INT | NOT NULL | 入荷数量 |
| inbounded_at | DATE | NOT NULL | 入荷日 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- 在庫加算は **Service 層で実装**（規約 1-1）。`inbounds` 登録と `inventories.quantity` 加算は同一トランザクションで実行。
- `warehouse_id` / `supplier_id` のマスタ化は本フェーズのスコープ外。マスタ化されるまで NULL 許容。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_inbounds_product_id` | 商品別の入荷履歴取得 |
| `idx_inbounds_inbounded_at` | 期間別集計 |

---

## inventories テーブル（新規：商品×倉庫の現在在庫／RR-6 対応）

r1 で曖昧だった「在庫テーブル」を本書で明示する。倉庫別在庫を将来扱うため、`product_id × warehouse_id` の複合キー構造とする。

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | NULL | 倉庫ID。倉庫マスタ未整備の間は NULL（=「全社単一倉庫」を表す） |
| quantity | INT | NOT NULL | 現在在庫数 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `UNIQUE(product_id, warehouse_id)` 制約。
- 倉庫マスタが整備されるまでは `warehouse_id IS NULL` の単一行で「全社在庫」を表現。倉庫マスタ整備後は `inbounds.warehouse_id` と整合する形で複数行に分割する。
- 既存に `products.stock` カラムが残っている場合、本フェーズで `inventories` に集約し、`products.stock` は廃止する（移行マイグレーションを設ける）。
- 在庫減算（販売時）は phase14 の購入処理 Service と整合させる。本書では加算方向（入荷）のみ責任を持つ。

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

# 配送予定日の計算仕様（R-6 / RR-2 / RR-8 対応）

| 項目 | 仕様 |
|------|------|
| 計算タイミング | **注文確定時に Core 側で算出**し `deliveries.scheduled_date` を初期値設定。出荷時点で前後する場合・入荷再計算時は更新 API で上書き。 |
| 入力 | (a) 注文日, (b) phase14 `address.prefecture`（地域マスタ代替）, (c) `inventories.quantity`（在庫有無）, (d) `shipping_methods` の標準リードタイム |
| 計算ロジックの所在 | `DeliveryScheduleService.calculate(...)` に集約（規約 1-1） |
| 在庫切れ時 | `scheduled_date = NULL`。Market は「入荷待ち」として表示。入荷時（`inbounds` 登録時）に再計算。 |
| 設定値（リードタイム） | **初期は `config/app/Delivery.php`（PHP）／ `application.yml`（Java）に定数として定義**（規約 3-1）。47都道府県 × 配送方法分のテーブルを config に持つ。**将来、季節要因・キャンペーン等で頻繁な変更需要が出てきた段階でマスタテーブル（`shipping_lead_times`）化する**（RR-8 対応：段階的方針） |
| 変更履歴 | `scheduled_date` の変更（手動修正・入荷再計算・出荷遅延）はすべて `operation_logs.action = update_scheduled_date` で記録（RR-2 対応） |

---

# 技術検討事項
- 配送ステータスの遷移ルール（上記表で確定。キャンセル等はスコープ外）
- 配送予定日の自動計算（上記仕様で確定。地域マスタは phase14 `address.prefecture` を流用）
- `tracking_code` の生成方式：**配送業者の発行番号をそのまま登録**（自動採番しない）。手動入力 or 外部 API 連携を想定。
- 入荷管理と在庫管理の連動：Service 層・同一トランザクションで実施（`inventories` を更新）
- 配送情報の Market 反映タイミング：`deliveries` 更新時に同期反映（ポーリング不要。購入履歴 API が `deliveries` を直接 JOIN）
- 将来的な問い合わせ管理画面との統合：`inquiries.target_type='delivery'` 経由（phase18 と協議。下記「phase18 への要請事項」参照）
- 外部配送 API 連携（phase14 と同方針）

---

# TDDテストケース

## Amazia Core / JUnit

### 正常系
- 注文確定と同時に `deliveries` が `PENDING` で生成される（`sales` と同一トランザクション／RR-4）
- 配送情報（`deliveries`）が正しく登録・更新・取得できる
- 配送ステータスの更新が正しく反映される（PENDING → SHIPPED → DELIVERED）
- 入荷情報（`inbounds`）登録時に `inventories.quantity` が正しく増加する（同一トランザクション）
- 入荷登録により、在庫切れで `scheduled_date = NULL` だった `deliveries` の予定日が再計算される
- `tracking_code` が正しく保存・取得できる
- 配送予定日が地域・配送方法・在庫状況を入力に正しく算出される
- 在庫切れ時に `scheduled_date = NULL` で登録される

### 異常系（R-7 対応）
- **不正な配送ステータス遷移を拒否**：DELIVERED → PENDING、SHIPPED → PENDING など
- **存在しない `sales_id` での配送登録を拒否**（FK 違反 / Service 層チェック）
- **同一 `sales_id` での重複登録を拒否**（`UNIQUE(sales_id)` 制約による／RR-3）
- **存在しない `product_id` での入荷登録を拒否**
- 入荷登録の在庫加算（`inventories` 更新）が失敗した場合、`inbounds` レコードもロールバックされる（トランザクション境界の検証）
- `shipping_status_id` に存在しないマスタ ID を指定した場合の拒否
- `shipping_method_id` に存在しないマスタ ID を指定した場合の拒否

## Amazia Console / PHPUnit
- 配送管理画面で配送情報が正しく表示される
- 配送ステータス更新時に `operation_logs` にレコードが記録される（action / target_type / target_id / screen_name / api_name）
- 配送先変更時に `operation_logs` にレコードが記録される（旧住所・新住所が comment に含まれる）
- **配送予定日変更時に `operation_logs.action = update_scheduled_date` で記録される**（旧予定日・新予定日・変更理由を comment に含む／RR-2）
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

# 共通命名規約への提案（RR-10 対応）

phase14 で `operation_logs` に `screen_name` / `api_name` が追加されたが、両カラムに入る具体値の命名規約は phase14 / phase15 のいずれにも未定義。テストでアサートする値が揺れるため、以下の命名規約を **共通 ai_context（例：`docs/ai_context/operation_logs_naming.md`）に定義することを提案**する。

| カラム | 規約案 | 例 |
|--------|--------|----|
| `screen_name` | `{App}{Domain}{Action}Page`（PascalCase） | `ConsoleDeliveryListPage` / `ConsoleDeliveryDetailPage` / `ConsoleInboundCreatePage` |
| `api_name` | `{HTTP_METHOD} {path}`（path は実 URL） | `POST /api/console/deliveries/{id}/status` / `PUT /api/console/deliveries/{id}/scheduled_date` |

本書スコープ外（共通規約）として扱うが、phase14 / phase15 のテスト実装前に確定が望ましい。

---

# レビューコメント対応サマリ（r1 → r2）

## r1 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| R-1 | 🔴 必須 | `deliveries` を「実配送」、`sales` を「注文スナップショット」と責務分離。住所は `address.id` FK、ステータスは `shipping_statuses.id` FK 化 |
| R-2 | 🔴 必須 | `tracking_code` を配送追跡番号に限定。問い合わせ連携は `inquiries.target_type/target_id` 方式（phase18 へ要請） |
| R-3 | 🔴 必須 | `inbounds` テーブル新規追加。在庫加算は Service 層・同一トランザクション |
| R-4 | 🟡 推奨 | 配送ステータスを phase14 マスタ（PENDING/SHIPPED/DELIVERED/RETURN_REQUESTED/RETURNED）に統一 |
| R-5 | 🟢 任意 | r1 では `deliveries.user_id` の理由を明記したが、r2 では RR-5 を受けて削除（YAGNI） |
| R-6 | 🟡 推奨 | 配送予定日の計算仕様（タイミング・入力・所在・在庫切れ時挙動）を明文化 |
| R-7 | 🟡 推奨 | 異常系テスト（不正遷移・FK 違反・トランザクション境界・マスタ不整合）を追加 |
| R-8 | 🟡 推奨 | `operation_logs` 記録対象（ステータス更新・住所変更・追跡番号登録・入荷登録）を明記 |
| R-9 | 🟢 任意 | `shipping_methods` マスタ化。`deliveries.shipping_method_id` で参照 |
| R-10 | 🟢 任意 | インデックス方針を追記 |
| R-11 | 🟢 任意 | Market 表示は `deliveries` を主に整理（r2 では RR-4 で生成タイミング確定済のため、フォールバックは保険扱い） |
| R-12 | 🟢 任意 | 冒頭の余分な空行を削除 |

## r2 で新規対応（RR-1 〜 RR-10）
| ID | 優先度 | 対応 |
|----|--------|------|
| RR-1 | 🟡 推奨 | キャンセル・配達失敗・再配達は **本フェーズのスコープ外**として明示。phase14 r2 で `shipping_statuses` マスタ拡張時に対応 |
| RR-2 | 🟡 推奨 | 配送予定日変更時に `operation_logs.action = update_scheduled_date` を記録するよう明記。テストケースも追加 |
| RR-3 | 🟡 推奨 | `sales : deliveries = 1 : 1` を確定。`UNIQUE(sales_id)` 制約を追加。分割配送はスコープ外 |
| RR-4 | 🟡 推奨 | `deliveries` は **注文確定と同時に生成**（`sales` と同一トランザクション）。Market フォールバックは保険扱いに格下げ |
| RR-5 | 🟢 任意 | `deliveries.user_id` を YAGNI で **削除**。ギフト配送はスコープ外 |
| RR-6 | 🟢 任意 | 在庫テーブルとして `inventories(product_id, warehouse_id, quantity)` を本フェーズで明示的に定義 |
| RR-7 | 🟢 任意 | phase18 依存解決プロセスを明記（phase18 r1 を本フェーズ着手前に確定／E2E は phase18 実装後） |
| RR-8 | 🟢 任意 | リードタイムは「初期は config 駆動、頻繁な変更需要が出たらマスタ化」と段階的方針を明記 |
| RR-9 | 🟢 任意 | 改訂履歴の「初版」日付を `2026-04-下旬` に補完（厳密な日付が判明したら更新） |
| RR-10 | 🟢 任意 | `screen_name` / `api_name` の命名規約を共通 ai_context に定義することを提案 |

---

# 再々レビューコメント（r2 に対して）

レビュー日：2026-05-06
レビュー対象：phase15_delivery_management_r2.md
参照：phase14_shipping.md / phase18_inquiry_management.md / docs/coding_guidelines.md
前回までの指摘：R-1 〜 R-12（初版）／ RR-1 〜 RR-10（r1）

---

## 総評

RR-1 〜 RR-10 はすべて反映され、特に RR-3（多重度）・RR-4（生成タイミング）・RR-6（`inventories` 明示）の論点はクリアに決着している。スコープ外宣言で議論を畳んだ判断（キャンセル / 配達失敗 / 再配達 / 分割配送 / ギフト配送）も、本フェーズの範囲を絞って実装着手しやすくする観点で適切。

**設計品質としては実装着手 OK レベルに到達している**。ただし RR-6 で新規追加された `inventories` テーブルが既存設計（特に phase14 やそれ以前の `products.stock`）と接続される箇所にいくつか論点が残っており、これだけは r3 で詰めるか、別途「在庫管理移行設計書」を切り出して扱うべき。

以下、優先度順に再指摘する。

---

## 🔴 重大（実装で必ずぶつかる）

### RRR-1. `inventories` への移行が phase14 と未整合（影響範囲が本書を超える）

r2 で `inventories(product_id, warehouse_id, quantity)` を本フェーズで定義する判断は妥当だが、以下が宙に浮いている。

- 既存 `products.stock` の **廃止と移行マイグレーション**を本書スコープに含めると明言（242行目）しているが、phase14 の購入処理（在庫減算）は `products.stock` を前提に書かれている可能性が高い
- 「在庫減算（販売時）は phase14 の購入処理 Service と整合させる」（243行目）とあるが、**phase14 設計書の購入処理がどう変わるか**が未定義
- `BACK_ORDER` / `SOLD_OUT` 判定（phase14 予約ステータスロジック）も `products.stock` を見ている可能性が高く、ここも `inventories` に切り替えが必要

つまり、本フェーズで `products.stock` を廃止すると、**phase14 の予約ステータス判定と購入処理がデグレする可能性**がある。

**推奨対応：** 以下のいずれか。

- (A) `products.stock` 廃止は本フェーズではやらず、`inventories` を**並行運用**として導入。`inbounds` 登録時は両方更新するか、`inventories` のみ更新して読み取り側は `products.stock` のまま。完全移行は phase14 r2 と合わせて別途実施
- (B) 本フェーズで完全移行するなら、phase14 設計書の予約ステータス判定・購入処理・`sales_return` 返金時の在庫戻しなど、**`products.stock` を読み書きしている全箇所を `inventories` 参照に書き換える設計**を本書 or phase14 r2 に追記する

私としては **(A) 並行運用** を推奨。本フェーズの責務は「配送管理＋入荷」であり、在庫データモデルの全社的書き換えは phase14 r2 / 別フェーズで扱う方が安全。

### RRR-2. 注文確定時の `deliveries` 生成と在庫減算の順序が未定義

RR-4 で「注文確定と同時に `sales` と `deliveries` を同一トランザクションで生成」と決まったが、

- 在庫減算（`inventories.quantity` 減算）も同一トランザクションに含まれるはず
- 在庫切れ（`quantity = 0`）時、phase14 の予約フラグ（`is_preorder = true`）であれば `deliveries.scheduled_date = NULL` で生成される
- 通常購入で在庫切れの場合は注文自体が成立しないはず

ここの順序とガード条件が r2 内に明示されていない。`DeliveryScheduleService.calculate(...)` の入力に「在庫有無」があると書かれているが、**在庫が0の通常購入で注文が成立してしまう**と、`scheduled_date = NULL` の在庫無しレコードが生成され続ける。

**推奨対応：** 注文確定 Service のフロー（擬似コード）を本書または phase14 r2 で明示。

```
1. validateOrder(sales_request)
   - is_preorder == false かつ inventories.quantity <= 0 なら拒否
2. begin transaction
3. inventories.quantity -= order_quantity  (予約購入の場合は減算しない)
4. INSERT sales
5. INSERT deliveries (scheduled_date = DeliveryScheduleService.calculate())
6. commit
```

phase14 とまたがる論点なので、本書では「フローは phase14 r2 の購入処理セクションで定義」と明記し、依存先を示すだけでも可。

---

## 🟡 中程度（明確化推奨）

### RRR-3. `inventories` の倉庫マスタ未整備期の運用が運用トラブルを呼ぶ

r2 では「倉庫マスタ未整備の間は `warehouse_id IS NULL` の単一行で『全社在庫』を表現」（241行目）としているが、

- `UNIQUE(product_id, warehouse_id)` 制約は **DB によっては NULL 列の UNIQUE が複数許容**される（MySQL/PostgreSQL は NULL を区別する仕様で、`(1, NULL)` が複数行入りうる）
- 結果、運用初期に「同じ商品の在庫が2行以上できて、加算がどちらに乗ったか分からない」事故が起きやすい

**推奨対応：** いずれか。

- (A) 倉庫マスタ未整備期は **ダミー倉庫 `warehouses(id=0, name='default')` を1行登録**し、`warehouse_id NOT NULL DEFAULT 0` で運用する
- (B) MySQL の場合、`UNIQUE` 制約に加え、Service 層で「`warehouse_id` が NULL のレコードは1商品1行のみ」を強制するチェックを入れる

(A) の方が DB 制約だけで一意性を担保できるので推奨。`warehouses` マスタを最低限 1行だけ先行で作る判断はコスト低い。

### RRR-4. 入荷再計算の対象範囲が未定義

r2 の `inbounds` 登録 Service 内に「在庫切れにより `scheduled_date = NULL` だった `deliveries` を再計算する」（137行目）とあるが、

- 対象は **その商品の `deliveries` 全件**か、**先入れ先出しで在庫充足分のみ**か
- 入荷数量が在庫切れ注文数より少ない場合、どの `deliveries` を優先して再計算するか（注文日順？ 配送方法順？）

要件として未定義。実装時に揺れる。

**推奨対応：** 「注文日（`sales.created_at`）昇順で `inventories.quantity` を消費していき、充足できた `deliveries` のみ `scheduled_date` を更新する」など、優先度ルールを明記。

### RRR-5. `update_scheduled_date` の `comment` 区分が運用とテストで揺れる

r2 で `comment` の区分として「手動 / 入荷再計算 / 出荷遅延」（118行目）と書かれているが、

- 区分はフリーテキストの一部か、enum か
- enum なら、operation_logs にもう1カラム（`reason_code` 等）を持たせるべきでは？
- 集計（「どれだけの予定日変更が入荷再計算によるものか」など）をしたい場合、フリーテキストでは扱いにくい

**推奨対応：** 軽量に運ぶなら `comment` の先頭を `[手動]` `[入荷再計算]` `[出荷遅延]` などのプレフィックスに固定する規約を本書に明記。集計をしっかり扱うなら `operation_logs` 側のスキーマ拡張を phase14 r2 へ要請。

### RRR-6. `inventories` の更新履歴が残らない

r2 では `inventories.updated_at` のみで履歴が残らない構造だが、

- 監査要件（「いつ・何件・なぜ在庫が増減したか」）は `inbounds` で入荷側だけ追跡可能
- 在庫**減算**側（販売・キャンセル・返品復元）の履歴は `sales` / `sales_return` を辿るしかない
- 棚卸（手動補正）の履歴がどこにも残らない

**推奨対応：** 本フェーズのスコープ外でも、「将来 `inventory_movements` テーブル（増減ログ）の追加を検討」と本書の技術検討事項に1行追記。フェーズ間の認識齟齬を防ぐ。

---

## 🟢 軽微（改善提案）

### RRR-7. スコープ外宣言の「ギフト配送」と `sales.user_id` 単独運用の整合性

r2 で `deliveries.user_id` は削除（YAGNI）され、配送先は `sales.user_id` 経由で辿るとなった。これ自体は良いが、

- 「ギフト配送がスコープ外」と書く以上、**現状の `address` テーブルの `user_id` 制約**が「自分の住所しか登録できない」前提でよいか確認が必要
- phase14 の `address.user_id` が「住所の所有者」なら、自分の住所しか配送先にできない仕様で OK

**推奨対応：** 「`sales.shipping_address_id` は `sales.user_id` が所有する `address` のみ参照可能」を Service 層バリデーションで強制する旨を1行追記。

### RRR-8. `inventories.quantity` の負数ガード

`INT NOT NULL` で定義されているが、

- 同時注文による減算競合で負数になる可能性がある（楽観ロック / 悲観ロックの方針が未定義）
- DB 制約で `CHECK (quantity >= 0)` を入れる選択肢もある

**推奨対応：** 「在庫減算は `SELECT ... FOR UPDATE`（悲観ロック）または楽観ロック（`version` カラム）で同時実行制御する」方針を1行明記。`CHECK (quantity >= 0)` 制約は付ける派・付けない派あるが、付けておくとテストでバグを拾える。

### RRR-9. 共通命名規約の `screen_name` 例が Vue/PHP 規約と整合しているか

RR-10 対応の命名規約案で `ConsoleDeliveryListPage`（PascalCase）が示されているが、

- Console は Vue ベースのため、Vue コンポーネント命名（PascalCase）と揃うのは良い
- ただし、Vue のページコンポーネント（`pages/Delivery/List.vue`）と PascalCase 文字列のマッピングが暗黙
- マッピングルール（コンポーネント名から `screen_name` を生成する規則）を共通 ai_context に書くと、AI が機械的に推論できる

**推奨対応：** 共通 ai_context（`docs/ai_context/operation_logs_naming.md`）に、Vue コンポーネントパス → `screen_name` のマッピング例を1〜2行追加することを提案。

### RRR-10. `inbounds` の `quantity` に正の数のみ許容するガードがない

- `quantity` が0や負数の入荷登録は無意味だが、現状制約なし
- DB 制約 `CHECK (quantity > 0)` を入れる、または Service 層でバリデーション

**推奨対応：** Service 層でバリデーションする旨を1行明記。

### RRR-11. r2 の改訂履歴で「初版」が `2026-04-下旬`（曖昧表現）

RR-9 対応で日付を補完したものの「下旬」という曖昧表現は将来見返すときに不便。git log や設計書の Markdown フロントマターから初版コミット日を特定して、可能なら厳密な日付に置換するのが望ましい。

ただし本書スコープでは git 履歴が確認不能（プロジェクトが non-git）のため、「初版日不明」と明記するか現状維持で可。

---

## まとめ：r3 が必要かどうかの判定

| 優先度 | 項目 | r3 必要性 |
|--------|------|----------|
| 🔴 必須 | RRR-1（`products.stock` 廃止 vs 並行運用） | **必要**（phase14 への影響大） |
| 🔴 必須 | RRR-2（注文確定時の在庫減算フロー） | **必要**（phase14 r2 と協調） |
| 🟡 推奨 | RRR-3（`warehouse_id NULL` の UNIQUE 一意性） | r3 で解決可能 |
| 🟡 推奨 | RRR-4（入荷再計算の対象範囲） | r3 で解決可能 |
| 🟡 推奨 | RRR-5（`comment` 区分の構造化） | r3 で解決可能 |
| 🟡 推奨 | RRR-6（在庫増減ログ） | 将来課題明記でOK |
| 🟢 任意 | RRR-7 〜 RRR-11 | 実装と並行可 |

---

## 結論

r2 は本フェーズ単独で見れば実装着手レベルだが、**RRR-1 / RRR-2 で phase14 への波及が確認された**ため、本書だけを r3 化しても解決しない。以下のいずれかが必要：

- **(推奨) phase14 r2 を先行作成し、`products.stock` 廃止と注文確定フローを phase14 側で確定 → 本書 r3 で `inventories` 並行運用 or 完全移行を選択**
- (代替) 本書 r3 で `inventories` を**並行運用**に格下げし、`products.stock` 廃止は phase14 r2 のスコープに切り出す

特に **RRR-1（在庫モデル移行）は影響範囲が大きい**ので、ここを曖昧にしたまま実装着手すると、phase14 の予約ステータス判定・購入処理・返品処理が `inventories` を参照しないままデグレする。最低限「並行運用」の方針だけは r3 で確定することを強く推奨する。

それ以外（RRR-3 〜 RRR-11）は実装と並行で詰めても致命的ではない。

