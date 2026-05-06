# フェーズ14：購入機能（改訂版 r2）

## ステータス
🔲 未着手

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 初版日不明（git 履歴未取得） | 初稿（フェーズ14購入機能の基本設計） |
| 改訂版（r0 相当） | 改訂日不明（本書 r1 化時点で日付未取得） | 分析画面連携方針・予約ステータスロジックの一元化・配送ステータスのマスタ化・返品ワークフロー詳細化など。 |
| r1 | 2026-05-06 | phase15 r3／r4 から積み上がった要請（P14-1 〜 P14-12）と phase14 単独観点（P14-13 〜 P14-17）を反映。在庫モデル完全移行と並行運用期のフック・注文確定フロー確定・`sales.shipping_method_id` 追加・`shipping_statuses` 拡張・`inventory_movements` 新設・住所オーナー検証・悲観ロック方針・改訂履歴と表記揺れの整形など。 |
| r2 | 2026-05-06 | 再レビューコメント Q14-1 〜 Q14-14 を反映。phase14 r1 と phase15 r3／r4 の実装順序（Step A 〜 D）を冒頭に明記、`sales.quantity` / `sales_return.quantity` 追加の前提（本番リリース前スキーマ確定）を宣言、予約購入の在庫減算タイミングを「出荷時減算」に確定、`inventory_movements.quantity` を符号付きに変更し `direction` 廃止、住所オーナー検証の `is_active` 扱い明記、`payment_id` UNIQUE 違反時の冪等処理、完全移行 Step 2 の機能フラグ段階化、その他軽微指摘の反映。 |

## 範囲
- Amazia Console
- Amazia Market
- Amazia Core
- DB設計（新規テーブル・マスタ化含む）
- **在庫モデルの完全移行（`products.stock` 廃止 → `inventories` 一本化）**（P14-1 対応：r1 で本フェーズに取り込み）
- **注文確定フローの確定**（P14-2 対応：r1 で擬似コードレベルまで確定）
- **予約購入の在庫減算（出荷時減算）の Service 化**（Q14-3 対応：r2 で確定）

## 前提（Q14-2 対応：r2 で明記）

本書および phase15 一連の設計は **Amazia の本番リリース前スキーマ確定**を前提とする。

- `sales` / `sales_return` への `quantity` カラム追加（後述）に伴う既存レコードのデータ移行は不要。
- 並行運用開始前に `inventories` を `products.stock` から複製するマイグレーション（phase15 r4 RRRR-1）も同様に「初期データ複製のみ」で済む。
- 既存ユーザの売上・在庫履歴を引き継ぐ移行マイグレーションは本書のスコープ外（運用後にスキーマ変更が必要になった場合は別フェーズで対応）。

---

# 実装段取り（Step A 〜 D／Q14-1 対応：r2 で冒頭明記）

phase14 r2 と phase15 r4 は記述上互いを前提に置く部分があるため、循環依存に見える。実装は以下の Step A → D の順で行うことで、依存関係を解消できる。

| Step | 対象 | 主な作業 | 完了条件 |
|------|------|---------|---------|
| **Step A** | phase14 r2 のスキーマ変更だけ先行 | `sales.shipping_method_id` 追加 / `sales.quantity` 追加 / `sales.payment_id` UNIQUE 制約 / `sales_return.quantity` 追加 / `shipping_statuses` マスタ拡張（CANCELED 等）/ `operation_logs.screen_name` / `api_name` 追加 / `address.is_active` 追加 | スキーマだけが入った状態。Service・UI は未着手で OK |
| **Step B** | phase15 r4 実装 | `warehouses`（ダミー1行）/ `inventories` / `inbounds` / `shipping_methods` / `deliveries` テーブル作成 / `InventorySyncService`（並行運用フック）作成 / 入荷・配送 Service 実装 / 注文確定 Service の在庫減算は `products.stock` を主、`InventorySyncService` を従 / `DeliveryCreationService.createForSales(sales_id)`（**長期シグネチャ**：Step A で `sales.shipping_method_id` 追加済のため過渡期シグネチャは不要） | 並行運用フェーズで Amazia が動作する状態。`products.stock == SUM(inventories.quantity)` の不変条件が常に成立 |
| **Step C** | phase14 r2 の残り（在庫モデル完全移行） | `inventory_movements` 作成 / 完全移行マイグレーション Step 1 〜 4 実施（後述） / `InventorySyncService` 削除 / 読み取り側を `inventories` 参照に切替（機能フラグで段階化／Q14-8 対応） | `products.stock` カラムが DROP され、すべての読み書きが `inventories` を正本とする状態 |
| **Step D** | phase15 r5（フック削除のクリーンアップ） | `InventorySyncService` 呼び出し箇所の削除確認 / phase15 設計書の改訂履歴に「Step C 完了で並行運用フックを撤去」を反映 / コード検査と統合テスト | phase15 設計書 r5 と実装が整合し、過渡期コードがリポジトリに残らない状態 |

実装担当者は **必ず Step A → B → C → D の順序**で進める。Step A の前に Step B 以降を着手すると、phase15 r4 が過渡期シグネチャ `(sales_id, shipping_method_id)` を抱えることになり、後続のリファクタが発生する。

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

### ■ 予約ステータスの在庫参照先（P14-1 / Q14-8 対応）
完全移行後の `BACK_ORDER` / `SOLD_OUT` 判定は **`inventories.quantity`** を参照する。並行運用期は `products.stock` 参照を維持し、Step C 中の機能フラグ `inventory.read_source` で段階的に切替（後述「在庫モデル完全移行マイグレーション仕様」参照）。

### ■ タイムゾーン方針（P14-17 / Q14-12 対応）
「今日」「公開日」「発売日」の判定はすべて **JST 0:00 基準**で行う。サーバ／DB のタイムゾーン設定（`Asia/Tokyo`）と整合させ、テストでも JST 基準で境界値を組む。**サマータイム・海外展開は現時点でスコープ外**（日本国内のみのサービスを前提）。将来海外展開する場合はユーザロケールに応じた境界判定が必要になる旨を技術検討事項に残す。

→ Market / Console はこのステータスを参照して UI 表示を統一。

---

# 配送ステータスのマスタ化（Enum）

`shipping_statuses` マスタは以下に拡張する（P14-4 対応）。**機能としての対応フェーズ名を明記**（Q14-4 対応）。

| ステータス | 説明 | 追加版 | 機能対応フェーズ |
|-----------|------|-------|----------------|
| PENDING | 配送準備中 | 既存 | phase15 |
| SHIPPED | 配送済 | 既存 | phase15 |
| DELIVERED | 配送完了 | 既存 | phase15 |
| RETURN_REQUESTED | 返品申請中 | 既存 | phase14 / phase15 |
| RETURNED | 返品完了 | 既存 | phase14 / phase15 |
| CANCELED | 発送前キャンセル | r1 追加 | **将来 phase21 配送オペレーション拡張**（仮称） |
| DELIVERY_FAILED | 配達失敗・持ち戻り | r1 追加 | **将来 phase21 配送オペレーション拡張**（仮称） |
| RESCHEDULED | 再配達手配中 | r1 追加 | **将来 phase21 配送オペレーション拡張**（仮称） |

