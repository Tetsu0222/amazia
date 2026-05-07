# テーブル定義書：inbounds

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | inbounds |
| 論理名 | 商品入荷ヘッダ |
| 所属システム | Core |
| 説明 | 商品の入荷（仕入）情報を保持。入荷登録時に `inventories.quantity` と `products.stock` を同一トランザクションで加算し、在庫切れ `deliveries.scheduled_date` を FIFO で再計算する |
| 追加フェーズ | フェーズ15（r5 / R-3 / RRR-10） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 入荷ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | product_id | 商品ID | BIGINT | - | NOT NULL | - | FK → `products.id` |
| 3 | warehouse_id | 倉庫ID | BIGINT | - | NOT NULL | 1 | FK → `warehouses.id`（並行運用期は常に 1） |
| 4 | supplier_id | 仕入先ID | BIGINT | - | NULL | NULL | 将来マスタ化（本フェーズではスコープ外） |
| 5 | quantity | 入荷数量 | INT | - | NOT NULL | - | `CHECK (quantity > 0)` で 0・負数を拒否（RRR-10） |
| 6 | inbounded_at | 入荷日 | DATE | - | NOT NULL | - | 入荷実施日 |
| 7 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | Entity の `@PrePersist` で設定 |
| 8 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | Entity の `@PreUpdate` で更新 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_inbounds_product_id | INDEX | product_id |
| idx_inbounds_inbounded_at | INDEX | inbounded_at |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| chk_inbounds_quantity_pos | CHECK | `quantity > 0` |
| fk_inbounds_product | FK | `product_id` → `products.id` |
| fk_inbounds_warehouse | FK | `warehouse_id` → `warehouses.id` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| products | N:1 | 入荷対象の商品 |
| warehouses | N:1 | 入荷先倉庫 |
| inventories | 同期更新 | 入荷登録 Service が `inventories.quantity` も同一トランザクションで加算 |

## 設計上の注意

- 入荷ヘッダ単位（`product_id × warehouse_id × 数量 × 入荷日`）。SKU 単位の在庫増分は `RegisterInboundService` 内部で既存 `ReceiveProductSkuStockService` を呼び出して `product_sku_stocks` を更新する（P5-5）。
- Console UI では倉庫選択フィールドを表示せず、バックエンドが `warehouse_id = 1`（ダミー倉庫）を自動セットする（RRRR-5 / `warehouses` が2行以上になった時点で UI に追加）。
- 入荷登録時、対象商品の在庫切れにより `scheduled_date = NULL` だった `deliveries` を `sales.created_at` 昇順 FIFO で再計算する（`DeliveryRescheduleService.recalculateForProduct`）。
- 入荷登録は `OperationLogService.record(action='register_inbound', target_type='inbounds', ...)` で操作履歴に記録（命名規約 §6）。

## Entity

`com.example.inbound.entity.Inbound`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ15 / r5）
