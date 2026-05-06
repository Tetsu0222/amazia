# フェーズ14：購入機能（改訂版 r4）

## ステータス
🔲 未着手

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 初版日不明（git 履歴未取得） | 初稿（フェーズ14購入機能の基本設計） |
| 改訂版（r0 相当） | 改訂日不明 | 分析画面連携方針・予約ステータスロジックの一元化・配送ステータスのマスタ化・返品ワークフロー詳細化など。 |
| r1 | 2026-05-06 | phase15 r3／r4 から積み上がった要請（P14-1 〜 P14-12）と phase14 単独観点（P14-13 〜 P14-17）を反映。 |
| r2 | 2026-05-06 | 再レビューコメント Q14-1 〜 Q14-14 を反映。実装段取り Step A〜D・本番リリース前スキーマ確定前提・予約購入の出荷時減算・機能フラグ段階化など。 |
| r3 | 2026-05-06 | 再々レビューコメント S14-1 〜 S14-12 を反映。出荷時在庫不足の挙動を「例外＋PENDING 維持」に確定、予約購入 `sale` 記録の `created_by_user_id` を出荷操作管理者に細分化、機能フラグの継承ロジック明文化と Console 表示キー追加、Step 4 で機能フラグ撤去、`payment_id` 冪等処理に user_id/product_id/amount 一致チェック追加（セキュリティ対策）など。 |
| **r4** | **2026-05-06** | **【大幅改訂】フェーズ10で実装済みの SKU 単位在庫モデル（`product_skus` / `product_sku_stocks` / `product_sku_stock_transactions` / `ReceiveProductSkuStockService`）を在庫の正本として採用。これに伴い r3 の以下を削除：(1) `inventories` / `inbounds` / `inventory_movements` / `warehouses` 新設、(2) `InventorySyncService` による並行運用、(3) 機能フラグ `inventory.read_source` による段階移行、(4) 在庫モデル完全移行 Step C〜E、(5) `products.stock` 廃止計画。また phase13 で実装済みの `market_customers` を踏まえ、`sales.user_id` の参照先を `market_customers.id` に確定。会員住所（`market_customers.postal_code`/`address`）と注文時配送先住所（新設 `address` テーブル：1注文1スナップショット）を完全に分離。`payment_methods` マスタを新設し、`market_customers.payment_method` は会員ごとの既定値として残置（注文時は `sales.payment_method_id` で個別選択）。在庫キーを `product_id` から `sku_id` に変更。同時実行制御は既存 `@Version` 楽観ロックを維持。Step 構造を Step 0 / A / B の3段階に縮小。 |

## 範囲
- Amazia Console
- Amazia Market
- Amazia Core
- DB設計（新規テーブル：`address` / `payment_methods` / `shipping_statuses` / `sales` / `sales_return` / `operation_logs`）
- **既存 SKU 単位在庫モデル（`product_sku_stocks` / `product_sku_stock_transactions`）の活用**
- **注文確定フローの確定**（P14-2 対応）
- **予約購入の在庫減算（出荷時減算）の Service 化**（Q14-3 / S14-1 / S14-2 対応）

## 前提（r4 で再整理）

### A. フェーズ10で実装済み（SKU 単位在庫モデル）
本書は以下を **既存資産として活用** する。新設はしない。

| 既存テーブル / Service | 役割 | r4 での扱い |
|-----------------------|------|-----------|
| `product_skus(id, product_id, sku_code, color, size, status)` | SKU マスタ。`UNIQUE(product_id, color, size)` | `sales.sku_id` の参照先 |
| `product_sku_stocks(id, sku_id, quantity, version)` | **SKU 別在庫の正本**。`@Version` 楽観ロック | 注文確定時の在庫減算・予約出荷時の減算・返品復元の対象 |
| `product_sku_stock_transactions` | **在庫増減ログの正本**（旧 r3 で `inventory_movements` として新設予定だったもの） | sale / cancel / return の在庫増減を記録 |
| `ReceiveProductSkuStockService` | **入荷受入処理**（旧 r3 で `RegisterInboundService` として新設予定だったもの） | 入荷時の在庫加算 + transaction 記録 |
| `Product.stock` カラム | 商品単位の在庫（フェーズ10以降は使われていない可能性が高い死カラム） | **本書では触らない**。将来課題として「未使用カラムの棚卸し・DROP」を別途登録 |

### B. フェーズ13で実装済み（会員管理）
| 既存テーブル / 概念 | 役割 | r4 での扱い |
|-------------------|------|-----------|
| `market_customers(id, name_*, postal_code, address(VARCHAR), birthday, email, password_hash, payment_method, ...)` | 会員マスタ。1顧客1住所。1顧客1既定決済方法 | **触らない**。`sales.user_id` の参照先 |
| `market_customers.postal_code` / `address` | 会員の現住所（VARCHAR1カラム） | **触らない**。注文時に配送先住所マスタへスナップショット複製 |
| `market_customers.payment_method` | 会員の既定決済方法（VARCHAR） | **触らない**。注文画面の初期値として使用（B-1） |
| `users`（auth）/ `User` Entity | 管理画面ユーザ（管理者） | `operation_logs.user_id` の参照先（管理者操作の記録用） |

### C. 本番リリース前スキーマ確定前提
本書および phase15 一連の設計は **Amazia の本番リリース前スキーマ確定**を前提とする。

- `sales` / `sales_return` / `address` / `payment_methods` / `shipping_statuses` / `operation_logs` は本書の Step 0 で新規作成。既存レコード移行は不要。
- 既存ユーザの売上・在庫履歴を引き継ぐ移行マイグレーションは本書のスコープ外。

### D. DB マイグレーション管理方針（r4 で明文化／**037 起因で 2026-05-06 訂正**）

> **訂正注記（[037](../../troubles/037_flyway_misassumed_phase14_tables_missing.md) 起因）**：r4 改訂時に「Flyway で管理」と記述したが、**本プロジェクトは Flyway 未導入**（pom.xml に依存なし）であった。実態は `schema.sql` を `spring.sql.init.mode=always` で起動時実行する方式。`db/migration/V*.sql` は過去の名残ファイルで起動時に何も実行されない。下記方針は実態に合わせて訂正済み。