### マスタ存在 ≠ 入力許容（Q14-4 対応：r2 で明記）
phase14 r2 / phase15 r4 の Service 層では、**未対応ステータス（CANCELED / DELIVERY_FAILED / RESCHEDULED）への遷移リクエストはバリデーションで拒否する**。マスタにレコードが存在することは「将来の拡張余地」を意味し、現フェーズで API 入力として許容することを意味しない。`shipping_statuses` マスタの行ごとに `is_enabled` フラグを持たせるか、Service 層の許容ステータスリスト（config）で制御するかは Step A 実装時に決定。

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
- **販売数量集計**（r2 で追加／`sales.quantity` 追加に伴う／Q14-10）

→ 分析画面との役割分担
- Console：日常運用向けの軽量集計
- 分析画面：高度な分析（BIツール連携前提）

### ■ 表示項目
- ユーザ名
- 購入商品
- **数量（r2 追加）**
- 金額（`amount = product.price × quantity` の整合性が前提／Q14-10）
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
- **返品数量（`sales_return.quantity` で管理／r1 追加）**
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

`screen_name` / `api_name` の命名規約は **`docs/ai_context/operation_logs_naming.md` に従う**（P14-10 対応）。

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

### ■ payment_id の採番方式（P14-13 / Q14-7 / Q14-14 対応）
- **採番ロジックの所在**：Amazia Core の `PaymentService`（模擬決済時のみ）。本番決済 API 接続後は外部発行値をそのまま受領する。
- **形式**：模擬決済期は **UUID v7**（時系列ソート性のため）。本番決済 API 接続後は外部システム由来（最大長想定で `VARCHAR(100)` を確保済み）。
- **採番ライブラリ依存（Q14-14 対応：r2 で明記）**：
  - Java：標準 `java.util.UUID` は v4 のみ。v7 は外部ライブラリ（`uuid-creator` 等）が必要。`build.gradle` で依存追加。
  - PHP：`ramsey/uuid` v4.7+ が v7 対応。`composer.json` で依存指定。
- **重複チェックと冪等処理（Q14-7 対応：r2 で明記）**：
  - `sales.payment_id` を `UNIQUE` 制約で担保。
  - **本番決済の二重送信などで同じ `payment_id` が再到来した場合、UNIQUE 違反を捕捉して既存 `sales` を返す（冪等処理）**。新規 INSERT は試みない。
  - これにより「決済は成功したが sales 作成に失敗 → ユーザに二重課金」を防止。

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
- **数量（r2 追加）**
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

# 注文確定フロー（P14-2 / Q14-3 / Q14-6 対応：r2 で確定）

注文確定時の処理は以下のフローで Service 層に集約する。バリデーション・在庫減算・`sales` INSERT・`deliveries` 生成は**同一トランザクション**で完結させる。

```
OrderConfirmationService.confirm(order_request):

  1. validateOrder(order_request)
     - is_preorder == false かつ getStock(product_id) <= 0 なら拒否
     - sales.shipping_address_id について
         address.user_id == order_request.user_id を Service 層で検証
         （is_active は問わない／Q14-6 対応：過去住所も「自分のもの」なら参照可）
     - sales.shipping_method_id が shipping_methods マスタに存在し、
       かつ「許容ステータス」リストに含まれることを検証（Q14-4）

  2. begin transaction

  3. 通常購入の在庫減算（is_preorder == false のみ）
     ※ 同時実行制御：SELECT ... FOR UPDATE で対象行をロック
     - 並行運用期：products.stock -= order_quantity
                  + InventorySyncService.applyDelta(product_id, 1, -order_quantity)
     - 完全移行後：inventories.quantity -= order_quantity（直接）
     - 予約購入（is_preorder == true）はこの時点では減算しない（Q14-3）

  4. INSERT sales
     （quantity, shipping_address_id, shipping_method_id,
       payment_method_id, payment_id, is_preorder, ...）

     ※ payment_id UNIQUE 違反時は既存 sales を返す冪等処理（Q14-7）

  5. DeliveryCreationService.createForSales(sales.id)
     ※ Step A 完了後の長期シグネチャ。Step A 未完了で phase15 を先行実装する場合は
       過渡期シグネチャ createForSales(sales.id, shipping_method_id) を使用。
     → INSERT deliveries (
          sales_id,
          shipping_address_id = sales.shipping_address_id,
          shipping_method_id  = sales.shipping_method_id,
          shipping_status_id  = PENDING,
          scheduled_date      = DeliveryScheduleService.calculate(...)
        )

  6. commit
```

## 予約購入の在庫減算タイミング（Q14-3 対応：r2 で「出荷時減算」に確定）

予約購入（`sales.is_preorder = true`）の在庫減算タイミングは **(C) 出荷時（`deliveries.shipping_status_id = SHIPPED` に遷移した時点）** に確定する。

| タイミング | 採否 | 理由 |
|-----------|------|------|
| (A) 発売日到来時にバッチで一括減算 | ✕ | バッチが必要・障害時の再実行が複雑・在庫減算と発送が分離して整合性が取りにくい |
| (B) 注文確定時（通常購入と同時に減算） | ✕ | 在庫がマイナスになる可能性を許容することになり、`CHECK (quantity >= 0)` 制約と矛盾 |
| **(C) 出荷時（`SHIPPED` 遷移時）に減算** | **採用** | `deliveries` 状態遷移と Service 内で完結。発送＝在庫消費という現実の業務フローと整合 |

### 実装方針
- `deliveries` の `shipping_status_id` を `PENDING → SHIPPED` に遷移する Service（phase15）に、**`sales.is_preorder == true` の場合のみ在庫減算フックを追加**する。
- 通常購入（`is_preorder == false`）はすでに注文確定時に減算済みなので、出荷時に再減算しない（`is_preorder` で分岐）。
- 在庫減算は同一トランザクション内で `inventories.quantity -= sales.quantity` を実行（並行運用期は `products.stock` も同時減算）。

### 再構築 SQL の修正（Q14-3 対応：予約購入の出荷有無を反映）

