# テーブル定義書：deliveries

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | deliveries |
| 論理名 | 配送実体 |
| 所属システム | Core |
| 説明 | 注文確定と同時に `sales` 1:1 で生成される配送実体。配送ステータス遷移・追跡番号・配送予定日などの実オペレーション情報を保持する |
| 追加フェーズ | フェーズ15（r5 / RR-3 / R-1 / R-9 / RR-4） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 配送ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | sales_id | 売上ID | BIGINT | - | NOT NULL | - | FK → `sales.id`、UNIQUE（RR-3 / 1:1） |
| 3 | shipping_address_id | 配送先住所ID | BIGINT UNSIGNED | - | NOT NULL | - | FK → `address.id` |
| 4 | shipping_method_id | 配送方法ID | BIGINT | - | NOT NULL | - | FK → `shipping_methods.id` |
| 5 | shipping_status_id | 配送ステータスID | BIGINT | - | NOT NULL | - | FK → `shipping_statuses.id` |
| 6 | tracking_code | 追跡番号 | VARCHAR | 100 | NULL | NULL | 配送業者発行番号（R-2） |
| 7 | scheduled_date | 配送予定日 | DATE | - | NULL | NULL | 在庫切れ等で確定不能なときは NULL（入荷時 FIFO 再計算で確定） |
| 8 | shipped_date | 発送日 | DATE | - | NULL | NULL | SHIPPED 遷移時に Service 層が `LocalDate.now()` を設定 |
| 9 | delivered_date | 配達完了日 | DATE | - | NULL | NULL | DELIVERED 遷移時に同上 |
| 10 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | Entity の `@PrePersist` で設定 |
| 11 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | Entity の `@PreUpdate` で更新 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_deliveries_sales_id | UNIQUE | sales_id |
| idx_deliveries_shipping_status_id | INDEX | shipping_status_id |
| idx_deliveries_tracking_code | INDEX | tracking_code |
| idx_deliveries_scheduled_date | INDEX | scheduled_date |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| uk_deliveries_sales_id | UNIQUE | `sales:deliveries = 1:1` の保証（RR-3） |
| fk_deliveries_sales | FK | `sales_id` → `sales.id` |
| fk_deliveries_address | FK | `shipping_address_id` → `address.id` |
| fk_deliveries_shipping_method | FK | `shipping_method_id` → `shipping_methods.id` |
| fk_deliveries_shipping_status | FK | `shipping_status_id` → `shipping_statuses.id` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| sales | 1:1 | 売上スナップショット |
| address | N:1 | 配送先住所スナップショット |
| shipping_methods | N:1 | 配送方法マスタ（home_delivery / konbini_pickup / dropoff） |
| shipping_statuses | N:1 | 配送ステータスマスタ（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED） |

## 配送ステータス遷移ルール（R-4 / R-7 / RR-1）

| 現在 → 次 | PENDING | SHIPPED | DELIVERED | RETURN_REQUESTED | RETURNED |
|-----------|---------|---------|-----------|------------------|----------|
| PENDING | - | ✅ | ❌ | ❌ | ❌ |
| SHIPPED | ❌ | - | ✅ | ❌ | ❌ |
| DELIVERED | ❌ | ❌ | - | ✅ | ❌ |
| RETURN_REQUESTED | ❌ | ❌ | ❌ | - | ✅ |
| RETURNED | ❌ | ❌ | ❌ | ❌ | - |

CANCELED / DELIVERY_FAILED / RESCHEDULED は phase15 r5 のスコープ外（マスタ存在 ≠ 入力許容）。

## 設計上の注意

- 注文確定時に `OrderConfirmationService.confirm()` から `DeliveryCreationService.createForSales(salesId, shippingMethodId)` を呼び出して `PENDING` で生成する（過渡期シグネチャ／RRRR-3）。phase14 r2 で `sales.shipping_method_id` カラム追加後は `createForSales(salesId)` 単引数に移行。
- 通常購入の SHIPPED 遷移は在庫を変更しない（注文確定時に減算済み）。予約購入（`is_preorder=true`）の SHIPPED 遷移時に `product_sku_stocks.quantity` と `inventories.quantity` を同時減算する（P5-3）。
- 出荷時に在庫不足を検出した場合は `ResponseStatusException(409)` を投げて `PENDING` のまま維持し、`operation_logs.action='shipping_blocked_insufficient_stock'` を記録（P5-4）。
- 配送先ユーザは `sales.user_id` から辿れるため `deliveries.user_id` は持たない（RR-5）。
- 配送先住所変更時は `sales.user_id` 所有の `address` のみ参照可能を Service 層バリデーションで強制（RRR-7）。
- `scheduled_date` の更新はすべて `operation_logs.action='update_scheduled_date'` で記録し、`comment` 先頭に `[manual]` / `[inbound_recalc]` / `[shipping_delay]` プレフィックス付与（RRR-5）。
- 問い合わせとの紐付けは `deliveries` 側に `inquiry_id` を持たず、`inquiries.target_type='delivery' / target_id=deliveries.id` 方式（phase18 と整合）。

## Entity

`com.example.delivery.entity.Delivery`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ15 / r5）