- **業務テーブル（`address` / `payment_methods` / `shipping_statuses` / `sales` / `sales_return` / `operation_logs` 等）は Core 側 `schema.sql`（`amazia-core/src/main/resources/schema.sql`）で管理する**
  - `CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE` / `ALTER TABLE ... ADD COLUMN`（重複は `spring.sql.init.continue-on-error=true` で許容）の冪等構文で記述
- 既存方針との整合：roles / permissions / users / market_customers / postal_addresses / workflow_* など、業務テーブルはすべて schema.sql に冪等版が存在する
- Console Laravel migrations には業務テーブルを追加しない（Laravel 標準テーブル `users`（personal_access_tokens 用）/ `cache` / `jobs` のみ管理）
- フェーズ10 の SKU 在庫モデル（`product_skus` / `product_sku_stocks` / `product_sku_stock_transactions` 等）は JPA `@Entity` から本番でも生成されている既存実装を踏襲。Step A で `product_sku_stock_transactions` を拡張する際は **schema.sql 末尾に `ALTER TABLE ... ADD COLUMN` を追加**する（`continue-on-error=true` で再起動時の重複は無視）
- テスト環境は `spring.sql.init.schema-locations=` を空に設定して schema.sql を読み込まず、`ddl-auto=create-drop` で JPA Entity から H2 上にテーブル自動生成。本番 MySQL とテスト H2 の DB 初期化方式は異なるため、`mvn test` 緑＝本番動作とは限らない（[037](../../troubles/037_flyway_misassumed_phase14_tables_missing.md)）

---

# 実装段取り（Step 0 / A / B の3段階構成）

r3 の Step A〜E のうち Step C / D / E は **在庫モデル完全移行のための Step** であった。フェーズ10 の SKU 在庫モデルが既に正本として機能しているため、これらの Step は **不要**となり削除した。

| Step | 対象 | 主な作業 | 完了条件 |
|------|------|---------|---------|
| **Step 0** | 前提整備（設計書外だが現コードベースで必要） | 共通命名規約 `operation_logs_naming.md` 新規作成 / ベーススキーマ作成（`address` / `payment_methods` / `shipping_statuses` / `sales` / `sales_return` / `operation_logs`） / Core JPA Entity・Repository 新規作成 / 設計書 r4 への改訂作業 / 再発防止メモを `operational_insights.md` に追記 | `php artisan migrate` 通過 / `mvn test` 通過 / 既存機能に影響なし |
| **Step A** | phase14 r4 のスキーマ拡張 | `sales` 拡張（`sku_id` / `quantity` / `payment_id UNIQUE` / `shipping_method_id` / `shipping_address_id` / `payment_method_id` / `is_preorder`）/ `sales_return.quantity` 追加 / `shipping_statuses` マスタ拡張（CANCELED 等）/ `operation_logs.screen_name` / `api_name` 追加 / UUID v7 採番ライブラリ選定 | スキーマだけが入った状態。既存機能（商品一覧 / ログイン等）が壊れていない |
| **Step B** | phase14 r4 の機能実装 | 注文確定フロー（OrderConfirmationService）/ 模擬決済（PaymentService）/ 売上管理（Console）/ 配送先住所スナップショット / 返品ワークフロー / 操作履歴画面 / 出荷時減算（予約購入）/ Market 購入導線（購入ボタン → 未ログイン → /login → ログイン後チェックアウト → 注文確定）/ phase15 r4 の `deliveries` / `shipping_methods` 連携 | Market から購入確定 → sales/deliveries 生成 → Console から配送ステータス更新 → 購入履歴反映が end-to-end で動く |

実装担当者は **必ず Step 0 → A → B の順序**で進める。

---

# 全体方針

## 🔹 在庫モデル方針（r4 で大幅変更）

### 採用方針
- **既存 SKU 単位在庫モデル（`product_sku_stocks`）を在庫の正本として採用**
- 在庫キー = `sku_id`（FK to `product_skus.id`）
- 同時実行制御 = 既存の `@Version` **楽観ロック**を維持（r3 で要求した悲観ロック切替は行わない）
- 在庫増減ログ = 既存 `product_sku_stock_transactions`
- 入荷受入 = 既存 `ReceiveProductSkuStockService`

### r3 から削除された内容
以下は r4 では **すべて削除**された：

- `inventories` テーブル新設
- `inbounds` テーブル新設
- `inventory_movements` テーブル新設
- `warehouses` テーブル新設（倉庫マスタ）
- `InventorySyncService`（並行運用フック）
- 機能フラグ `inventory.read_source` および子キー（market_display / delivery_schedule / preorder_status / console_display）
- 完全移行マイグレーション（Step 1: 整合性チェック / Step 2: 読み取り側切替 / Step 3: 書き込みフック削除）
- `ALTER TABLE products DROP COLUMN stock`
- 在庫モデル完全移行 Step C〜E

理由: フェーズ10で実装済みの SKU 単位在庫モデルが既に同等の役割を果たしており、新設すると二重実装になる。

## 🔹 分析画面との連携方針
将来の分析画面を見据え、以下の分析軸を想定しデータ保持方針を決定する：

- **RFM分析**（Recency / Frequency / Monetary）
- **商品別売上分析**（カテゴリ別・発売日別・予約比率など。`product_skus.product_id` で JOIN 集計）
- **SKU別売上分析**（色別・サイズ別。`sales.sku_id` 直接集計）（**r4 で追加**）
- **ユーザ行動分析**（購入導線、予約→購入転換率など）
- **配送方法別売上分析**（`sales.shipping_method_id`）

→ 売上テーブルは「過度に正規化しない柔軟構造」を維持しつつ、**マスタテーブル化（決済方法・配送ステータス・配送方法）**を行う。

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

### ■ 予約ステータスの在庫参照先（r4 で SKU 化）
`BACK_ORDER` / `SOLD_OUT` 判定は **`product_sku_stocks.quantity`** を参照する（SKU 単位）。商品全体の在庫状況は同一商品の SKU 在庫を `SUM` して判定する：

```
商品全体の在庫 = SUM(product_sku_stocks.quantity)
              WHERE sku_id IN (SELECT id FROM product_skus WHERE product_id = ?)
```

### ■ タイムゾーン方針
「今日」「公開日」「発売日」の判定はすべて **JST 0:00 基準**で行う。サーバ／DB のタイムゾーン設定（`Asia/Tokyo`）と整合させる。**サマータイム・海外展開は現時点でスコープ外**。

→ Market / Console はこのステータスを参照して UI 表示を統一。

---