完全移行マイグレーションの再構築 SQL は、予約購入のうち**出荷済みのもののみ**を販売減算に含めるよう修正する：

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
  LEFT JOIN deliveries d ON d.sales_id = s.id
  WHERE s.product_id = i.product_id
    AND (
      s.is_preorder = false                              -- 通常購入は注文時減算
      OR (s.is_preorder = true AND d.shipped_date IS NOT NULL)  -- 予約購入は出荷時減算
    )
) + (
  SELECT COALESCE(SUM(sr.quantity), 0)
  FROM sales_return sr JOIN sales s ON sr.sales_id = s.id
  WHERE s.product_id = i.product_id AND sr.status = 'REFUNDED'
);
```

## 在庫の同時実行制御方針（P14-8 対応）
- 在庫減算（`products.stock` および `inventories.quantity`）は **`SELECT ... FOR UPDATE`（悲観ロック）**で対象行を取得してから減算する。
- 並行運用期は両テーブルの行を同一トランザクション内でロック取得する。
- 完全移行後は `inventories.quantity` のみ。
- DB 制約 `CHECK (inventories.quantity >= 0)` で負数化を防止（phase15 r4 RRR-8 と整合）。

## シグネチャ移行の整理（P14-3 / Q14-1 対応）

| Step / 期間 | `DeliveryCreationService.createForSales` のシグネチャ | `shipping_method_id` の出所 |
|------|---------------------------------------------------|-------------------------|
| Step A 未完 + Step B 単独実装した場合（推奨しない） | `createForSales(sales_id, shipping_method_id)` | API リクエスト由来（`sales` には保存されていない） |
| **Step A 完了後（推奨）／Step B 以降** | `createForSales(sales_id)` | `sales.shipping_method_id` |

phase14 r2 では Step A → B → C → D の順序を厳守することで、phase15 r4 が**最初から長期シグネチャで実装可能**となる。

---

# 在庫モデル完全移行（P14-1 / Q14-8 対応）

## 設計判断
- phase15 r4（Step B）で `inventories` の並行運用と `InventorySyncService` が確立された前提で、**phase14 r2 の Step C で `products.stock` を廃止し `inventories` に一本化**する。
- 並行運用は Step B 〜 Step C 中の暫定状態であり、本フェーズでクローズする。

## 完全移行の段取り（マイグレーション仕様／P14-9 対応）

```
Step 1: 並行運用整合性チェック
  - SUM(inventories.quantity GROUP BY product_id) と products.stock を全件比較
  - 乖離があれば、販売・返品履歴から再構築（前掲「再構築 SQL」を実行）
  - チェックが通るまで Step 2 以降に進まない

Step 2: 読み取り側を inventories 参照に切替（機能フラグで段階化／Q14-8 対応）
  - config/app/Inventory.php に機能フラグ inventory.read_source を導入
    値： products_stock | inventories
  - 切替対象の各機能を順番に inventories 参照へ切替
    (a) Market の在庫表示
    (b) 配送予定日計算（DeliveryScheduleService.calculate）
    (c) 予約ステータス判定（BACK_ORDER / SOLD_OUT）
    (d) Console の在庫表示（あれば）
  - 各切替後に並行運用整合性テストでデグレを検出
  - すべての読み取り箇所が inventories 参照になったら次へ

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

## 機能フラグ（Q14-8 対応：r2 で明記）

| キー | 値域 | 既定値 | 用途 |
|------|------|-------|------|
| `inventory.read_source` | `products_stock` / `inventories` | `products_stock`（Step B〜C 序盤） | 在庫値の読み取り正本を切替 |
| `inventory.read_source.market_display` | 同上または継承 | 親キーから継承 | Market 在庫表示のみ先行切替したい場合 |
| `inventory.read_source.delivery_schedule` | 同上または継承 | 親キーから継承 | 配送予定日計算のみ先行切替 |
| `inventory.read_source.preorder_status` | 同上または継承 | 親キーから継承 | 予約ステータス判定のみ先行切替 |

`config('inventory.read_source')` 等で取得（規約 3-1 / 4-1）。テストもこれを `config()` 経由でアサート（規約 4-1）。

## 完全移行後の構造
- 販売・予約ステータス判定・返品復元・入荷のすべてが `inventories` を読み書き正本とする。
- `inventories` の同時実行制御は `SELECT ... FOR UPDATE`（悲観ロック）。
- 倉庫マスタ（phase15 r3 で導入）はダミー1行のまま。複数倉庫対応は別フェーズで。

---

# inventory_movements テーブル新設（P14-6 / Q14-5 / Q14-11 対応）

P14-1 の完全移行と密接に関連するため、phase14 r2 で同時設計する。完全移行後に履歴を再構築するのは現実的ではないため、**完全移行と同時に履歴記録を開始**する。

## inventory_movements テーブル（新規：在庫増減ログの一元化）

r2 で `direction` カラムを廃止し、`quantity` を符号付きにする（Q14-5 対応：方針 (C) 採用）。

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | NOT NULL | 倉庫ID（`warehouses.id` への FK／DEFAULT 1） |
| movement_type | VARCHAR(50) | NOT NULL | 増減種別（`inbound` / `sale` / `cancel` / `return` / `adjustment`） |
| **quantity** | **INT** | **NOT NULL** | **増減量（符号付き：正数=増加、負数=減少）。`CHECK (quantity != 0)`。`direction` カラムは廃止／Q14-5** |
| reference_type | VARCHAR(50) | NULL | 参照元種別（`inbounds` / `sales` / `sales_return` / `manual_adjustment`） |
| reference_id | BIGINT | NULL | 参照元レコードID |
| comment | TEXT | NULL | 棚卸補正等の自由記述 |
| created_by_user_id | BIGINT | NULL | 操作ユーザ（運用ルールは下記参照／Q14-11） |
| created_at | DATETIME | NOT NULL | 記録日時 |

