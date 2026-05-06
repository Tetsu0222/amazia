# フェーズ14：購入機能（改訂版 r1）

## ステータス
🔲 未着手

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 初版日不明（git 履歴未取得） | 初稿（フェーズ14購入機能の基本設計） |
| 改訂版 | 改訂日不明 | 分析画面連携方針・予約ステータスロジックの一元化・配送ステータスのマスタ化・返品ワークフロー詳細化など。 |
| r1 | 2026-05-06 | phase15 r3／r4 から積み上がった要請（P14-1 〜 P14-12）と phase14 単独観点（P14-13 〜 P14-17）を反映。在庫モデル完全移行と並行運用期のフック・注文確定フロー確定・`sales.shipping_method_id` 追加・`shipping_statuses` 拡張・`inventory_movements` 新設・住所オーナー検証・悲観ロック方針・改訂履歴と表記揺れの整形など。 |

## 範囲
- Amazia Console
- Amazia Market
- Amazia Core
- DB設計（新規テーブル・マスタ化含む）
- **在庫モデルの完全移行（`products.stock` 廃止 → `inventories` 一本化）**（P14-1 対応：r1 で本フェーズに取り込み）
- **注文確定フローの確定**（P14-2 対応：r1 で擬似コードレベルまで確定）

---

# 全体方針（レビュー反映）

## 🔹 分析画面との連携方針（最低限の方向性を定義）
将来の分析画面を見据え、以下の分析軸を想定しデータ保持方針を決定する：

- **RFM分析**（Recency / Frequency / Monetary）
- **商品別売上分析**（カテゴリ別・発売日別・予約比率など）
- **ユーザ行動分析**（購入導線、予約→購入転換率など）
- **配送方法別売上分析**（r1 で `sales.shipping_method_id` 追加に伴い／P14-3 対応）

→ これにより、売上テーブルは「過度に正規化しない柔軟構造」を維持しつつ、**マスタテーブル化（決済方法・配送ステータス・配送方法）**を行う。

---

# 予約ステータスロジックの一元化（Core に集約）

Console / Market で重複していた予約判定ロジックを **Amazia Core に統合**し、以下のステータスを返す API を提供する。

### ■ 予約ステータス一覧（Enum）
| ステータス | 説明 |
|-----------|------|
| NOT_PUBLIC | 公開前（公開日未到達） |
| PRE_ORDER_NOT_STARTED | 公開済・予約開始前 |
| PRE_ORDER | 予約受付中（公開日 < 発売日） |
| ON_SALE | 発売済（公開日 >= 発売日） |
| BACK_ORDER | 発売済・在庫切れだが予約受付可能 |
| SOLD_OUT | 完売（予約不可） |

### ■ 予約ステータスの在庫参照先（P14-1 対応：r1 で確定）
完全移行後の `BACK_ORDER` / `SOLD_OUT` 判定は **`inventories.quantity`** を参照する（販売側の在庫減算と同じ正本を見る）。並行運用期は `products.stock` 参照を維持し、完全移行スイッチが入った段階で読み取り側を `inventories` 参照に切り替える（後述「在庫モデル完全移行マイグレーション仕様」参照）。

### ■ タイムゾーン方針（P14-17 対応：r1 で明記）
「今日」「公開日」「発売日」の判定はすべて **JST 0:00 基準**で行う。サーバ／DB のタイムゾーン設定（`Asia/Tokyo`）と整合させ、テストでも JST 基準で境界値を組む（後述テストケース参照）。

→ Market / Console はこのステータスを参照して UI 表示を統一。

---

# 配送ステータスのマスタ化（Enum）

`shipping_statuses` マスタは r1 で以下に拡張する（P14-4 対応）。機能としての「キャンセル UI」「配達失敗フロー」「再配達手配 UI」は別フェーズに切り出すが、**マスタ拡張だけは r1 で先行実施**して将来の拡張余地を残す。

| ステータス | 説明 | 追加版 |
|-----------|------|-------|
| PENDING | 配送準備中 | 既存 |
| SHIPPED | 配送済 | 既存 |
| DELIVERED | 配送完了 | 既存 |
| RETURN_REQUESTED | 返品申請中 | 既存 |
| RETURNED | 返品完了 | 既存 |
| CANCELED | 発送前キャンセル | **r1 追加** |
| DELIVERY_FAILED | 配達失敗・持ち戻り | **r1 追加** |
| RESCHEDULED | 再配達手配中 | **r1 追加** |

phase15 r4 のステータス遷移表は本マスタ拡張後も「CANCELED / DELIVERY_FAILED / RESCHEDULED は phase15 ではスコープ外」で運用される（マスタには存在するが phase15 の Service 遷移ガードでは未対応）。後続フェーズで遷移ルールを追加する。

---

# Amazia Console（改訂）

## 売上管理画面

### ■ 集計要件（拡張）
- 年/月/日単位
- **商品別集計**
- **ユーザ別集計**
- **決済方法別集計**
- **配送方法別集計**（r1 で追加／`sales.shipping_method_id` 追加に伴う／P14-3）
- **予約売上 / 通常売上の区別**

→ 分析画面との役割分担
- Console：日常運用向けの軽量集計
- 分析画面：高度な分析（BIツール連携前提）

### ■ 表示項目
- ユーザ名
- 購入商品
- 金額
- 配送日
- 売上日
- 配送ステータス（Enum）
- 決済方法（マスタ参照）
- **配送方法（マスタ参照／r1 追加）**
- 予約 or 通常購入区分

---

## 配送先変更（履歴管理を明確化）
- 管理者が配送先を変更可能
- **変更履歴は operation_logs に記録**
  - action: "update_shipping_address"
  - target_type: "sales"
  - target_id: sales.id
  - comment: 変更理由・旧住所・新住所を記録

---

## 返品・返金ワークフロー（詳細追加）

### ■ ステータス
| ステータス | 説明 |
|-----------|------|
| REQUESTED | ユーザが返品申請 |
| APPROVED | 管理者承認 |
| REJECTED | 管理者却下 |
| REFUNDED | 返金完了 |

### ■ 管理項目
- 返品理由（ユーザ入力。フリーテキスト）
- **返品理由コード（`return_reason_code`／将来課題：P14-14）** — マスタ化は将来検討。技術検討事項参照
- 承認者（管理者ID）
- 承認日時
- 返金方法（決済方法に依存）

→ 返品情報は **sales_return テーブル（新規）** を追加して管理。

---

## 操作履歴画面（5W1Hの補強）
- いつ（created_at）
- 誰が（user_id）
- 何を（action）
- どこで（**screen_name / api_name を追加**）
- なぜ（comment）
- どのように（target_type / target_id）