# 配送ステータスのマスタ化（Enum）

| ステータス | 説明 | 追加版 | 機能対応フェーズ |
|-----------|------|-------|----------------|
| PENDING | 配送準備中 | 既存 | phase15 |
| SHIPPED | 配送済 | 既存 | phase15 |
| DELIVERED | 配送完了 | 既存 | phase15 |
| RETURN_REQUESTED | 返品申請中 | 既存 | phase14 / phase15 |
| RETURNED | 返品完了 | 既存 | phase14 / phase15 |
| CANCELED | 発送前キャンセル | r1 追加 | 将来 phase21 配送オペレーション拡張（仮称） |
| DELIVERY_FAILED | 配達失敗・持ち戻り | r1 追加 | 将来 phase21 配送オペレーション拡張（仮称） |
| RESCHEDULED | 再配達手配中 | r1 追加 | 将来 phase21 配送オペレーション拡張（仮称） |

### マスタ存在 ≠ 入力許容（Q14-4 対応）
phase14 r4 / phase15 r4 の Service 層では、**未対応ステータスへの遷移リクエストはバリデーションで拒否する**。Service 層の許容ステータスリスト（config）で制御。

---

# Amazia Console（改訂）

## 売上管理画面

### ■ 集計要件
- 年/月/日単位
- **商品別集計**（`product_skus.product_id` で JOIN 集計）
- **SKU別集計**（`sales.sku_id` 直接集計／**r4 で追加**）
- **ユーザ別集計**（`sales.user_id` = `market_customers.id`）
- **決済方法別集計**
- **配送方法別集計**
- **予約売上 / 通常売上の区別**
- **販売数量集計**

### ■ 表示項目
- ユーザ名（`market_customers.name_last + name_first`）
- 購入商品（商品名 + 色 + サイズ）
- **数量**
- 金額（`amount = product_sku_prices.price × quantity` の整合性が前提）
- 配送日
- 売上日
- 配送ステータス（Enum）
- 決済方法（マスタ参照）
- **配送方法（マスタ参照）**
- 予約 or 通常購入区分

---

## 配送先変更（履歴管理を明確化）
- 管理者が配送先を変更可能
- **変更履歴は operation_logs に記録**
  - action: "update_shipping_address"
  - target_type: "sales"
  - target_id: sales.id
  - comment: 変更理由・旧住所・新住所を記録
- **UI フィルタ**：Console での配送先変更時、過去住所（`is_active=false`）は選択肢に出さない。API バリデーションは通すが、UI で表示しないことで運用上の事故を防ぐ（S14-10）。

---

## 返品・返金ワークフロー

### ■ ステータス
| ステータス | 説明 |
|-----------|------|
| REQUESTED | ユーザが返品申請 |
| APPROVED | 管理者承認 |
| REJECTED | 管理者却下 |
| REFUNDED | 返金完了 |

### ■ 管理項目
- 返品理由（ユーザ入力。フリーテキスト）
- **返品理由コード（`return_reason_code`／将来課題）**
- 承認者（管理者ID）
- 承認日時
- **返品数量（`sales_return.quantity`）**
- 返金方法（決済方法に依存）

→ 返品情報は **sales_return テーブル（新規）** を追加して管理。

### ■ 返品時の在庫戻し（r4 で SKU 化）
返品承認・返金完了時に **`product_sku_stocks.quantity += sales_return.quantity`** を実行（同一トランザクション）。同時に `product_sku_stock_transactions` に `transaction_type='return'` で記録。

---

## 操作履歴画面（5W1Hの補強）
- いつ（created_at）
- 誰が（user_id = `users.id`／管理者）
- 何を（action）
- どこで（**screen_name / api_name を追加**）
- なぜ（comment）
- どのように（target_type / target_id）

`screen_name` / `api_name` の命名規約は **`docs/ai_context/operation_logs_naming.md` に従う**。

---

# Amazia Market（改訂）

## カート機能（セッション or DB 切替可能設計）
初期はセッション管理だが、将来の拡張を見据え以下を定義：

- **ログインユーザは DB カートを優先**
- 未ログインユーザはセッションカート
- ログイン時にセッションカート → DB カートへマージ

→ cart_items テーブルを将来追加できる構造にする。

## 購入ボタンと未ログイン時の挙動（r4 で明文化）
- ProductDetail 画面の SKU 選択後（色 + サイズ確定後）、「購入する」ボタンを表示
- ボタン押下時:
  - **未ログインの場合**: `/login` にリダイレクト。`location.state.redirectTo` で購入予定画面へ復帰できるようにする
  - **ログイン済みの場合**: `/checkout/:productId?sku_id=...&quantity=...` へ遷移
- ログイン成功後、`redirectTo` があればそこへ遷移

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

### ■ payment_id の採番方式
- **採番ロジックの所在**：Amazia Core の `PaymentService`（模擬決済時のみ）。本番決済 API 接続後は外部発行値をそのまま受領する。
- **形式**：模擬決済期は **UUID v7**（時系列ソート性のため）。本番決済 API 接続後は外部システム由来（最大長想定で `VARCHAR(100)` を確保済み）。
- **採番ライブラリ依存**：
  - Java：標準 `java.util.UUID` は v4 のみ。v7 は外部ライブラリ（`uuid-creator` 等）が必要
  - PHP：`ramsey/uuid` v4.7+ が v7 対応
  - **採用ライブラリは Step A 実装時に License（Apache-2.0 / MIT 推奨）と GitHub Star・最新リリース日で判断**
- **重複チェックと冪等処理**（r4 で SKU 化）：
  - `sales.payment_id` を `UNIQUE` 制約で担保
  - **UNIQUE 違反を捕捉した際は、`payment_id` のみで一致と判断せず、`user_id` / `sku_id` / `quantity` / `amount` のすべてが既存 `sales` と一致する場合に限り既存 `sales` を返す（真の二重送信のみ冪等扱い）**
  - **`payment_id` のみ一致で他項目が異なる場合**は、決済 API のバグ・なりすまし疑いとして **エラーログ（要監視レベル）に記録し、例外を投げて拒否する**

```
擬似コード：
try:
    INSERT sales(user_id, sku_id, quantity, amount, payment_id, ...)
catch UniqueViolation on payment_id:
    existing = SELECT * FROM sales WHERE payment_id = :payment_id
    if existing.user_id == request.user_id
       AND existing.sku_id    == request.sku_id
       AND existing.quantity  == request.quantity
       AND existing.amount    == request.amount:
        return existing            // 真の二重送信 → 冪等処理
    else:
        log.error('payment_id reuse with mismatched fields', ...)
        throw PaymentIdConflictException
```

