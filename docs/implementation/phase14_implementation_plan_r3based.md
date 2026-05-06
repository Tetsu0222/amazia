# フェーズ14 実装計画（購入機能）

## 概要
- 対象設計書: `docs/design/phase11_20/phase14_shipping.md`（r3 / 2026-05-06、Step 0 内で r4 へ改訂予定）
- 対象範囲: Amazia Console / Amazia Market / Amazia Core / DB設計（9テーブル新設・改訂）
- 段取り: 設計書 r3 の **Step A → B → C → D → E** を厳守。本書では現コードベースとの差分を埋めるため **Step 0（前提整備）** を冒頭に追加する。
- 作成日: 2026-05-06
- 改訂: 2026-05-06（フェーズ13実装結果を踏まえた前提整理を追加：`market_customers` を user 参照先とすること、会員住所と配送先住所の分離、payment_method 案 B-1）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | 設計書 r3 の **Step 0 → A → B → C → D → E** を厳守。Step を跨いだ部分実装は禁止 |
| 規模感 | 9 テーブル新設/改訂、Core/Console/Market 全層、TDD 含めると相当量。1 Step ごとに区切って commit / 動作確認 |
| TDD | 設計書「TDDテストケース」セクションに列挙された全項目を Step ごとに割り当てて実装 |
| コーディング規約 | `docs/coding_guidelines.md` 厳守（Service にロジック寄せ・config 駆動・1ファイル1ユースケース） |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` + `phpunit.xml`（Console）+ `application-test.properties`（Core）をセット更新 |
| テスト値 | ハードコードせず `config()` / `@Value` 経由で取得 |
| メモリ事項 | Core 側 Heap 制限（`-Xmx384m`）を意識し、重い在庫一括処理（再構築 SQL 等）はバッチではなく単発トランザクションで実行する |
| 同時実行制御 | 在庫減算は必ず `SELECT ... FOR UPDATE` + ロック後再確認（CHECK 制約に頼らず Service 層で明示判定） |

---

## 1. Step 0 — 前提整備（設計書外だが現コードベースで必要）

### 背景
phase14 r3 設計書は以下を「既存」前提で記述しているが、現状コードには **未実装**。
- Core: `sales` / `sales_return` / `address` / `payment_methods` / `shipping_statuses` / `operation_logs` パッケージ未作成
- Console: 同関連マイグレーションなし（users / cache / jobs / personal_access_tokens のみ）
- Console `app/Sales/` は `GetSalesService` がモックデータを返すスタブのみ

Step A の「スキーマ変更先行」を成立させるため、土台を Step 0 として整備する。

### 1-1. 既存実装との整合性（フェーズ13調査結果）

フェーズ13で会員登録機能を実装済みで、以下が既存：

| 項目 | 既存実装 | phase14 での扱い |
|------|---------|-----------------|
| 会員テーブル | `market_customers`（id, name_last, name_first, postal_code, address(VARCHAR255), birthday, email, password_hash, payment_method, card_token, active_flag, ...） | **触らない**。会員住所は 1 顧客 1 住所のまま現状維持 |
| 会員住所マスタ | `market_customers.postal_code` / `address`（VARCHAR1カラム） | **触らない**。会員自身が編集する「現住所」として現状維持 |
| 会員の決済方法 | `market_customers.payment_method`（VARCHAR） | **触らない**。注文画面の「既定の決済方法」初期値として残す（案 B-1 確定） |
| Core パッケージ | `com.example.market.customer` 配下に Controller/Service/Entity/Repository/DTO 一式 | そのまま利用 |
| 管理画面ユーザ | `com.example.auth` 配下の `User` / `Role` / `Permission`（`users` テーブル） | Console 側の管理者として継続利用。`sales.user_id` の参照先ではない |

**確定方針（フェーズ13実装を踏まえた読み替え）**:
- `sales.user_id` の参照先 = **`market_customers.id`**（FK）
- `address` テーブルは設計書 r3 の通り **新設**するが、これは「**注文時の配送先住所スナップショット**」を保存するための独立マスタであり、`market_customers.address` とは **完全に別物**
- `address.user_id` の参照先 = **`market_customers.id`**（FK）。ただし設計書記載の `is_active` 運用や複数住所登録機能は本フェーズで成立させる（注文ごとにスナップショットを蓄積するため、結果的に複数行になる）
- `payment_methods` マスタを新設し、`sales.payment_method_id` で参照（注文ごとに決済方法を選択可能）
- `market_customers.payment_method` は「会員ごとの既定決済方法」として残置。注文画面の初期値として使用（案 B-1）

### 1-2. 共通命名規約の作成（P14-10 / R-10）
**新規ファイル**: `docs/ai_context/operation_logs_naming.md`

記載内容:
- `screen_name` の命名規則（例: `console.sales.list` / `console.delivery.update_status` / `market.checkout.confirm`）
- `api_name` の命名規則（例: `POST /api/orders/confirm` / `PATCH /api/deliveries/{id}/status`）
- phase14 / phase15 / 将来フェーズで利用される値の一覧
- 採番方針（小文字スネークケース・ドメイン名から始める）

### 1-3. 既存テーブル群のベース作成

#### Console（Laravel マイグレーション）
| テーブル | 内容 |
|---------|------|
| `address` | `id, user_id(FK to market_customers.id), postal_code, prefecture, city, address_line, building, is_active(BOOLEAN), created_at`。**会員住所とは独立した配送先住所マスタ** |
| `payment_methods` | マスタ。INSERT: `1=credit_card, 2=d_payment, 3=cash_on_delivery` |
| `shipping_statuses` | マスタ（base 形）。INSERT: `PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED`。CANCELED 等は Step A で追加 |
| `sales` | base 形（`user_id` は FK to `market_customers.id`、`quantity` / `payment_id UNIQUE` / `shipping_method_id` は Step A で追加） |
| `sales_return` | base 形（`quantity` は Step A で追加） |
| `operation_logs` | base 形（`screen_name` / `api_name` は Step A で追加）。`user_id` は管理者操作用に `users.id`（auth）参照 |
| `products.stock` カラム | 既に Core 側に存在するか確認。なければ追加（並行運用の起点） |

#### Core（Spring Boot Entity）
- 上記 Console 側スキーマに対応する JPA Entity / Repository を新規パッケージで作成
  - `com.example.sales`
  - `com.example.salesreturn`（または `sales` 配下）
  - `com.example.address`
  - `com.example.paymentmethod`
  - `com.example.shippingstatus`
  - `com.example.operationlog`

### 1-4. 設計書 phase14 r4 への改訂

Step 0 の作業として、設計書 `docs/design/phase11_20/phase14_shipping.md` を **r3 → r4** に改訂する。

**r4 で追加する内容**:
- 改訂履歴に r4 行を追加（日付・内容）
- 「`sales.user_id` の参照先 = `market_customers.id`」を明記
- 「`address.user_id` の参照先 = `market_customers.id`」を明記
- 「会員住所（`market_customers.address`）と配送先住所マスタ（新設 `address`）は完全に別物」を明記
- 「`market_customers.payment_method` は会員ごとの既定決済方法として残置。注文ごとの決済方法は `sales.payment_method_id` で個別選択（案 B-1）」を明記
- フェーズ13で実装済みの `market_customers` 関連実装を前提とすることを明記

### 1-5. Step 0 完了条件
- `php artisan migrate` で全テーブル作成成功
- `mvn test` で Entity 認識成功
- `docs/ai_context/operation_logs_naming.md` レビュー完了
- `phase14_shipping.md` r4 改訂完了

---

## 2. Step A — phase14 r3 のスキーマ変更先行

### 2-1. マイグレーション（Console）
- `sales.shipping_method_id BIGINT NOT NULL` 追加
  - FK to `shipping_methods.id`（Step B で実体作成のため、Step A 時点では FK 制約は仮置き or 遅延付与）
- `sales.quantity INT NOT NULL CHECK (quantity > 0)` 追加
- `sales.payment_id VARCHAR(100) NOT NULL UNIQUE` 追加
- `sales.payment_method_id` の FK 参照先 = `payment_methods.id`（Step 0 で作成済み）
- `sales.shipping_address_id` の FK 参照先 = `address.id`（Step 0 で作成済み）。住所はスナップショットのため、注文確定時に `market_customers.postal_code/address` から `address` テーブルへ複製した行の id を参照（または既存住所行を再利用）
- `sales_return.quantity INT NOT NULL CHECK (quantity > 0)` 追加
- `shipping_statuses` に `CANCELED` / `DELIVERY_FAILED` / `RESCHEDULED` を INSERT
  - **マスタ存在 ≠ 入力許容**（Q14-4）。Service 層の許容ステータスリストで制御
- `operation_logs.screen_name VARCHAR(100)` / `operation_logs.api_name VARCHAR(100)` 追加
- 注: `address.is_active` は r0 相当で追加済み扱い（S14-8）。Step 0 で投入済みのためここでは作業対象外

### 2-2. Core 側 JPA Entity 更新
- `Sales` Entity に `quantity / paymentId / shippingMethodId` フィールド追加
- `SalesReturn` Entity に `quantity` 追加
- `OperationLog` Entity に `screenName / apiName` 追加
- 各 Repository / DTO に対応するフィールド追加

### 2-3. UUID v7 採番ライブラリ選定（P14-13 / Q14-7 / Q14-14 / S14-5 / S14-11）
**Java（Core）候補**:
- `com.github.f4b6a3:uuid-creator`（Apache-2.0、Star/Release を Step A 着手時に確認）
- `com.fasterxml.uuid:java-uuid-generator`（Apache-2.0）

**PHP（Console）候補**:
- `ramsey/uuid` v4.7+（v7 対応、MIT）

選定基準（S14-11）:
- License: Apache-2.0 / MIT 推奨
- GitHub Star 数
- 最新リリース日（直近1年以内）

採用後:
- Core: `pom.xml` に追加
- Console: `composer.json` に追加
- `PaymentService.generatePaymentId()` を薄いラッパで隠蔽（後の本番決済 API 切替に備える）

### 2-4. config 化
**Console**: `config/app/Sales.php` 新規
- `payment_methods.credit_card_id` 等のマスタ ID
- `shipping_statuses.pending_id` 等
- 配送ステータス遷移許容リスト（Q14-4）

**Core**: `application.yml` の `amazia.sales` 配下
- 同上

`config/app.php` に `'sales' => require __DIR__.'/app/Sales.php'` を明示追加（規約 2-1 補足3）。

### 2-5. テスト（TDD）
正常系:
- マイグレーション直後、`shipping_statuses` マスタが 8 件（既存5 + 追加3）存在
- `sales.payment_id` に同じ値を 2 件 INSERT すると UNIQUE 違反

異常系:
- `sales.quantity = 0` を INSERT すると CHECK 制約違反
- `sales_return.quantity <= 0` を INSERT すると CHECK 制約違反

### 2-6. Step A 完了条件
- 全マイグレーション通過
- 既存機能（商品一覧 / ログイン等）が壊れていない
- UUID v7 採番ライブラリが pom.xml / composer.json に追加済み

---

## 3. Step B — phase15 r4 実装（配送・在庫並行運用）

設計書 phase15 r4 を本書 r3 の前提として実装する。phase15 設計書 `docs/design/phase11_20/phase15_delivery_management.md` も併せて参照。

### 3-1. テーブル作成（Console マイグレーション）
| テーブル | キーポイント |
|---------|-------------|
| `warehouses` | `(1, 'default', '全社単一倉庫')` ダミー1行 INSERT |
| `inventories` | `UNIQUE(product_id, warehouse_id)` / `CHECK (quantity >= 0)` / 初期データを `products.stock` から複製 |
| `inbounds` | `CHECK (quantity > 0)` |
| `shipping_methods` | INSERT: `1=home_delivery, 2=konbini_pickup, 3=dropoff` |
| `deliveries` | `UNIQUE(sales_id)` / 初期 `shipping_status_id = PENDING` |

### 3-2. Core 側 Service / Controller

#### com.example.inventory（既存パッケージ拡張）
- `InventorySyncService.applyDelta(productId, warehouseId, delta)`
  - `SELECT ... FOR UPDATE` で対象行ロック
  - `inventories.quantity += delta`（CHECK 制約で `quantity >= 0` 違反時は例外）
  - **過渡期のみ存在**。Step C → D で削除予定

#### com.example.delivery（新規）
- `DeliveryCreationService.createForSales(salesId)` — **長期シグネチャ**（r3 で確定）
  - `INSERT deliveries(sales_id, shipping_address_id, shipping_method_id, shipping_status_id=PENDING, scheduled_date=...)`
- `DeliveryStatusTransitionService` — 遷移ガード
  - `PENDING → SHIPPED` 遷移時、`sales.is_preorder == true` の場合のみ在庫減算フック呼び出し
  - 在庫不足時は **例外＋PENDING 維持**（S14-1 方針 A）
- `DeliveryScheduleService.calculate(...)` — 配送予定日算出
  - 注文確定時: `products.stock` 参照（並行運用期）
  - 入荷再計算時: `inventories` 最新値（`FOR UPDATE`）+ Service 内ローカル変数で消費トラッキング（RRRR-4）
- `UpdateShippingStatusController` / `UpdateShippingAddressController` / `RegisterTrackingCodeController` / `UpdateScheduledDateController`

#### com.example.inbound（新規）
- `RegisterInboundService.register(inboundRequest)`
  - `inbounds INSERT` + `inventories.quantity += n` + `products.stock += n`（同一トランザクション）
  - `scheduled_date IS NULL` の `deliveries` を `sales.created_at` 昇順 FIFO で再計算（RRR-4）
  - `operation_logs` に `register_inbound` を記録

#### com.example.sales（新規）
- `OrderConfirmationService.confirm(orderRequest)` — 設計書「注文確定フロー」擬似コード通り
  1. `validateOrder` — 予備バリデーション
     - **配送先住所のスナップショット作成**: `market_customers.postal_code/address` を `address` テーブルに **新規行として INSERT**（`is_active=true`）し、その id を `sales.shipping_address_id` にセット。これにより会員が後で住所変更しても過去注文の配送先は不変
     - 配送方法存在チェック / 在庫予備チェック
     - `address.user_id == order_request.user_id`（= `market_customers.id`）の検証
  2. `begin transaction`
  3. 通常購入の在庫減算（`SELECT ... FOR UPDATE` でロック後再確認 / S14-6）
  4. `INSERT sales`（payment_id UNIQUE 違反時の冪等処理 / S14-5）
  5. `DeliveryCreationService.createForSales(sales.id)`
  6. `commit`
- `PaymentService.generatePaymentId()` — UUID v7 採番
- `PaymentService.handleUniqueViolation(salesData)` — `user_id / product_id / quantity / amount` 全項目一致時のみ既存 sales を返す。不一致なら **エラーログ（要監視レベル）+ 例外**（S14-5）

##### 注文確定 API リクエスト形式（Market 側からの入力）
```json
{
  "product_id": 123,
  "quantity": 2,
  "payment_method_id": 1,         // 注文ごとに選択。Customer の既定値が初期表示されるが変更可能（B-1）
  "shipping_method_id": 1,
  "is_preorder": false
  // shipping_address は market_customers の現住所を Service 側で自動スナップショット
}
```

会員住所と異なる住所への配送（ギフト・実家配送等）は phase14 のスコープ外。phase15 r4 でも `address.user_id` 制約上スコープ外と明記されている。

#### com.example.salesreturn（新規）
- `RequestSalesReturnService` / `ApproveSalesReturnService` / `RejectSalesReturnService` / `RefundSalesReturnService`
- 返品承認時に `inventories.quantity += sales_return.quantity`（並行運用期は `products.stock` も同時加算）
- 通知状態（`notified_user / notified_admin`）の管理

### 3-3. Console 管理画面（PHP / Laravel）

#### Sales（既存スタブを実装データ化）
- `app/Sales/Service/GetSalesService.php` — Core API 経由で売上一覧取得
- 集計: 年/月/日 + 商品別/ユーザ別/決済方法別/配送方法別/数量集計
- 表示項目: ユーザ名 / 購入商品 / 数量 / 金額 / 配送日 / 売上日 / 配送ステータス / 決済方法 / 配送方法 / 予約 or 通常購入区分

#### Delivery（新規）
- `app/Delivery/Controller/UpdateShippingStatusController.php`
- `app/Delivery/Controller/UpdateShippingAddressController.php` — UI で `is_active=false` の住所をフィルタ（S14-10）
- `app/Delivery/Controller/UpdateScheduledDateController.php` — comment にプレフィックス `[manual]` / `[inbound_recalc]` / `[shipping_delay]`
- `app/Delivery/Controller/RegisterTrackingCodeController.php`

#### Inbound（新規）
- `app/Inbound/Controller/RegisterInboundController.php`
- UI に倉庫選択フィールド **出さない**（RRRR-5）。バックエンドが `warehouse_id=1` を自動セット

#### SalesReturn（新規）
- `app/SalesReturn/Controller/ListSalesReturnController.php`
- `app/SalesReturn/Controller/ApproveSalesReturnController.php`
- `app/SalesReturn/Controller/RejectSalesReturnController.php`

#### OperationLog（新規）
- `app/OperationLog/Controller/ListOperationLogController.php` — `screen_name` / `api_name` で検索可能

### 3-4. Market 購入導線（**ユーザー指示の核**「購入ボタン押下⇒未ログイン⇒ログイン画面」）

#### ProductDetail.jsx 改修
- 「購入する」ボタン追加
- 押下時:
  ```js
  const { user } = useAuth();
  if (!user) {
    navigate('/login', { state: { redirectTo: `/checkout/${productId}` } });
  } else {
    navigate(`/checkout/${productId}`, { state: { sku: selectedSku, quantity } });
  }
  ```

#### 新規ページ
- `features/checkout/pages/Checkout.jsx`
  - 配送先選択（`address` から `is_active=true` のみ表示）
  - 配送方法選択（home_delivery / konbini_pickup / dropoff）
  - 決済方法選択（credit_card / d_payment / cash_on_delivery）
  - 数量変更
  - 確認画面 → 注文確定 API
- `features/checkout/pages/Confirm.jsx` — 確認画面
- `features/checkout/pages/Complete.jsx` — 完了画面
- `features/orders/pages/PurchaseHistory.jsx` — 購入履歴（数量・配送方法・配送ステータス・配送予定日）

#### 予約ステータス表示
- Core API `/api/products/{id}/preorder-status` を新設
- ProductDetail / ProductList で表示分岐
  - PRE_ORDER: 「予約受付中」+ 発売日表示
  - BACK_ORDER: 「再入荷予約受付中」
  - SOLD_OUT: 「完売」（購入ボタン非表示）

#### Login.jsx 改修
- ログイン成功後、`location.state.redirectTo` があればそこへ遷移

### 3-5. テスト（TDD）

#### Core（JUnit）正常系
- 注文確定時に `deliveries` が PENDING で生成される（同一トランザクション）
- `is_preorder=false` の通常購入は注文時に在庫減算され、出荷時には再減算されない
- `is_preorder=true` の予約購入は注文時に減算されず、出荷時（SHIPPED 遷移時）に減算される
- `payment_id UNIQUE` 違反時、全項目一致なら既存 sales が返される（真の冪等）
- 配送ステータス遷移ガード（PENDING→SHIPPED→DELIVERED→RETURN_REQUESTED→RETURNED）
- 入荷登録による `scheduled_date NULL` の deliveries 再計算（FIFO）

#### Core 並行運用整合性テスト
- マイグレーション直後 `inventories.quantity == products.stock`
- 入荷 1 件ごとに不変条件維持
- 販売 1 件ごとに不変条件維持
- 返品復元 1 件ごとに不変条件維持

#### Core 異常系
- 通常購入で在庫切れの注文を予備バリデーションで拒否
- ロック取得後の在庫再確認で在庫不足を検知し例外（S14-6）
- `address.user_id != order_request.user_id` の住所を拒否
- `sales.shipping_method_id` がマスタに存在しない場合に拒否
- 未対応 `shipping_status_id` への遷移を拒否
- 不正な配送ステータス遷移を拒否（DELIVERED→PENDING 等）
- 同一 `sales_id` での重複 deliveries 登録を UNIQUE 違反で拒否
- `payment_id UNIQUE` 違反かつ他項目不一致で **エラーログ + 例外**（S14-5）
- 出荷時 `inventories.quantity < sales.quantity` で例外＋PENDING 維持（S14-1）

#### Console（PHPUnit）
- 配送管理画面で配送情報表示
- 配送ステータス更新時に `operation_logs` 記録
- 配送先変更画面で過去住所（`is_active=false`）が選択肢に **表示されない**（S14-10）
- API 直叩きで `is_active=false` の住所を指定した場合は通る（オーナー検証のみ）
- 入荷登録 UI に倉庫選択フィールドが **表示されない**（RRRR-5）
- `update_scheduled_date` の comment 先頭が `[manual]` / `[inbound_recalc]` / `[shipping_delay]`

#### Market（PHPUnit / Vitest）
- 注文確定リクエストに `shipping_method_id` が含まれない場合のエラー
- `quantity = 0` のエラー
- 購入履歴に数量・配送方法表示
- 予約ステータスの境界値（JST 0:00 基準）
- **未ログイン状態で購入ボタン押下 → /login にリダイレクト**
- ログイン成功後 `redirectTo` があればそこへ遷移

### 3-6. Step B 完了条件
- 並行運用不変条件 `products.stock == SUM(inventories.quantity)` が常時成立
- Market から実際に商品購入 → sales/deliveries 生成 → Console から配送ステータス更新 → 購入履歴反映 が end-to-end で動く
- phase15 r4 設計書記載のテストケース全グリーン

---

## 4. Step C — 在庫モデル完全移行

### 4-1. inventory_movements テーブル新設（P14-6 / Q14-5 / S14-7）

| カラム | 型 | 説明 |
|--------|----|----|
| id | BIGINT | PK |
| product_id | BIGINT | FK |
| warehouse_id | BIGINT | FK / DEFAULT 1 |
| movement_type | VARCHAR(50) | `inbound` / `sale` / `cancel` / `return` / `adjustment` |
| quantity | INT | 符号付き（正=増加 / 負=減少）/ `CHECK (quantity != 0)` |
| reference_type | VARCHAR(50) | NULL 可 |
| reference_id | BIGINT | NULL 可 |
| comment | TEXT | NULL 可 |
| created_by_user_id | BIGINT | NULL 可（運用ルールは S14-2） |
| created_at | DATETIME | NOT NULL |

**DB CHECK 制約（S14-7 / r3 で追加）**:
```sql
CHECK (
  (movement_type IN ('inbound', 'cancel', 'return') AND quantity > 0) OR
  (movement_type = 'sale' AND quantity < 0) OR
  movement_type = 'adjustment'
)
```

**インデックス**:
- `idx_inventory_movements_product_id`
- `idx_inventory_movements_created_at`
- `idx_inventory_movements_reference (reference_type, reference_id)`

### 4-2. created_by_user_id 運用ルール（Q14-11 / S14-2）

| movement_type | 発火タイミング | created_by_user_id |
|---------------|--------------|-------------------|
| sale（通常購入） | 注文確定時 | sales.user_id（注文者） |
| **sale（予約購入）** | **出荷時** | **出荷操作を実行した管理者の user_id** |
| cancel | キャンセル実行時 | 実行した管理者の user_id（ユーザキャンセルなら注文者） |
| return | 返品承認時 | 承認した管理者の user_id |
| inbound | 入荷登録時 | 登録した管理者の user_id |
| adjustment | 棚卸補正時 | 実行した管理者の user_id |
| バッチ自動処理 | バッチ実行時 | NULL |

### 4-3. 機能フラグ導入（`config/app/Inventory.php` / Core `application.yml`）

| キー | 値域 | 既定値 | 用途 |
|------|------|-------|------|
| `inventory.read_source` | `products_stock` / `inventories` | `products_stock` | 親キー |
| `inventory.read_source.market_display` | + `inherit` | `inherit` | Market 在庫表示 |
| `inventory.read_source.delivery_schedule` | 同上 | `inherit` | 配送予定日計算 |
| `inventory.read_source.preorder_status` | 同上 | `inherit` | 予約ステータス判定 |
| `inventory.read_source.console_display` | 同上 | `inherit` | Console 在庫表示（S14-3） |

**継承ロジック**（Core / Console 両側に実装）:
```
function getReadSource(featureKey):
    specific = config('inventory.read_source.' + featureKey)
    if specific is not null and specific != 'inherit':
        return specific
    return config('inventory.read_source')