`screen_name` / `api_name` の命名規約は **`docs/ai_context/operation_logs_naming.md` に従う**（P14-10 対応：phase15 r4 で提案された共通規約を参照）。

---

# Amazia Market（改訂）

## カート機能（セッション or DB 切替可能設計）
初期はセッション管理だが、将来の拡張を見据え以下を定義：

- **ログインユーザは DB カートを優先**
- 未ログインユーザはセッションカート
- ログイン時にセッションカート → DB カートへマージ

→ cart_items テーブルを将来追加できる構造にする。

---

## 模擬決済（将来の本番決済を見据えた抽象化）

### ■ 決済インターフェース
```
PaymentResult process(PaymentRequest request)
```

### ■ バリデーション例
- カード番号形式チェック
- d払いID形式チェック
- 着払いは住所必須

### ■ エラーケース
- バリデーションエラー
- 決済情報不足
- 決済APIモックの異常応答

→ 模擬決済でも **payment_id（UUID）を発行** し sales に保存。

### ■ payment_id の採番方式（P14-13 対応：r1 で明記）
- **採番ロジックの所在**：Amazia Core の `PaymentService`（模擬決済時のみ）。本番決済 API 接続後は外部発行値をそのまま受領する。
- **形式**：模擬決済期は **UUID v7**（時系列ソート性のため）。本番決済 API 接続後は外部システム由来（最大長想定で `VARCHAR(100)` を確保済み）。
- **重複チェック**：模擬決済 / 本番決済いずれも `sales.payment_id` を `UNIQUE` 制約で担保。

---

## 配送情報（仕様補強）
- 登録住所 or 新規住所
- コンビニ受け取り：**店舗検索API（将来）を想定**
- 置き配：**商品属性（allow_dropoff）と連携**
- **配送方法（`shipping_method_id`）を注文確定リクエストに含める**（r1 で確定／phase15 r4 RRRR-3 対応）。受領した値は `sales.shipping_method_id` および `deliveries.shipping_method_id` に同期保存。

---

## 購入履歴
- 購入日時
- 商品名
- 金額
- 配送予定日
- 配送ステータス
- **配送方法**（r1 で表示項目に追加）
- 予約 or 通常購入区分

---

## 予約機能（Core のステータスを利用）
Market 側は Core の予約ステータスを参照し、以下の UI を表示：

| ステータス | Market 表示 |
|-----------|-------------|
| NOT_PUBLIC | 非表示 |
| PRE_ORDER_NOT_STARTED | 「予約開始前」 |
| PRE_ORDER | 「予約受付中」＋発売日表示 |
| ON_SALE | 通常販売 |
| BACK_ORDER | 「再入荷予約受付中」 |
| SOLD_OUT | 「完売」 |

---

# 注文確定フロー（P14-2 対応：r1 で確定）

注文確定時の処理は以下のフローで Service 層に集約する。バリデーション・在庫減算・`sales` INSERT・`deliveries` 生成は**同一トランザクション**で完結させる。

```
OrderConfirmationService.confirm(order_request):

  1. validateOrder(order_request)
     - is_preorder == false かつ getStock(product_id) <= 0 なら拒否
     - sales.shipping_address_id が order_request.user_id 所有の address のみ参照可能
       であることを検証（P14-7／phase15 RRRR-7 対応）
     - sales.shipping_method_id が shipping_methods マスタに存在することを検証

  2. begin transaction

  3. 在庫減算（同時実行制御：SELECT ... FOR UPDATE で対象行をロック）
     - 並行運用期：products.stock -= order_quantity
                  + InventorySyncService.applyDelta(product_id, 1, -order_quantity)
     - 完全移行後：inventories.quantity -= order_quantity（直接）
     - is_preorder == true は減算しない（在庫切れでも注文成立）

  4. INSERT sales
     （shipping_address_id, shipping_method_id, payment_method_id, payment_id,
       is_preorder, ...）

  5. DeliveryCreationService.createForSales(sales.id)
     ※ 完全移行後の長期シグネチャ。並行運用期の過渡期は
       DeliveryCreationService.createForSales(sales.id, shipping_method_id)
       を使用（phase15 r4 RRRR-3 と整合）。
     → INSERT deliveries (
          sales_id,
          shipping_address_id = sales.shipping_address_id,
          shipping_method_id  = sales.shipping_method_id,
          shipping_status_id  = PENDING,
          scheduled_date      = DeliveryScheduleService.calculate(...)
        )

  6. commit
```

### 在庫の同時実行制御方針（P14-8 対応：r1 で明記）
- 在庫減算（`products.stock` および `inventories.quantity`）は **`SELECT ... FOR UPDATE`（悲観ロック）**で対象行を取得してから減算する。
- 並行運用期は両テーブルの行を同一トランザクション内でロック取得する。
- 完全移行後は `inventories.quantity` のみ。
- DB 制約 `CHECK (inventories.quantity >= 0)` で負数化を防止（phase15 r4 RRR-8 と整合）。

### 過渡期と完全移行後のシグネチャ（P14-3 / phase15 RRRR-3 対応）

| 期間 | `DeliveryCreationService.createForSales` のシグネチャ | `shipping_method_id` の出所 |
|------|---------------------------------------------------|-------------------------|
| 並行運用 + `sales.shipping_method_id` 追加前（過渡期） | `createForSales(sales_id, shipping_method_id)` | API リクエスト由来（`sales` には保存されていない） |
| 並行運用 + `sales.shipping_method_id` 追加後 | `createForSales(sales_id)` | `sales.shipping_method_id` |
| 完全移行後 | `createForSales(sales_id)` | `sales.shipping_method_id` |

phase14 r1 では **`sales.shipping_method_id` カラム追加と過渡期解消（長期シグネチャへの移行）を本フェーズ内で完結**させる。これにより phase15 実装着手時には長期シグネチャで開発できる。

---

# 在庫モデル完全移行（P14-1 対応：r1 で本フェーズに取り込み）

## 設計判断
- phase15 r3／r4 で `inventories` の並行運用と販売側フック（`InventorySyncService.applyDelta`）が確立された前提で、**phase14 r1 で `products.stock` を廃止し `inventories` に一本化**する。
- 並行運用は phase15 実装期間中の暫定状態であり、本フェーズでクローズする。

## 完全移行の段取り（マイグレーション仕様／P14-9 対応）