### 命名注意（P14-6 / phase15 RRRR-9 対応）
- 他 ERP（Odoo / ERPNext 等）にも `inventory_movements` 同名テーブルが存在する。本テーブルの `movement_type` enum 値（`inbound` / `sale` / `cancel` / `return` / `adjustment`）は **Amazia 独自定義**であり、他 ERP の同名テーブルとは互換性を取らない方針。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_inventory_movements_product_id` | 商品別の在庫履歴取得 |
| `idx_inventory_movements_created_at` | 期間別集計 |
| `idx_inventory_movements_reference` | (`reference_type`, `reference_id`) 複合インデックス。元レコードからの逆引き |

### 記録タイミングと符号（Q14-5 対応：r2 で更新）
- 入荷登録：`movement_type='inbound', quantity=+inbound.quantity, reference_type='inbounds'`
- 販売（注文確定または出荷時）：`movement_type='sale', quantity=-sales.quantity, reference_type='sales'`
- キャンセル復元：`movement_type='cancel', quantity=+sales.quantity, reference_type='sales'`
- 返品復元：`movement_type='return', quantity=+sales_return.quantity, reference_type='sales_return'`
- 棚卸補正（将来）：`movement_type='adjustment', quantity=±n, reference_type='manual_adjustment'`

集計時は `SUM(quantity)` で在庫純増減が一発で算出できる。

### `created_by_user_id` の運用ルール（Q14-11 対応：r2 で明記）

| `movement_type` | `created_by_user_id` の値 | 理由 |
|-----------------|-------------------------|------|
| `sale` | `sales.user_id`（注文者） | ユーザの注文操作に伴う在庫減算 |
| `cancel` | キャンセルを実行した管理者の `user_id`（ユーザキャンセルなら注文者） | 操作主体を保持 |
| `return` | 返品を承認した管理者の `user_id` | 承認者を保持 |
| `inbound` | 入荷登録した管理者の `user_id` | 操作主体を保持 |
| `adjustment` | 棚卸補正を実行した管理者の `user_id` | 監査の起点 |
| バッチ自動処理（将来） | NULL | 自動処理は人間の操作主体がないため |

監査時に「誰の行動が在庫を動かしたか」が辿れるようにする。

`inventories.quantity` の更新と `inventory_movements` への INSERT は同一トランザクションで実行する。

---

# DB設計（改訂）

> **テーブル所属の前提（Q14-13 対応：r2 で明記）**
> - `inbounds` / `inventories` / `warehouses` / `shipping_methods` / `deliveries` は **phase15 r4 で定義済み**。本書ではこれらを前提とし、**`sales` / `address` / `operation_logs` / `sales_return` の改訂と `inventory_movements` の新設**を扱う。
> - phase15 で定義されたテーブルへの参照（FK・SQL内 `FROM` 句など）は本書で利用するが、定義そのものは phase15 r4 を参照のこと。

## sales テーブル（r1 改訂版・r2 で前提明記）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| user_id | BIGINT | NOT NULL | 購入ユーザID |
| product_id | BIGINT | NOT NULL | 商品ID |
| quantity | INT | NOT NULL | 購入数量（**r1 で追加** + **`CHECK (quantity > 0)`**／Q14-2：本番リリース前スキーマ確定前提により `NOT NULL DEFAULT 1` で問題なし） |
| amount | INT | NOT NULL | 金額（`amount = product.price × quantity` の整合性を Service で担保／Q14-10） |
| payment_method_id | BIGINT | NOT NULL | 決済方法マスタID |
| **shipping_method_id** | BIGINT | NOT NULL | **配送方法マスタID（r1 で新規追加／P14-3 対応）** |
| shipping_address_id | BIGINT | NOT NULL | 住所マスタID |
| shipping_date | DATE | NULL | 配送日 |
| sales_date | DATE | NOT NULL | 売上日 |
| shipping_status_id | BIGINT | NOT NULL | 配送ステータスマスタID |
| payment_id | VARCHAR(100) | NOT NULL | 決済ID（**`UNIQUE` 制約**／P14-13・冪等処理は Service 層で／Q14-7） |
| is_preorder | BOOLEAN | NOT NULL | 予約購入か |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `shipping_method_id` は `shipping_methods.id`（phase15 で定義）への FK。
- `shipping_address_id` は `address.id` への FK。Service 層で「`order_request.user_id == address.user_id`」を強制（`is_active` は問わない／Q14-6）。
- `payment_id` は `UNIQUE` 制約。違反時は冪等処理として既存 `sales` を返す（Q14-7）。

### インデックス方針（既存維持 + r1 追加）
| インデックス | 用途 |
|-------------|------|
| `idx_sales_sales_date` | 売上日別集計 |
| `idx_sales_product_id` | 商品別集計 |
| `idx_sales_user_id` | ユーザ別集計 |
| `idx_sales_payment_method_id` | 決済方法別集計 |
| `idx_sales_shipping_method_id` | **配送方法別集計（r1 で追加）** |

---

## address テーブル（既存維持 + Q14-6 明記）
住所の再利用・履歴管理のため分離。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | **所有ユーザ（P14-7：「住所の所有者」を表す。注文時の検証は `order_request.user_id == address.user_id` のみで、`is_active` は問わない／Q14-6）** |
| postal_code | VARCHAR(20) | 郵便番号 |
| prefecture | VARCHAR(50) | 都道府県 |
| city | VARCHAR(100) | 市区町村 |
| address_line | VARCHAR(255) | 住所 |
| building | VARCHAR(255) | 建物名 |
| is_active | BOOLEAN | 現在利用中の住所か（false なら過去住所） |
| created_at | DATETIME | 作成日時 |

### 住所編集時の運用（P14-15 / Q14-6 対応）
- ユーザーが住所を「編集」した場合、**旧住所は `is_active=false` に UPDATE して残し、新住所を `is_active=true` で INSERT する**（UPDATE で上書きしない）。
- `sales.shipping_address_id` は過去住所を参照し続け、購入時点の配送先が壊れない。
- ユーザの住所選択 UI では `is_active=true` のレコードのみを表示。
- **注文時の検証は `address.user_id == order_request.user_id` のみで、`is_active` は問わない**（過去住所だが自分のもの＝注文時に再利用可能）。

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

phase14 r2 では `sales.shipping_method_id` が本マスタを参照する FK となる。マスタ自体は phase15 で定義する（Q14-13）。

---

## shipping_statuses（マスタ／r1 で拡張）
前述の Enum を格納（CANCELED / DELIVERY_FAILED / RESCHEDULED を r1 で追加／P14-4 対応）。Service 層では未対応ステータスへの遷移リクエストをバリデーションで拒否（Q14-4）。

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
phase15 r4 で `update_scheduled_date` の comment プレフィックス方式が採用されており、現状の集計需要では十分。**先行追加せず、将来課題として残す**。集計需要が高まった段階で別途追加マイグレーション。

### `comment` カラムの検索性（P14-16 対応：技術検討事項）
本格運用時に検索性が問題になった場合、フルテキスト検索（MySQL の `FULLTEXT INDEX` 等）またはログ専用基盤（Elasticsearch / OpenSearch）への流し込みを検討。本フェーズでは技術検討事項としてのみ言及。

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
| quantity | INT | **返品数量（r1 追加 / `CHECK (quantity > 0)` / Q14-2：本番リリース前スキーマ確定前提のため `NOT NULL DEFAULT 1` で問題なし）** |
| notified_user | BOOLEAN | ユーザ通知済みか |
| notified_admin | BOOLEAN | 管理者通知済みか |
| created_at | DATETIME | 作成日時 |

### 返品理由分類（P14-14 対応：将来課題）
- 現状は `reason TEXT` フリーテキストのみ。
- 将来、返品分析（不良品 / イメージ違い / サイズ違い等）を扱う場合は `return_reason_codes` マスタと `sales_return.return_reason_code` の追加を検討。

---

# 技術検討事項（補強）

- 予約ステータスは **リアルタイム判定**（バッチ不要）。判定の基準時刻は **JST 0:00**（P14-17）。サマータイム・海外展開は現時点でスコープ外（Q14-12）
- 売上集計のインデックス
  - sales_date / product_id / user_id / payment_method_id / **shipping_method_id（r1 で追加）**
- 決済インターフェースは将来の本番決済APIに差し替え可能な構造にする
- `payment_id` は模擬決済期は UUID v7（時系列ソート性）。**採番ライブラリ依存：Java は `uuid-creator` 等、PHP は `ramsey/uuid` v4.7+**（Q14-14）
- 配送ステータスはマスタ化し、外部配送API連携も想定。CANCELED / DELIVERY_FAILED / RESCHEDULED の **マスタは r1 で先行追加。機能対応は将来 phase21 配送オペレーション拡張**（仮称）（Q14-4）
- 在庫の同時実行制御は **`SELECT ... FOR UPDATE`（悲観ロック）** に統一（P14-8 / phase15 RRR-8）
- **予約購入の在庫減算は出荷時（`SHIPPED` 遷移時）に実施**（Q14-3）
- **将来課題：** `return_reason_codes` マスタ（返品分析用／P14-14）
- **将来課題：** `operation_logs.comment` のフルテキスト検索基盤（P14-16）
- **将来課題：** `operation_logs.reason_code` カラム追加（集計需要が高まった段階／P14-5）
- **将来課題：** 海外展開時のロケール別タイムゾーン判定（Q14-12）

---

# TDDテストケース（異常系追加）

## Amazia Core / JUnit

### 正常系
- 売上登録・更新・取得
- 集計処理（年/月/日 + 商品別/ユーザ別/決済方法別/**配送方法別**／r1 追加 / **販売数量集計**／Q14-10）
- 操作履歴の記録
- 予約ステータス判定（境界値：公開日＝今日、発売日＝今日。**JST 0:00 基準**／P14-17）
- 注文確定フローが同一トランザクションで完結する（在庫減算 + sales INSERT + deliveries 生成）
- `is_preorder=true` のとき注文時は在庫減算されない。**出荷時（`SHIPPED` 遷移時）に在庫減算される**（Q14-3）
- `is_preorder=false` の通常購入は注文確定時に在庫減算され、出荷時には再減算されない（Q14-3）
- 完全移行後の `BACK_ORDER` / `SOLD_OUT` 判定が `inventories.quantity` 参照で動作する（機能フラグ `inventory.read_source = inventories` 時／Q14-8）
- `payment_id` の `UNIQUE` 制約で重複採番が拒否される（P14-13）
- **`payment_id` UNIQUE 違反時に既存 sales が返される（冪等処理）／Q14-7**
- 住所編集時、旧住所が `is_active=false` で残り、`sales.shipping_address_id` の参照が壊れない（P14-15）
- **過去住所（`is_active=false`）でも `address.user_id == order_request.user_id` なら注文時に参照可能／Q14-6**
- `sales.amount == product.price × sales.quantity` が成立する（Q14-10）

### 異常系
- 通常購入で在庫切れ（`stock <= 0`）の注文を拒否（P14-2）
- `address.user_id != order_request.user_id` の住所を指定した場合に拒否（Q14-6）
- `sales.shipping_method_id` が `shipping_methods` マスタに存在しない場合に拒否（P14-3）
- `shipping_status_id` が**未対応ステータス**（CANCELED / DELIVERY_FAILED / RESCHEDULED）の場合に Service 層で拒否（Q14-4）
- 在庫減算で `inventories.quantity < 0` になる更新を `CHECK` 制約で拒否
- 在庫減算が同時実行で競合した場合、`SELECT ... FOR UPDATE` のロック解放後に正しく直列化される（P14-8）
- `sales.quantity <= 0` を Service 層・`CHECK` 制約で拒否
- `sales_return.quantity <= 0` を Service 層・`CHECK` 制約で拒否
- 不正ステータス・不正日付

### 在庫モデル完全移行の検証（P14-1 / P14-9 / Q14-8 対応）
- 並行運用期の整合性チェック：任意時点で `products.stock(product_id) == SUM(inventories.quantity WHERE product_id=...)` が成立する（phase15 r4 と同じ不変条件）
- 完全移行マイグレーション直後、すべての商品で `inventories` の値が正しい（販売・入荷・返品履歴からの再構築結果と一致）。**予約購入は出荷済みのみ販売減算に含む**（Q14-3）
- 完全移行 Step 2 で機能フラグ `inventory.read_source` を切り替えた直後、機能ごとに正しい値が読み出される（Q14-8）
- 完全移行後、`products.stock` への参照が販売・予約ステータス判定・配送予定日計算のいずれにも残っていない（コード検査／統合テスト）
- `InventorySyncService` および呼び出し箇所が完全移行 Step 3 後にすべて削除されている（コード検査）

### inventory_movements の検証（P14-6 / Q14-5 / Q14-11 対応）
- 入荷登録時に `inventory_movements` に `movement_type='inbound', quantity=+n` の行が記録される
- 販売（注文確定または出荷時）に `inventory_movements` に `movement_type='sale', quantity=-n` の行が記録される
- 返品復元時に `inventory_movements` に `movement_type='return', quantity=+n` の行が記録される
- 各 `movement` の `reference_type` / `reference_id` から元レコードを正しく逆引きできる
- `inventories.quantity` の更新と `inventory_movements` INSERT が同一トランザクションでロールバックされる
- `created_by_user_id` が運用ルール（Q14-11）どおりに記録される（`sale` は注文者、`adjustment` は管理者、バッチは NULL 等）
- `SUM(quantity GROUP BY product_id)` が現在の `inventories.quantity` と一致する（履歴と現在値の整合性）

## Amazia Market / PHPUnit
- カート追加・削除
- セッション → DB カートマージ
- 模擬決済成功
- 模擬決済エラー（バリデーション）
- 配送情報未入力エラー
- 注文確定リクエストに `shipping_method_id` が含まれない場合のエラー（P14-3）
- 注文確定リクエストの `quantity` が 0 以下の場合のエラー（Q14-10）
- 購入履歴表示（数量・配送方法を含む）
- 予約ステータス表示（Core API 連携。境界値は JST 0:00 基準でテスト／P14-17）

### テスト値の config 経由化（phase15 r4 RRRR-8 / Q14-8 と整合）
規約 4-1「テスト内で URL や設定値をハードコードせず `config()` / `@Value` 経由で取得する」と整合させ、本書のテストでも以下の値を `config()` / `@Value` 経由で取得：

- `payment_methods` / `shipping_methods` / `shipping_statuses` のマスタ ID
- 並行運用のダミー倉庫 ID
- `inventory_movements` の `movement_type` enum 値
- 予約ステータス判定のタイムゾーン（`Asia/Tokyo`）
- 機能フラグ `inventory.read_source` の値（Q14-8）

---

# 🔧 追加で検討するとさらに良くなる点（既存改訂版から維持）

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

→ この優先順位を API 仕様として Core に固定。在庫参照先は **完全移行後は `inventories`**（P14-1）、判定基準時刻は **JST 0:00**（P14-17）。

---

## 2. address テーブルの履歴管理方針（P14-15 / Q14-6 と統合）
住所編集 = 論理削除＋新規 INSERT。注文時の住所オーナー検証は `is_active` を問わない。

---

## 3. 返品ワークフローの通知設計（軽微だが重要）

### ■ 通知タイミング
| タイミング | 通知先 | 内容 |
|------------|---------|------|
| ユーザが返品申請 | 管理者 | 返品申請の発生 |
| 管理者が承認 | ユーザ | 承認通知（返送手順など） |
| 管理者が却下 | ユーザ | 却下理由の通知 |
| 返金完了 | ユーザ | 返金完了通知 |

`sales_return.notified_user` / `notified_admin` で送信状態を管理。

---

# phase15 への要請事項（r2 で確定した事項）

phase15 r4 が長期シグネチャ・完全移行後の構造で実装着手できるよう、phase14 r2 で確定した以下を phase15 側に反映する：

| 項目 | 内容 |
|------|------|
| 実装段取り（Step A 〜 D） | phase15 r4 は **Step A 完了後に Step B として着手**する。これにより長期シグネチャ `createForSales(sales_id)` で実装可能（Q14-1） |
| `sales.shipping_method_id` 追加完了（Step A） | phase15 の `DeliveryCreationService.createForSales` を **長期シグネチャ `(sales_id)` で実装可**。過渡期シグネチャは不要 |
| 注文確定フローの確定 | phase15 は協調仕様のみで OK（本書の擬似コードに従う注文確定 Service が phase14 r2 で実装される） |
| **予約購入の在庫減算は出荷時** | phase15 r5 で `deliveries` 状態遷移 Service（PENDING → SHIPPED）に「`sales.is_preorder == true` の場合のみ在庫減算フックを追加」する設計変更を反映（Q14-3） |
| 在庫モデル完全移行 | phase15 の並行運用フック（`InventorySyncService`）は phase14 r2 完了時点（Step C 終了）で削除対象。phase15 r4 → r5（Step D）で削除 |
| `shipping_statuses` マスタ拡張 | CANCELED / DELIVERY_FAILED / RESCHEDULED が利用可。phase15 のステータス遷移表に追加するかは別途判断（マスタ存在 ≠ 入力許容／Q14-4） |
| `inventory_movements` 利用 | phase15 の入荷登録 Service は `inventory_movements` への INSERT も追加（同一トランザクション）。`quantity` は符号付き、`direction` カラムなし（Q14-5） |
| 機能フラグ `inventory.read_source` | phase15 の在庫参照箇所も機能フラグで切替可能に実装（Q14-8） |
| 共通命名規約 | `docs/ai_context/operation_logs_naming.md` を本書 r1 で参照宣言済み |

---

# レビューコメント対応サマリ（r1 → r2）

## r1 で対応済み（再掲）
| ID | 由来 | 対応 |
|----|------|------|
| P14-1 〜 P14-3 | phase15 r3 / r4 起点（🔴 必須） | r1 で完了 |
| P14-4 〜 P14-8 | phase15 r3 / r4 起点（🟡 推奨） | r1 で完了 |
| P14-9 〜 P14-17 | phase15 起点 + phase14 単独（🟢 軽微） | r1 で完了 |

## r2 で新規対応（Q14-1 〜 Q14-14）
| ID | 優先度 | 対応 |
|----|--------|------|
| Q14-1 | 🔴 必須 | **実装段取り Step A 〜 D を冒頭に明記**。phase14 r2 → phase15 r4 の循環依存を解消 |
| Q14-2 | 🔴 必須 | `sales.quantity` / `sales_return.quantity` 追加に伴う既存レコード初期値の前提として「**本番リリース前スキーマ確定**」を本書「前提」セクションで宣言 |
| Q14-3 | 🔴 必須 | **予約購入の在庫減算タイミングを「出荷時（`SHIPPED` 遷移時）」に確定**。注文確定フロー擬似コードと再構築 SQL を整合修正。phase15 への要請事項にも反映 |
| Q14-4 | 🟡 推奨 | `shipping_statuses` 未対応ステータスの機能対応フェーズ名を「**将来 phase21 配送オペレーション拡張**（仮称）」と明記。Service 層で「マスタ存在 ≠ 入力許容」運用を強制 |
| Q14-5 | 🟡 推奨 | `inventory_movements.direction` カラムを廃止し、`quantity` を符号付きに変更（方針 (C) 採用）。`SUM(quantity)` で在庫純増減が一発算出可能に |
| Q14-6 | 🟡 推奨 | 住所オーナー検証は `address.user_id == order_request.user_id` のみで `is_active` を問わないことを明記。住所編集運用と整合 |
| Q14-7 | 🟡 推奨 | `payment_id` UNIQUE 違反時の冪等処理（既存 `sales` を返す）を本書に明記。二重課金リスクを解消 |
| Q14-8 | 🟡 推奨 | 完全移行 Step 2 を**機能フラグ `inventory.read_source`** で段階化。Market 在庫表示・配送予定日計算・予約ステータス判定の各機能を順番に切替可能に |
| Q14-9 | 🟢 任意 | 改訂履歴の「改訂版」を「改訂版（r0 相当）」と表記し、r1 / r2 との差分を明確化 |
| Q14-10 | 🟢 任意 | `sales.quantity` 追加に伴う集計テスト・整合性テスト（`amount = price × quantity`）を追加 |
| Q14-11 | 🟢 任意 | `inventory_movements.created_by_user_id` の運用ルール（`sale` は注文者、`adjustment` は管理者、バッチは NULL 等）を表で明記 |
| Q14-12 | 🟢 任意 | サマータイム・海外展開はスコープ外と明記。技術検討事項に将来課題として残す |
| Q14-13 | 🟢 任意 | DB 設計セクション冒頭に「`inbounds` / `inventories` / `warehouses` / `shipping_methods` / `deliveries` は phase15 r4 で定義済み」の前提を明記 |
| Q14-14 | 🟢 任意 | UUID v7 採番ライブラリ依存（Java：`uuid-creator` 等／PHP：`ramsey/uuid` v4.7+）を技術検討事項に追記 |

---

# 再々レビューコメント（phase14 r2 に対して）

レビュー日：2026-05-06
レビュー対象：phase14_shipping_r2.md
参照：phase15_delivery_management_r3.md / phase15_delivery_management_r4.md（作成中）/ docs/coding_guidelines.md
前回までの指摘：P14-1 〜 P14-17（r1）／ Q14-1 〜 Q14-14（r1 への再レビュー）

---

## 総評

Q14-1 〜 Q14-14 はすべて反映され、特に重大3件（実装段取り Step A〜D・本番リリース前スキーマ確定前提・予約購入の出荷時減算）はクリーンに着地している。

- **Q14-1（実装段取り）**：Step A〜D を冒頭に置いたことで、phase14 r2 と phase15 r4 の循環依存が解消され、実装担当者が迷わず着手できる
- **Q14-3（予約購入の出荷時減算）**：再構築 SQL の `LEFT JOIN deliveries` で `shipped_date IS NOT NULL` 判定する形に修正されており、論理が一貫している
- **Q14-5（`direction` 廃止 + 符号付き `quantity`）**：シンプルかつ集計しやすい構造に整理されている

**設計書としての完成度は高い**。phase15 r3 → r4 に進める前提として phase14 r2 で確定すべき事項は概ね出揃った。

ただし、Q14-3（予約購入の出荷時減算）が新たに導入された結果として残った論点と、本書 r2 で初めて踏み込んだ機能フラグ・移行段取りに関連して、最終確認として詰めておきたい点がいくつかある。以下、優先度順に提示。

---

## 🟡 中程度（実装直前に確認推奨）

### S14-1. 予約購入の出荷時減算で「在庫切れ時の挙動」が未定義

Q14-3 で予約購入の在庫減算は「出荷時（`SHIPPED` 遷移時）」に確定したが、

- 出荷時に対象商品の `inventories.quantity < sales.quantity` だった場合、Service はどう振る舞うべきか
- `CHECK (quantity >= 0)` 制約があるので例外は発生するが、その後の運用（Console での再入荷待ちにする / 注文をキャンセルする）が未定義
- 現実には「予約受付時には在庫があると予測して受付したが、発売時点で予約数が在庫を超えた」ケースが起きる

**推奨対応：** 以下のいずれかを本書に明記。

- (A) 出荷時に在庫不足なら例外を投げ、`deliveries.shipping_status_id` は `PENDING` のまま据え置く（管理者が入荷後に再試行）
- (B) 予約段階で「予約上限（`product.preorder_max`）」を設けて、超過受付を防ぐ
- (C) 出荷を分割する（在庫充足分は出荷、不足分は `PENDING` 維持）

(A) が最もシンプル。本書では (A) を採用する旨を「予約購入の在庫減算タイミング」セクション（304 行目以降）に追記推奨。

### S14-2. 出荷時減算が `inventory_movements` に記録される際の `created_by_user_id` の扱い

Q14-11 の運用ルール（462 行目）では `movement_type='sale'` は `sales.user_id`（注文者）と書かれている。しかし、

- 通常購入の `sale` 記録は注文確定時 = 注文者の操作なので `sales.user_id` で自然
- **予約購入の `sale` 記録は出荷時 = 管理者の操作**なので、`sales.user_id` を入れるのは違和感がある

→ 「ユーザの注文操作に伴う在庫減算」という説明（462 行目）と矛盾する。

**推奨対応：** 運用ルール表を以下のように細分化。

| `movement_type` | 発火タイミング | `created_by_user_id` の値 |
|-----------------|--------------|-------------------------|
| `sale`（通常購入） | 注文確定時 | `sales.user_id`（注文者） |
| `sale`（予約購入） | 出荷時 | 出荷操作を実行した管理者の `user_id` |

これに合わせて Q14-3 の実装方針（314-317 行目）にも「`inventory_movements` 記録時の `created_by_user_id` は出荷操作の管理者」を追記。

### S14-3. 機能フラグの粒度と命名整合

Q14-8 対応の機能フラグ（402-407 行目）は以下4つ：

- `inventory.read_source`（親）
- `inventory.read_source.market_display`
- `inventory.read_source.delivery_schedule`
- `inventory.read_source.preorder_status`

しかし、

- Console の在庫表示（Step 2 の (d)）に対応するキー（`inventory.read_source.console_display`）が抜けている
- 「親キーから継承」の実装ルール（読み取り順序・null fallback の扱い）が未定義

**推奨対応：** Console 表示用のキーを追加し、継承ロジックを Service 層の擬似コードレベルで明記。

```
function getReadSource(feature_key):
    specific = config('inventory.read_source.' + feature_key)
    if specific is not null and specific != 'inherit':
        return specific
    return config('inventory.read_source')  // 親キーへフォールバック