```

### 4-4. 完全移行マイグレーション 4 段階

**Step 1: 並行運用整合性チェック**
- `SUM(inventories.quantity GROUP BY product_id) == products.stock` を全件検証
- 乖離があれば **再構築 SQL 実行**（予約購入は出荷済みのみ販売減算に含む / Q14-3）
  ```sql
  UPDATE inventories i
  SET quantity = (入荷加算) - (販売減算 / 予約は出荷済みのみ) + (返品加算);
  ```
- チェック通過まで Step 2 以降に進まない

**Step 2: 読み取り側を inventories 参照に切替**
- 4 機能を順番に `inventories` 参照へ切替
  1. Market 在庫表示 → `inventory.read_source.market_display = 'inventories'`
  2. 配送予定日計算 → `inventory.read_source.delivery_schedule = 'inventories'`
  3. 予約ステータス判定 → `inventory.read_source.preorder_status = 'inventories'`
  4. Console 在庫表示 → `inventory.read_source.console_display = 'inventories'`
- 各切替後に並行運用整合性テストでデグレ検出

**Step 3: 書き込み側のフック削除**
- 販売 Service の `products.stock` 減算を削除
- 返品復元 Service の `products.stock` 加算を削除
- 入荷 Service の `products.stock` 加算を削除
- `InventorySyncService` 削除は Step D で実施

**Step 4（= Step E に移行）**: 後述

### 4-5. テスト
- 並行運用期の整合性チェック
- 完全移行マイグレーション直後、`inventories` の値が販売・入荷・返品履歴からの再構築結果と一致（**予約購入は出荷済みのみ販売減算に含む**）
- 機能フラグ継承ロジックが 4 機能で正しく動作
- inventory_movements の CHECK 制約違反が DB レベルで拒否される（例: `inbound` で `quantity < 0`）
- inventory_movements の SUM(quantity) が現在の inventories.quantity と一致
- 予約購入の sale 記録の created_by_user_id が出荷管理者である（S14-2）
- 通常購入の sale 記録の created_by_user_id が注文者である

### 4-6. Step C 完了条件
- すべての読み書きが `inventories` を正本とする
- 並行運用整合性テストが全切替状態でグリーン
- inventory_movements の整合性テストグリーン

---

## 5. Step D — phase15 r5 クリーンアップ

### 5-1. 作業内容
- `InventorySyncService` の呼び出し箇所を grep で確認
  ```
  grep -rn "InventorySyncService" amazia-core/src amazia-console/app
  ```
- すべての呼び出しが Step C の Step 3 で削除済みであることを確認
- `InventorySyncService` クラス本体を削除
- `phase15_delivery_management.md` の改訂履歴に `r5 行` を追記
  - 「2026-MM-DD: phase14 r3 Step D 完了によりフック削除」

### 5-2. Step D 完了条件
- `grep -r "InventorySyncService"` が 0 件
- 過渡期コードがリポジトリに残らない

---

## 6. Step E — 最終クリーンアップ（S14-4）

### 6-1. 作業内容
1. `ALTER TABLE products DROP COLUMN stock`（マイグレーション）
2. `config/app/Inventory.php` から `inventory.read_source` 系キーを **すべて削除**
3. Core `application.yml` から同等キーを削除
4. `if (read_source == 'products_stock')` 等の分岐コードを **すべて削除**
5. 死コード検査:
   ```
   grep -r "products.stock\|products\.stock\|getStock\|setStock" amazia-core/src amazia-console/app amazia-market/src
   grep -r "read_source\|InventorySyncService" amazia-core/src amazia-console/app
   ```
   → 0 件であることを確認
6. `phase14_shipping.md` のステータスを `🔲 未着手` → `✅ 完了（YYYY-MM-DD）` に更新
7. 改訂履歴に「r3 実装完了」行を追記
8. `次タスク.txt` のフェーズ14欄を完了マーク

### 6-2. Step E 完了条件
- `products.stock` カラム DROP 完了
- 機能フラグおよび分岐コード完全削除
- 死コード検査グリーン
- 設計書ステータス更新済み

---

## 7. 横断タスク（全 Step で意識）

| 項目 | 内容 |
|------|------|
| TDD | 各 Step で設計書記載のテストケースを実装。先にテスト → 実装 → グリーン → 次 Step |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` / `phpunit.xml` / `application-test.properties` セット更新 |
| テスト値 | `config()` / `@Value` 経由（テスト内ハードコード禁止） |
| 操作履歴 | Console の各更新画面で `operation_logs` への記録を Service 層で必ず行う |
| 同時実行制御 | 在庫減算は必ず `SELECT ... FOR UPDATE` + ロック後再確認（CHECK 制約に頼らない） |
| 文書 | 完了時に phase14_shipping.md の改訂履歴に「r3 実装完了 (YYYY-MM-DD)」を追記、`次タスク.txt` のフェーズ14欄を更新 |
| トラブル対応 | 不具合発生時は `docs/troubles/NNN_<概要>.md` を新規作成し再発防止策を記録（CLAUDE.md ルール） |