```
Step 1: 並行運用整合性チェック
  - SUM(inventories.quantity GROUP BY product_id) と products.stock を全件比較
  - 乖離があれば、販売・返品履歴から再構築（後述「乖離検出時の再構築手順」）
  - チェックが通るまで Step 2 以降に進まない

Step 2: 読み取り側を inventories 参照に切替
  - 予約ステータス判定（BACK_ORDER / SOLD_OUT）の在庫読み取りを inventories へ
  - 配送予定日計算（DeliveryScheduleService.calculate）の在庫読み取りも inventories へ
  - Market の在庫表示も inventories 参照
  - この時点では products.stock は残しているが書き込み専用（読み取りなし）

Step 3: 書き込み側のフック削除
  - 販売 Service の products.stock 減算を削除
  - 返品復元 Service の products.stock 加算を削除
  - 入荷 Service（phase15）の products.stock 加算を削除
  - InventorySyncService と呼び出し箇所を削除
  - この時点で products.stock への更新が消える

Step 4: products.stock カラム DROP
  - ALTER TABLE products DROP COLUMN stock
  - 完全移行完了
```

## 乖離検出時の再構築手順
並行運用期に何らかの理由で `products.stock != SUM(inventories.quantity)` が発生していた場合、`inventories` 側を販売・入荷・返品履歴から再構築する：

```sql
-- 商品ごとの「正しい在庫」を、入荷加算 - 販売減算 + 返品加算で再計算
UPDATE inventories i
SET quantity = (
  SELECT COALESCE(SUM(inb.quantity), 0)
  FROM inbounds inb
  WHERE inb.product_id = i.product_id AND inb.warehouse_id = i.warehouse_id
) - (
  SELECT COALESCE(SUM(s.quantity), 0)
  FROM sales s
  WHERE s.product_id = i.product_id AND s.is_preorder = false
) + (
  SELECT COALESCE(SUM(sr.quantity), 0)
  FROM sales_return sr JOIN sales s ON sr.sales_id = s.id
  WHERE s.product_id = i.product_id AND sr.status = 'REFUNDED'
);
```

> 注：上記 SQL は単一倉庫前提（`warehouse_id = 1`）。倉庫別在庫が導入された段階では倉庫別に計算する必要があるが、本フェーズの完全移行時点では単一倉庫運用のため簡略化。

## 完全移行後の構造
- 販売・予約ステータス判定・返品復元・入荷のすべてが `inventories` を読み書き正本とする。
- `inventories` の同時実行制御は `SELECT ... FOR UPDATE`（悲観ロック）。
- 倉庫マスタ（phase15 r3 で導入）はダミー1行のまま。複数倉庫対応は別フェーズで。

---

# inventory_movements テーブル新設（P14-6 対応：r1 で同時設計）

P14-1 の完全移行と密接に関連するため、phase14 r1 で同時設計する。完全移行後に履歴を再構築するのは現実的ではないため、**完全移行と同時に履歴記録を開始**する。

## inventory_movements テーブル（新規：在庫増減ログの一元化）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | NOT NULL | 倉庫ID（`warehouses.id` への FK／DEFAULT 1） |
| movement_type | VARCHAR(50) | NOT NULL | 増減種別（`inbound` / `sale` / `cancel` / `return` / `adjustment`） |
| quantity | INT | NOT NULL | 増減量（正数のみ。減算は `movement_type` で表現／**`CHECK (quantity > 0)`**） |
| direction | VARCHAR(10) | NOT NULL | `in` / `out` の方向（増加 or 減少） |
| reference_type | VARCHAR(50) | NULL | 参照元種別（`inbounds` / `sales` / `sales_return` / `manual_adjustment`） |
| reference_id | BIGINT | NULL | 参照元レコードID |
| comment | TEXT | NULL | 棚卸補正等の自由記述 |
| created_by_user_id | BIGINT | NULL | 操作ユーザ（自動処理は NULL） |
| created_at | DATETIME | NOT NULL | 記録日時 |

