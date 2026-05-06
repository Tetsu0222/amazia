# フェーズ14.5 実装計画（予約ステータス判定 API + phase15 連携整理）

## 概要
- 対象設計書: [phase14_5_preorder_status.md](../design/phase11_20/phase14_5_preorder_status.md)（初版 / 2026-05-06）
- 対象範囲: Amazia Core / Amazia Console / Amazia Market / DB 設計
- 段取り: 設計書記載の **Step C（予約ステータス判定 API）→ Step D（phase15 r5 要請整理）** の2段階構成
- 作成日: 2026-05-06
- 親フェーズ: [phase14_implementation_plan.md](phase14_implementation_plan.md)（phase14 r4 完了済み）

---

## 0. 大方針

| 項目 | 方針 |
|------|------|
| 段取り | Step C-1 → C-2 → C-3 → C-4 → Step D の順序を厳守。Step を跨いだ部分実装は禁止 |
| 規模感 | Product Entity 4 カラム追加 + Service 1 本 + Controller 1 本 + Console UI 改修 + Market 表示分岐 |
| TDD | Step C-2 の `PreorderStatusService` 単体テストでステータス 6 種の境界値を網羅 |
| コーディング規約 | `docs/coding_guidelines.md` 厳守（Service にロジック寄せ・config 駆動・1ファイル1ユースケース） |
| 環境変数 | 追加時は **必ず** `docker-compose.yml` + `phpunit.xml`（Console）+ `application-test.properties`（Core）をセット更新 |
| テスト値 | ハードコードせず `config()` / `@Value` 経由で取得 |
| メモリ事項 | Core 側 Heap 制限（`-Xmx384m`）を意識し、SUM 集計クエリに `product_id` 絞り込みを必ず付ける |
| 同時実行制御 | 予約ステータス判定は読み取り専用のため楽観ロック対象外 |

### 設計書 §2-5 公開日 NULL の取り扱い（着手時方針確定）

**phase14 既存挙動（NULL = 常時公開）に揃える。**
- 既存 `Product#isPublished()` が `publishStart/publishEnd` の NULL を「制限なし」と扱っており、データ移行コストを避けるため整合性を優先
- `PreorderStatusService` の判定優先順位 1（NOT_PUBLIC）は **`publish_start IS NOT NULL AND publish_start > today` のときのみ発動**
- `publish_start IS NULL` の場合は次の優先順位（PRE_ORDER_NOT_STARTED 以降）に進む

### 設計書 §2-3 既存データへの配慮

| 既存 product 行 | 4 カラムの値 | 判定結果 |
|-----------------|-------------|---------|
| 公開期間内 + 在庫あり | `release_date=NULL` / `accept_backorder=FALSE` | `ON_SALE` |
| 公開期間内 + 在庫なし | `release_date=NULL` / `accept_backorder=FALSE` | `SOLD_OUT` |

phase14 r4 完了時点の挙動と整合する。

---

## 1. Step C-1：schema.sql / Product Entity / test-data 補完 ✅ 完了（2026-05-06）

### 1-1. 背景
phase14 r4 設計書は予約機能のための 4 カラムを「Product Entity に存在する」前提で記述していたが、実装上は未追加。本ステップで追加する。

### 1-2. schema.sql 追記（本番 MySQL 向け）

`amazia-core/src/main/resources/schema.sql` 末尾に「フェーズ14.5: 予約ステータス判定」セクションを追加する。

```sql
-- ============================================================================
-- フェーズ14.5: 予約ステータス判定（設計書 phase14_5_preorder_status.md §2-3）
-- ============================================================================
-- 既存 products テーブルに 4 カラム追加。重複実行は continue-on-error で許容。
ALTER TABLE products ADD COLUMN release_date         DATE    NULL;
ALTER TABLE products ADD COLUMN preorder_start_date  DATE    NULL;
ALTER TABLE products ADD COLUMN accept_preorder      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN accept_backorder     BOOLEAN NOT NULL DEFAULT FALSE;
```

| カラム | 型 | NULL | 既定 | 用途 |
|--------|-----|-----|-----|------|
| `release_date` | DATE | YES | NULL | 発売日。NULL のときは「公開即発売 = `ON_SALE` 起点」 |
| `preorder_start_date` | DATE | YES | NULL | 予約開始日。NULL のときは「公開と同時に予約可」 |
| `accept_preorder` | BOOLEAN | NO | FALSE | 予約購入を受け付けるか |
| `accept_backorder` | BOOLEAN | NO | FALSE | 在庫切れ時に予約継続を受け付けるか |

### 1-3. Product Entity 拡張

`amazia-core/src/main/java/com/example/product/entity/Product.java` に 4 フィールドを追加。

- `releaseDate: LocalDate`
- `preorderStartDate: LocalDate`
- `acceptPreorder: boolean`（カラム名 `accept_preorder` に Hibernate naming で写像）
- `acceptBackorder: boolean`

各フィールドに getter / setter を追加。BOOLEAN フィールドは `@Column(nullable=false)` を付与し、Java フィールド初期値 `false` で揃える。