```

### S14-4. Step C の機能フラグ完全切替後、フラグ自体の撤去タイミングが未定義

Q14-8 で機能フラグを導入したのは良いが、

- Step 4（`products.stock` DROP）完了後、機能フラグ `inventory.read_source` は不要になる
- いつ `config/app/Inventory.php` からフラグを撤去するか未定義
- フラグが残ったままだと、コード上は分岐があるのに片方しか使われない死コードが累積する

**推奨対応：** Step 4 の作業内容に「機能フラグ `inventory.read_source` および各分岐コードの削除」を1行追加。または「Step 5（クリーンアップ）」を追加して明示。

### S14-5. `payment_id` UNIQUE 違反時の冪等処理で「sales 内容が違う場合」の判断が未定義

Q14-7 対応で「UNIQUE 違反時は既存 sales を返す」と決まったが、

- 本番決済 API から **同じ `payment_id` で異なる商品・金額**の二重連携が来た場合、それは「真の冪等」ではなく「決済 API のバグ・なりすまし」の可能性
- 既存 sales を返すと、悪意のある第三者が決済 ID を盗んで他人の購入を取得できる脆弱性になりかねない

**推奨対応：** 冪等処理に「`payment_id` 一致 + 注文者 user_id・商品 product_id・金額 amount が一致する場合のみ既存 sales を返す。それ以外は例外」を追加。Q14-7 セクション（219-222 行目）を以下のように補強：

```
- payment_id 一致だけでなく、user_id / product_id / amount の一致もチェック
- 全項目一致 → 既存 sales を返す（真の二重送信）
- payment_id のみ一致で他項目が異なる → エラーログを記録し例外を投げる
  （決済 API のバグ・なりすまし疑い）
