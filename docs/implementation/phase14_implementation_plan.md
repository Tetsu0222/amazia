# フェーズ14 実装計画（購入機能）

## 概要
- 対象設計書: `docs/design/phase11_20/phase14_shipping.md`（**r4 / 2026-05-06**）
- 対象範囲: Amazia Console / Amazia Market / Amazia Core / DB設計（6 テーブル新設：`address` / `payment_methods` / `shipping_statuses` / `sales` / `sales_return` / `operation_logs`）
- 段取り: 設計書 r4 の **Step 0 → A → B** の3段階構成
- 作成日: 2026-05-06
- 改訂: 2026-05-06（フェーズ10で実装済みの SKU 在庫モデルを活用、phase14 を r4 に大幅改訂、Step C〜E を削除）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | 設計書 r4 の **Step 0 → A → B** を厳守。Step を跨いだ部分実装は禁止 |
| 規模感 | 6 テーブル新設、Core/Console/Market 全層、TDD 含めて Step ごとに区切って commit / 動作確認 |
| TDD | 設計書「TDDテストケース」セクションに列挙された全項目を Step ごとに割り当てて実装 |
| コーディング規約 | `docs/coding_guidelines.md` 厳守（Service にロジック寄せ・config 駆動・1ファイル1ユースケース） |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` + `phpunit.xml`（Console）+ `application-test.properties`（Core）をセット更新 |
| テスト値 | ハードコードせず `config()` / `@Value` 経由で取得 |
| メモリ事項 | Core 側 Heap 制限（`-Xmx384m`）を意識し、重い在庫一括処理は避ける |
| 同時実行制御 | **既存の `@Version` 楽観ロックを維持**（r3 で要求した悲観ロック切替は r4 で取り下げ） |
| 在庫モデル | **既存 `product_sku_stocks` を正本として活用**。`inventories` / `inbounds` / `inventory_movements` / `warehouses` の新設は **行わない** |

---

## 1. Step 0 — 前提整備

### 背景
phase14 r4 設計書は以下を「既存」前提で記述しているが、現状コードには **未実装**。
- Core: `sales` / `sales_return` / `address` / `payment_methods` / `shipping_statuses` / `operation_logs` パッケージ未作成
- Console: 同関連マイグレーションなし（users / cache / jobs / personal_access_tokens のみ）
- Console `app/Sales/` は `GetSalesService` がモックデータを返すスタブのみ

Step A の「スキーマ拡張」を成立させるため、土台を Step 0 として整備する。

### 1-1. 既存実装との整合性（フェーズ10 + フェーズ13 調査結果）

#### フェーズ10で実装済み（SKU 単位在庫モデル）
| 既存テーブル / Service | 役割 | r4 での扱い |
|-----------------------|------|-----------|
| `product_skus(id, product_id, sku_code, color, size, status)` | SKU マスタ | `sales.sku_id` の参照先 |
| `product_sku_stocks(id, sku_id, quantity, version)` | **SKU 別在庫の正本**。`@Version` 楽観ロック | 注文確定時の在庫減算・予約出荷時の減算・返品復元の対象 |
| `product_sku_stock_transactions` | **在庫増減ログの正本** | sale / cancel / return の在庫増減を記録 |
| `ReceiveProductSkuStockService` | **入荷受入処理** | 入荷時の在庫加算 + transaction 記録 |
| `Product.stock` カラム | フェーズ10以降は使われていない可能性が高い死カラム | **本書では触らない**。将来課題として登録 |

#### フェーズ13で実装済み（会員管理）
| 既存テーブル / 概念 | 役割 | r4 での扱い |
|-------------------|------|-----------|
| `market_customers` | 会員マスタ。1顧客1住所、1顧客1既定決済方法 | `sales.user_id` の参照先（FK）。**触らない** |
| `market_customers.postal_code` / `address` | 会員の現住所 | 注文時に `address` テーブルへスナップショット複製 |
| `market_customers.payment_method` | 会員の既定決済方法 | 注文画面の初期値として使用（B-1） |
| `users`（auth） | 管理画面ユーザ（管理者） | `operation_logs.user_id` の参照先 |

### 1-2. Step 0-1: 設計書 phase14_shipping.md を r4 に改訂 ✅（本作業で完了済み）
- r3 をアーカイブ（`docs/design/phase11_20/old/phase14_shipping_r3.md`）
- r4 として全面書き換え（既存 SKU 在庫モデル採用、Step 構造を 0/A/B に縮小、`market_customers` 参照確定、`address` スナップショット運用、payment_method B-1 を明文化）

### 1-3. Step 0-2: 実装計画 md を r4 反映版に書き換え ✅（本書）

### 1-4. Step 0-3: 再発防止メモを `docs/ai_context/operational_insights.md` に追記
**追記内容**:
> **新設計書を書く前に「既存実装で同等の役割を持つテーブル / Service / カラムがないか」を必ず棚卸しする**
> - 在庫・売上・住所・通知など、汎用的な概念ほど過去フェーズで実装済みの可能性が高い
> - `@Table(name=...)` 全件 grep / `Service` クラス全件リスト / DB 設計書 `docs/database_design/` の TBL_*.md を一度通読する
> - 既存資産があれば「既存を活かす方針」を設計書冒頭に明記し、新設テーブルとの責務境界を整理する
> - 同じ役割を別の粒度で実装する（商品単位 vs SKU 単位など）前に、粒度をどう揃えるかを決定する
> - 実例：phase14 r1〜r3 では `inventories` / `inbounds` / `inventory_movements` / `warehouses` を新設する設計だったが、フェーズ10 で `product_sku_stocks` / `product_sku_stock_transactions` / `ReceiveProductSkuStockService` が既に同等役割を果たしていたため、r4 で大幅縮小

### 1-5. Step 0-4: 共通命名規約 `docs/ai_context/operation_logs_naming.md` 新規作成
記載内容:
- `screen_name` の命名規則（例: `console.sales.list` / `console.delivery.update_status` / `market.checkout.confirm`）
- `api_name` の命名規則（例: `POST /api/orders/confirm` / `PATCH /api/deliveries/{id}/status`）
- phase14 / phase15 / 将来フェーズで利用される値の一覧
- 採番方針（小文字スネークケース・ドメイン名から始める）

### 1-6. Step 0-5: ベーススキーマ作成（**Core `schema.sql` への追記**）

> **訂正注記（[037](../troubles/037_flyway_misassumed_phase14_tables_missing.md) 起因 / 2026-05-06）**：当初 Step 0-5 は「Core Flyway V6 を新規作成」と記述していたが、**本プロジェクトは Flyway 未導入**（pom.xml に依存なし）。実態は `schema.sql` を `spring.sql.init.mode=always` で起動時実行する方式。`db/migration/V*.sql` は過去の名残で起動時に何も動かない。本記述は実態に合わせて訂正済み。Step 0/A で誤って作成した `V6_*.sql 〜 V11_*.sql` は本対応で削除済み。

業務テーブルは既存方針に合わせて **Core 側 `schema.sql`** に冪等版で追記する（Console Laravel migrations には業務テーブルを追加しない）。

**追記先**: `amazia-core/src/main/resources/schema.sql`（末尾に「フェーズ14: 購入機能」セクションを追加）

冪等構文：`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE` / `ALTER TABLE ... ADD COLUMN`（重複は `spring.sql.init.continue-on-error=true` で許容）

| テーブル | 内容 |
|---------|------|
| `address` | `id, user_id(FK to market_customers.id), postal_code, prefecture, city, address_line, building, is_active(BOOLEAN), created_at`。配送先住所スナップショット用。MySQL 用に `BIGINT UNSIGNED` で `market_customers.id` と型を揃える |
| `payment_methods` | マスタ。INSERT: `1=credit_card, 2=d_payment, 3=cash_on_delivery` |
| `shipping_statuses` | マスタ（base 形）。INSERT: `1=PENDING, 2=SHIPPED, 3=DELIVERED, 4=RETURN_REQUESTED, 5=RETURNED`。CANCELED 等は Step A で `V?__expand_shipping_statuses.sql` として追加 |
| `sales` | base 形（必須カラムのみ）。`user_id` は FK to `market_customers.id`、`sku_id` / `quantity` / `payment_id UNIQUE` / `shipping_method_id` 等の拡張カラムは Step A で追加 |
| `sales_return` | base 形。`quantity` は Step A で追加 |
| `operation_logs` | base 形。`screen_name` / `api_name` は Step A で追加。`user_id` は `users.id`（auth）参照 |

**Console 側は触らない**（Laravel migrations への業務テーブル追加なし）。

### 1-7. Step 0-6: Core JPA Entity / Repository を新規作成

新規パッケージ:
- `com.example.sales` — Sales Entity / Repository / DTO（base 形）
- `com.example.salesreturn` — SalesReturn Entity / Repository / DTO（base 形）
- `com.example.address` — Address Entity / Repository / DTO
- `com.example.paymentmethod` — PaymentMethod Entity / Repository
- `com.example.shippingstatus` — ShippingStatus Entity / Repository
- `com.example.operationlog` — OperationLog Entity / Repository / DTO

各 Entity は base 形（必須カラムのみ）。Step A で拡張カラム追加。

### 1-8. Step 0-7: 完了確認
- Core を再起動して `schema.sql` の冪等 SQL がエラーなく流れる（`docker compose -f docker-compose.local.yml restart amazia-core`）。MySQL に `payment_methods` / `address` / `sales` / `sales_return` / `shipping_statuses` / `operation_logs` が作成され、`product_sku_stock_transactions` に拡張カラムが追加されることを `DESCRIBE` で確認
- `mvn test` で Entity 認識成功（既存テスト通過）
- 既存機能（商品一覧 / ログイン / 商品登録 / 会員登録等）に影響なし
- `docs/ai_context/operation_logs_naming.md` レビュー完了
- `docs/ai_context/operational_insights.md` 再発防止メモ追記完了

### 1-9. Step 0 で確定済みの論点

| # | 論点 | 確定方針 |
|---|------|---------|
| 1 | DB マイグレーションの管理元 | ✅ **Core 側 `schema.sql`**（spring.sql.init で起動時実行）。Flyway は本プロジェクトでは未導入（[037](../troubles/037_flyway_misassumed_phase14_tables_missing.md) 起因で訂正）。Console Laravel migrations には業務テーブルを追加しない |
| 2 | `address.prefecture` / `city` / `address_line` / `building` の構造化分解 | ✅ `market_customers.address`（VARCHAR255）が構造化されていないため、`address.address_line` に住所文字列をそのまま格納、`prefecture` / `city` / `building` は NULL 許容で運用 |
| 3 | `product_sku_stock_transactions` の既存スキーマ | ✅ 既存は `id / sku_id / type / quantity / created_at` のみ。phase14 で必要な `reference_type / reference_id / created_by_user_id / comment` カラム不足。**選択肢 A：既存テーブルを Step A で拡張**（`V?__expand_sku_stock_transactions.sql`）。`type` の許容値も `receive / adjust` から `sale / return / cancel` を追加 |
| 4 | `product_sku_stock_transactions.created_by_user_id` の所属 | ✅ Step A 実装時に確定。管理者 = `users.id`、会員 = `market_customers.id` を区別する場合は別カラム化を検討（例: `created_by_admin_id` / `created_by_customer_id` の2カラム化）。本フェーズでは `created_by_user_id` は管理者主体（`users.id`）に揃え、通常購入の `sale` レコードはトリガー元（注文者）の market_customers.id を別途記録できるよう設計 |

---

## 2. Step A — phase14 r4 のスキーマ拡張

### 2-1. マイグレーション（Console）
- `sales.sku_id BIGINT NOT NULL` 追加（FK to `product_skus.id`）
- `sales.quantity INT NOT NULL CHECK (quantity > 0)` 追加
- `sales.amount INT NOT NULL` 追加
- `sales.payment_method_id BIGINT NOT NULL` 追加（FK to `payment_methods.id`）
- `sales.shipping_method_id BIGINT NOT NULL` 追加（FK to `shipping_methods.id`／phase15 で実体作成のため Step A 時点では仮置き or 遅延付与）
- `sales.shipping_address_id BIGINT NOT NULL` 追加（FK to `address.id`）
- `sales.shipping_status_id BIGINT NOT NULL` 追加（FK to `shipping_statuses.id`）
- `sales.payment_id VARCHAR(100) NOT NULL UNIQUE` 追加
- `sales.is_preorder BOOLEAN NOT NULL` 追加
- `sales_return.quantity INT NOT NULL CHECK (quantity > 0)` 追加
- `shipping_statuses` に `CANCELED` / `DELIVERY_FAILED` / `RESCHEDULED` を INSERT
- `operation_logs.screen_name VARCHAR(100)` / `operation_logs.api_name VARCHAR(100)` 追加
- `product_sku_stock_transactions` に不足カラムがあれば追加（Step 0 の確認結果に基づく）

### 2-2. Core JPA Entity 更新
- `Sales` Entity に `skuId / quantity / amount / paymentMethodId / shippingMethodId / shippingAddressId / shippingStatusId / paymentId / isPreorder` フィールド追加
- `SalesReturn` Entity に `quantity` 追加
- `OperationLog` Entity に `screenName / apiName` 追加
- 各 Repository / DTO に対応するフィールド追加

### 2-3. UUID v7 採番ライブラリ選定
**Java（Core）候補**:
- `com.github.f4b6a3:uuid-creator`（Apache-2.0、Star/Release を Step A 着手時に確認）
- `com.fasterxml.uuid:java-uuid-generator`（Apache-2.0）

**PHP（Console）候補**:
- `ramsey/uuid` v4.7+（v7 対応、MIT）

選定基準:
- License: Apache-2.0 / MIT 推奨
- GitHub Star 数
- 最新リリース日（直近1年以内）

採用後:
- Core: `pom.xml` に追加
- Console: `composer.json` に追加
- `PaymentService.generatePaymentId()` を薄いラッパで隠蔽

### 2-4. config 化
**Console**: `config/app/Sales.php` 新規
- `payment_methods.credit_card_id` 等のマスタ ID
- `shipping_statuses.pending_id` 等
- 配送ステータス遷移許容リスト

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
- `sales.sku_id` に存在しない SKU ID を入れると FK 違反

### 2-6. Step A 完了条件
- 全マイグレーション通過
- 既存機能（商品一覧 / ログイン等）が壊れていない
- UUID v7 採番ライブラリが pom.xml / composer.json に追加済み
- `config/app/Sales.php` / `application.yml > amazia.sales` 設定済み

---

## 3. Step B — phase14 r4 の機能実装

### 3-1. Core 側 Service / Controller

#### com.example.sales（新規）
- `OrderConfirmationService.confirm(orderRequest)` — 設計書 r4「注文確定フロー」擬似コード通り
  1. `validateOrder` — 予備バリデーション
     - `market_customers.id == order_request.user_id` を検証
     - `product_skus.id == order_request.sku_id` が ACTIVE
     - 商品が公開中
     - `sales.shipping_method_id` が `shipping_methods` マスタに存在
     - `sales.payment_method_id` が `payment_methods` マスタに存在
     - 在庫予備チェック（`product_sku_stocks.quantity > 0` / `is_preorder=false` のとき）
  2. **配送先住所スナップショット作成**: `market_customers.postal_code/address` から `address` テーブルへ INSERT、`is_active=true`
  3. `begin transaction`
  4. 通常購入の在庫減算（`product_sku_stocks.quantity -= n`、`@Version` 楽観ロック）
     - `product_sku_stock_transactions` に `transaction_type='sale'`, `quantity=-n`, `reference_type='sales'` を記録（reference_id は 5 後に更新）
     - `is_preorder=true` のときはこのステップをスキップ
  5. `INSERT sales`（payment_id UNIQUE 違反時の冪等処理：`user_id/sku_id/quantity/amount` 全項目一致なら既存 sales を返す。不一致なら **エラーログ + 例外**）
  6. `product_sku_stock_transactions.reference_id = sales.id` で更新
  7. `DeliveryCreationService.createForSales(sales.id)` で deliveries 生成（phase15 と協調）
  8. `commit`
- `PaymentService.generatePaymentId()` — UUID v7 採番
- `PaymentService.handleUniqueViolation(salesData)` — `user_id / sku_id / quantity / amount` 全項目一致時のみ既存 sales を返す。不一致なら **エラーログ + 例外**
- `ListSalesService` — 売上一覧取得（管理画面用、集計対応）

##### 注文確定 API リクエスト形式（Market から）
```json
{
  "sku_id": 456,
  "quantity": 2,
  "payment_method_id": 1,
  "shipping_method_id": 1,
  "is_preorder": false
}
```
- `user_id` は認証情報から取得
- 配送先は `market_customers` の現住所を Service 側で自動スナップショット
- 会員と異なる住所への配送はスコープ外

#### com.example.salesreturn（新規）
- `RequestSalesReturnService` — 返品申請（REQUESTED で INSERT）
- `ApproveSalesReturnService` — 管理者承認（APPROVED）
- `RejectSalesReturnService` — 管理者却下（REJECTED）
- `RefundSalesReturnService` — 返金完了（REFUNDED）。在庫戻し（`product_sku_stocks.quantity += sales_return.quantity`）と `product_sku_stock_transactions` への `transaction_type='return'` 記録を同一トランザクションで実行
- 通知状態（`notified_user / notified_admin`）の管理

> **Step B-5 細分化（2026-05-06 追記）**：返品ワークフローは Core / Console / Market を横断するため、コミット粒度を機能単位に揃える。詳細は §3-6 を参照。

#### com.example.address（新規）
- `CreateAddressSnapshotService` — 注文確定時に呼ばれる。会員住所からスナップショット INSERT
- `ListAddressService` — Console の配送先変更画面用（オーナー検証 + `is_active=true` フィルタ）

#### com.example.delivery / com.example.inbound（phase15 領域）
phase15 r4 設計書をベースに実装。ただし以下の調整が必要：
- **`inventories` / `inbounds` / `warehouses` 新設は行わない**（既存 `product_sku_stocks` を活用）
- `DeliveryCreationService.createForSales(salesId)` — 長期シグネチャで実装
- `DeliveryStatusTransitionService` — 遷移ガード、`PENDING→SHIPPED` 時に `is_preorder=true` のみ在庫減算フック
- 在庫不足時は **例外＋PENDING 維持**（S14-1 方針 A）
- `RegisterInboundService` は **既存 `ReceiveProductSkuStockService` を流用**（または phase15 で改名／統合）
- phase15 設計書（r4）も r5 として r4 ベースの整合改訂が将来必要だが、本実装計画では phase14 として最小限の協調実装にとどめる

### 3-2. Console 管理画面（PHP / Laravel）

#### Sales（既存スタブを実装データ化）
- `app/Sales/Service/GetSalesService.php` — Core API 経由で売上一覧取得
- 集計: 年/月/日 + 商品別/SKU別/ユーザ別/決済方法別/配送方法別/数量集計
- 表示項目: ユーザ名 / 購入商品（名 + 色 + サイズ）/ 数量 / 金額 / 配送日 / 売上日 / 配送ステータス / 決済方法 / 配送方法 / 予約 or 通常購入区分
- 既存 API 形式を設計書の表示項目に合わせて拡張

#### Delivery（新規）
- `app/Delivery/Controller/UpdateShippingStatusController.php`
- `app/Delivery/Controller/UpdateShippingAddressController.php` — UI で `is_active=false` の住所をフィルタ（S14-10）
- `app/Delivery/Controller/UpdateScheduledDateController.php` — comment にプレフィックス `[manual]` / `[inbound_recalc]` / `[shipping_delay]`
- `app/Delivery/Controller/RegisterTrackingCodeController.php`

#### SalesReturn（新規）
- `app/SalesReturn/Controller/ListSalesReturnController.php`
- `app/SalesReturn/Controller/ApproveSalesReturnController.php`
- `app/SalesReturn/Controller/RejectSalesReturnController.php`
- `app/SalesReturn/Controller/RefundSalesReturnController.php`

#### OperationLog（新規）
- `app/OperationLog/Controller/ListOperationLogController.php` — `screen_name` / `api_name` で検索可能

### 3-3. Market 購入導線（**ユーザー指示の核**）

#### ProductDetail.jsx 改修
- 「購入する」ボタン追加（SKU 選択後にのみ活性化）
- 押下時:
  ```js
  const { user } = useAuth();
  if (!user) {
    navigate('/login', { state: { redirectTo: `/checkout?sku_id=${selectedSku.id}&quantity=${quantity}` } });
  } else {
    navigate(`/checkout?sku_id=${selectedSku.id}&quantity=${quantity}`);
  }
  ```

#### 新規ページ
- `features/checkout/pages/Checkout.jsx`
  - 配送先表示（`market_customers` の現住所を読み取り専用で表示）
  - 配送方法選択（home_delivery / konbini_pickup / dropoff）
  - 決済方法選択（既定値は `market_customers.payment_method`、変更可）
  - 数量変更
  - 確認画面 → 注文確定 API
- `features/checkout/pages/Confirm.jsx` — 確認画面
- `features/checkout/pages/Complete.jsx` — 完了画面
- `features/orders/pages/PurchaseHistory.jsx` — 購入履歴（数量・色・サイズ・配送方法・配送ステータス・配送予定日）

#### 予約ステータス表示
- Core API `/api/products/{id}/preorder-status` を新設（SKU SUM 集計）
- ProductDetail / ProductList で表示分岐
  - PRE_ORDER: 「予約受付中」+ 発売日表示
  - BACK_ORDER: 「再入荷予約受付中」
  - SOLD_OUT: 「完売」（購入ボタン非表示）

#### Login.jsx 改修
- ログイン成功後、`location.state.redirectTo` があればそこへ遷移

### 3-4. テスト（TDD）

#### Core（JUnit）正常系
- 注文確定時に `deliveries` が PENDING で生成される（同一トランザクション）
- 注文確定時、配送先スナップショットが `address` に新規 INSERT され `sales.shipping_address_id` に紐付く
- `is_preorder=false` の通常購入は注文時に `product_sku_stocks` から減算され、出荷時には再減算されない
- `is_preorder=true` の予約購入は注文時に減算されず、出荷時（SHIPPED 遷移時）に減算される
- `payment_id UNIQUE` 違反時、全項目一致なら既存 sales が返される（真の冪等）
- 配送ステータス遷移ガード（PENDING→SHIPPED→DELIVERED→RETURN_REQUESTED→RETURNED）
- 過去注文の `sales.shipping_address_id` は会員住所変更後も不変

#### Core 異常系
- 通常購入で在庫切れの注文を予備バリデーションで拒否
- `@Version` 楽観ロックで OptimisticLockException 発生時のリトライ or 例外伝播
- `address.user_id != order_request.user_id` の住所を拒否
- `sales.shipping_method_id` がマスタに存在しない場合に拒否
- `sales.payment_method_id` がマスタに存在しない場合に拒否
- `sales.sku_id` の SKU が `status != ACTIVE` の場合に拒否
- 未対応 `shipping_status_id` への遷移を拒否
- 不正な配送ステータス遷移を拒否（DELIVERED→PENDING 等）
- `payment_id UNIQUE` 違反かつ他項目不一致で **エラーログ + 例外**
- 出荷時 `product_sku_stocks.quantity < sales.quantity` で例外＋PENDING 維持

#### Console（PHPUnit）
- 配送管理画面で配送情報表示
- 配送ステータス更新時に `operation_logs` 記録
- 配送先変更画面で過去住所（`is_active=false`）が選択肢に **表示されない**
- API 直叩きで `is_active=false` の住所を指定した場合は通る（オーナー検証のみ）
- 売上管理画面で SKU 単位の集計（色別・サイズ別）が表示できる

#### Market（Vitest）
- 注文確定リクエストに `shipping_method_id` が含まれない場合のエラー
- 注文確定リクエストに `payment_method_id` が含まれない場合のエラー
- `quantity = 0` のエラー
- 購入履歴に数量・色・サイズ・配送方法表示
- 予約ステータスの境界値（JST 0:00 基準）
- **未ログイン状態で購入ボタン押下 → /login にリダイレクト**
- ログイン成功後 `redirectTo` があればそこへ遷移

### 3-5. Step B 完了条件
- Market から実際に商品購入 → sales/deliveries 生成 → Console から配送ステータス更新 → 購入履歴反映 が end-to-end で動く
- 設計書 r4 記載のテストケース全グリーン
- `phase14_shipping.md` のステータスを `🔲 未着手` → `✅ 完了（YYYY-MM-DD）` に更新
- `次タスク.txt` のフェーズ14欄を完了マーク

### 3-6. Step B-5（返品ワークフロー）の細分化（2026-05-06 追記）

返品ワークフローは Core / Console / Market を横断し、書き込み3本（申請 / 承認・却下 / 返金完了+在庫戻し）と読み取り1本に加えて UI が3層分発生するため、コミット粒度を機能単位に揃えるためサブステップ化する。各サブステップで `mvn test` / `phpunit` / `vitest` のうち該当層が緑になることを完了条件とする。

| サブ | 内容 | 主な実装物 | 完了確認 |
|------|------|-----------|---------|
| **B-5-1** | Core 返品申請（会員 → 管理者キューに積む） | `RequestSalesReturnService` / DTO / Controller `POST /api/customer/sales-returns` / 検証（DELIVERED の sales のみ・本人所有のみ・quantity ≤ sales.quantity・重複申請防止）/ JUnit | mvn test 緑、API 直叩きで REQUESTED 1 件作成 |
| **B-5-2** | Core 返品承認・却下（在庫変動なし） | `ApproveSalesReturnService` / `RejectSalesReturnService` / Controller `POST /api/sales-returns/{id}/approve` / `POST /api/sales-returns/{id}/reject` / 配送ステータスを `RETURN_REQUESTED` に更新（APPROVED 時）/ JUnit | mvn test 緑、API 直叩きで APPROVED/REJECTED に遷移 |
| **B-5-3** | Core 返金完了 + **在庫戻し**（要点） | `RefundSalesReturnService` / `product_sku_stocks.quantity += n`（楽観ロック）/ `product_sku_stock_transactions` に return 記録 / 配送ステータスを `RETURNED` に更新（同一 TX）/ JUnit | mvn test 緑、API 直叩きで在庫が増え transaction が記録される |
| **B-5-4** | Core 返品一覧（管理者向け） | `ListSalesReturnService` / DTO `AdminSalesReturnItem`（顧客名・商品名+色+サイズ・数量・状態・申請日 等）/ Controller `GET /api/sales-returns` / JUnit | mvn test 緑、API 直叩きで一覧 JSON が返る |
| **B-5-5** | Console 中継 4 本（Service + Controller + phpunit + ルート） | `ListSalesReturnService` / `ApproveSalesReturnService` / `RejectSalesReturnService` / `RefundSalesReturnService` + Http::fake テスト | phpunit 緑 |
| **B-5-6** | Console Vue 画面（管理者用） | `features/salesReturn/` 一式：API クライアント / 一覧ページ（ステータス絞込）/ 各操作ボタン（承認・却下・返金完了の確認モーダル）/ ルート + メニュー | UI で REQUESTED → APPROVED → REFUNDED が通る |
| **B-5-7** | Market 購入履歴に「返品申請」ボタン | PurchaseHistory 改修：DELIVERED かつ未申請のみ表示 / 申請モーダル（数量・理由）/ 申請後の状態表示（申請中・承認済み・却下・返金完了）/ Vitest | Market から申請 → Console で承認 → 返金完了 → 在庫増 が end-to-end で通る |
| **B-5-8** | operation_logs 記録（横断・最後） | 各 Service（B-5-1〜3）に `operation_logs` 記録を追加 / `screen_name` / `api_name` を `operation_logs_naming.md` に追記 / JUnit で記録確認 | mvn test 緑、operation_logs に 4 種類の action が積まれる |

#### 依存関係と進め方

- 直列依存：**B-5-1 → B-5-2 → B-5-3**（在庫戻しは承認・却下の後段なので最後）
- 並行可能：**B-5-4** は B-5-1〜3 と独立（先に着手しても可）
- Console（B-5-5/6）は Core 4 本（B-5-1〜4）完了後に着手
- Market（B-5-7）は Console 完了後（または Console と並行）
- **B-5-8 を最後に分離した理由**：operation_logs 記録は横断的で、各 Service の本筋ロジックと混ぜると差分レビュー時に「主要処理 vs ログ記録」の見分けがつきにくい。Service が動く状態を作ってから加えるほうが PR 粒度が明瞭

---

## 4. 横断タスク（全 Step で意識）

| 項目 | 内容 |
|------|------|
| TDD | 各 Step で設計書記載のテストケースを実装。先にテスト → 実装 → グリーン → 次 Step |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` / `phpunit.xml` / `application-test.properties` セット更新 |
| テスト値 | `config()` / `@Value` 経由（テスト内ハードコード禁止） |
| 操作履歴 | Console の各更新画面で `operation_logs` への記録を Service 層で必ず行う |
| 同時実行制御 | 既存 `@Version` 楽観ロックを維持。OptimisticLockException 発生時の挙動を Service で明示的にハンドル |
| 文書 | 完了時に phase14_shipping.md の改訂履歴に「r4 実装完了 (YYYY-MM-DD)」を追記、`次タスク.txt` のフェーズ14欄を更新 |
| トラブル対応 | 不具合発生時は `docs/troubles/NNN_<概要>.md` を新規作成し再発防止策を記録（CLAUDE.md ルール） |