### 1-4. テスト用 test-data.sql の補完

`amazia-core/src/test/resources/test-data.sql` には現状 `INSERT INTO products` がない。テストは ddl-auto=create-drop で JPA が Entity からスキーマ生成するため、Entity に 4 カラムが入っていれば追加 INSERT は不要（既存テストデータ補完作業はテストで products を直接 INSERT しているケースが見つかれば対応）。

設計書 §2-4 C-1「test-data.sql 既存行への CURRENT/NULL 補完」については、現状 products INSERT 行がないため対応不要。**着手時に再確認**。

### 1-5. DB 設計書（CLAUDE.md ルール準拠）

`docs/database_design/TBL_products.md` のカラム定義表に 4 カラム（#12〜#15）を追加し、変更履歴に「フェーズ14.5: 予約機能用カラム 4 種を追加」を追記する。

### 1-6. Step C-1 完了条件（2026-05-06 完了）

- [x] `amazia-core/src/main/resources/schema.sql` に 4 つの ALTER TABLE 追記
- [x] `Product.java` に 4 フィールド + getter/setter 追加（`LocalDate releaseDate` / `LocalDate preorderStartDate` / `boolean acceptPreorder` / `boolean acceptBackorder`）
- [x] `TBL_products.md` 更新（カラム表 #12〜#15 / 変更履歴 / マイグレーションファイル節）
- [x] `ER_diagram.md` の `products` テーブルに 4 カラム反映
- [x] `mvn test`（amazia-core）234/234 グリーン（既存テストへの影響なし）
- [ ] Core 起動時に schema.sql の冪等 SQL がエラーなく流れる（**未確認**：本番 MySQL での `ALTER TABLE ... ADD COLUMN` 重複時の挙動は次回 Core 再起動時に確認）

---

## 2. Step C-2：PreorderStatusService + JUnit

### 2-1. 配置とシグネチャ

```
com.example.product.service.PreorderStatusService
    public PreorderStatus judge(Long productId)
```

`PreorderStatus` は新規 Enum（`com.example.product.entity.PreorderStatus`）として定義：
- `NOT_PUBLIC` / `PRE_ORDER_NOT_STARTED` / `PRE_ORDER` / `ON_SALE` / `BACK_ORDER` / `SOLD_OUT`

### 2-2. 判定ロジック（設計書 §2-2 を継承）

「今日」は **JST 0:00 基準**（`LocalDate.now(ZoneId.of("Asia/Tokyo"))`）。

優先順位：
1. `publish_start IS NOT NULL AND today < publish_start` → `NOT_PUBLIC`
2. `preorder_start_date IS NOT NULL AND today < preorder_start_date` → `PRE_ORDER_NOT_STARTED`
3. `release_date IS NOT NULL AND today < release_date` → `PRE_ORDER`
4. それ以外（today >= release_date or release_date IS NULL）：
   - 在庫 SUM > 0 → `ON_SALE`
   - 在庫 SUM = 0 AND `accept_backorder=true` → `BACK_ORDER`
   - 在庫 SUM = 0 AND `accept_backorder=false` → `SOLD_OUT`

### 2-3. 在庫集計 SQL

`ProductSkuStockRepository` に SUM クエリを追加（既存があれば再利用）：

```sql
SELECT COALESCE(SUM(s.quantity), 0)
FROM product_sku_stocks s
WHERE s.sku_id IN (SELECT id FROM product_skus WHERE product_id = :productId)
```

メモリ制約のため `product_id` 絞り込みは必須。SKU 全件 SUM は禁止。

### 2-4. JUnit 観点

正常系：6 ステータスそれぞれの代表ケース。
境界値：
- 公開日 = 今日 0:00 ちょうど → 公開済み判定 → 4 のルートに進む（`NOT_PUBLIC` ではない）
- 予約開始日 = 今日 0:00 ちょうど → `PRE_ORDER`（4 に進む手前で 2 が `today < preorder_start_date` で false）
- 発売日 = 今日 0:00 ちょうど → `ON_SALE` 系（在庫により分岐）
- `publish_start IS NULL` → 1 をスキップ（常時公開）
- `release_date IS NULL` → 3 をスキップ、即 4 へ

異常系：
- `productId` 不在 → `ProductNotFoundException`（既存例外があれば再利用）

---

## 3. Step C-3：Controller + Console 商品登録 UI 改修

### 3-1. Core Controller

```
com.example.product.controller.GetPreorderStatusController
    GET /api/products/{id}/preorder-status
    Response: { "productId": 1, "status": "PRE_ORDER", "releaseDate": "2026-06-01", ... }
```

`Core_API.md` に追記。

### 3-2. Console 商品登録 UI（既存 ProductForm.vue にセクション追加）

既存 `amazia-console/resources/vue/src/features/products/pages/ProductForm.vue` に「予約・発売」セクションを追加。

入力欄：
- `release_date`（DatePicker）
- `preorder_start_date`（DatePicker）
- `accept_preorder`（Checkbox）
- `accept_backorder`（Checkbox）

