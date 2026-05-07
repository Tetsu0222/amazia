# フェーズ15 実装計画（配送管理）

## 概要
- 対象設計書: [phase15_delivery_management.md](../design/phase11_20/phase15_delivery_management.md)（**r5 / 2026-05-07**）
- 対象範囲: Amazia Console / Amazia Market / Amazia Core / DB 設計
- 段取り: 設計書 r5 の構成を **Step 0（前提整備）→ A（Core スキーマ + マスタ）→ B（Core Service / Controller）→ C（Console UI）→ D（Market 表示反映）→ E（並行運用整合性とフェーズ完了確認）** の 5 段階で実施
- 作成日: 2026-05-07
- 親フェーズ: [phase14_implementation_plan.md](phase14_implementation_plan.md)（phase14 r4 完了済み）/ [phase14_5_implementation_plan.md](phase14_5_implementation_plan.md)（phase14_5 r2 完了済み・2026-05-07）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | Step 0 → A → B → C → D → E を厳守。Step を跨いだ部分実装は禁止。各 Step 末で `mvn test` / `phpunit` / `vitest` の該当層が緑であることを完了条件とする |
| 規模感 | Core 5 テーブル新設（deliveries / shipping_methods / warehouses / inbounds / inventories）+ Service 8 本程度 + Controller 6 本 + Console 5 画面 + Market 表示拡張 |
| TDD | 設計書「TDDテストケース」セクションに列挙された全項目を Step ごとに割り当てて実装。並行運用整合性テストは Step E に集約 |
| コーディング規約 | [coding_guidelines.md](../coding_guidelines.md) 厳守（Service にロジック寄せ・config 駆動・1 ファイル 1 ユースケース・ドメイン単位パッケージ） |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` + `phpunit.xml`（Console）+ `application-test.properties`（Core）をセット更新（規約 4-3） |
| テスト値 | ハードコードせず `config()` / `@Value` 経由で取得（規約 4-1 / RRRR-8） |
| メモリ事項 | Core 側 Heap 制限（`-Xmx384m`）を意識し、入荷再計算ループは `product_id` 絞り込みを必須にする（test_insights カテゴリ7-2 / phase14_5 §1-7 と同方針） |
| 同時実行制御 | 在庫減算は `SELECT ... FOR UPDATE`（悲観ロック／RRR-8）+ phase14 既存 `@Version` 楽観ロックの併用。`product_sku_stocks` 側は既存どおり楽観、`inventories` 側は本フェーズで新設するため悲観で確定（設計書 §inventories §制約・備考） |
| 在庫モデル | 設計書 r5 の **並行運用方針**：`product_sku_stocks`（phase10 既存・読み取り正本かつ販売側書き込み正本）と `inventories`（本フェーズ新設・並行運用書き込み正本）を `InventorySyncService` で同期。完全移行は phase14 r2 へ送る |
| マイグレーション | 業務テーブルは Core `schema.sql` に冪等構文（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE` / `ALTER TABLE ADD COLUMN`）で追記（[037](../troubles/037_flyway_misassumed_phase14_tables_missing.md) / phase14 計画 §1-6 と同方針）。Console Laravel migrations には業務テーブルを追加しない |
| H2 互換 | schema.sql の MySQL 専用構文を持ち込まないことを最優先（test_insights カテゴリ7-2）。テストは `application-test.properties > spring.sql.init.schema-locations=` を空のまま、Entity から ddl-auto=create-drop で生成 |

### 設計書からの「本フェーズのスコープ外」確認（再掲）