---

## 配送情報（仕様補強）
- **配送先住所は `market_customers.postal_code` / `address` から自動スナップショット**（r4 で確定）
- 1 顧客 1 住所のため、注文確定時に Service 層で `address` テーブルへ INSERT し `sales.shipping_address_id` に紐付ける
- **会員と異なる住所への配送（ギフト・実家配送等）は本フェーズのスコープ外**
- コンビニ受け取り：**店舗検索API（将来）を想定**
- 置き配：**商品属性（allow_dropoff）と連携**
- **配送方法（`shipping_method_id`）を注文確定リクエストに含める**。受領した値は `sales.shipping_method_id` および `deliveries.shipping_method_id` に同期保存

---

## 購入履歴
- 購入日時
- 商品名 + 色 + サイズ（**r4 で SKU 化**）
- **数量**
- 金額
- 配送予定日
- 配送ステータス
- **配送方法**
- 予約 or 通常購入区分

---

## 予約機能（Core のステータスを利用）
| ステータス | Market 表示 |
|-----------|-------------|
| NOT_PUBLIC | 非表示 |
| PRE_ORDER_NOT_STARTED | 「予約開始前」 |
| PRE_ORDER | 「予約受付中」＋発売日表示 |
| ON_SALE | 通常販売 |
| BACK_ORDER | 「再入荷予約受付中」 |
| SOLD_OUT | 「完売」（購入ボタン非表示） |

---

# 注文確定フロー（r4 で SKU 化 + 配送先スナップショット明文化）

注文確定時の処理は以下のフローで Service 層に集約する。バリデーション・配送先スナップショット作成・在庫減算・`sales` INSERT・`deliveries` 生成は**同一トランザクション**で完結させる。

```
OrderConfirmationService.confirm(order_request):

  1. validateOrder(order_request)  ※ 予備的バリデーション
     - market_customers.id == order_request.user_id を検証
     - product_skus.id == order_request.sku_id が ACTIVE であることを検証
     - product_skus.product_id 経由で取得した商品が公開中であることを検証
     - sales.shipping_method_id が shipping_methods マスタに存在し、
       かつ「許容ステータス」リストに含まれることを検証
     - sales.payment_method_id が payment_methods マスタに存在することを検証
     - 在庫の予備チェック：is_preorder == false かつ
       product_sku_stocks.quantity <= 0 なら拒否
       ※ ただしこれは予備的判定。最終確定はステップ 4 のロック取得後

  2. 配送先住所スナップショット作成（r4 で明文化）
     - market_customers.postal_code / address から address テーブルへ
       新規行を INSERT（is_active=true、user_id=order_request.user_id）
     - 戻り値の id を shipping_address_id として保持

  3. begin transaction

  4. 通常購入の在庫減算（is_preorder == false のみ）
     ※ 同時実行制御：既存の @Version 楽観ロックを使用
     ※ 在庫不足なら例外でロールバック
     - product_sku_stocks.quantity -= order_quantity
       （version 不一致で OptimisticLockException が出たらリトライまたはエラー）
     - product_sku_stock_transactions に transaction_type='sale',
       quantity=-order_quantity, sku_id=..., reference_type='sales' を記録
       （sales.id は 5 で確定するため、5 後に reference_id を更新）
     - 予約購入（is_preorder == true）はこの時点では減算しない（出荷時減算）

  5. INSERT sales
     （user_id, sku_id, quantity, amount, payment_method_id, payment_id,
       shipping_method_id, shipping_address_id, sales_date, is_preorder, ...）

     ※ payment_id UNIQUE 違反時は「user_id/sku_id/quantity/amount すべて一致」
       のときのみ既存 sales を返す冪等処理。それ以外は例外

  6. product_sku_stock_transactions.reference_id を sales.id で更新

  7. DeliveryCreationService.createForSales(sales.id)
     → INSERT deliveries (
          sales_id,
          shipping_address_id = sales.shipping_address_id,
          shipping_method_id  = sales.shipping_method_id,
          shipping_status_id  = PENDING,
          scheduled_date      = DeliveryScheduleService.calculate(...)
        )

  8. commit
```

## 予約購入の在庫減算タイミングと出荷時挙動（r4 で SKU 化）

予約購入（`sales.is_preorder = true`）の在庫減算は **出荷時（`deliveries.shipping_status_id` を `PENDING → SHIPPED` に遷移する瞬間）** に実施する。

### 採用方針
| タイミング | 採否 | 理由 |
|-----------|------|------|
| (A) 発売日到来時にバッチで一括減算 | ✕ | バッチが必要・障害時の再実行が複雑 |
| (B) 注文確定時（通常購入と同時に減算） | ✕ | 在庫がマイナスになる可能性 |
| **(C) 出荷時（`SHIPPED` 遷移時）に減算** | **採用** | `deliveries` 状態遷移と Service 内で完結。発送＝在庫消費という現実の業務フローと整合 |

### 出荷時に在庫不足だった場合の挙動

予約受付時には在庫があると予測して受付したが、発売／出荷時点で予約数が在庫を超えるケースが現実に起き得る。本書では以下の方針 (A) を採用する。

| 方針 | 採否 | 理由 |
|------|------|------|
| **(A) 例外＋PENDING 維持** | **採用** | 出荷時に `product_sku_stocks.quantity < sales.quantity` を検知したら例外を投げ、`deliveries.shipping_status_id` は `PENDING` のまま据え置く。管理者が入荷後に再度出荷操作を行う |
| (B) 予約上限を SKU マスタに設けて超過受付防止 | 将来課題 | 予約 UX の精度向上には有効だが、本フェーズでは扱わない |
| (C) 出荷分割（在庫充足分は出荷、不足分は PENDING 維持） | 将来課題 | 分割配送は phase15 r4 でスコープ外宣言済み |