### 命名注意（P14-6 / phase15 RRRR-9 対応）
- 他 ERP（Odoo / ERPNext 等）にも `inventory_movements` 同名テーブルが存在するため、参照例を引きやすい反面、独自仕様が混ざると参照先と紛らわしい。
- 本テーブルの `movement_type` enum 値（`inbound` / `sale` / `cancel` / `return` / `adjustment`）は **Amazia 独自定義**であり、他 ERP の同名テーブルとは互換性を取らない方針を本書で明記。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_inventory_movements_product_id` | 商品別の在庫履歴取得 |
| `idx_inventory_movements_created_at` | 期間別集計 |
| `idx_inventory_movements_reference` | (`reference_type`, `reference_id`) 複合インデックス。元レコードからの逆引き |

### 記録タイミング
- 入荷登録：`movement_type='inbound', direction='in', reference_type='inbounds'`
- 販売（注文確定）：`movement_type='sale', direction='out', reference_type='sales'`
- キャンセル復元：`movement_type='cancel', direction='in', reference_type='sales'`
- 返品復元：`movement_type='return', direction='in', reference_type='sales_return'`
- 棚卸補正（将来）：`movement_type='adjustment', direction='in/out', reference_type='manual_adjustment'`

`inventories.quantity` の更新と `inventory_movements` への INSERT は同一トランザクションで実行する。

---

# DB設計（改訂）

## sales テーブル（r1 改訂版）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| user_id | BIGINT | NOT NULL | 購入ユーザID |
| product_id | BIGINT | NOT NULL | 商品ID |
| quantity | INT | NOT NULL | 購入数量（**r1 で追加**：`inventory_movements` 再構築 SQL の前提） |
| amount | INT | NOT NULL | 金額 |
| payment_method_id | BIGINT | NOT NULL | 決済方法マスタID |
| **shipping_method_id** | BIGINT | NOT NULL | **配送方法マスタID（r1 で新規追加／P14-3 対応）** |
| shipping_address_id | BIGINT | NOT NULL | 住所マスタID |
| shipping_date | DATE | NULL | 配送日 |
| sales_date | DATE | NOT NULL | 売上日 |
| shipping_status_id | BIGINT | NOT NULL | 配送ステータスマスタID |
| payment_id | VARCHAR(100) | NOT NULL | 決済ID（**`UNIQUE` 制約**／P14-13） |
| is_preorder | BOOLEAN | NOT NULL | 予約購入か |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `shipping_method_id` は `shipping_methods.id`（phase15 で定義）への FK。
- `shipping_address_id` は `address.id` への FK。Service 層で「`user_id` 所有の `address` のみ参照可能」を強制（P14-7／phase15 RRRR-7 対応）。
- `payment_id` は `UNIQUE` 制約（模擬決済の重複採番・本番決済の二重連携を防止／P14-13）。

### インデックス方針（既存維持 + r1 追加）
| インデックス | 用途 |
|-------------|------|
| `idx_sales_sales_date` | 売上日別集計 |
| `idx_sales_product_id` | 商品別集計 |
| `idx_sales_user_id` | ユーザ別集計 |
| `idx_sales_payment_method_id` | 決済方法別集計 |
| `idx_sales_shipping_method_id` | **配送方法別集計（r1 で追加）** |

---

## address テーブル（既存維持）
住所の再利用・履歴管理のため分離。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | **所有ユーザ（P14-7：「住所の所有者」を表す。`sales.shipping_address_id` から参照する場合は `sales.user_id == address.user_id` を Service 層で強制）** |
| postal_code | VARCHAR(20) | 郵便番号 |
| prefecture | VARCHAR(50) | 都道府県 |
| city | VARCHAR(100) | 市区町村 |
| address_line | VARCHAR(255) | 住所 |
| building | VARCHAR(255) | 建物名 |
| is_active | BOOLEAN | 現在利用中の住所か（false なら過去住所） |
| created_at | DATETIME | 作成日時 |

### 住所編集時の運用（P14-15 対応：r1 で明文化）
- ユーザーが住所を「編集」した場合、**旧住所は `is_active=false` に UPDATE して残し、新住所を `is_active=true` で INSERT する**（UPDATE で上書きしない）。
- これにより `sales.shipping_address_id` は過去住所を参照し続け、購入時点の配送先が壊れない。
- ユーザの住所選択 UI では `is_active=true` のレコードのみを表示。

---

## payment_methods（マスタ）
| id | name |
|----|------|
| 1 | credit_card |
| 2 | d_payment |
| 3 | cash_on_delivery |

---

## shipping_methods（マスタ／参照：phase15 r4 で定義）
| id | name |
|----|------|
| 1 | home_delivery |
| 2 | konbini_pickup |
| 3 | dropoff |

phase14 r1 では `sales.shipping_method_id` が本マスタを参照する FK となる。マスタ自体は phase15 で定義する。

---

## shipping_statuses（マスタ／r1 で拡張）
前述の Enum を格納（CANCELED / DELIVERY_FAILED / RESCHEDULED を r1 で追加／P14-4 対応）。

---

## operation_logs（改訂）
| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | 操作ユーザ |
| action | VARCHAR(100) | 操作内容 |
| target_type | VARCHAR(50) | 対象種別 |
| target_id | BIGINT | 対象ID |
| screen_name | VARCHAR(100) | 画面名（命名規約は `docs/ai_context/operation_logs_naming.md`／P14-10） |
| api_name | VARCHAR(100) | API名（同上） |
| comment | TEXT | 任意コメント（プレフィックス `[manual]` / `[inbound_recalc]` / `[shipping_delay]` 等を活用／phase15 r4 RRR-5 と整合） |
| created_at | DATETIME | 操作日時 |

### `reason_code` カラムの先行追加について（P14-5 対応）
phase15 r4 で `update_scheduled_date` の comment プレフィックス方式が採用されており、現状の集計需要では十分。**r1 では `reason_code` カラムを先行追加せず、将来課題として残す**。集計需要が高まった段階で別途追加マイグレーションを行う方針。

### `comment` カラムの検索性（P14-16 対応：技術検討事項）
`comment TEXT` でフリーテキストを格納する。本格運用時に検索性が問題になった場合、フルテキスト検索（MySQL の `FULLTEXT INDEX` 等）またはログ専用基盤（Elasticsearch / OpenSearch）への流し込みを検討する。本フェーズでは技術検討事項としてのみ言及し、実装はしない。

---

## sales_return テーブル（既存維持 + r1 追記）
返品ワークフロー管理。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| sales_id | BIGINT | 対象売上 |
| status | VARCHAR(50) | REQUESTED / APPROVED / REJECTED / REFUNDED |
| reason | TEXT | 返品理由（フリーテキスト） |
| approver_id | BIGINT | 承認者 |
| approved_at | DATETIME | 承認日時 |
| quantity | INT | **返品数量（r1 で追加：`inventory_movements` 再構築 SQL の前提）** |
| notified_user | BOOLEAN | ユーザ通知済みか |
| notified_admin | BOOLEAN | 管理者通知済みか |
| created_at | DATETIME | 作成日時 |

### 返品理由分類（P14-14 対応：将来課題）
- 現状は `reason TEXT` フリーテキストのみ。
- 将来、返品分析（不良品 / イメージ違い / サイズ違い等）を扱う場合は `return_reason_codes` マスタと `sales_return.return_reason_code` の追加を検討。
- 本フェーズでは技術検討事項としてのみ言及。

---

# 技術検討事項（補強）

- 予約ステータスは **リアルタイム判定**（バッチ不要）。判定の基準時刻は **JST 0:00**（P14-17）。
- 売上集計のインデックス
  - sales_date
  - product_id
  - user_id
  - payment_method_id
  - **shipping_method_id（r1 で追加）**
- 決済インターフェースは将来の本番決済APIに差し替え可能な構造にする
- `payment_id` は模擬決済期は UUID v7（時系列ソート性）。本番決済 API 接続後は外部発行値（P14-13）
- 配送ステータスはマスタ化し、外部配送API連携も想定。CANCELED / DELIVERY_FAILED / RESCHEDULED の **マスタは r1 で先行追加**（機能としての対応は別フェーズ／P14-4）
- 在庫の同時実行制御は **`SELECT ... FOR UPDATE`（悲観ロック）** に統一（P14-8 / phase15 RRR-8）
- **将来課題：** `return_reason_codes` マスタ（返品分析用／P14-14）
- **将来課題：** `operation_logs.comment` のフルテキスト検索基盤（P14-16）
- **将来課題：** `operation_logs.reason_code` カラム追加（集計需要が高まった段階／P14-5）

---

# TDDテストケース（異常系追加）

## Amazia Core / JUnit

### 正常系
- 売上登録・更新・取得
- 集計処理（年/月/日 + 商品別/ユーザ別/決済方法別/**配送方法別**／r1 追加）
- 操作履歴の記録
- 予約ステータス判定（境界値：公開日＝今日、発売日＝今日。**JST 0:00 基準**／P14-17）
- 注文確定フローが同一トランザクションで完結する（在庫減算 + sales INSERT + deliveries 生成）
- `is_preorder=true` のとき在庫切れでも注文成立、在庫減算されない
- 完全移行後の `BACK_ORDER` / `SOLD_OUT` 判定が `inventories.quantity` 参照で動作する
- `payment_id` の `UNIQUE` 制約で重複採番が拒否される（P14-13）
- 住所編集時、旧住所が `is_active=false` で残り、`sales.shipping_address_id` の参照が壊れない（P14-15）

### 異常系
- 通常購入で在庫切れ（`stock <= 0`）の注文を拒否（注文確定 Service バリデーション／P14-2）
- `sales.shipping_address_id` が `sales.user_id` 所有でない `address` を指す場合に拒否（P14-7／phase15 RRR-7 と整合）
- `sales.shipping_method_id` が `shipping_methods` マスタに存在しない場合に拒否（P14-3）
- 在庫減算で `inventories.quantity < 0` になる更新を `CHECK` 制約で拒否
- 在庫減算が同時実行で競合した場合、`SELECT ... FOR UPDATE` のロック解放後に正しく直列化される（悲観ロック動作確認／P14-8）
- 不正ステータス・不正日付

### 在庫モデル完全移行の検証（P14-1 / P14-9 対応）
- 並行運用期の整合性チェック：任意時点で `products.stock(product_id) == SUM(inventories.quantity WHERE product_id=...)` が成立する（phase15 r4 と同じ不変条件）
- 完全移行マイグレーション直後、すべての商品で `inventories` の値が正しい（販売・入荷・返品履歴からの再構築結果と一致）
- 完全移行後、`products.stock` への参照が販売・予約ステータス判定・配送予定日計算のいずれにも残っていない（コード検査／統合テスト）
- `InventorySyncService` および呼び出し箇所が完全移行 Step 3 後にすべて削除されている（コード検査）

### inventory_movements の検証（P14-6 対応）
- 入荷登録時に `inventory_movements` に `inbound / in` の行が記録される
- 販売（注文確定）時に `inventory_movements` に `sale / out` の行が記録される
- 返品復元時に `inventory_movements` に `return / in` の行が記録される
- 各 `movement` の `reference_type` / `reference_id` から元レコードを正しく逆引きできる
- `inventories.quantity` の更新と `inventory_movements` INSERT が同一トランザクションでロールバックされる

## Amazia Market / PHPUnit
- カート追加・削除
- セッション → DB カートマージ
- 模擬決済成功
- 模擬決済エラー（バリデーション）
- 配送情報未入力エラー
- 注文確定リクエストに `shipping_method_id` が含まれない場合のエラー（r1 で追加／P14-3）
- 購入履歴表示（配送方法を含む）
- 予約ステータス表示（Core API 連携。境界値は JST 0:00 基準でテスト／P14-17）

### テスト値の config 経由化（phase15 r4 RRRR-8 と整合）
規約 4-1「テスト内で URL や設定値をハードコードせず `config()` / `@Value` 経由で取得する」と整合させ、本書のテストでも以下の値を `config()` / `@Value` 経由で取得する：

- `payment_methods` / `shipping_methods` / `shipping_statuses` のマスタ ID
- 並行運用のダミー倉庫 ID
- `inventory_movements` の `movement_type` enum 値
- 予約ステータス判定のタイムゾーン（`Asia/Tokyo`）

---

# 🔧 追加で検討するとさらに良くなる点（軽微・追記案／既存改訂版から維持）

## 1. 予約ステータス判定の「優先順位ルール」明文化

予約ステータスは複数の条件（公開日・予約開始日・発売日・在庫）が絡むため、**優先順位ルールを Core に明示的に定義**する。

### ■ 予約ステータス判定の優先順位（上から順に適用）
1. **公開前（公開日 > 今日）** → NOT_PUBLIC
2. **予約開始日が設定されており、今日 < 予約開始日** → PRE_ORDER_NOT_STARTED
3. **公開済 & 発売日前（公開日 ≤ 今日 < 発売日）** → PRE_ORDER
4. **発売日以降（今日 ≥ 発売日）**
   - 在庫あり → ON_SALE
   - 在庫なし → BACK_ORDER or SOLD_OUT（予約可否で分岐）
5. **在庫なし & 予約不可** → SOLD_OUT

→ この優先順位を API 仕様として Core に固定し、Console / Market はこの結果のみを利用する。在庫参照先は **完全移行後は `inventories`**（P14-1）、判定基準時刻は **JST 0:00**（P14-17）。

---

## 2. address テーブルの履歴管理方針（P14-15 と統合）

「論理削除（is_active = false）で過去住所を保持」を採用済み。**住所編集 = 論理削除＋新規 INSERT** の運用ルールを r1 で本文に明記（前掲 `address` テーブル節参照）。

---

## 3. 返品ワークフローの通知設計（軽微だが重要）

返品・返金ワークフローは承認制のため、**通知設計を追加することで UX と運用性が向上**する。

### ■ 通知タイミング
| タイミング | 通知先 | 内容 |
|------------|---------|------|
| ユーザが返品申請 | 管理者 | 返品申請の発生 |
| 管理者が承認 | ユーザ | 承認通知（返送手順など） |
| 管理者が却下 | ユーザ | 却下理由の通知 |
| 返金完了 | ユーザ | 返金完了通知 |

### ■ 通知手段
- メール通知（初期実装）
- 将来的にアプリ内通知（push）も追加可能

`sales_return.notified_user` / `notified_admin` で送信状態を管理（前掲テーブル参照）。

---

# phase15 への要請事項（r1 で確定した事項）

phase15 r4 が長期シグネチャ・完全移行後の構造で実装着手できるよう、phase14 r1 で確定した以下を phase15 側に反映する：

| 項目 | 内容 |
|------|------|
| `sales.shipping_method_id` 追加完了 | phase15 の `DeliveryCreationService.createForSales` を **長期シグネチャ `(sales_id)` で実装可**。過渡期シグネチャは不要 |
| 注文確定フローの確定 | phase15 は協調仕様のみで OK（本書の擬似コードに従う注文確定 Service が phase14 r1 で実装される） |
| 在庫モデル完全移行 | phase15 の並行運用フック（`InventorySyncService`）は phase14 r1 完了時点で削除対象。phase15 r4 → r5 で削除する設計変更を行う |
| `shipping_statuses` マスタ拡張 | CANCELED / DELIVERY_FAILED / RESCHEDULED が利用可。phase15 のステータス遷移表に追加するかは別途判断 |
| `inventory_movements` 利用 | phase15 の入荷登録 Service は `inventory_movements` への INSERT も追加する（同一トランザクション） |
| 共通命名規約 | `docs/ai_context/operation_logs_naming.md` を本書 r1 で参照宣言済み |

---

# レビューコメント対応サマリ（改訂版 → r1）

## 🔴 必須対応（phase15 r3 / r4 起点）
| ID | 由来 | 対応 |
|----|------|------|
| P14-1 | phase15 RRR-1 / RRRR-2 | `products.stock` 廃止と `inventories` 完全移行を本フェーズで実施。完全移行マイグレーション仕様（4 ステップ）と乖離検出時の再構築手順を明記。`InventorySyncService` フックは Step 3 で削除 |
| P14-2 | phase15 RRR-2 | 注文確定フローを擬似コードレベルで確定。バリデーション → 在庫減算（悲観ロック）→ `sales` INSERT → `DeliveryCreationService.createForSales` → commit |
| P14-3 | phase15 RRRR-3 | `sales` テーブルに `shipping_method_id BIGINT NOT NULL`（FK to `shipping_methods.id`）を追加。長期シグネチャ `createForSales(sales_id)` への移行を r1 内で完結 |

## 🟡 推奨対応（phase15 起点の中程度論点）
| ID | 由来 | 対応 |
|----|------|------|
| P14-4 | phase15 RR-1 | `shipping_statuses` マスタに CANCELED / DELIVERY_FAILED / RESCHEDULED を r1 で追加。機能としての対応は別フェーズ |
| P14-5 | phase15 RRR-5 | `operation_logs.reason_code` は r1 で先行追加せず、将来課題として残す（phase15 のプレフィックス方式で十分） |
| P14-6 | phase15 RRR-6 | `inventory_movements` テーブルを r1 で新設。完全移行と同時に履歴記録を開始。命名は他 ERP との互換性を取らない方針を明記 |
| P14-7 | phase15 RRRR-7 | `address.user_id` を「住所の所有者」と明文化。`sales.user_id == address.user_id` を Service 層で強制 |
| P14-8 | phase15 RRRR-8 | 在庫の同時実行制御を `SELECT ... FOR UPDATE`（悲観ロック）で統一 |

## 🟢 軽微対応
| ID | 由来 | 対応 |
|----|------|------|
| P14-9 | phase15 RRRR-1 | 完全移行マイグレーション仕様（4 ステップ + 乖離検出時の再構築 SQL）を本書に追加 |
| P14-10 | phase15 RR-10 / RRR-9 | `operation_logs` セクションに「`screen_name` / `api_name` の命名規約は `docs/ai_context/operation_logs_naming.md` に従う」と明記 |
| P14-11 | phase14 単独 | 改訂履歴セクションを冒頭に追加 |
| P14-12 | phase14 単独 | 冒頭の `# #` 表記揺れを `#` に整形 |
| P14-13 | phase14 単独 | `payment_id` 採番方式を明記（模擬決済期は UUID v7 / Core 採番）。`UNIQUE` 制約も追加 |
| P14-14 | phase14 単独 | `return_reason_codes` マスタ化を将来課題として技術検討事項に明記 |
| P14-15 | phase14 単独 | 住所編集 = 論理削除＋新規 INSERT 運用を本文に明文化 |
| P14-16 | phase14 単独 | `operation_logs.comment` のフルテキスト検索基盤を将来課題として技術検討事項に明記 |
| P14-17 | phase14 単独 | 予約ステータス判定の基準時刻を **JST 0:00** と明記。テストも JST 基準で組む |