```

---

## 🟢 軽微（任意）

### S14-6. 注文確定フロー擬似コードのトランザクション範囲とバリデーション順

擬似コード（265-302 行目）は以下の順：

```
1. validateOrder（住所・配送方法・在庫）
2. begin transaction
3. 在庫減算（FOR UPDATE）
4. INSERT sales
5. DeliveryCreationService.createForSales(sales.id)
6. commit
```

ステップ 1 のバリデーションで `getStock(product_id) <= 0` をチェックするが、これは **トランザクション外**で行われている。同時実行で別トランザクションが在庫を減らすと、ステップ 3 で `CHECK (quantity >= 0)` 制約に引っかかってロールバックすることはあるが、それでもユーザに対して「在庫切れ」の正確なエラーが返るかは曖昧。

**推奨対応：** 「ステップ 1 のバリデーションは予備的なもので、最終的な在庫切れ判定はステップ 3 のロック取得後に再確認する」を明記。または、ステップ 1 をトランザクション内に移してロックを早めに取る方針に統一。

### S14-7. `inventory_movements` の `quantity` 符号と `CHECK` 制約

Q14-5 対応で `quantity` を符号付き INT に変更し `CHECK (quantity != 0)` を追加（432 行目）。これは良いが、

- `movement_type='inbound'` で `quantity < 0` のような不整合（増加すべき記録が負数）が DB レベルで検出できない
- 規約として `movement_type` と `quantity` 符号の対応を Service 層で強制するのは妥当だが、DB 制約があれば二重防御になる

**推奨対応：** 任意で以下の `CHECK` 制約を追加。

```sql
CHECK (
  (movement_type IN ('inbound', 'cancel', 'return') AND quantity > 0) OR
  (movement_type = 'sale' AND quantity < 0) OR
  movement_type = 'adjustment'
)
```

`adjustment` のみ符号自由。実装時に煩雑になるなら不要だが、データ整合性の保険としては有効。

### S14-8. Step A の作業に `address.is_active` 追加が含まれているが、本書の「DB設計」では既存維持として記載

39 行目（Step A）で「`address.is_active` 追加」が作業項目になっているが、DB 設計セクション（517 行目以降）の `address` テーブルには `is_active` がすでにあり「既存維持」と書かれている。

これは r1 化のタイミングで取り込んだのか、それ以前から存在していたのかが読み手に伝わらない。

**推奨対応：** 軽微なので、Step A の作業項目から `address.is_active` を削除する（既存ならスキーマ変更不要）か、DB 設計の `address` 節に「`is_active` は r1 で追加された」と注釈を入れる、のどちらか。

### S14-9. テストケースで「予約購入の出荷時減算」の異常系が抜けている

Q14-3 のテストケースは正常系（636 行目「予約購入は出荷時減算」）はあるが、異常系として：

- 出荷時に在庫不足だった場合の挙動（S14-1 と連動）
- 予約購入の出荷を試みたが `deliveries.shipping_status_id` の遷移ガードで拒否された場合
- 出荷時減算 → 直後に返品申請が来た場合の `inventory_movements` の整合性

これらのテストが抜けている。

**推奨対応：** S14-1 の決定（在庫不足時の挙動）を反映した上で、異常系テストを追加。

### S14-10. `address.is_active` で過去住所が論理削除される一方、注文時は「自分のものなら is_active 問わない」とする方針の UX 確認

Q14-6 対応で「過去住所も自分のものなら参照可能」となったが、

- ユーザーは住所編集 UI で過去住所を選べない（`is_active=true` のみ表示）
- 注文確定 API には過去住所の `address.id` を直接送信できる
- これは API 直叩き時のバリデーション論理として正しいが、Console 管理者が過去住所を指定した「配送先変更」操作などで意図せず使われる可能性

**推奨対応：** 軽微だが、「Console での配送先変更時、過去住所（`is_active=false`）は選択肢に出さない（API バリデーションは通すが UI ではフィルタ）」を運用ルールに追加。UX の一貫性を保つ。

### S14-11. UUID v7 ライブラリの選定基準

Q14-14 で Java の v7 ライブラリ候補として `uuid-creator` を挙げているが、

- 他にも `f4b6a3c-uuid-creator` / `juuid-creator` / `Anthropic 製でない `jug` など複数の選択肢がある
- ライセンス・メンテ状況の確認が必要

