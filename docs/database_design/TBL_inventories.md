# テーブル定義書：inventories

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | inventories |
| 論理名 | 商品×倉庫の現在在庫 |
| 所属システム | Core |
| 説明 | 商品×倉庫の現在在庫。並行運用書き込み正本：入荷・販売・返品復元のすべての経路から `InventorySyncService` 経由で同期更新される。読み取り正本は phase14 r2 まで `products.stock` のまま |
| 追加フェーズ | フェーズ15（r5 / RRRR-1 / RRRR-2 / RRR-8） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 在庫ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | product_id | 商品ID | BIGINT | - | NOT NULL | - | FK → `products.id` |
| 3 | warehouse_id | 倉庫ID | BIGINT | - | NOT NULL | 1 | FK → `warehouses.id`（並行運用期は常に 1） |
| 4 | quantity | 在庫数 | INT | - | NOT NULL | - | `CHECK (quantity >= 0)` で負数を拒否（RRR-8） |
| 5 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | Entity の `@PrePersist` / `@PreUpdate` で更新 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_inventories_product_warehouse | UNIQUE | (product_id, warehouse_id) |
| idx_inventories_product_id | INDEX | product_id |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| uk_inventories_product_warehouse | UNIQUE | 商品×倉庫の組合せの一意性 |
| chk_inventories_quantity_nonneg | CHECK | `quantity >= 0` |
| fk_inventories_product | FK | `product_id` → `products.id` |
| fk_inventories_warehouse | FK | `warehouse_id` → `warehouses.id` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| products | N:1 | 在庫対象の商品 |
| warehouses | N:1 | 在庫を保持する倉庫（並行運用期はダミー id=1） |

## 並行運用更新ルール（RRRR-2）

| 経路 | products.stock | inventories.quantity |
|------|----------------|---------------------|
| 入荷登録 | 加算（同一トランザクション） | 加算（同一トランザクション） |
| 販売（注文確定 / phase14 既存） | 減算 | 減算（`InventorySyncService.applyDelta` で同期） |
| 返品復元（phase14 既存） | 加算 | 加算（同上） |
| 予約購入の出荷時減算（P5-3） | 減算 | 減算 |

不変条件：任意時点で `products.stock(product_id) == SUM(inventories.quantity WHERE product_id=...)`。

## 設計上の注意

- 並行運用期は **書き込み正本**。phase14 r2 で完全移行されるまで読み取り正本は `products.stock` のまま。
- 在庫減算は `SELECT ... FOR UPDATE`（悲観ロック）で同時実行制御（`InventoriesRepository.findByProductIdAndWarehouseIdForUpdate`）。
- 並行運用開始時のマイグレーションで、既存 `products.stock` の値を `inventories` に複製する初期データ投入が必須（RRRR-1）。schema.sql で `INSERT IGNORE INTO inventories ... SELECT id, 1, COALESCE(stock,0), CURRENT_TIMESTAMP FROM products` で実施。
- `inventories` 行が存在しない `product_id` で `applyDelta` 呼び出しが行われた場合は `IllegalStateException` で停止（RRRR-1）。
- phase14 r2 で `products.stock` 廃止と読み取り正本切替後、`InventorySyncService` フックは削除予定。

## Entity

`com.example.inventory.entity.Inventories`（既存 `GetInventoryService`（SKU 横断）と用途を区別するため複数形採用）。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ15 / r5）