---

# 再レビューコメント（phase14 r1 に対して）

レビュー日：2026-05-06
レビュー対象：phase14_shipping_r1.md
参照：phase15_delivery_management_r3.md / phase15_delivery_management_r4.md（作成中）/ docs/coding_guidelines.md
前回指摘：P14-1 〜 P14-17

---

## 総評

P14-1 〜 P14-17 はすべて反映され、特に **P14-1（在庫モデル完全移行を本フェーズに取り込み）／ P14-2（注文確定フロー擬似コード化）／ P14-3（`sales.shipping_method_id` 追加）** の重大3件が r1 内で完結する設計に着地したのは大きな前進。phase15 側は長期シグネチャで実装着手でき、過渡期コード（`InventorySyncService`）が r1 完了時点で削除対象として整理されているのも明確。

末尾の「phase15 への要請事項」セクション（635 行〜）は、phase14 r1 → phase15 r5 の引き継ぎチェックリストとして極めて有効。**設計書としては実装着手 OK レベルに到達している**。

ただし、本書 r1 で新規に踏み込んだ「完全移行マイグレーション」「`inventory_movements` 設計」「過渡期と長期シグネチャの段取り」は、**phase15 r3 までの検討範囲を超えた領域**であり、実装計画の段取りとして詰めておきたい論点が残っている。以下、優先度順に提示する。