**推奨対応：** 軽微なので技術検討事項に「採用ライブラリは Step A 実装時に License (Apache-2.0/MIT 推奨) と GitHub Star・最新リリース日で判断」と1行追記。

### S14-12. 改訂履歴の「改訂版（r0 相当）」表記

Q14-9 対応で「改訂版（r0 相当）」となったが、これは初版から数えると 2 つ目の版を r0 と呼ぶ形でやや不自然。

**推奨対応（任意）：** 「改訂版（r0 相当）」を「v0」または「pre-r1」と表記する案もある。本書 r1 / r2 / r3... と一貫させたいなら、初版を「r-1」、改訂版を「r0」と扱う命名規則を採用するのは合理的。本書ですでに採用済みなので、軽微な指摘として残す。

---

## まとめ：r3 の必要性判定

| 優先度 | 項目 | r3 必要性 |
|--------|------|----------|
| 🟡 推奨 | S14-1（予約購入の出荷時在庫不足の挙動） | **明記推奨**（実装で必ずぶつかる） |
| 🟡 推奨 | S14-2（予約購入の `sale` 記録の `created_by_user_id`） | **明記推奨**（運用ルール表の細分化） |
| 🟡 推奨 | S14-3（機能フラグの粒度と継承ロジック） | r3 で簡単補強可 |
| 🟡 推奨 | S14-4（移行完了後の機能フラグ撤去） | r3 で1行追加 |
| 🟡 推奨 | S14-5（`payment_id` 冪等処理の脆弱性対策） | **明記推奨**（セキュリティ観点） |
| 🟢 任意 | S14-6 〜 S14-12 | 実装と並行可 |