Console Laravel API（CreateProduct / UpdateProduct）が 4 カラムを受け取って Core に中継できるよう拡張。

`Console_API.md` の対応エンドポイントに 4 カラムを追記。

### 3-3. Console PHPUnit 観点
- 4 カラムが正しく Core に転送される
- バリデーション：`preorder_start_date <= release_date`（任意制約、設計書要再確認）

---

## 4. Step C-4：Market 表示分岐 + Vitest

### 4-1. Market 表示分岐対象

- `amazia-market/src/features/products/pages/ProductDetail.jsx`
- `amazia-market/src/features/products/pages/ProductList.jsx`（または `ProductMarketList`）

### 4-2. ステータス別 UI

| Status | ラベル | 購入ボタン | 補足表示 |
|--------|-------|----------|---------|
| `NOT_PUBLIC` | （非表示） | - | 一覧から除外 |
| `PRE_ORDER_NOT_STARTED` | 予約開始前 | 非活性 | 予約開始日表示 |
| `PRE_ORDER` | 予約受付中 | 「予約する」 | 発売日表示 |
| `ON_SALE` | 通常販売 | 「購入する」 | 在庫数 |
| `BACK_ORDER` | 再入荷予約受付中 | 「予約する」 | 在庫切れ表示 |
| `SOLD_OUT` | 完売 | 非表示 | - |

### 4-3. Vitest 観点
- 6 ステータスそれぞれの UI スナップショット / 文言検証
- 未ログイン状態で予約ボタン押下 → /login にリダイレクト（既存挙動踏襲）

---

## 5. Step D：phase15 r5 への要請整理

### 5-1. 成果物

`docs/design/phase11_20/phase15_delivery_management.md` を r5 として改訂、または `phase15_r5_requirements.md` を新規作成。

phase14_5_preorder_status.md §3-1 の表 6 項目を要件リストとして取り込む：

| 項目 | 内容 |
|------|------|
| `shipping_methods` マスタ作成 | INSERT IGNORE で home_delivery / konbini_pickup / dropoff |
| `DeliveryCreationService.createForSales(salesId)` | OrderConfirmationService から呼び出し |
| 出荷時の予約購入 SKU 在庫減算 | `is_preorder=true` のときのみ `PENDING→SHIPPED` 遷移で減算 |
| 出荷時在庫不足の挙動 | 例外＋PENDING 維持 |
| `RegisterInboundService` 改名・統合 | 既存 `ReceiveProductSkuStockService` を流用 |
| 配送ステータス CANCELED / DELIVERY_FAILED / RESCHEDULED | phase15 r5 ではスコープ外確認のみ |

### 5-2. Step D 完了条件
- [ ] phase15 r5 設計書（または要件リスト）に 6 項目すべて取り込み済み
- [ ] phase14_5_preorder_status.md §3 を「✅ 反映済み」として閉じる

---

## 6. 横断タスク（全 Step で意識）

| 項目 | 内容 |
|------|------|
| TDD | Step C-2 はテスト先行。境界値 6 種を必ず書く |
| 環境変数 | 追加時は `docker-compose.yml` / `phpunit.xml` / `application-test.properties` セット更新 |
| テスト値 | `config()` / `@Value` 経由（テスト内ハードコード禁止） |
| 操作履歴 | 商品登録 / 更新時に `operation_logs` への 4 カラム変更記録（既存 phase14 仕組みを流用） |
| 文書 | 完了時に phase14_5_preorder_status.md の改訂履歴に「実装完了 (YYYY-MM-DD)」追記、TBL_products.md / Core_API.md / Console_API.md / Market_API.md を同フェーズ内で更新 |
| トラブル対応 | 不具合発生時は `docs/troubles/NNN_<概要>.md` を新規作成し再発防止策を記録（CLAUDE.md ルール） |

---

## 7. 完了条件（フェーズ全体）

- [ ] Step C-1 〜 C-4 / Step D すべて完了
- [ ] Core / Console / Market 全層テストグリーン
- [ ] Console 商品登録 UI から 4 カラムを登録でき、Market で 6 種類のステータスが表示分岐される
- [ ] phase15 r5 設計書に §3-1 の要件が取り込まれている
- [ ] `docs/design/phase11_20/phase14_5_preorder_status.md` のステータスを `🔲 未着手` → `✅ 完了（YYYY-MM-DD）` に更新

---

## 8. 参考リンク

- 設計書: [phase14_5_preorder_status.md](../design/phase11_20/phase14_5_preorder_status.md)
- 親フェーズ: [phase14_implementation_plan.md](phase14_implementation_plan.md)
- 関連設計書: [phase15_delivery_management.md](../design/phase11_20/phase15_delivery_management.md)
- コーディング規約: [coding_guidelines.md](../coding_guidelines.md)
- AIコンテキスト（テスト観点）: [test_insights.md](../ai_context/test_insights.md)
- AIコンテキスト（実装・運用パターン）: [operational_insights.md](../ai_context/operational_insights.md)
- プロジェクトAIコンテキスト: [CLAUDE.md](../../CLAUDE.md)