---

## 🔴 重大（実装段取りで必ずぶつかる）

### Q14-1. phase14 r1 と phase15 r3／r4 の **実装順序** が宙に浮いている

本書 r1 は「phase15 r3 / r4 で並行運用と `InventorySyncService` が確立された前提で、phase14 r1 で完全移行する」（285 行目）と書いているが、

- phase15 r3 / r4 が **未実装**の段階で phase14 r1 を実装着手する場合、`inventories` テーブル自体が存在しない（phase15 で定義されている）
- `inbounds` / `warehouses` / `inventory_movements` の整合関係も phase15 で先に作る必要がある
- 一方、phase15 r4（作成中）は **phase14 r1 で `sales.shipping_method_id` が追加済み**を前提に長期シグネチャで実装したい

つまり「phase14 r1 が phase15 を待ち、phase15 が phase14 r1 を待つ」**循環依存**になっている。

**推奨対応：** 実装フェーズの段取りを以下のように分解して本書冒頭に明記すべき。

```
Step A: phase14 r1 のスキーマ変更だけ先行
  - sales.shipping_method_id 追加
  - shipping_statuses マスタ拡張（CANCELED 等）
  - sales.payment_id UNIQUE 制約
  - operation_logs に screen_name / api_name 追加
  → phase15 r4 はこの状態で実装着手可（長期シグネチャ）

Step B: phase15 r4 実装
  - inventories / warehouses / inbounds / shipping_methods / deliveries 作成
  - InventorySyncService（並行運用フック）作成
  - 注文確定 Service の在庫減算は products.stock を主、InventorySyncService を従

Step C: phase14 r1 残り（在庫モデル完全移行）実装
  - inventory_movements 作成
  - Step 1〜4 のマイグレーション実施
  - InventorySyncService 削除

Step D: phase15 r5 でフック削除を反映（コードクリーンアップ）
```

これがないと、開発者は「phase14 r1 を実装するために何が前提で何が後続か」が分からず、Step が逆順になって実装ブロックを生む。

### Q14-2. 完全移行 SQL に `sales.quantity` / `sales_return.quantity` を新規追加しているが、既存 `sales` レコードの初期値が未定義