---

## 結論

phase14 r2 は **設計書としての完成度が高く、実装着手レベルに到達している**。Step A〜D の段取り明示・予約購入の出荷時減算・機能フラグ段階化など、r1 で残っていた重大論点はすべて解消済み。

ただし、Q14-3（出荷時減算）が新たに導入された結果、**S14-1（出荷時の在庫不足挙動）／ S14-2（`created_by_user_id` 細分化）／ S14-5（冪等処理の脆弱性対策）** の3点は実装時に必ずぶつかる論点。これらは r3 で軽量に追記すれば実装段取りが完全に固まる。

**最低限：** 以下5点を r3 で追記すれば、本書は完全に実装可能な状態になる。

1. S14-1：予約購入の出荷時に在庫不足だった場合の挙動（推奨は (A) 例外＋PENDING 維持）
2. S14-2：予約購入の `sale` 記録の `created_by_user_id` を「出荷操作の管理者」に細分化
3. S14-3：機能フラグの継承ロジックの擬似コード明記 + Console 表示用キー追加
4. S14-4：Step 4 / 5 で機能フラグ撤去を作業に含める
5. S14-5：`payment_id` 冪等処理に user_id / product_id / amount の一致チェックを追加（脆弱性対策）

それ以外（S14-6 〜 S14-12）は実装と並行で詰めても致命的ではない。

なお、phase15 r5 への申し送り事項として「予約購入の出荷時在庫減算フックでの S14-1 / S14-2 の挙動」を **phase15 への要請事項表（735 行目）に追加する**ことを推奨する（phase15 が実装するロジックなので、phase15 設計書側でも詰める必要がある）。