---

## 5. 着手前の確認事項

### 5-1. 解消済み項目（2026-05-06 ユーザー回答）

| # | 確認事項 | 確定方針 |
|---|---------|---------|
| 1 | Step 0 の扱い | ✅ 本フェーズ Step 0 として実施 |
| 2 | `sales.user_id` の参照先 | ✅ `market_customers.id` |
| 3 | 会員住所の扱い | ✅ `market_customers.postal_code` / `address` は **触らない** |
| 4 | 配送先住所マスタの扱い | ✅ 設計書 r4 通り `address` テーブルを新設。注文時スナップショット |
| 5 | スコープ完結 | ✅ phase14 で完結 |
| 6 | payment_method の扱い | ✅ 案 B-1 |
| 7 | 設計書改訂 | ✅ Step 0-1 で `phase14_shipping.md` を r4 に改訂完了 |
| 8 | 進め方 | ✅ Step ごとに区切って commit + 動作確認 |
| 9 | UUID v7 ライブラリ | ✅ Step A で正式選定 |
| 10 | Market チェックアウト UI | ✅ モックなしで設計書記載項目から組み立て |
| 11 | Console 既存 `Sales/GetSalesService` のモックを設計書の表示項目に拡張 | ✅ 拡張する |
| 12 | 在庫モデル | ✅ 既存 `product_sku_stocks`（SKU 単位）を正本として活用 |
| 13 | 同時実行制御 | ✅ 既存 `@Version` 楽観ロックを維持（悲観ロック切替は取り下げ） |
| 14 | phase15 の改訂 | ✅ phase14 r4 完了後に別途実施（本実装計画には含まれない） |
| 15 | Product.stock 死カラム | ✅ 触らない。将来課題として登録 |
| 16 | 再発防止メモ | ✅ Step 0-3 で `operational_insights.md` に追記 |

### 5-2. 残課題
（現時点でなし。Step 0 着手中に新たな論点が出れば追記）

---

## 6. 参考リンク

- 設計書: [phase14_shipping.md](../design/phase11_20/phase14_shipping.md)
- 旧版（r3）: [old/phase14_shipping_r3.md](../design/phase11_20/old/phase14_shipping_r3.md)
- 関連設計書: [phase15_delivery_management.md](../design/phase11_20/phase15_delivery_management.md)（phase14 r4 完了後に r5 改訂予定）
- コーディング規約: [coding_guidelines.md](../coding_guidelines.md)
- AIコンテキスト（テスト観点）: [test_insights.md](../ai_context/test_insights.md)
- AIコンテキスト（実装・運用パターン）: [operational_insights.md](../ai_context/operational_insights.md)
- プロジェクトAIコンテキスト: [CLAUDE.md](../../CLAUDE.md)