r1 で `sales` テーブルに `quantity INT NOT NULL`（395 行目）、`sales_return` テーブルに `quantity INT`（503 行目）を新規追加。完全移行マイグレーションの再構築 SQL（318 行目以降）はこれらを前提に書かれている。

しかし：

- `sales.quantity` / `sales_return.quantity` 追加 ALTER 時、**既存レコードの初期値**が未定義
- 初期値が `0` だと、在庫再構築 SQL が「過去の販売は数量0」と判定し、`inventories.quantity` が実態より過剰になる
- 初期値が `1` だと、過去の販売も「1個ずつ」とみなされ、まだましだが正確ではない

**推奨対応：** いずれか。

- (A) 既存データが無い段階（本番リリース前）で導入するなら、`NOT NULL DEFAULT 1` で問題なし。**本書の前提として「本番リリース前にスキーマ確定」を明記**
- (B) 既存データがある段階で導入するなら、移行マイグレーションで「`sales` の `amount` から `products.price` で割って `quantity` を逆算する」など、推定ロジックが必要

phase14 自体が「未着手」ステータスなので (A) で問題ないはずだが、**前提を明記しないと実装担当者が困る**。

### Q14-3. 完全移行の Step 1（整合性チェック）に「乖離があれば再構築」とあるが、再構築 SQL が `sales.is_preorder = false` の販売しか減算していない

318 行目の再構築 SQL：

```sql
- (
  SELECT COALESCE(SUM(s.quantity), 0)
  FROM sales s
  WHERE s.product_id = i.product_id AND s.is_preorder = false
)
```

これは「予約購入は在庫減算しない」前提だが、**注文確定フロー擬似コード**（239-243 行目）では：

```
- 並行運用期：products.stock -= order_quantity
            + InventorySyncService.applyDelta(product_id, 1, -order_quantity)
- 完全移行後：inventories.quantity -= order_quantity（直接）
- is_preorder == true は減算しない（在庫切れでも注文成立）
```

と書かれている。ここで **予約購入が「発売後に在庫減算される」ロジックがどこにあるか**が未定義。`is_preorder=true` の sales は永遠に在庫減算されないことになり、発売日以降の在庫整合が壊れる。

**推奨対応：** 予約購入の在庫減算タイミングを明確にする。候補：

- (A) 発売日到来時にバッチで `is_preorder=true` の sales をすべて在庫減算
- (B) 注文確定時に予約購入も在庫減算（在庫がマイナスになる可能性を許容、または `BACK_ORDER` ステータスとして扱う）
- (C) 商品発送（出荷）タイミングで在庫減算

(C) が直感的だが、phase15 の `deliveries` 状態遷移と密接に関わる。決定したら再構築 SQL も「予約購入で `delivered_date IS NOT NULL` なら減算」等に修正が必要。

---

## 🟡 中程度（実装前に確認推奨）

### Q14-4. `shipping_statuses` マスタ拡張だけ先行追加だが、機能対応は「別フェーズ」が曖昧

P14-4 対応で CANCELED / DELIVERY_FAILED / RESCHEDULED をマスタに追加（72-74 行目）し、「機能対応は別フェーズ」（76 行目）としているが、

- どのフェーズで扱うのか具体名がない（phase15 r5? phase16? 新フェーズ?）
- マスタにステータスが存在するのに Service 遷移ガードが対応していない期間、**Console UI で誤って遷移させる**リスク
- API 経由で外部から該当ステータスを書き込まれた場合の振る舞い未定義

**推奨対応：**
- 機能対応のフェーズ名を本書に明記（仮名でもよい：「将来の phase21 配送オペレーション拡張で対応」など）
- マスタに存在するが未対応のステータスについて、**Service 層でも入力バリデーションで拒否する**運用ルールを追加（マスタ存在 ≠ 入力許容）

### Q14-5. `inventory_movements.quantity` の `direction` カラムが冗長気味

356-357 行目：

```
| quantity | INT | NOT NULL | 増減量（正数のみ。減算は movement_type で表現／CHECK (quantity > 0)） |
| direction | VARCHAR(10) | NOT NULL | in / out の方向（増加 or 減少） |
```

`movement_type` enum（`inbound` / `sale` / `cancel` / `return` / `adjustment`）から `direction` は機械的に決まる：

- `inbound` → `in`
- `sale` → `out`
- `cancel` → `in`
- `return` → `in`
- `adjustment` → `in` or `out`（双方あり得る）

`adjustment` 以外は冗長で、データ不整合を生む可能性がある（`movement_type='inbound'` なのに `direction='out'` の不正レコード）。

**推奨対応：** いずれか。

- (A) `direction` を撤廃し、`movement_type` から導出する（Service 層で計算）。`adjustment` のみ `quantity` を符号付き（INT で負数許容）にする
- (B) `direction` を残すなら、DB 制約 `CHECK ((movement_type IN ('inbound','cancel','return') AND direction='in') OR (movement_type='sale' AND direction='out') OR movement_type='adjustment')` で整合を強制
- (C) そもそも `quantity` を符号付きにして `direction` カラムを削除（最もシンプル）

(C) が一番ノイズが少ない。集計時も `SUM(quantity)` で在庫増減が一発で出る。

### Q14-6. 注文確定フローの「住所オーナー検証」のスコープが曖昧

擬似コード 233-234 行目：

```
- sales.shipping_address_id が order_request.user_id 所有の address のみ参照可能
  であることを検証（P14-7／phase15 RRRR-7 対応）
```

これは「自分の住所しか配送先にできない」を強制するが、

- 過去住所（`is_active=false`）も含めて参照可能か？（過去住所だが自分のもの = 通常 OK）
- `address.user_id` が変更された場合（運用上はないはずだが）どうするか

**推奨対応：** 「**`is_active` は問わず、`address.user_id == sales.user_id` のみを検証**」と明記。これは住所編集時の運用（P14-15：論理削除して新規 INSERT）と整合させるため。

### Q14-7. `payment_id` の `UNIQUE` 制約と本番決済 API の二重連携時の挙動

411 行目「`payment_id` は `UNIQUE` 制約（模擬決済の重複採番・本番決済の二重連携を防止／P14-13）」とあるが、

- 本番決済で「ユーザーが二重送信した結果、同じ `payment_id` が来た」場合、UNIQUE 違反例外をどう扱うか
- 冪等性（idempotency）を保つには、UNIQUE 違反 = 既存の sales を返す、が望ましい

**推奨対応：** 「`payment_id` の UNIQUE 違反は既存 sales を返す（冪等処理）」を本書に明記。さもないと「決済は成功したが sales 作成に失敗 → ユーザに二重課金」が起きる。