---

## 8. 着手前の確認事項

### 8-1. 解消済み項目（2026-05-06 ユーザー回答）

| # | 確認事項 | 確定方針 |
|---|---------|---------|
| 1 | Step 0 の扱い | ✅ 本フェーズ Step 0 として実施（設計書外の前提整備として明示） |
| 2 | `sales.user_id` の参照先 | ✅ `market_customers.id`（フェーズ13で会員テーブルが Console 既存 `users` から `market_customers` に分離されているため） |
| 3 | 会員住所の扱い | ✅ `market_customers.postal_code` / `address`（VARCHAR）は **触らない**。1 顧客 1 住所として現状維持 |
| 4 | 配送先住所マスタの扱い | ✅ 設計書 r3 通り `address` テーブルを新設。会員住所とは独立した「注文時の配送先住所スナップショット」として運用。`address.user_id` は `market_customers.id` を参照 |
| 5 | スコープ完結 | ✅ 配送先住所マスタも `sales` のリレーションも phase14 で完結（phase15 は配送実体 `deliveries` の利用者） |
| 6 | payment_method の扱い | ✅ 案 B-1：`payment_methods` マスタを新設、`sales.payment_method_id` で個別選択。`market_customers.payment_method` は会員ごとの既定値として残置（注文画面の初期値） |
| 7 | 設計書改訂 | ✅ Step 0 内で `phase14_shipping.md` を r4 に改訂（user_id 参照先・住所方針・payment_method 方針を明文化） |