| 項目 | 取り扱い |
|------|---------|
| 発送前キャンセル / 配達失敗 / 再配達 / 分割配送 / ギフト配送 | 設計書 §本フェーズのスコープ外。本計画でも一切触れない |
| `products.stock` 廃止と完全移行 | phase14 r2 のスコープ。本フェーズは並行運用のみ |
| 注文確定フロー本体（バリデーション・トランザクション境界） | phase14 r4（OrderConfirmationService）で既に確定済み。本フェーズは末尾スタブコメント（[OrderConfirmationService.java:165-166](../../amazia-core/src/main/java/com/example/order/service/OrderConfirmationService.java#L165-L166)）を `DeliveryCreationService.createForSales(savedSales.getId(), request.getShippingMethodId())` の実コールに置き換えるのみ |

---

## 1. Step 0 — 前提整備

### 1-1. 既存実装との整合性（2026-05-07 時点の棚卸し結果）

| 既存資産 | 場所 | フェーズ15 での利用方針 |
|---------|------|----------------------|
| `OrderConfirmationService` | [amazia-core/src/main/java/com/example/order/service/](../../amazia-core/src/main/java/com/example/order/service/OrderConfirmationService.java) | 末尾スタブコメント（L165-166）を `DeliveryCreationService.createForSales(...)` の実コールに置換（Step B-1） |
| `ReceiveProductSkuStockService` | `amazia-core/src/main/java/com/example/sku/service/` | `RegisterInboundService` 内部から呼び出し再利用（設計書 P5-5・本計画 Step B-3） |
| `operation_logs` 一式 | `amazia-core/src/main/java/com/example/operationlog/` | 本フェーズで追加する 5 種類の action（`update_shipping_status` / `update_shipping_address` / `update_scheduled_date` / `register_tracking_code` / `register_inbound`）を Service 層から記録（Step B-2 / B-4） |
| `inventory` パッケージ | 既存（`GetInventoryService` / `Controller` あり） | 旧来の SKU 横断の在庫照会用途。本フェーズで追加する `inventories` テーブル（並行運用）と**名前空間が衝突しないよう** Entity / Repository は `com.example.inventory` 配下に追加（既存と同パッケージ）し、命名で区別する（`Inventories` Entity / `InventoriesRepository`）。区別が紛らわしい場合は新規パッケージ `com.example.warehouse` 側にまとめることを Step 0 で確定する |
| `shipping_statuses` マスタ | `schema.sql` に 8 種登録済（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED / CANCELED / DELIVERY_FAILED / RESCHEDULED） | 本フェーズで使うのは PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED の 5 種のみ（CANCELED 等はスコープ外） |
| Market `Checkout.jsx` | `amazia-market/src/features/checkout/pages/` | `shippingMethodId` を既に state 管理し API リクエストに含めているため、Step D は表示反映が中心。`shipping_methods` マスタが Core に実体化（Step A）した後で `SHIPPING_METHODS` 定数を Core 由来の値で再確認 |
| Market `PurchaseHistory.jsx` | `amazia-market/src/features/orders/pages/` | 既に `SHIPPING_STATUS_LABEL` / `SHIPPING_METHOD_LABEL` で配送ステータス・方法を表示済み。`deliveries` 由来の `scheduled_date` / `delivered_date` / `tracking_code` 表示を Step D で追加 |
| 命名規約 | [operation_logs_naming.md](../ai_context/operation_logs_naming.md) | 既に phase15 用の `screen_name` / `api_name` / `action` の値が一覧化済み（§6）。本フェーズはこの値をそのまま使う |

### 1-2. Step 0-1: パッケージ構成の確定

新規 Java パッケージ：

```
com.example.delivery
├── controller   GetDeliveryController / UpdateShippingStatusController / UpdateShippingAddressController /
│                UpdateScheduledDateController / RegisterTrackingCodeController
├── service      DeliveryCreationService / DeliveryStatusTransitionService / DeliveryScheduleService /
│                DeliveryRescheduleService / GetDeliveryService / UpdateShippingAddressService /
│                UpdateScheduledDateService / RegisterTrackingCodeService
├── entity       Delivery
├── repository   DeliveryRepository
└── dto          DeliveryResponse / UpdateShippingStatusRequest / 等

com.example.inbound
├── controller   RegisterInboundController / ListInboundController
├── service      RegisterInboundService / ListInboundService
├── entity       Inbound
├── repository   InboundRepository
└── dto          InboundResponse / RegisterInboundRequest

com.example.shippingmethod
├── controller   ListShippingMethodController
├── entity       ShippingMethod
└── repository   ShippingMethodRepository

com.example.warehouse
├── entity       Warehouse
└── repository   WarehouseRepository

com.example.inventory（既存パッケージに追加）
├── entity       Inventories（既存 Inventory と命名衝突を避けるため複数形を採用）
├── repository   InventoriesRepository
└── service      InventorySyncService（販売・返品復元・入荷の全経路から呼ばれるフック）
```

`inventory` パッケージの既存 `GetInventoryService` / `GetInventoryController`（SKU 横断の在庫一覧）は触らない。本フェーズで追加する `Inventories` Entity は `inventories` テーブル（product × warehouse）を表現し、用途が異なる旨を JavaDoc に明記する。

### 1-3. Step 0-2: Console / Market のフォルダ構成

**Console**:
```
app/Delivery/
├── Controller/   ListDeliveryController / UpdateShippingStatusController / UpdateShippingAddressController /
│                  UpdateScheduledDateController / RegisterTrackingCodeController
└── Service/      ListDeliveryService / UpdateShippingStatusService / UpdateShippingAddressService /
                  UpdateScheduledDateService / RegisterTrackingCodeService

app/Inbound/
├── Controller/   ListInboundController / RegisterInboundController
└── Service/      ListInboundService / RegisterInboundService

config/app/Delivery.php  （新規）
routes/api/Delivery.php  （新規・api.php に明示 require 追加）
routes/api/Inbound.php   （新規）

resources/vue/src/features/delivery/
├── api/
└── pages/   DeliveryList.vue / DeliveryDetail.vue / DeliveryStatusUpdateDialog.vue 等

resources/vue/src/features/inbound/
├── api/
└── pages/   InboundList.vue / InboundCreate.vue
```

**Market**:
- 新規ページなし。`features/orders/pages/PurchaseHistory.jsx` と `features/checkout/` の表示拡張のみ。

### 1-4. Step 0-3: 設定ファイルの追加項目（Step A 着手前にスケルトン作成）

**Core `application.properties`** に追加：
```properties
amazia.delivery.shipping-methods.home-delivery-id=1
amazia.delivery.shipping-methods.konbini-pickup-id=2
amazia.delivery.shipping-methods.dropoff-id=3
amazia.delivery.default-warehouse-id=1
amazia.delivery.scheduled-date-reasons.manual=[manual]
amazia.delivery.scheduled-date-reasons.inbound-recalc=[inbound_recalc]
amazia.delivery.scheduled-date-reasons.shipping-delay=[shipping_delay]
amazia.delivery.lead-time-days.home-delivery=3
amazia.delivery.lead-time-days.konbini-pickup=4
amazia.delivery.lead-time-days.dropoff=2
```

**Core `application-test.properties`** にも同じキーをテスト用値で追加（規約 4-3）。

**Console `config/app/Delivery.php`** に同等の値を定義し、`config/app.php` に `'delivery' => require __DIR__.'/app/Delivery.php'` を明示追加（規約 2-1 補足3）。

**`docker-compose.yml`** に新規環境変数があれば該当サービスに追記（本フェーズでは現時点では追加環境変数は想定しないが、リードタイム等を環境変数化する場合はセット更新）。

### 1-5. Step 0-4: 完了条件

- [ ] パッケージ構成が `coding_guidelines.md` 2-1 と整合していることを確認
- [ ] `application.properties` / `application-test.properties` / `config/app/Delivery.php` のスケルトンが作成され、空状態でも既存 247 件以上のテストが緑（amazia-core 250 / amazia-console 83 / amazia-market 73 — phase14_5 完了時点）
- [ ] DB 設計書 / API 設計書の更新タスクをこの段階で発行（Step A〜D 完了時に都度更新する CLAUDE.md ルール準拠）

---

## 2. Step A — Core スキーマ + マスタ実体化

### 2-1. schema.sql 追記（`amazia-core/src/main/resources/schema.sql` 末尾）

設計書 §マイグレーション仕様（warehouses → inventories → 既存 stock 複製）と §shipping_methods マイグレーション仕様の通り、以下の順序で追記する。**MySQL 専用構文（`CREATE TABLE` 内インライン INDEX、`ON UPDATE CURRENT_TIMESTAMP`、`ADD COLUMN IF NOT EXISTS` 等）は H2 で爆発する**ので、`CREATE INDEX IF NOT EXISTS` 分離・`ALTER TABLE ADD COLUMN` は冪等性を `continue-on-error` に任せる方針（test_insights カテゴリ7-2）。

```sql
-- ============================================================================
-- フェーズ15: 配送管理（設計書 phase15_delivery_management.md r5）
-- ============================================================================

-- 1. shipping_methods マスタ（P5-1）
CREATE TABLE IF NOT EXISTS shipping_methods (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) NULL
);
INSERT IGNORE INTO shipping_methods (id, name, description) VALUES
    (1, 'home_delivery', '宅配'),
    (2, 'konbini_pickup', 'コンビニ受取'),
    (3, 'dropoff', '置き配');

-- 2. warehouses マスタ + ダミー1行（RRR-3）
CREATE TABLE IF NOT EXISTS warehouses (
    id          BIGINT NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL
);
INSERT IGNORE INTO warehouses (id, name, description) VALUES (1, 'default', '全社単一倉庫');

-- 3. inventories（並行運用書き込み正本／RRRR-1）
CREATE TABLE IF NOT EXISTS inventories (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL DEFAULT 1,
    quantity     INT NOT NULL,
    updated_at   DATETIME NOT NULL,
    UNIQUE KEY uk_inventories_product_warehouse (product_id, warehouse_id),
    CONSTRAINT fk_inventories_product   FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_inventories_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);
CREATE INDEX IF NOT EXISTS idx_inventories_product_id ON inventories (product_id);
-- CHECK 制約は MySQL 8 で有効。H2 互換も OK。
ALTER TABLE inventories ADD CONSTRAINT chk_inventories_quantity_nonneg CHECK (quantity >= 0);

-- 4. 既存 products.stock を inventories に初期複製（並行運用初期同期 / RRRR-1）
--    INSERT IGNORE で UNIQUE 制約により再実行しても二重投入されない。
INSERT IGNORE INTO inventories (product_id, warehouse_id, quantity, updated_at)
SELECT p.id, 1, COALESCE(p.stock, 0), NOW()
FROM products p;

-- 5. inbounds（入荷管理／R-3）
CREATE TABLE IF NOT EXISTS inbounds (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL DEFAULT 1,
    supplier_id  BIGINT NULL,
    quantity     INT NOT NULL,
    inbounded_at DATE NOT NULL,
    created_at   DATETIME NOT NULL,
    updated_at   DATETIME NOT NULL,
    CONSTRAINT fk_inbounds_product   FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_inbounds_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);
CREATE INDEX IF NOT EXISTS idx_inbounds_product_id   ON inbounds (product_id);
CREATE INDEX IF NOT EXISTS idx_inbounds_inbounded_at ON inbounds (inbounded_at);
ALTER TABLE inbounds ADD CONSTRAINT chk_inbounds_quantity_pos CHECK (quantity > 0);

-- 6. deliveries（配送実体／RR-3 / R-1 / R-9）
CREATE TABLE IF NOT EXISTS deliveries (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sales_id             BIGINT NOT NULL,
    shipping_address_id  BIGINT NOT NULL,
    shipping_method_id   BIGINT NOT NULL,
    shipping_status_id   BIGINT NOT NULL,
    tracking_code        VARCHAR(100) NULL,
    scheduled_date       DATE NULL,
    shipped_date         DATE NULL,
    delivered_date       DATE NULL,
    created_at           DATETIME NOT NULL,
    updated_at           DATETIME NOT NULL,
    CONSTRAINT uk_deliveries_sales_id UNIQUE (sales_id),
    CONSTRAINT fk_deliveries_sales            FOREIGN KEY (sales_id)            REFERENCES sales(id),
    CONSTRAINT fk_deliveries_address          FOREIGN KEY (shipping_address_id) REFERENCES address(id),
    CONSTRAINT fk_deliveries_shipping_method  FOREIGN KEY (shipping_method_id)  REFERENCES shipping_methods(id),
    CONSTRAINT fk_deliveries_shipping_status  FOREIGN KEY (shipping_status_id)  REFERENCES shipping_statuses(id)
);
CREATE INDEX IF NOT EXISTS idx_deliveries_shipping_status_id ON deliveries (shipping_status_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_tracking_code      ON deliveries (tracking_code);
CREATE INDEX IF NOT EXISTS idx_deliveries_scheduled_date     ON deliveries (scheduled_date);
```

### 2-2. JPA Entity / Repository 新規作成

- `Delivery` Entity（`@Table(name="deliveries")`）。`@Version` は持たず、ステータス遷移は Service 層でアトミック制御
- `Inbound` Entity（`@Table(name="inbounds")`）
- `Inventories` Entity（`@Table(name="inventories")`、`@Column(nullable=false)` で `quantity` を NOT NULL 明示。test_insights カテゴリ7-2 §038 起因の規律）
- `ShippingMethod` Entity / `Warehouse` Entity（読み取り中心のマスタ）
- 各 Repository（`JpaRepository` 継承）。`InventoriesRepository#findByProductIdAndWarehouseIdForUpdate(...)` を `@Lock(LockModeType.PESSIMISTIC_WRITE)` 付きで定義（RRR-8）
- `DeliveryRepository#findBySalesIdInAndScheduledDateIsNullOrderBySalesCreatedAtAsc(...)` 相当を入荷再計算用に追加。**SKU 単位ではなく product 単位**で `sales` を JOIN して取得（設計書 §入荷再計算ロジック）

### 2-3. テスト（TDD）

#### スキーマレベル
- マイグレーション直後、`shipping_methods` マスタ 3 件存在
- マイグレーション直後、`warehouses` マスタ 1 件存在（`id=1, name='default'`）
- マイグレーション直後、すべての既存 `products` 行に対応する `inventories` 行が存在し、`inventories.quantity == products.stock`（または NULL のとき 0）
- `inventories.quantity` を負数で UPDATE すると CHECK 制約違反（H2 / MySQL 双方）
- `inbounds.quantity` を 0 で INSERT すると CHECK 制約違反
- `deliveries.sales_id` に同じ値を 2 件 INSERT すると UNIQUE 違反

#### Entity / Repository
- `DeliveryRepository.findBySalesId(...)` で 1:1 取得できる
- `InventoriesRepository.findByProductIdAndWarehouseIdForUpdate(...)` が `@Lock(PESSIMISTIC_WRITE)` で動作する（H2 でも `@Lock` のメタデータ検証）
- 存在しない `product_id` での `inventories` INSERT は FK 違反

### 2-4. Step A 完了条件
- [ ] schema.sql 追記が `mvn test`（amazia-core）で全テスト緑（既存 250 → +スキーマレベルテスト 6〜8 件）
- [ ] `docker compose down -v && docker compose up --build` で本番想定 MySQL に対しても起動成功（test_insights カテゴリ9）
- [ ] DB 設計書 5 ファイル新規作成（`TBL_deliveries.md` / `TBL_inbounds.md` / `TBL_inventories.md` / `TBL_warehouses.md` / `TBL_shipping_methods.md`）
- [ ] `ER_diagram.md` に 5 テーブルとリレーション追加
- [ ] `database_design/README.md` のファイル一覧表に 5 テーブル追記（CLAUDE.md ルール）

---

## 3. Step B — Core Service / Controller

各 Service / Controller は coding_guidelines 2-2「1ファイル 1ユースケース」に従う。各 Service には `operation_logs` 記録の責務を直書きせず、`OperationLogService` を DI して呼び出す。

### 3-1. Step B-1: DeliveryCreationService + OrderConfirmationService 連携（P5-2）

#### 実装物
- `com.example.delivery.service.DeliveryCreationService.createForSales(long salesId, long shippingMethodId)`（過渡期シグネチャ／RRRR-3）
  1. `sales` レコード取得。存在しなければ `ResponseStatusException(404)`
  2. 防御的バリデーション：`sales.is_preorder == false && product_sku_stocks.quantity < sales.quantity` の場合は `ResponseStatusException(409, "out of stock")`（RRR-2 / 注文確定 Service が漏らした場合の最後の砦）
  3. `DeliveryScheduleService.calculate(salesId, productStockSnapshot)` で `scheduled_date` 算出。在庫切れ（予約購入かつ stock=0）なら `null`
  4. `INSERT deliveries` (`shipping_status_id = PENDING_ID`, `tracking_code/shipped_date/delivered_date = NULL`)
- `OrderConfirmationService` の L165-166 のコメントを実コールに置換：
  ```java
  // 6. phase15 r5 連携：deliveries 生成
  deliveryCreationService.createForSales(savedSales.getId(), request.getShippingMethodId());
  ```
  既存の `@Transactional` 配下にあるため、`DeliveryCreationService` の例外発生時は注文確定全体がロールバック（設計書 §P5-2）

#### Step B-1 のテスト
- 注文確定時（OrderConfirmationServiceTest 既存 8 件 + 新規追加）：
  - 通常購入の `confirm()` 成功時、`deliveries` が `PENDING` で 1 件生成され、`scheduled_date` が NOT NULL
  - 予約購入（`is_preorder=true`）の `confirm()` 成功時、`deliveries` は生成されるが `scheduled_date = NULL`
  - `DeliveryCreationService` が例外を投げた場合、`sales` も生成されない（トランザクション境界）
- DeliveryCreationServiceTest（単体）：
  - 通常購入で在庫切れ sales が渡された場合の防御的例外（409）
  - `sales_id` が存在しない場合の 404
  - `UNIQUE(sales_id)` 違反は通常起きないが、二重呼び出し時に DataIntegrityViolationException

### 3-2. Step B-2: DeliveryStatusTransitionService（出荷時の在庫処理 P5-3 / P5-4 を含む）

#### 実装物
- `DeliveryStatusTransitionService.transition(long deliveryId, long nextStatusId, String reason)`
  - 設計書 §配送ステータス遷移ルール の遷移可否表を Service 層でガード（不正遷移は `ResponseStatusException(400)`）
  - **`PENDING → SHIPPED` 遷移時の在庫処理（P5-3 / P5-4）**：
    1. 対象 `sales` の `is_preorder` を見て分岐
    2. `is_preorder == false`：何もしない（注文確定時に減算済み）
    3. `is_preorder == true`：
       - `product_sku_stocks.quantity < sales.quantity` なら `ResponseStatusException(409, "preorder shipment blocked: insufficient stock")` を投げ、`deliveries.shipping_status_id` は **PENDING のまま維持**（トランザクション全体ロールバック）
       - 在庫充足なら `product_sku_stocks.quantity -= sales.quantity`（`@Version` 楽観ロック）+ `product_sku_stock_transactions` に `type='sale_preorder_shipment'` / `reference_type='sales'` / `reference_id=sales.id` で記録
       - 並行運用同期：`InventorySyncService.applyDelta(productId, 1, -sales.quantity)`
    4. 在庫不足例外時は `OperationLogService.record(action='shipping_blocked_insufficient_stock', target_type='deliveries', target_id=deliveryId, comment="sales_id=N, shortage=M")` を記録
  - 正常遷移時は `deliveries.shipping_status_id` 更新 + `shipped_date`（SHIPPED 時）/ `delivered_date`（DELIVERED 時）を `LocalDate.now()` で埋める
  - `OperationLogService.record(action='update_shipping_status', target_type='deliveries', target_id=deliveryId, comment="旧: 〇 → 新: 〇 / 理由: ...")` を記録

#### Step B-2 のテスト
- 正常遷移：PENDING→SHIPPED→DELIVERED→RETURN_REQUESTED→RETURNED の 4 段階を順に実行できる
- 不正遷移：DELIVERED→PENDING、SHIPPED→PENDING、RETURNED→任意 等が拒否される
- 通常購入の SHIPPED 遷移は `product_sku_stocks` を変更しない
- 予約購入の SHIPPED 遷移は `product_sku_stocks.quantity` と `inventories.quantity` を同時減算する
- 予約購入で在庫不足の SHIPPED 遷移は CONFLICT 例外を投げ、`deliveries.shipping_status_id == PENDING` のまま、在庫も変動なし、`operation_logs` に `shipping_blocked_insufficient_stock` 1 件記録（テストでアサート値は `config('amazia.delivery.shipping-method-ids.*')` 等を経由）
- `operation_logs.action == 'update_shipping_status'` が遷移成功時に記録される

### 3-3. Step B-3: RegisterInboundService + ReceiveProductSkuStockService 流用（P5-5）

#### 実装物
- `com.example.inbound.service.RegisterInboundService.register(RegisterInboundRequest req)`
  1. バリデーション：`product_id` 存在、`quantity > 0`、`warehouse_id == config('amazia.delivery.default-warehouse-id')`（UI から渡されない場合は自動セット／RRRR-5）
  2. `INSERT inbounds`（同一トランザクション）
  3. **既存 `ReceiveProductSkuStockService.receive(skuId, quantity)` を SKU 単位で呼び出し**（`product_id` から該当 SKU をどう特定するかは Step B-3 着手時に確認：商品が複数 SKU を持つ場合の入荷分配は phase15 のスコープ外なので、本フェーズは「`product_id × warehouse_id` 単位の入荷ヘッダ」と「SKU 単位の在庫増分」を切り離し、SKU 単位の在庫増分は Console UI 側で SKU を選択して別 API（既存 `POST /api/sku-stocks/receive`）に投げる二段運用とする）
     - **着手時方針確定が必要**：設計書 §RegisterInboundService の責務 4 ステップ目に「`ReceiveProductSkuStockService` を呼び出して `product_sku_stocks` の対応 SKU を加算」とあるが、`product_id → SKU` の写像は曖昧。Step B-3 着手前にユーザー確認の上で確定する。本計画では一旦「Console UI で SKU を選ぶ前提で `RegisterInboundService` のシグネチャを `register(productId, skuId, quantity, ...)` とする」案を採用
  4. `InventorySyncService.applyDelta(productId, 1, +quantity)`（並行運用同期）
  5. `DeliveryRescheduleService.recalculateForProduct(productId)` 呼び出し（次サブステップ B-4）
  6. `OperationLogService.record(action='register_inbound', target_type='inbounds', target_id=inbound.id, comment="商品ID=N, 数量=M, 倉庫=1")`

#### Step B-3 のテスト
- 入荷登録 1 件で `inbounds` INSERT、`product_sku_stocks` 加算、`inventories.quantity` 加算が同一トランザクションで実行される
- 入荷登録の途中で例外発生時、3 つの変更すべてロールバック（並行運用整合性 / RRRR-7）
- `quantity <= 0` のリクエストを拒否（CHECK 制約 + Service バリデーション）
- 存在しない `product_id` でのリクエストを拒否
- リクエストに `warehouse_id` が無くても `default-warehouse-id` が自動セットされる（RRRR-5）
- `operation_logs` に `register_inbound` 1 件記録

### 3-4. Step B-4: DeliveryRescheduleService（入荷再計算 FIFO）

#### 実装物
- `DeliveryRescheduleService.recalculateForProduct(long productId)`
  - 設計書 §入荷再計算ロジック の擬似コード通りに実装
  - `inventories` を `FOR UPDATE` で取得（RRRR-4 / RRR-8）
  - `deliveries JOIN sales WHERE sales.product_id（実際は sku_id 経由で product_id）= productId AND scheduled_date IS NULL ORDER BY sales.created_at ASC FOR UPDATE`
  - ループローカル変数 `available` で消費トラッキング（RRRR-4）
  - 充足できた `deliveries` の `scheduled_date` を `DeliveryScheduleService.calculate(salesId, available)` で算出
  - 各更新時に `OperationLogService.record(action='update_scheduled_date', target_type='deliveries', target_id=delivery.id, comment="[inbound_recalc] 旧:NULL → 新:YYYY-MM-DD", screen_name='core.batch.inbound_recalc', api_name=NULL)`
  - **`inventories.quantity` の DB 反映はこのループでは行わない**（実販売減算は SHIPPED 遷移時 / 設計書 §入荷再計算ロジック 末尾）
- `DeliveryScheduleService.calculate(long salesId, int stockAvailable)`
  - 入力：注文日（sales.created_at）、配送先都道府県（address.prefecture から SKU 配送方法のリードタイム）、`shipping_methods` 標準リードタイム（config から取得）、stock_available
  - 出力：`LocalDate` または `null`（在庫不足）
  - リードタイム値は `application.properties > amazia.delivery.lead-time-days.*` から `@Value` で取得（規約 4-1）

#### Step B-4 のテスト
- 入荷登録により、在庫切れで `scheduled_date = NULL` だった `deliveries` が `sales.created_at` 昇順 FIFO で再計算される
- 入荷数量 < 在庫切れ注文数の場合、古い順から充足し、充足できなかった `deliveries.scheduled_date` は NULL のまま保持
- 再計算時、`DeliveryScheduleService.calculate` の在庫入力は `inventories` 最新値（`FOR UPDATE`）を参照（RRRR-4）
- 各 `scheduled_date` 更新ごとに `operation_logs` に `[inbound_recalc]` プレフィックス付きで記録
- リードタイムを `@Value` 経由で取得していること（テスト内で `application-test.properties` の値を `@Value` 注入してアサート）

### 3-5. Step B-5: InventorySyncService（販売側フック RRRR-2）

#### 実装物
- `com.example.inventory.service.InventorySyncService.applyDelta(long productId, long warehouseId, int delta)`
  - `SELECT ... FOR UPDATE` で `inventories` 行取得（RRR-8）
  - `quantity = quantity + delta` で UPDATE（CHECK 制約違反は例外として伝播）
  - `inventories` 行が存在しない `product_id`（並行運用マイグレーション未実施）の場合は明示的なエラー（`IllegalStateException("inventories row missing for productId=N")`／RRRR-1）
- 呼び出し位置の埋め込み（phase14 既存コードへの最小修正）：
  - **販売処理**：`OrderConfirmationService.confirm()` の L141-143（`stock.setQuantity(...) + skuStockRepository.save(stock)` 直後）に `inventorySyncService.applyDelta(sku.getProductId(), 1, -request.getQuantity())` を追加
  - **返品復元**：`com.example.salesreturn.service.RefundSalesReturnService`（phase14 で実装済）の在庫戻し直後に `inventorySyncService.applyDelta(sku.getProductId(), 1, +salesReturn.getQuantity())` を追加
  - **入荷**：`RegisterInboundService.register()` 内（Step B-3 で実装）
  - **予約出荷時減算**：`DeliveryStatusTransitionService` 内（Step B-2 で実装）

#### Step B-5 のテスト
- 販売（注文確定）1 件後、対象 `productId` の `inventories.quantity == products.stock`（並行運用整合性）
- 返品復元 1 件後、同様に `inventories.quantity == products.stock`
- 入荷登録 1 件後、同様
- 予約出荷時の在庫減算後、同様
- `inventories` 行が存在しない `product_id` で `applyDelta` 呼び出し → `IllegalStateException`（RRRR-1）
- 並行実行（2 スレッドで同時減算）でもデッドロックや race condition なく整合性が保たれる（`@Lock(PESSIMISTIC_WRITE)` のシリアライズで担保）

### 3-6. Step B-6: 配送 CRUD Controller（GET / 各 PATCH）

#### 実装物（命名規約は `operation_logs_naming.md` §6 を参照）
| Controller | エンドポイント | Service |
|-----------|--------------|---------|
| `GetDeliveryController` | `GET /api/deliveries/{id}` | `GetDeliveryService` |
| `ListDeliveryController` | `GET /api/deliveries`（ページング・ステータスフィルタ） | `ListDeliveryService` |
| `UpdateShippingStatusController` | `PATCH /api/deliveries/{id}/status` | `DeliveryStatusTransitionService`（B-2 で既実装） |
| `UpdateShippingAddressController` | `PATCH /api/deliveries/{id}/address` | `UpdateShippingAddressService`（オーナー検証 RRR-7） |
| `UpdateScheduledDateController` | `PATCH /api/deliveries/{id}/scheduled-date` | `UpdateScheduledDateService`（手動更新時は reason='[manual]' 固定） |
| `RegisterTrackingCodeController` | `PATCH /api/deliveries/{id}/tracking-code` | `RegisterTrackingCodeService` |
| `ListShippingMethodController` | `GET /api/shipping-methods`（マスタ） | 直接 Repository から返却 |
| `RegisterInboundController` | `POST /api/inbounds` | `RegisterInboundService`（B-3 で既実装） |
| `ListInboundController` | `GET /api/inbounds` | `ListInboundService` |

各 Controller は coding_guidelines 1-1 に従い、Service 呼び出しのみ。バリデーションは `@Valid` + DTO の `@NotNull` / `@Min(1)` 等。

#### Step B-6 のテスト
- 各 Controller の正常系 / 異常系（401 / 404 / 409 / 422）
- `UpdateShippingAddress` で `sales.user_id` 所有外の `address.id` を指定した場合に拒否（RRR-7）
- `UpdateScheduledDate` 手動更新時、`operation_logs.comment` 先頭が `[manual]`
- `RegisterTrackingCode` 後、`operation_logs.action == 'register_tracking_code'` 記録
- `GlobalExceptionHandler` 経由で `ResponseStatusException` のメッセージが JSON `{ "message": "..." }` で返ることを確認（test_insights カテゴリ2 / 021 起因）

### 3-7. Step B 完了条件
- [ ] amazia-core `mvn test` 全件緑（phase14_5 完了時 250 → +30〜40 件想定）
- [ ] OrderConfirmationServiceTest が `deliveries` 生成を含む形で全件緑
- [ ] `Core_API.md` に追加した 9 エンドポイントの記載完了
- [ ] `docs/database_design/TBL_*.md` 5 ファイルの整合（外部キー制約等）を再確認

---

## 4. Step C — Console UI

### 4-1. Step C-1: Delivery 中継 Service / Controller（Laravel）

#### 実装物（`app/Delivery/`）
- `ListDeliveryService` / `ListDeliveryController` — Core API `/api/deliveries` への中継。検索条件（ステータス・日付範囲・配送方法・追跡番号）を `Http::` 経由で転送
- `UpdateShippingStatusService` / `UpdateShippingStatusController` — 中継 + 認可（`auth.jwt`）+ レスポンスの 404/409 を Console エラーレスポンスに整形
- `UpdateShippingAddressService` / `UpdateShippingAddressController` — 同上。UI で `is_active=true` の住所のみ表示する仕様（S14-10 と同様、Console PHPUnit でフィルタ確認）
- `UpdateScheduledDateService` / `UpdateScheduledDateController`
- `RegisterTrackingCodeService` / `RegisterTrackingCodeController`

各 Service は `Http::baseUrl(config('app.core_url'))` 経由で Core を叩く（test_insights カテゴリ7 / config 経由のハードコード排除）。

#### Step C-1 のテスト（PHPUnit）
- `Http::fake()` で Core を偽装し、各 Controller が正常系・404・409・422 を Console レスポンスに整形できる
- 認可：未認証で叩くと 401（公開ルートに混入していないこと／カテゴリ8）
- リクエストペイロードに `config('amazia.delivery.shipping-methods.home-delivery-id')` 等を `config()` 経由で参照しているか（規約 4-1）

### 4-2. Step C-2: Inbound 中継 Service / Controller

`app/Inbound/` に `ListInboundService` / `RegisterInboundService`（Console 側）+ Controller。

**`warehouse_id` は UI からは送られない**前提で Console Controller も `warehouse_id` を受け取らず、Core の `default-warehouse-id` 自動セットに任せる（RRRR-5）。Console PHPUnit で「リクエストに `warehouse_id` が含まれていないこと」をアサート。

### 4-3. Step C-3: Vue 画面実装

#### `resources/vue/src/features/delivery/`
- `DeliveryList.vue` — 一覧 + フィルタ（ステータス・配送方法・期間・追跡番号）
- `DeliveryDetail.vue` — 詳細表示。下記のダイアログを呼び出し
- `DeliveryStatusUpdateDialog.vue` — 遷移可否を Vue 側でも制御（Service 層が最終判定）
- `DeliveryAddressUpdateDialog.vue` — `is_active=true` の住所一覧をプルダウン表示
- `DeliveryScheduledDateUpdateDialog.vue` — 日付ピッカー + 理由テキスト入力（自動で `[manual]` プレフィックス付与）
- `DeliveryTrackingCodeRegisterDialog.vue` — テキスト入力

#### `resources/vue/src/features/inbound/`
- `InboundList.vue` — 入荷履歴一覧（商品ID・数量・入荷日）
- `InboundCreate.vue` — 入荷登録フォーム。**倉庫選択フィールドは表示しない**（RRRR-5）。商品選択 + SKU 選択（B-3 着手時方針確定に従う）+ 数量 + 入荷日

#### ルート登録 / メニュー登録（test_insights カテゴリ3）
- `router/index.js` に `/delivery` `/delivery/:id` `/inbound` `/inbound/create` を登録
- `App.vue` のサイドメニューに「配送管理」「入荷管理」リンク追加
- ルート定義の順序：静的 (`/inbound/create`) > 動的 (`/delivery/:id`)（カテゴリ2）

### 4-4. Step C-3 のテスト（Vitest / 一部 PHPUnit）
- DeliveryList が API レスポンスをテーブル表示できる
- DeliveryStatusUpdateDialog が PENDING→SHIPPED 遷移リクエストを送信できる
- AddressUpdateDialog のプルダウンに `is_active=false` の住所が**表示されない**
- ScheduledDateUpdateDialog から送信時、`reason` プレフィックス `[manual]` が**自動付与される**（Service 層で固定）
- InboundCreate に**倉庫選択フィールドが表示されない**（RRRR-5）
- InboundCreate からのリクエストペイロードに `warehouse_id` が含まれない（Console PHPUnit）
- 不正な遷移を Console から要求した場合、エラーレスポンスを画面に表示

### 4-5. Step C 完了条件
- [ ] amazia-console `phpunit` 全件緑（phase14_5 完了時 83 → +20〜25 件想定）
- [ ] Vue 画面が router/メニュー含めてブラウザで動作確認済（CLAUDE.md ルール / test_insights カテゴリ3）
- [ ] `Console_API.md` に追加した 7 エンドポイントの記載完了
- [ ] スクリーンショットを各画面分取得（フェーズ完了の定義 §3）

---

## 5. Step D — Market 表示反映

### 5-1. PurchaseHistory.jsx の拡張

既存の `SHIPPING_STATUS_LABEL` / `SHIPPING_METHOD_LABEL` 定数を活用しつつ、`deliveries` 由来の以下を追加表示：
- `scheduled_date`（NULL のときは「入荷待ち」と表示／RR-4 / 設計書 §Market）
- `shipped_date` / `delivered_date`（あれば表示）
- `tracking_code`（あれば「追跡番号: XXX」表示）

API レスポンス契約の確認（test_insights 035 起因）：
- Core `GET /api/customer/sales`（既存）の SalesResponse に `delivery: { scheduledDate, shippedDate, deliveredDate, trackingCode, shippingMethodId, shippingStatusId }` をネストして含める形に拡張
- フロントのモックデータも実 JSON と同じフィールド名で書く

### 5-2. Checkout.jsx の確認

`shippingMethodId` は既に state 管理 + API 送出済み。Step A で `shipping_methods` マスタが Core 実体化したので、`SHIPPING_METHODS` 定数の値（id: 1, 2, 3）が Core マスタと一致することを再確認するのみ。差分が出れば config 経由で取得する形に揃える。

### 5-3. Step D のテスト（Vitest）
- PurchaseHistory に配送方法 / ステータス / 配送予定日 / 完了日 / 追跡番号が正しく表示される
- `scheduled_date == null` のときに「入荷待ち」表示
- 配送方法（home_delivery / konbini_pickup / dropoff）が正しい表示名
- 注文確定 API リクエストで `shipping_method_id` が正しく含まれる（既存テストの拡張で OK）
- `deliveries` モックデータが Core JSON フィールド名（camelCase）で書かれていること

### 5-4. Step D 完了条件
- [ ] amazia-market `vitest` 全件緑（phase14_5 完了時 73 → +5〜10 件想定）
- [ ] Market 購入履歴画面で配送情報が表示される（ブラウザ実機確認）
- [ ] `Market_API.md` の `/api/customer/sales` レスポンス記述更新

---

## 6. Step E — 並行運用整合性 + フェーズ完了確認

### 6-1. 並行運用整合性テスト（RRRR-7 / 設計書 §並行運用整合性テスト）

amazia-core に新規テストクラス `InventoryParallelOperationIntegrationTest`（or 既存の Service テストに分散）：
- マイグレーション直後、すべての商品で `inventories.quantity == products.stock`
- 注文確定（販売）1 件後、対象商品の不変条件維持
- 返品復元 1 件後、対象商品の不変条件維持
- 入荷登録 1 件後、対象商品の不変条件維持
- 予約出荷時減算 1 件後、対象商品の不変条件維持
- 入荷登録途中で例外発生時、`inbounds` / `inventories` / `products.stock` のすべてがロールバック

### 6-2. End-to-end 動作確認（CLAUDE.md フェーズ完了の定義）

**ローカル**：
1. `docker compose down -v && docker compose up --build` で DB 初期化からエラーなく起動（test_insights カテゴリ9）
2. Market から商品購入 → Console で配送ステータス更新（PENDING→SHIPPED→DELIVERED）→ Market 購入履歴に反映
3. Console で入荷登録 → 在庫切れで NULL だった他注文の `scheduled_date` が再計算
4. 予約購入注文 → 在庫不足のまま SHIPPED 試行 → 409 エラー + PENDING 維持を画面確認

**本番（CloudFront + EC2）**：
1. デプロイ後、両レイヤー（Console 8000 / Core 8080）に直接 curl して新エンドポイント応答確認（test_insights カテゴリ10）
2. 本番ドメインで Market 購入 → Console 配送管理操作 → Market 購入履歴反映の一連を実機確認（test_insights §本番初動でのみ顕在化する潜在不具合）
3. スクリーンショット添付

### 6-3. ドキュメント更新（CLAUDE.md ルール）

- [ ] `phase15_delivery_management.md` のステータスを `🔲 未着手` → `✅ 完了（YYYY-MM-DD）` に更新
- [ ] 改訂履歴に「r5 実装完了 (YYYY-MM-DD)」を追加
- [ ] `次タスク.txt` のフェーズ15欄を完了マーク
- [ ] `docs/database_design/`：`TBL_deliveries.md` / `TBL_inbounds.md` / `TBL_inventories.md` / `TBL_warehouses.md` / `TBL_shipping_methods.md` / `TBL_sales.md`（FK 追加分）/ `TBL_address.md`（FK 追加分）/ `ER_diagram.md` / `README.md` 更新済
- [ ] `docs/api_design/`：`Core_API.md` / `Console_API.md` / `Market_API.md` 更新済
- [ ] `docs/ai_context/test_insights.md` に新規テスト観点を抽出して追記（並行運用整合性 / 出荷時在庫不足の挙動 / `inventories` 行欠落時のエラー等）

### 6-4. Step E 完了条件
- [ ] Core / Console / Market 全層テストグリーン
- [ ] 並行運用整合性テスト 5 観点すべて緑
- [ ] ローカル `down -v` からの起動成功
- [ ] 本番 end-to-end 動作確認 + スクリーンショット
- [ ] phase15 設計書ステータス更新

---

## 7. 横断タスク（全 Step で意識）

| 項目 | 内容 |
|------|------|
| TDD | 各 Step で設計書 §TDDテストケース の該当項目を実装。先にテスト → 実装 → グリーン → 次 Step |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` / `phpunit.xml`（Console）/ `application-test.properties`（Core）セット更新（規約 4-3） |
| テスト値 | `config()` / `@Value` 経由（規約 4-1 / RRRR-8） |
| 操作履歴 | 各 Service で `operation_logs` への記録を直書きせず `OperationLogService` に集約。`screen_name` / `api_name` / `action` の値は `operation_logs_naming.md` §6 に従う |
| 同時実行制御 | 在庫減算は `@Lock(PESSIMISTIC_WRITE)`（`inventories` 側）+ `@Version`（`product_sku_stocks` 側）の併用。デッドロック回避のため、複数行更新時は `productId` 昇順でロック取得 |
| 文書 | 完了時に phase15_delivery_management.md の改訂履歴に「r5 実装完了 (YYYY-MM-DD)」追記、`次タスク.txt` のフェーズ15欄更新 |
| トラブル対応 | 不具合発生時は `docs/troubles/NNN_<概要>.md` を新規作成し再発防止策を記録（CLAUDE.md ルール） |
| H2 互換 | schema.sql は MySQL 専用構文を避け、`CREATE INDEX IF NOT EXISTS` 等で H2 と両対応（test_insights カテゴリ7-2） |
| 本番初動確認 | フェーズ完了前に `docker compose down -v && up --build` でローカル DB 初期化から起動成功させる（カテゴリ9 / 018・029 起因） |

---

## 8. 着手前の確認事項（Step 0 着手時にユーザー確認推奨）

| # | 確認事項 | 候補方針 |
|---|---------|---------|
| 1 | `RegisterInboundService` のシグネチャ：`product_id × warehouse_id` 単位の入荷ヘッダと SKU 単位在庫増分の関係 | 案A: `register(productId, skuId, quantity, ...)`（UI で SKU 選択） / 案B: 入荷ヘッダだけ作り SKU 在庫増分は既存 `POST /api/sku-stocks/receive` で別操作（二段運用） |
| 2 | `DeliveryRescheduleService` の SKU 単位 vs product 単位 | 設計書は `s.product_id = :product_id` 前提だが、`sales` テーブルは `sku_id` 保持。`sales JOIN product_skus ON sales.sku_id = product_skus.id WHERE product_skus.product_id = :productId` で取得する想定 |
| 3 | `DeliveryScheduleService.calculate()` の都道府県ベースリードタイム | 初期は `shipping_methods` × 全国一律のリードタイム（config 値）でスタート。都道府県別はマスタ化を将来課題に登録 |
| 4 | `inventory` 既存パッケージとの命名衝突回避 | 案A: 既存パッケージに `Inventories` Entity を追加（複数形で区別）/ 案B: 新規パッケージ `com.example.inventories` を切る |
| 5 | Step C の Vue 画面はどこまでデザインを揃えるか | phase14 の Console 画面（売上一覧・返品管理）のスタイルに合わせる。既存テンプレート流用前提 |

---

## 9. 参考リンク

- 設計書: [phase15_delivery_management.md](../design/phase11_20/phase15_delivery_management.md)
- 親フェーズ: [phase14_implementation_plan.md](phase14_implementation_plan.md) / [phase14_5_implementation_plan.md](phase14_5_implementation_plan.md)
- 関連設計書: [phase14_shipping.md](../design/phase11_20/phase14_shipping.md) / [phase14_5_preorder_status.md](../design/phase11_20/phase14_5_preorder_status.md)
- コーディング規約: [coding_guidelines.md](../coding_guidelines.md)
- AIコンテキスト（テスト観点）: [test_insights.md](../ai_context/test_insights.md)
- AIコンテキスト（実装・運用パターン）: [operational_insights.md](../ai_context/operational_insights.md)
- 命名規約: [operation_logs_naming.md](../ai_context/operation_logs_naming.md)
- プロジェクトAIコンテキスト: [CLAUDE.md](../../CLAUDE.md)