### Q14-8. 完全移行 Step 2 の「読み取り側切替」が一括スイッチか段階的かが未定義

296-300 行目の Step 2 は「読み取り側を inventories 参照に切替」を一括的に書いているが、対象が複数：

- 予約ステータス判定（Core）
- 配送予定日計算（Core / phase15）
- Market の在庫表示
- Console の在庫表示（ある場合）

これらを **同時に切り替える**のか、**機能フラグ（feature flag）で段階的に切り替える**のかが未定義。一括だとリスクが高い。

**推奨対応：** 機能フラグ `inventory.read_source = products_stock | inventories` を `config/app/Inventory.php` に持たせ、段階的に切替できる構造にする。`config()` 経由で読む（規約 3-1 / 4-1 整合）。

---

## 🟢 軽微（任意）

### Q14-9. 改訂履歴の「改訂版」日付が「改訂日不明」のまま

10 行目「改訂版 | 改訂日不明 | …」。P14-11 で改訂履歴セクションを追加した良い対応だが、現行版の改訂日が不明のままだとトレーサビリティが弱い。git 履歴未取得とはいえ、本書 r1 化のタイミングで「改訂版」を r0 として日付推定するか、「改訂版（r0）」と表記して r1 との差分を明確にするかの整理推奨。

### Q14-10. `sales.quantity` 追加に伴う既存テストへの影響

r1 で `sales` に `quantity` カラムを新規追加したが、既存テスト（金額計算・集計）が `amount = 単価 × 数量` を意識していなかった可能性がある。`sales.quantity` 追加に伴い、

- 集計テスト：`SUM(amount)` だけでなく `SUM(quantity)`（販売数）の集計テストも追加
- 注文確定テスト：`amount = product.price × quantity` の整合性確認

これらのテストケースを r1 のテスト節（537 行目以降）に追加推奨。現状は配送方法別集計しか触れていない。

### Q14-11. `inventory_movements.created_by_user_id` のシステム自動操作の扱い

361 行目「自動処理は NULL」とあるが、

- 注文確定時の在庫減算（`movement_type='sale'`）は **ユーザの注文操作に伴う**が、Service 層から自動で発火する
- これは「ユーザ自身の操作」と扱って `sales.user_id` を入れるのか、「システム自動処理」として NULL にするのか

**推奨対応：** 「`movement_type='sale'` は注文者の `sales.user_id` を、`movement_type='adjustment'` は管理者の `user_id` を、その他バッチ自動処理は NULL を記録」と運用ルールを明記。監査時に「誰の行動が在庫を動かしたか」が辿れる。

### Q14-12. 予約ステータスのタイムゾーン明記は良いが、サマータイム未対応

55 行目「JST 0:00 基準」「タイムゾーン設定（`Asia/Tokyo`）」と明記された点は良い。日本国内のみのサービスでは問題ないが、

- 将来海外展開を視野に入れるなら、ユーザのロケールに応じた境界判定が必要になる
- 現状は「日本国内のみ」と本書で明記しておくと、将来要件の混入を防げる

軽微なので技術検討事項に1行追記する程度でよい。

### Q14-13. `inbounds` テーブル参照を本書でも宣言しているが、定義先は phase15

322 行目の再構築 SQL で `FROM inbounds` を使うが、`inbounds` は phase15 r3 で定義されたテーブル。本書の「DB 設計（改訂）」セクションには `inbounds` の説明がなく、参照だけしている形。

**推奨対応：** 本書 r1 の「DB 設計」冒頭に「`inbounds` / `inventories` / `warehouses` / `shipping_methods` / `deliveries` は phase15 r3 で定義済み。本書ではこれらを前提に `sales` / `address` / `operation_logs` / `sales_return` の改訂と `inventory_movements` の新設を扱う」と明記。テーブル所属が読み手に伝わりやすくなる。

### Q14-14. 模擬決済の `payment_id` UUID v7 採番ライブラリ依存

185-187 行目「模擬決済期は UUID v7（時系列ソート性のため）」と明記された点は良いが、

- Java の場合：標準 `java.util.UUID` は v4 のみ。v7 は外部ライブラリ（`uuid-creator` / `jug` 等）が必要
- PHP の場合：`ramsey/uuid` v4.7+ が v7 対応

**推奨対応：** 採番ライブラリ依存を技術検討事項に1行追記推奨（実装者が「v7 が標準にない」で迷う）。

---

## まとめ：r2 の必要性判定

| 優先度 | 項目 | r2 必要性 |
|--------|------|----------|
| 🔴 必須 | Q14-1（実装順序の循環依存解消） | **必要**（実装着手前に Step A〜D を明記） |
| 🔴 必須 | Q14-2（`sales.quantity` 既存レコード初期値の前提） | **必要**（「本番リリース前にスキーマ確定」前提を明記） |
| 🔴 必須 | Q14-3（予約購入の在庫減算タイミング） | **必要**（再構築 SQL の正当性に直結） |
| 🟡 推奨 | Q14-4（マスタ拡張の機能対応フェーズ名） | r2 で確定 |
| 🟡 推奨 | Q14-5（`inventory_movements.direction` 冗長化） | r2 で簡素化 |
| 🟡 推奨 | Q14-6（住所オーナー検証の `is_active` 扱い） | r2 で明記 |
| 🟡 推奨 | Q14-7（`payment_id` UNIQUE 違反の冪等処理） | r2 で明記 |
| 🟡 推奨 | Q14-8（完全移行 Step 2 の機能フラグ化） | r2 で明記 |
| 🟢 任意 | Q14-9 〜 Q14-14 | 実装と並行可 |

---

## 結論

phase14 r1 は P14-1 〜 P14-17 を漏れなく反映し、phase15 r3／r4 と整合する設計に到達している。「phase15 への要請事項」セクションも引き継ぎとして秀逸。

ただし、**Q14-1（実装順序）／ Q14-2（`sales.quantity` 既存値）／ Q14-3（予約購入の在庫減算）** の3点は、**設計書を実装計画に変換する瞬間に必ず詰まる論点**であり、実装着手前に解消する必要がある。

特に **Q14-1 の循環依存** は、本書と phase15 r3 / r4 が同時並行で書かれているから生じる構造で、設計書を読む側からは「どっちを先に作るんだ？」が判断不能。Step A〜D の段取りを本書冒頭の「範囲」セクションに明記することで、実装担当者が迷わず着手できるようになる。

**最低限：** Q14-1 / Q14-2 / Q14-3 を r2 で追記すれば、phase14 r1 → phase15 r5 の実装段取りが完全に整合する。それ以外（Q14-4 〜 Q14-14）は実装と並行で詰めても致命的ではない。