### 8-2. 残課題

| # | 確認事項 |
|---|---------|
| 1 | **進め方**: 全 Step を 1 セッションで通すか、Step ごとに区切って進捗確認・コミットするか（推奨：Step ごと） |
| 2 | **UUID v7 ライブラリ**: 候補（`uuid-creator` for Java / `ramsey/uuid` for PHP）を Step A で正式選定する形で良いか |
| 3 | **Market のチェックアウト UI**: モックアップ無しで設計書記載項目から組み立てて良いか（決済画面のレイアウト等） |
| 4 | **Console 既存 `Sales/GetSalesService` のモックデータ**: Step B で実データ化する際に既存 API 形式を維持するか、設計書の表示項目に合わせて拡張するか |

---

## 9. 参考リンク

- 設計書: [phase14_shipping.md](../design/phase11_20/phase14_shipping.md)
- 関連設計書: [phase15_delivery_management.md](../design/phase11_20/phase15_delivery_management.md)
- コーディング規約: [coding_guidelines.md](../coding_guidelines.md)
- AIコンテキスト（テスト観点）: [test_insights.md](../ai_context/test_insights.md)
- AIコンテキスト（実装・運用パターン）: [ai_context/operational_insights.md](../ai_context/operational_insights.md)
- プロジェクトAIコンテキスト: [CLAUDE.md](../../CLAUDE.md)