### 出荷時減算の実装方針（r4 で SKU 化）
- `deliveries` の `shipping_status_id` を `PENDING → SHIPPED` に遷移する Service（phase15）に、`sales.is_preorder == true` の場合のみ在庫減算フックを追加する
- 通常購入は注文確定時に減算済みのため出荷時には再減算しない（`is_preorder` で分岐）
- 在庫減算は同一トランザクション内で `product_sku_stocks.quantity -= sales.quantity`（楽観ロック）
- 不足なら例外を投げ、`SHIPPED` 遷移は行わない（`PENDING` 維持）
- `product_sku_stock_transactions` に `transaction_type='sale', quantity=-sales.quantity, reference_type='sales', reference_id=sales.id, created_by_user_id=出荷管理者の users.id` を記録（S14-2）

---

# DB設計（r4 改訂版）

> **本書で新設するテーブル**
> - `address`（注文時配送先住所スナップショット）
> - `payment_methods`（決済方法マスタ）
> - `shipping_statuses`（配送ステータスマスタ）
> - `sales`（売上）
> - `sales_return`（返品）
> - `operation_logs`（操作履歴）
>
> **既存活用するテーブル（フェーズ10）**
> - `product_skus` / `product_sku_stocks` / `product_sku_stock_transactions` / `product_sku_prices`
>
> **既存活用するテーブル（フェーズ13）**
> - `market_customers`（変更なし）
>
> **r3 から削除されたテーブル**
> - `inventories` / `inbounds` / `inventory_movements` / `warehouses`（フェーズ10 SKU モデルで代替）

## sales テーブル（r4 改訂版）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| user_id | BIGINT | NOT NULL | 購入ユーザID（**FK to `market_customers.id`**） |
| sku_id | BIGINT | NOT NULL | **SKU ID（FK to `product_skus.id`）／r4 で `product_id` から変更** |
| quantity | INT | NOT NULL | 購入数量（**`CHECK (quantity > 0)`**） |
| amount | INT | NOT NULL | 金額（`amount = product_sku_prices.price × quantity` の整合性を Service で担保） |
| payment_method_id | BIGINT | NOT NULL | 決済方法マスタID（FK to `payment_methods.id`） |
| **shipping_method_id** | BIGINT | NOT NULL | 配送方法マスタID（FK to `shipping_methods.id`／phase15 で定義） |
| shipping_address_id | BIGINT | NOT NULL | 配送先住所ID（FK to `address.id`／注文時スナップショット） |
| shipping_date | DATE | NULL | 配送日 |
| sales_date | DATE | NOT NULL | 売上日 |
| shipping_status_id | BIGINT | NOT NULL | 配送ステータスマスタID（FK to `shipping_statuses.id`） |
| payment_id | VARCHAR(100) | NOT NULL | 決済ID（**`UNIQUE` 制約**／冪等処理は Service 層） |
| is_preorder | BOOLEAN | NOT NULL | 予約購入か |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `user_id` は `market_customers.id` への FK
- `sku_id` は `product_skus.id` への FK（r4 で SKU 化）
- `shipping_method_id` は `shipping_methods.id`（phase15 で定義）への FK
- `shipping_address_id` は `address.id` への FK。Service 層で「`order_request.user_id == address.user_id`」を強制
- `payment_id` は `UNIQUE` 制約。違反時は **`user_id` / `sku_id` / `quantity` / `amount` の全項目一致時のみ既存 `sales` を返す冪等処理**

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_sales_sales_date` | 売上日別集計 |
| `idx_sales_sku_id` | **SKU別集計（r4）** |
| `idx_sales_user_id` | ユーザ別集計 |
| `idx_sales_payment_method_id` | 決済方法別集計 |
| `idx_sales_shipping_method_id` | 配送方法別集計 |

---

## address テーブル（r4 で新設明記）

注文時の配送先住所スナップショットマスタ。会員住所（`market_customers.postal_code` / `address`）とは **完全に別物**。1 注文ごとに新規 INSERT して `sales.shipping_address_id` で参照する。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | **所有ユーザ（FK to `market_customers.id`）。注文時の検証は `order_request.user_id == address.user_id`** |
| postal_code | VARCHAR(20) | 郵便番号 |
| prefecture | VARCHAR(50) | 都道府県（**会員住所の VARCHAR(255) を構造化分解する想定。または住所全体を address_line に格納する暫定運用も可。Step 0 で確定**） |
| city | VARCHAR(100) | 市区町村 |
| address_line | VARCHAR(255) | 住所 |
| building | VARCHAR(255) | 建物名 |
| is_active | BOOLEAN | 現在利用中の住所か（false なら過去住所） |
| created_at | DATETIME | 作成日時 |

### 配送先スナップショット生成の運用（r4 で確定）
- 注文確定時、`market_customers.postal_code` / `address` から `address` テーブルへ新規行を INSERT
  - 会員住所の VARCHAR が構造化されていない場合、`address_line` に住所文字列をそのまま格納（`prefecture` / `city` / `building` は NULL でも可。Step 0 で運用方針確定）
  - `is_active = true` で投入
- 注文ごとに新規 INSERT するため、同一会員の `address` レコードが複数行蓄積される
- 過去注文の配送先表示は `sales.shipping_address_id` 経由で固定された住所を表示。会員が後で住所変更しても影響を受けない
- **Console での配送先変更時**：API バリデーションは「`address.user_id == sales.user_id`」のみ。UI では `is_active = false` の住所を選択肢に出さない（S14-10）

### 注: 設計書 r3 までの「住所編集時に旧住所 is_active=false に UPDATE して残す」運用について
r3 までは「住所マスタを共通化して編集時に履歴として残す」前提だったが、r4 では会員住所と注文時配送先を分離したため、`address` テーブルは **常に注文時スナップショットとして INSERT される**。`is_active` の意味は「現在も配送可能な住所として UI 選択肢に出すか」のフラグとなる。住所の更新は基本的に行わず、新規 INSERT で対応する。

---

## payment_methods（マスタ／新設）
| id | name |
|----|------|
| 1 | credit_card |
| 2 | d_payment |
| 3 | cash_on_delivery |

備考: `market_customers.payment_method`（VARCHAR）は会員ごとの **既定** 決済方法として残置。注文画面の初期値として使用。注文ごとの確定値は `sales.payment_method_id` で個別選択可能（B-1）。

---

## shipping_methods（マスタ／参照：phase15 r4 で定義）
| id | name |
|----|------|
| 1 | home_delivery |
| 2 | konbini_pickup |
| 3 | dropoff |

phase14 r4 では `sales.shipping_method_id` が本マスタを参照する FK となる。

---

## shipping_statuses（マスタ／新設）
前述の Enum を格納（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED + CANCELED / DELIVERY_FAILED / RESCHEDULED）。Service 層では未対応ステータスへの遷移リクエストをバリデーションで拒否（Q14-4）。

---

## operation_logs（新設）
| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | 操作ユーザ（FK to `users.id`／管理者） |
| action | VARCHAR(100) | 操作内容 |
| target_type | VARCHAR(50) | 対象種別 |
| target_id | BIGINT | 対象ID |
| screen_name | VARCHAR(100) | 画面名（命名規約は `docs/ai_context/operation_logs_naming.md`） |
| api_name | VARCHAR(100) | API名（同上） |
| comment | TEXT | 任意コメント（プレフィックス `[manual]` / `[inbound_recalc]` / `[shipping_delay]` 等） |
| created_at | DATETIME | 操作日時 |

### `reason_code` カラムの先行追加について
phase15 r4 のプレフィックス方式で十分。先行追加せず、将来課題として残す。

### `comment` カラムの検索性
本格運用時に検索性が問題になった場合、フルテキスト検索またはログ専用基盤への流し込みを検討。本フェーズではスコープ外。

---

## sales_return テーブル（新設）

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| sales_id | BIGINT | 対象売上（FK to `sales.id`） |
| status | VARCHAR(50) | REQUESTED / APPROVED / REJECTED / REFUNDED |
| reason | TEXT | 返品理由（フリーテキスト） |
| approver_id | BIGINT | 承認者（FK to `users.id`） |
| approved_at | DATETIME | 承認日時 |
| quantity | INT | **返品数量（`CHECK (quantity > 0)`）** |
| notified_user | BOOLEAN | ユーザ通知済みか |
| notified_admin | BOOLEAN | 管理者通知済みか |
| created_at | DATETIME | 作成日時 |

### 返品理由分類（将来課題）
将来、返品分析を扱う場合は `return_reason_codes` マスタと `sales_return.return_reason_code` の追加を検討。

### 返品時の在庫戻し（r4 で SKU 化）
返品承認・返金完了時、Service 層で以下を同一トランザクションで実行：
- `product_sku_stocks.quantity += sales_return.quantity`（対象 sku_id は `sales.sku_id` 経由）
- `product_sku_stock_transactions` に `transaction_type='return', quantity=+sales_return.quantity, reference_type='sales_return', reference_id=sales_return.id, created_by_user_id=返品承認管理者の users.id` を記録

---

# 在庫増減ログ（既存 `product_sku_stock_transactions` の活用）

r3 までは `inventory_movements` テーブルを新設する想定だったが、r4 では既存の `product_sku_stock_transactions` を活用する。phase14 r4 ではこのテーブルへ以下の `transaction_type` で記録する：

| transaction_type | 発火タイミング | sku_id | quantity | reference_type | reference_id | created_by_user_id |
|------------------|--------------|--------|----------|---------------|-------------|-------------------|
| `inbound`（既存） | 入荷登録時 | 入荷 SKU | +n | `inbounds`（既存）または相当 | 入荷ID | 入荷登録管理者 |
| **`sale`（通常購入）** | 注文確定時 | sales.sku_id | -n | `sales` | sales.id | sales.user_id（注文者＝市場会員。`market_customers.id` を `users.id` と区別する場合は要検討） |
| **`sale`（予約購入）** | **出荷時（SHIPPED 遷移時）** | sales.sku_id | -n | `sales` | sales.id | **出荷操作管理者の users.id** |
| **`return`** | 返品承認・返金完了時 | sales.sku_id | +n | `sales_return` | sales_return.id | 返品承認管理者の users.id |
| `cancel`（将来） | キャンセル時 | sales.sku_id | +n | `sales` | sales.id | キャンセル実行者 |
| `adjustment`（将来） | 棚卸補正時 | 補正対象 | ±n | `manual_adjustment` | NULL | 棚卸実行管理者 |

### `created_by_user_id` の所属に関する論点（r4 で要検討）
- `product_sku_stock_transactions.created_by_user_id` は管理画面ユーザ（`users.id`）を想定して設計されている可能性がある
- 一方、通常購入の `sale` 記録は注文者（`market_customers.id`）が操作主体となる
- **対応方針**: Step 0 で既存 `product_sku_stock_transactions` のスキーマと運用を確認したうえで、管理者と会員のどちらの ID を入れるか・両方扱えるよう拡張するか・別カラム化するかを決定する

### 既存 `product_sku_stock_transactions` のテーブル仕様確認（Step 0 のタスク）
- 既存カラムの確認（id / sku_id / quantity / transaction_type / reference_type / reference_id / created_by_user_id / created_at 等が揃っているか）
- 不足カラムがあれば Step A で追加マイグレーション
- `product_id × movement_type` × 符号 CHECK 制約が既存にあるか確認、必要なら Step A で追加

---

# 配送ステータス遷移ルール

## ステータス（マスタに統一）
PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED

phase14 / phase15 ではキャンセル / 配達失敗 / 再配達は扱わない（スコープ外）。

## 遷移可否（Service 層でガード）

| 現在 → 次 | PENDING | SHIPPED | DELIVERED | RETURN_REQUESTED | RETURNED |
|-----------|---------|---------|-----------|------------------|----------|
| PENDING | - | ✅ | ❌ | ❌ | ❌ |
| SHIPPED | ❌ | - | ✅ | ❌ | ❌ |
| DELIVERED | ❌ | ❌ | - | ✅ | ❌ |
| RETURN_REQUESTED | ❌ | ❌ | ❌ | - | ✅ |
| RETURNED | ❌ | ❌ | ❌ | ❌ | - |

巻き戻しは Service 層で例外を投げて拒否。`RETURN_REQUESTED` への遷移は phase14 `sales_return.status = REQUESTED` と連動。

---

# 技術検討事項（r4 改訂）

- 予約ステータスは **リアルタイム判定**（バッチ不要）。判定の基準時刻は **JST 0:00**。サマータイム・海外展開は現時点でスコープ外
- 売上集計のインデックス：sales_date / sku_id / user_id / payment_method_id / shipping_method_id
- 決済インターフェースは将来の本番決済APIに差し替え可能な構造
- `payment_id` は模擬決済期は UUID v7。**採番ライブラリは Step A 実装時に License (Apache-2.0/MIT 推奨) と GitHub Star・最新リリース日で判断**
- 配送ステータスはマスタ化済。CANCELED / DELIVERY_FAILED / RESCHEDULED の機能対応は将来 phase21 配送オペレーション拡張（仮称）
- **在庫の同時実行制御は既存の `@Version` 楽観ロック**（r3 で要求した悲観ロック切替は r4 で取り下げ）
- **予約購入の在庫減算は出荷時（`SHIPPED` 遷移時）。出荷時に在庫不足なら例外＋PENDING 維持**
- **`product_sku_stock_transactions` の `transaction_type` × `quantity` 符号は既存制約を踏襲**（Step 0 で既存制約の有無を確認）
- **将来課題：** 予約上限（`product_skus.preorder_max`）による超過受付防止
- **将来課題：** 出荷分割（`deliveries` 1:N 化）
- **将来課題：** `return_reason_codes` マスタ
- **将来課題：** `operation_logs.comment` のフルテキスト検索基盤
- **将来課題：** `operation_logs.reason_code` カラム追加
- **将来課題：** 海外展開時のロケール別タイムゾーン判定
- **将来課題：** `Product.stock` 死カラムの棚卸し・DROP（フェーズ10で SKU 在庫が正本になって以降、未使用の可能性が高い）
- **将来課題：** 倉庫マスタ（`warehouses`）導入。本フェーズでは単一倉庫前提で SKU 在庫を扱う

---

# TDDテストケース（r4 で SKU 化）

## Amazia Core / JUnit

### 正常系
- 売上登録・更新・取得（`sku_id` 起点）
- 集計処理（年/月/日 + 商品別/SKU別/ユーザ別/決済方法別/配送方法別/販売数量集計）
- 操作履歴の記録（`screen_name` / `api_name` を含む）
- 予約ステータス判定（境界値：公開日＝今日、発売日＝今日。**JST 0:00 基準**。在庫参照は `product_sku_stocks` を SKU SUM 集計）
- 注文確定フローが同一トランザクションで完結する
- 注文確定時、`market_customers.postal_code` / `address` から `address` テーブルへスナップショットが新規 INSERT され、`sales.shipping_address_id` に紐付く
- `is_preorder=true` のとき注文時は在庫減算されない。**出荷時（`SHIPPED` 遷移時）に `product_sku_stocks.quantity` から減算される**
- `is_preorder=false` の通常購入は注文確定時に減算され、出荷時には再減算されない
- `payment_id` の `UNIQUE` 制約で重複採番が拒否される
- **`payment_id` UNIQUE 違反時、user_id/sku_id/quantity/amount すべて一致なら既存 sales が返される（真の冪等）**
- 過去注文の `sales.shipping_address_id` は会員が後で住所変更しても変わらない（スナップショット性）
- `sales.amount == product_sku_prices.price × sales.quantity` が成立する
- **予約購入の `sale` 記録の `created_by_user_id` が出荷操作の管理者である**
- **通常購入の `sale` 記録の `created_by_user_id` が注文者である**

### 異常系
- 通常購入で在庫切れの注文を予備バリデーションで拒否
- ロック取得後の在庫再確認で在庫不足を検知し例外を投げる（`@Version` 楽観ロックでの OptimisticLockException 含む）
- `address.user_id != order_request.user_id` の住所を指定した場合に拒否
- `sales.shipping_method_id` がマスタに存在しない場合に拒否
- `sales.payment_method_id` がマスタに存在しない場合に拒否
- `sales.sku_id` の SKU が `status != ACTIVE` の場合に拒否
- `shipping_status_id` が未対応ステータスの場合に Service 層で拒否
- `sales.quantity <= 0` を Service 層・`CHECK` 制約で拒否
- `sales_return.quantity <= 0` を Service 層・`CHECK` 制約で拒否
- **`payment_id` UNIQUE 違反かつ user_id 等の他項目が一致しない場合、エラーログ記録 + 例外**

### 予約購入の出荷時減算 異常系
- **出荷時に `product_sku_stocks.quantity < sales.quantity` の場合、例外を投げて `deliveries.shipping_status_id` は `PENDING` のまま据え置く**
- 出荷時減算後すぐに返品申請が来た場合、`product_sku_stock_transactions` に `sale`（負数）と `return`（正数）の両方が時系列で記録され、`SUM(quantity)` が正しく合算される
- 予約購入の出荷を試みたが `deliveries.shipping_status_id` の遷移ガードで拒否された場合、在庫減算は実行されない
- 予約購入の出荷時にトランザクション内で例外を投げた場合、`deliveries` 状態遷移と `product_sku_stocks` 更新がともにロールバックされる

### product_sku_stock_transactions の検証
- 注文確定時に `transaction_type='sale', quantity=-n, reference_type='sales'` が記録される
- **予約購入の出荷時に `transaction_type='sale', quantity=-n, reference_type='sales'` が記録される（`created_by_user_id` は出荷操作の管理者）**
- 返品復元時に `transaction_type='return', quantity=+n, reference_type='sales_return'` が記録される
- 各 transaction の `reference_type` / `reference_id` から元レコードを正しく逆引きできる
- `product_sku_stocks.quantity` の更新と transaction INSERT が同一トランザクションでロールバックされる
- `SUM(quantity GROUP BY sku_id)` が現在の `product_sku_stocks.quantity` と一致する

## Amazia Market / Vitest（フロントエンド）
- カート追加・削除
- セッション → DB カートマージ
- 模擬決済成功
- 模擬決済エラー（バリデーション）
- 配送情報未入力エラー
- 注文確定リクエストに `shipping_method_id` が含まれない場合のエラー
- 注文確定リクエストに `payment_method_id` が含まれない場合のエラー
- 注文確定リクエストの `quantity` が 0 以下の場合のエラー
- 購入履歴表示（数量・配送方法・色・サイズを含む）
- 予約ステータス表示（境界値は JST 0:00 基準）
- **未ログイン状態で購入ボタン押下 → /login にリダイレクト**
- **ログイン成功後、`location.state.redirectTo` があればそこへ遷移**

## Amazia Console / PHPUnit
- Console の配送先変更画面で過去住所（`is_active=false`）が選択肢に表示されない（UI フィルタ）
- API 直叩きで `is_active=false` の住所を指定した場合は通る（オーナー検証のみ）
- 売上管理画面で SKU 単位の集計（色別・サイズ別）が表示できる

### テスト値の config 経由化
- `payment_methods` / `shipping_methods` / `shipping_statuses` のマスタ ID
- `product_sku_stock_transactions` の `transaction_type` enum 値
- 予約ステータス判定のタイムゾーン（`Asia/Tokyo`）

---

# 🔧 追加で検討するとさらに良くなる点

## 1. 予約ステータス判定の「優先順位ルール」明文化

### ■ 予約ステータス判定の優先順位（上から順に適用）
1. **公開前（公開日 > 今日）** → NOT_PUBLIC
2. **予約開始日が設定されており、今日 < 予約開始日** → PRE_ORDER_NOT_STARTED
3. **公開済 & 発売日前（公開日 ≤ 今日 < 発売日）** → PRE_ORDER
4. **発売日以降（今日 ≥ 発売日）**
   - 在庫あり → ON_SALE
   - 在庫なし → BACK_ORDER or SOLD_OUT（予約可否で分岐）
5. **在庫なし & 予約不可** → SOLD_OUT

→ 在庫参照先は `product_sku_stocks`（SKU SUM）。判定基準時刻は JST 0:00。

---

## 2. address テーブルの運用方針（r4 で再整理）
- 注文確定時に常に新規 INSERT（スナップショット）
- 会員住所の更新は `market_customers` 側で完結（`address` テーブルは触らない）
- Console UI では `is_active=false` を選択肢に出さない（S14-10）
- 住所オーナー検証は `address.user_id == sales.user_id`（= `market_customers.id`）

---

## 3. 返品ワークフローの通知設計

### ■ 通知タイミング
| タイミング | 通知先 | 内容 |
|------------|---------|------|
| ユーザが返品申請 | 管理者 | 返品申請の発生 |
| 管理者が承認 | ユーザ | 承認通知（返送手順など） |
| 管理者が却下 | ユーザ | 却下理由の通知 |
| 返金完了 | ユーザ | 返金完了通知 |

`sales_return.notified_user` / `notified_admin` で送信状態を管理。

---

# phase15 への要請事項（r4 で更新）

phase15 r4 → r5 が長期シグネチャ・SKU 在庫モデルで実装着手できるよう、phase14 r4 で確定した以下を phase15 側に反映する：

| 項目 | 内容 |
|------|------|
| 実装段取り | phase14 r4 Step 0 + Step A 完了後に phase15 r5 として着手 |
| `sales.shipping_method_id` 追加完了 | phase15 の `DeliveryCreationService.createForSales` を **長期シグネチャ `(sales_id)` で実装可** |
| 注文確定フローの確定 | phase15 は協調仕様のみで OK |
| **予約購入の在庫減算は出荷時** | phase15 r5 で `deliveries` 状態遷移 Service（PENDING → SHIPPED）に「`sales.is_preorder == true` の場合のみ在庫減算フックを追加」する設計変更を反映 |
| **出荷時の在庫不足挙動** | **方針 (A) 例外＋PENDING 維持**。phase15 r5 のステータス遷移 Service にロック後在庫再確認とエラー時 `PENDING` 維持の処理を実装 |
| **予約購入 `sale` の `created_by_user_id`** | **出荷操作を実行した管理者の `user_id`** を `product_sku_stock_transactions.created_by_user_id` に記録 |
| **在庫モデルは既存 `product_sku_stocks` を正本として活用** | phase15 で新設予定だった `inventories` / `inbounds` / `warehouses` / `InventorySyncService` は **新設しない**。既存 `product_sku_stocks` および `ReceiveProductSkuStockService` を活用 |
| `shipping_statuses` マスタ拡張 | CANCELED / DELIVERY_FAILED / RESCHEDULED が利用可。マスタ存在 ≠ 入力許容 |
| `product_sku_stock_transactions` 利用 | phase15 の入荷登録 Service は既存 `product_sku_stock_transactions` への INSERT を踏襲 |
| 共通命名規約 | `docs/ai_context/operation_logs_naming.md` を本書 r1 で参照宣言済み |

phase15 自体の改訂版（r5）作成は phase14 r4 完了後に着手する想定。本実装計画には含まれない。

---

# レビューコメント対応サマリ（r1 → r2 → r3 → r4）

## r1 で対応済み（再掲）
| ID | 由来 | 対応 |
|----|------|------|
| P14-1 〜 P14-3 | phase15 r3 / r4 起点（🔴 必須） | r1 で完了 |
| P14-4 〜 P14-8 | phase15 r3 / r4 起点（🟡 推奨） | r1 で完了 |
| P14-9 〜 P14-17 | phase15 起点 + phase14 単独（🟢 軽微） | r1 で完了 |

## r2 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| Q14-1 〜 Q14-14 | 🔴 / 🟡 / 🟢 | r2 で完了 |

## r3 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| S14-1 〜 S14-12 | 🟡 / 🟢 | r3 で完了 |

## r4 で新規対応
| 観点 | 対応 |
|------|------|
| **既存 SKU 在庫モデルとの整合** | フェーズ10で実装済みの `product_sku_stocks` / `product_sku_stock_transactions` / `ReceiveProductSkuStockService` を在庫の正本として採用。`inventories` / `inbounds` / `inventory_movements` / `warehouses` の新設を取り下げ |
| **在庫キーの SKU 化** | `sales.product_id` → `sales.sku_id`（FK to `product_skus.id`）に変更。集計時は `product_skus.product_id` 経由で商品単位集計、SKU 単位集計（色・サイズ別）も追加 |
| **同時実行制御** | r3 で要求した悲観ロック（`SELECT ... FOR UPDATE`）は取り下げ、既存の `@Version` 楽観ロックを維持 |
| **`market_customers` 参照の確定** | フェーズ13で実装済みの `market_customers` を `sales.user_id` の参照先として確定。`users`（管理者）と区別 |
| **配送先住所の運用** | 会員住所（`market_customers.postal_code` / `address`）と注文時配送先住所（`address` テーブル）を完全に分離。注文ごとに `address` へ新規 INSERT してスナップショット化 |
| **payment_method の運用** | `payment_methods` マスタを新設、`sales.payment_method_id` で個別選択。`market_customers.payment_method` は会員ごとの既定値として残置（B-1） |
| **Step 構造の縮小** | r3 の Step A〜E のうち Step C / D / E を削除。Step 0 / A / B の3段階構成に縮小 |
| **将来課題への移行** | r3 の機能フラグ `inventory.read_source` / `products.stock` DROP / 完全移行マイグレーションは将来課題に移行（または不要） |
