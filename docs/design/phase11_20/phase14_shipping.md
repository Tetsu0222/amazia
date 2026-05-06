# フェーズ14：購入機能（改訂版 r3）

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

## 範囲
- Amazia Console
- Amazia Market
- Amazia Core
- DB設計（新規テーブル・マスタ化含む）
- **在庫モデルの完全移行（`products.stock` 廃止 → `inventories` 一本化）**（P14-1 対応）
- **注文確定フローの確定**（P14-2 対応）
- **予約購入の在庫減算（出荷時減算）の Service 化**（Q14-3 / S14-1 / S14-2 対応）

## 前提（Q14-2 対応）

本書および phase15 一連の設計は **Amazia の本番リリース前スキーマ確定**を前提とする。

- `sales` / `sales_return` への `quantity` カラム追加（後述）に伴う既存レコードのデータ移行は不要。
- 並行運用開始前に `inventories` を `products.stock` から複製するマイグレーション（phase15 r4 RRRR-1）も同様に「初期データ複製のみ」で済む。
- 既存ユーザの売上・在庫履歴を引き継ぐ移行マイグレーションは本書のスコープ外。

---

# 実装段取り（Step A 〜 D + Step E／Q14-1 / S14-4 対応）

phase14 r3 と phase15 r4 は記述上互いを前提に置く部分があるため、循環依存に見える。実装は以下の Step A → E の順で行うことで、依存関係を解消できる。

| Step | 対象 | 主な作業 | 完了条件 |
|------|------|---------|---------|
| **Step A** | phase14 r3 のスキーマ変更だけ先行 | `sales.shipping_method_id` 追加 / `sales.quantity` 追加 / `sales.payment_id` UNIQUE 制約 / `sales_return.quantity` 追加 / `shipping_statuses` マスタ拡張（CANCELED 等）/ `operation_logs.screen_name` / `api_name` 追加（注：`address.is_active` は r0 相当で既存追加済のため Step A の作業対象外／S14-8 対応） | スキーマだけが入った状態 |
| **Step B** | phase15 r4 実装 | `warehouses`（ダミー1行）/ `inventories` / `inbounds` / `shipping_methods` / `deliveries` テーブル作成 / `InventorySyncService`（並行運用フック）作成 / 入荷・配送 Service 実装 / `DeliveryCreationService.createForSales(sales_id)`（**長期シグネチャ**） | `products.stock == SUM(inventories.quantity)` の不変条件が常に成立 |
| **Step C** | phase14 r3 の残り（在庫モデル完全移行） | `inventory_movements` 作成 / 完全移行マイグレーション Step 1 〜 3 実施 / `InventorySyncService` 削除 / 読み取り側を `inventories` 参照に切替（機能フラグで段階化／Q14-8） | すべての読み書きが `inventories` を正本とする状態 |
| **Step D** | phase15 r5（フック削除のクリーンアップ） | `InventorySyncService` 呼び出し箇所の削除確認 / phase15 設計書の改訂履歴更新 / コード検査 | 過渡期コードがリポジトリに残らない状態 |
| **Step E**（r3 で追加／S14-4 対応） | phase14 r3 の最終クリーンアップ | `ALTER TABLE products DROP COLUMN stock` / **機能フラグ `inventory.read_source` および各分岐コードの削除** / 死コード検査 | 機能フラグと `products.stock` カラムが完全に撤去された状態 |

実装担当者は **必ず Step A → B → C → D → E の順序**で進める。

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
完全移行後の `BACK_ORDER` / `SOLD_OUT` 判定は **`inventories.quantity`** を参照する。並行運用期は `products.stock` 参照を維持し、Step C 中の機能フラグ `inventory.read_source` で段階的に切替。

### ■ タイムゾーン方針（P14-17 / Q14-12 対応）
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
phase14 r3 / phase15 r4 の Service 層では、**未対応ステータスへの遷移リクエストはバリデーションで拒否する**。Service 層の許容ステータスリスト（config）で制御。

---

# Amazia Console（改訂）

## 売上管理画面

### ■ 集計要件（拡張）
- 年/月/日単位
- **商品別集計**
- **ユーザ別集計**
- **決済方法別集計**
- **配送方法別集計**（r1 追加／P14-3）
- **予約売上 / 通常売上の区別**
- **販売数量集計**（r2 追加／Q14-10）

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
- **UI フィルタ（r3 で追加／S14-10 対応）**：Console での配送先変更時、過去住所（`is_active=false`）は選択肢に出さない。API バリデーションは通すが、UI で表示しないことで運用上の事故を防ぐ。

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
- **返品理由コード（`return_reason_code`／将来課題：P14-14）**
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

### ■ payment_id の採番方式（P14-13 / Q14-7 / Q14-14 / S14-5 / S14-11 対応）
- **採番ロジックの所在**：Amazia Core の `PaymentService`（模擬決済時のみ）。本番決済 API 接続後は外部発行値をそのまま受領する。
- **形式**：模擬決済期は **UUID v7**（時系列ソート性のため）。本番決済 API 接続後は外部システム由来（最大長想定で `VARCHAR(100)` を確保済み）。
- **採番ライブラリ依存**：
  - Java：標準 `java.util.UUID` は v4 のみ。v7 は外部ライブラリ（`uuid-creator` 等）が必要。
  - PHP：`ramsey/uuid` v4.7+ が v7 対応。
  - **採用ライブラリは Step A 実装時に License（Apache-2.0 / MIT 推奨）と GitHub Star・最新リリース日で判断**（S14-11 対応）。
- **重複チェックと冪等処理（Q14-7 / S14-5 対応：r3 でセキュリティ強化）**：
  - `sales.payment_id` を `UNIQUE` 制約で担保。
  - **UNIQUE 違反を捕捉した際は、`payment_id` のみで一致と判断せず、`user_id` / `product_id` / `quantity` / `amount` のすべてが既存 `sales` と一致する場合に限り既存 `sales` を返す（真の二重送信のみ冪等扱い）**。
  - **`payment_id` のみ一致で他項目が異なる場合**は、決済 API のバグ・なりすまし疑いとして **エラーログ（要監視レベル）に記録し、例外を投げて拒否する**。これにより決済 ID の盗用による他人購入取得の脆弱性を塞ぐ（S14-5 対応）。

```
擬似コード：
try:
    INSERT sales(user_id, product_id, quantity, amount, payment_id, ...)
catch UniqueViolation on payment_id:
    existing = SELECT * FROM sales WHERE payment_id = :payment_id
    if existing.user_id == request.user_id
       AND existing.product_id == request.product_id
       AND existing.quantity   == request.quantity
       AND existing.amount     == request.amount:
        return existing            // 真の二重送信 → 冪等処理
    else:
        log.error('payment_id reuse with mismatched fields', ...)
        throw PaymentIdConflictException
```

---

## 配送情報（仕様補強）
- 登録住所 or 新規住所
- コンビニ受け取り：**店舗検索API（将来）を想定**
- 置き配：**商品属性（allow_dropoff）と連携**
- **配送方法（`shipping_method_id`）を注文確定リクエストに含める**（r1 で確定）。受領した値は `sales.shipping_method_id` および `deliveries.shipping_method_id` に同期保存。

---

## 購入履歴
- 購入日時
- 商品名
- **数量（r2 追加）**
- 金額
- 配送予定日
- 配送ステータス
- **配送方法**（r1 追加）
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
| SOLD_OUT | 「完売」 |

---

# 注文確定フロー（P14-2 / Q14-3 / Q14-6 / S14-6 対応）

注文確定時の処理は以下のフローで Service 層に集約する。バリデーション・在庫減算・`sales` INSERT・`deliveries` 生成は**同一トランザクション**で完結させる。

```
OrderConfirmationService.confirm(order_request):

  1. validateOrder(order_request)  ※ 予備的バリデーション（S14-6 対応）
     - sales.shipping_address_id について
         address.user_id == order_request.user_id を Service 層で検証
         （is_active は問わない／Q14-6）
     - sales.shipping_method_id が shipping_methods マスタに存在し、
       かつ「許容ステータス」リストに含まれることを検証（Q14-4）
     - 在庫の予備チェック：is_preorder == false かつ getStock(product_id) <= 0 なら拒否
       ※ ただしこれは予備的判定。最終確定はステップ 3 のロック取得後

  2. begin transaction

  3. 通常購入の在庫減算（is_preorder == false のみ）
     ※ 同時実行制御：SELECT ... FOR UPDATE で対象行をロック
     ※ ロック取得後に「ロック後在庫」を再確認（S14-6 対応）。在庫不足なら例外で
       ロールバック（CHECK 制約に頼らず Service 層で明示判定し、ユーザに正確な
       「在庫切れ」エラーを返す）
     - 並行運用期：products.stock -= order_quantity
                  + InventorySyncService.applyDelta(product_id, 1, -order_quantity)
     - 完全移行後：inventories.quantity -= order_quantity（直接）
     - 予約購入（is_preorder == true）はこの時点では減算しない（Q14-3）

  4. INSERT sales
     （quantity, shipping_address_id, shipping_method_id,
       payment_method_id, payment_id, is_preorder, ...）

     ※ payment_id UNIQUE 違反時は「user_id/product_id/quantity/amount すべて一致」
       のときのみ既存 sales を返す冪等処理。それ以外は例外（Q14-7 / S14-5）

  5. DeliveryCreationService.createForSales(sales.id)
     → INSERT deliveries (
          sales_id,
          shipping_address_id = sales.shipping_address_id,
          shipping_method_id  = sales.shipping_method_id,
          shipping_status_id  = PENDING,
          scheduled_date      = DeliveryScheduleService.calculate(...)
        )

  6. commit
```

## 予約購入の在庫減算タイミングと出荷時挙動（Q14-3 / S14-1 / S14-2 対応）

予約購入（`sales.is_preorder = true`）の在庫減算は **出荷時（`deliveries.shipping_status_id` を `PENDING → SHIPPED` に遷移する瞬間）** に実施する。

### 採用方針（再掲）
| タイミング | 採否 | 理由 |
|-----------|------|------|
| (A) 発売日到来時にバッチで一括減算 | ✕ | バッチが必要・障害時の再実行が複雑 |
| (B) 注文確定時（通常購入と同時に減算） | ✕ | 在庫がマイナスになる可能性、`CHECK (quantity >= 0)` と矛盾 |
| **(C) 出荷時（`SHIPPED` 遷移時）に減算** | **採用** | `deliveries` 状態遷移と Service 内で完結。発送＝在庫消費という現実の業務フローと整合 |

### 出荷時に在庫不足だった場合の挙動（S14-1 対応：r3 で確定）

予約受付時には在庫があると予測して受付したが、発売／出荷時点で予約数が在庫を超えるケースが現実に起き得る。本書では以下の方針 (A) を採用する。

| 方針 | 採否 | 理由 |
|------|------|------|
| **(A) 例外＋PENDING 維持** | **採用** | 出荷時に `inventories.quantity < sales.quantity` を検知したら例外を投げ、`deliveries.shipping_status_id` は `PENDING` のまま据え置く。管理者が入荷後に再度出荷操作を行う |
| (B) 予約上限を商品マスタに設けて超過受付防止 | 将来課題 | 予約 UX の精度向上には有効だが、本フェーズでは扱わない |
| (C) 出荷分割（在庫充足分は出荷、不足分は PENDING 維持） | 将来課題 | 分割配送は phase15 r4 でスコープ外宣言済み |

### 出荷時減算の実装方針

- `deliveries` の `shipping_status_id` を `PENDING → SHIPPED` に遷移する Service（phase15）に、`sales.is_preorder == true` の場合のみ在庫減算フックを追加する。
- 通常購入は注文確定時に減算済みのため出荷時には再減算しない（`is_preorder` で分岐）。
- 在庫減算は同一トランザクション内で `inventories.quantity -= sales.quantity` を実行（並行運用期は `products.stock` も同時減算）。
- **ロック取得後の在庫再確認**：`SELECT ... FOR UPDATE` で `inventories` 行をロックした直後に `quantity >= sales.quantity` を確認。不足なら例外を投げ、`SHIPPED` 遷移は行わない（`PENDING` 維持）。
- **`inventory_movements` の `created_by_user_id`（S14-2 対応）**：予約購入の `sale` 記録は注文者ではなく **出荷操作を実行した管理者の `user_id`** を入れる。Q14-11 の運用ルール表を細分化（後述）。

### 再構築 SQL（Q14-3 対応：予約購入の出荷有無を反映）

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
- DB 制約 `CHECK (inventories.quantity >= 0)` で負数化を防止。

## シグネチャ移行の整理（P14-3 / Q14-1 対応）

| Step / 期間 | `DeliveryCreationService.createForSales` のシグネチャ | `shipping_method_id` の出所 |
|------|---------------------------------------------------|-------------------------|
| Step A 完了後（推奨）／Step B 以降 | `createForSales(sales_id)` | `sales.shipping_method_id` |

phase14 r3 では Step A → B → C → D → E の順序を厳守することで、phase15 r4 が**最初から長期シグネチャで実装可能**となる。

---

# 在庫モデル完全移行（P14-1 / Q14-8 / S14-3 / S14-4 対応）

## 設計判断
- phase15 r4（Step B）で `inventories` の並行運用と `InventorySyncService` が確立された前提で、**phase14 r3 の Step C で `products.stock` を廃止**し、Step E で機能フラグ撤去まで完結する。

## 完全移行の段取り（マイグレーション仕様／P14-9 対応）

```
Step 1: 並行運用整合性チェック
  - SUM(inventories.quantity GROUP BY product_id) と products.stock を全件比較
  - 乖離があれば、販売・返品履歴から再構築（前掲「再構築 SQL」を実行）
  - チェックが通るまで Step 2 以降に進まない

Step 2: 読み取り側を inventories 参照に切替（機能フラグで段階化／Q14-8 / S14-3）
  - config/app/Inventory.php に機能フラグ inventory.read_source を導入
  - 切替対象の各機能を順番に inventories 参照へ切替
    (a) Market の在庫表示       → inventory.read_source.market_display
    (b) 配送予定日計算           → inventory.read_source.delivery_schedule
    (c) 予約ステータス判定       → inventory.read_source.preorder_status
    (d) Console の在庫表示       → inventory.read_source.console_display（S14-3 で追加）
  - 各切替後に並行運用整合性テストでデグレを検出
  - すべての読み取り箇所が inventories 参照になったら次へ

Step 3: 書き込み側のフック削除
  - 販売 Service の products.stock 減算を削除
  - 返品復元 Service の products.stock 加算を削除
  - 入荷 Service（phase15）の products.stock 加算を削除
  - InventorySyncService と呼び出し箇所を削除（→ phase15 Step D で実施）

Step 4 (= Step E)：products.stock カラム DROP + 機能フラグ撤去（S14-4 対応）
  - ALTER TABLE products DROP COLUMN stock
  - config/app/Inventory.php から inventory.read_source 系キーを撤去
  - inventories 参照に固定された分岐コード（if read_source == ...）を削除
  - 死コード検査（grep ベース）で products.stock 参照箇所が0件であることを確認
  - 完全移行完了
```

## 機能フラグ（Q14-8 / S14-3 対応）

| キー | 値域 | 既定値 | 用途 |
|------|------|-------|------|
| `inventory.read_source` | `products_stock` / `inventories` | `products_stock` | 在庫値の読み取り正本（親キー） |
| `inventory.read_source.market_display` | `products_stock` / `inventories` / `inherit` | `inherit` | Market 在庫表示のみ先行切替したい場合 |
| `inventory.read_source.delivery_schedule` | 同上 | `inherit` | 配送予定日計算のみ先行切替 |
| `inventory.read_source.preorder_status` | 同上 | `inherit` | 予約ステータス判定のみ先行切替 |
| **`inventory.read_source.console_display`**（S14-3 で追加） | 同上 | `inherit` | Console 在庫表示のみ先行切替 |

### 継承ロジック（S14-3 対応：r3 で擬似コード明記）

各機能は子キーを優先し、`inherit` または未設定なら親キーへフォールバックする：

```
function getReadSource(feature_key):
    specific = config('inventory.read_source.' + feature_key)
    if specific is not null and specific != 'inherit':
        return specific
    return config('inventory.read_source')   // 親キーへフォールバック
```

`config()` 経由で取得（規約 3-1 / 4-1）。テストもこれを `config()` 経由でアサート（規約 4-1）。

### 撤去タイミング（S14-4 対応）
Step 4 / Step E で `products.stock` を DROP すると同時に、上記すべてのキーを `config/app/Inventory.php` から撤去し、各分岐コード（`if (read_source == 'products_stock')` 等）も削除する。死コードを残さない。

## 完全移行後の構造
- 販売・予約ステータス判定・返品復元・入荷のすべてが `inventories` を読み書き正本とする。
- `inventories` の同時実行制御は `SELECT ... FOR UPDATE`（悲観ロック）。
- 倉庫マスタはダミー1行のまま。複数倉庫対応は別フェーズで。

---

# inventory_movements テーブル新設（P14-6 / Q14-5 / Q14-11 / S14-2 / S14-7 対応）

## inventory_movements テーブル（新規：在庫増減ログの一元化）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | NOT NULL | 倉庫ID（`warehouses.id` への FK／DEFAULT 1） |
| movement_type | VARCHAR(50) | NOT NULL | 増減種別（`inbound` / `sale` / `cancel` / `return` / `adjustment`） |
| **quantity** | **INT** | **NOT NULL** | **増減量（符号付き：正数=増加、負数=減少）。`CHECK (quantity != 0)` + `movement_type` × 符号 CHECK（S14-7／後述）** |
| reference_type | VARCHAR(50) | NULL | 参照元種別 |
| reference_id | BIGINT | NULL | 参照元レコードID |
| comment | TEXT | NULL | 棚卸補正等の自由記述 |
| created_by_user_id | BIGINT | NULL | 操作ユーザ（運用ルールは下記参照） |
| created_at | DATETIME | NOT NULL | 記録日時 |

### `movement_type` × `quantity` 符号の DB 制約（S14-7 対応：r3 で追加）

Service 層に加えて DB 制約でも符号整合を強制し、二重防御とする：

```sql
CHECK (
  (movement_type IN ('inbound', 'cancel', 'return') AND quantity > 0) OR
  (movement_type = 'sale' AND quantity < 0) OR
  movement_type = 'adjustment'
)
```

`adjustment` のみ符号自由（棚卸補正は ± 双方あり得る）。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_inventory_movements_product_id` | 商品別の在庫履歴取得 |
| `idx_inventory_movements_created_at` | 期間別集計 |
| `idx_inventory_movements_reference` | (`reference_type`, `reference_id`) 複合インデックス |

### 記録タイミングと符号
- 入荷登録：`movement_type='inbound', quantity=+inbound.quantity, reference_type='inbounds'`
- 通常購入の販売（注文確定時）：`movement_type='sale', quantity=-sales.quantity, reference_type='sales'`
- 予約購入の販売（出荷時）：`movement_type='sale', quantity=-sales.quantity, reference_type='sales'`
- キャンセル復元：`movement_type='cancel', quantity=+sales.quantity, reference_type='sales'`
- 返品復元：`movement_type='return', quantity=+sales_return.quantity, reference_type='sales_return'`
- 棚卸補正（将来）：`movement_type='adjustment', quantity=±n, reference_type='manual_adjustment'`

集計時は `SUM(quantity)` で在庫純増減が一発で算出できる。

### `created_by_user_id` の運用ルール（Q14-11 / S14-2 対応：r3 で細分化）

| `movement_type` | 発火タイミング | `created_by_user_id` の値 |
|-----------------|--------------|-------------------------|
| `sale`（**通常購入**） | 注文確定時 | `sales.user_id`（注文者） |
| **`sale`（予約購入）（S14-2 で細分化）** | **出荷時（`SHIPPED` 遷移時）** | **出荷操作を実行した管理者の `user_id`** |
| `cancel` | キャンセル実行時 | キャンセルを実行した管理者の `user_id`（ユーザキャンセルなら注文者） |
| `return` | 返品承認時 | 返品を承認した管理者の `user_id` |
| `inbound` | 入荷登録時 | 入荷登録した管理者の `user_id` |
| `adjustment` | 棚卸補正時 | 棚卸補正を実行した管理者の `user_id` |
| バッチ自動処理（将来） | バッチ実行時 | NULL（自動処理は人間の操作主体がない） |

予約購入の `sale` 記録の操作主体は出荷管理者であり、注文者ではない。「いつ・誰が・何のために在庫を動かしたか」が一貫して追跡できるよう、Q14-11 の表を S14-2 で細分化した。

`inventories.quantity` の更新と `inventory_movements` への INSERT は同一トランザクションで実行する。

---

# DB設計（改訂）

> **テーブル所属の前提（Q14-13 対応）**
> - `inbounds` / `inventories` / `warehouses` / `shipping_methods` / `deliveries` は **phase15 r4 で定義済み**。本書ではこれらを前提とし、**`sales` / `address` / `operation_logs` / `sales_return` の改訂と `inventory_movements` の新設**を扱う。

## sales テーブル（r3 改訂版）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| user_id | BIGINT | NOT NULL | 購入ユーザID |
| product_id | BIGINT | NOT NULL | 商品ID |
| quantity | INT | NOT NULL | 購入数量（**`CHECK (quantity > 0)`**） |
| amount | INT | NOT NULL | 金額（`amount = product.price × quantity` の整合性を Service で担保） |
| payment_method_id | BIGINT | NOT NULL | 決済方法マスタID |
| **shipping_method_id** | BIGINT | NOT NULL | **配送方法マスタID（r1 で新規追加／P14-3）** |
| shipping_address_id | BIGINT | NOT NULL | 住所マスタID |
| shipping_date | DATE | NULL | 配送日 |
| sales_date | DATE | NOT NULL | 売上日 |
| shipping_status_id | BIGINT | NOT NULL | 配送ステータスマスタID |
| payment_id | VARCHAR(100) | NOT NULL | 決済ID（**`UNIQUE` 制約**／冪等処理は Service 層／Q14-7 / S14-5） |
| is_preorder | BOOLEAN | NOT NULL | 予約購入か |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `shipping_method_id` は `shipping_methods.id`（phase15 で定義）への FK。
- `shipping_address_id` は `address.id` への FK。Service 層で「`order_request.user_id == address.user_id`」を強制（`is_active` は問わない／Q14-6）。
- `payment_id` は `UNIQUE` 制約。違反時は **`user_id` / `product_id` / `quantity` / `amount` の全項目一致時のみ既存 `sales` を返す冪等処理**（S14-5）。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_sales_sales_date` | 売上日別集計 |
| `idx_sales_product_id` | 商品別集計 |
| `idx_sales_user_id` | ユーザ別集計 |
| `idx_sales_payment_method_id` | 決済方法別集計 |
| `idx_sales_shipping_method_id` | **配送方法別集計（r1 追加）** |

---

## address テーブル（r0 相当で `is_active` 追加済／S14-8 対応）

`is_active` カラムは **r0 相当（改訂版）の段階で追加されている**ことを r3 で明記。Step A の作業項目には含まれない。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | **所有ユーザ（注文時の検証は `order_request.user_id == address.user_id` のみで、`is_active` は問わない／Q14-6）** |
| postal_code | VARCHAR(20) | 郵便番号 |
| prefecture | VARCHAR(50) | 都道府県 |
| city | VARCHAR(100) | 市区町村 |
| address_line | VARCHAR(255) | 住所 |
| building | VARCHAR(255) | 建物名 |
| is_active | BOOLEAN | 現在利用中の住所か（false なら過去住所）／**r0 相当で追加済（S14-8）** |
| created_at | DATETIME | 作成日時 |

### 住所編集時の運用（P14-15 / Q14-6 / S14-10 対応）
- 住所を「編集」した場合、**旧住所は `is_active=false` に UPDATE して残し、新住所を `is_active=true` で INSERT する**。
- `sales.shipping_address_id` は過去住所を参照し続け、購入時点の配送先が壊れない。
- ユーザの住所選択 UI では `is_active=true` のレコードのみを表示。
- **Console での配送先変更時も `is_active=false` の住所は UI 選択肢に出さない**（API バリデーションは通すが UI でフィルタ／S14-10）。
- 注文時の検証は `address.user_id == order_request.user_id` のみで、`is_active` は問わない（過去住所だが自分のもの＝注文時に再利用可能）。

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

phase14 r3 では `sales.shipping_method_id` が本マスタを参照する FK となる。

---

## shipping_statuses（マスタ／r1 で拡張）
前述の Enum を格納（CANCELED / DELIVERY_FAILED / RESCHEDULED を r1 で追加）。Service 層では未対応ステータスへの遷移リクエストをバリデーションで拒否（Q14-4）。

---

## operation_logs（改訂）
| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | 操作ユーザ |
| action | VARCHAR(100) | 操作内容 |
| target_type | VARCHAR(50) | 対象種別 |
| target_id | BIGINT | 対象ID |
| screen_name | VARCHAR(100) | 画面名（命名規約は `docs/ai_context/operation_logs_naming.md`） |
| api_name | VARCHAR(100) | API名（同上） |
| comment | TEXT | 任意コメント（プレフィックス `[manual]` / `[inbound_recalc]` / `[shipping_delay]` 等） |
| created_at | DATETIME | 操作日時 |

### `reason_code` カラムの先行追加について（P14-5 対応）
phase15 r4 のプレフィックス方式で十分。先行追加せず、将来課題として残す。

### `comment` カラムの検索性（P14-16 対応：技術検討事項）
本格運用時に検索性が問題になった場合、フルテキスト検索またはログ専用基盤への流し込みを検討。本フェーズではスコープ外。

---

## sales_return テーブル

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| sales_id | BIGINT | 対象売上 |
| status | VARCHAR(50) | REQUESTED / APPROVED / REJECTED / REFUNDED |
| reason | TEXT | 返品理由（フリーテキスト） |
| approver_id | BIGINT | 承認者 |
| approved_at | DATETIME | 承認日時 |
| quantity | INT | **返品数量（r1 追加 / `CHECK (quantity > 0)`）** |
| notified_user | BOOLEAN | ユーザ通知済みか |
| notified_admin | BOOLEAN | 管理者通知済みか |
| created_at | DATETIME | 作成日時 |

### 返品理由分類（P14-14 対応：将来課題）
将来、返品分析を扱う場合は `return_reason_codes` マスタと `sales_return.return_reason_code` の追加を検討。

---

# 技術検討事項（補強）

- 予約ステータスは **リアルタイム判定**（バッチ不要）。判定の基準時刻は **JST 0:00**。サマータイム・海外展開は現時点でスコープ外
- 売上集計のインデックス：sales_date / product_id / user_id / payment_method_id / shipping_method_id
- 決済インターフェースは将来の本番決済APIに差し替え可能な構造
- `payment_id` は模擬決済期は UUID v7。**採番ライブラリは Step A 実装時に License (Apache-2.0/MIT 推奨) と GitHub Star・最新リリース日で判断**（Q14-14 / S14-11）
- 配送ステータスはマスタ化済。CANCELED / DELIVERY_FAILED / RESCHEDULED の機能対応は将来 phase21 配送オペレーション拡張（仮称）
- 在庫の同時実行制御は `SELECT ... FOR UPDATE`（悲観ロック）に統一
- **予約購入の在庫減算は出荷時（`SHIPPED` 遷移時）。出荷時に在庫不足なら例外＋PENDING 維持**（S14-1）
- **`inventory_movements.movement_type` × `quantity` 符号は DB の `CHECK` 制約と Service 層で二重防御**（S14-7）
- **将来課題：** 予約上限（`product.preorder_max`）による超過受付防止（S14-1 (B)）
- **将来課題：** 出荷分割（`deliveries` 1:N 化／phase15 RR-3 と連動）（S14-1 (C)）
- **将来課題：** `return_reason_codes` マスタ
- **将来課題：** `operation_logs.comment` のフルテキスト検索基盤
- **将来課題：** `operation_logs.reason_code` カラム追加
- **将来課題：** 海外展開時のロケール別タイムゾーン判定

---

# TDDテストケース（異常系追加）

## Amazia Core / JUnit

### 正常系
- 売上登録・更新・取得
- 集計処理（年/月/日 + 商品別/ユーザ別/決済方法別/配送方法別/販売数量集計）
- 操作履歴の記録
- 予約ステータス判定（境界値：公開日＝今日、発売日＝今日。**JST 0:00 基準**）
- 注文確定フローが同一トランザクションで完結する
- `is_preorder=true` のとき注文時は在庫減算されない。**出荷時（`SHIPPED` 遷移時）に在庫減算される**
- `is_preorder=false` の通常購入は注文確定時に減算され、出荷時には再減算されない
- 完全移行後の `BACK_ORDER` / `SOLD_OUT` 判定が `inventories.quantity` 参照で動作する
- `payment_id` の `UNIQUE` 制約で重複採番が拒否される
- **`payment_id` UNIQUE 違反時、user_id/product_id/quantity/amount すべて一致なら既存 sales が返される（真の冪等／S14-5）**
- 住所編集時、旧住所が `is_active=false` で残り、`sales.shipping_address_id` の参照が壊れない
- 過去住所（`is_active=false`）でも `address.user_id == order_request.user_id` なら注文時に参照可能
- `sales.amount == product.price × sales.quantity` が成立する
- **予約購入の `sale` 記録の `created_by_user_id` が出荷操作の管理者である**（S14-2）
- **通常購入の `sale` 記録の `created_by_user_id` が注文者の `sales.user_id` である**

### 異常系
- 通常購入で在庫切れの注文を予備バリデーションで拒否（P14-2）
- **ロック取得後の在庫再確認で在庫不足を検知し、Service 層で例外を投げる（CHECK 制約に頼らない明示判定／S14-6）**
- `address.user_id != order_request.user_id` の住所を指定した場合に拒否
- `sales.shipping_method_id` がマスタに存在しない場合に拒否
- `shipping_status_id` が未対応ステータスの場合に Service 層で拒否
- 在庫減算で `inventories.quantity < 0` になる更新を `CHECK` 制約で拒否
- 同時実行で `SELECT ... FOR UPDATE` のロック解放後に正しく直列化される
- `sales.quantity <= 0` を Service 層・`CHECK` 制約で拒否
- `sales_return.quantity <= 0` を Service 層・`CHECK` 制約で拒否
- **`payment_id` UNIQUE 違反かつ user_id 等の他項目が一致しない場合、エラーログ記録 + 例外（決済 ID なりすまし対策／S14-5）**
- 不正ステータス・不正日付

### 予約購入の出荷時減算 異常系（S14-1 / S14-9 対応：r3 で追加）
- **出荷時に `inventories.quantity < sales.quantity` の場合、例外を投げて `deliveries.shipping_status_id` は `PENDING` のまま据え置く**（S14-1 (A)）
- 出荷時減算後すぐに返品申請が来た場合、`inventory_movements` に `sale`（負数）と `return`（正数）の両方が時系列で記録され、`SUM(quantity)` が正しく合算される
- 予約購入の出荷を試みたが `deliveries.shipping_status_id` の遷移ガードで拒否された場合、在庫減算は実行されない
- 予約購入の出荷時に `InventorySyncService` がトランザクション内で例外を投げた場合、`deliveries` 状態遷移と `inventories` 更新がともにロールバックされる

### 在庫モデル完全移行の検証（P14-1 / Q14-8 / S14-3 / S14-4 対応）
- 並行運用期の整合性チェック：`products.stock(product_id) == SUM(inventories.quantity WHERE product_id=...)`
- 完全移行マイグレーション直後、`inventories` の値が販売・入荷・返品履歴からの再構築結果と一致（**予約購入は出荷済みのみ販売減算に含む**）
- 機能フラグ `inventory.read_source` の継承ロジックが正しく動作する：
  - 子キーが `products_stock` / `inventories` の場合、子キー優先
  - 子キーが `inherit` または未設定の場合、親キーへフォールバック
  - 4 機能（market_display / delivery_schedule / preorder_status / **console_display**）すべてで動作確認（S14-3）
- Step E 完了後、`config/app/Inventory.php` から `inventory.read_source` 系キーが完全削除されている（S14-4）
- Step E 完了後、コード内に `if (read_source == 'products_stock')` 等の死コード分岐が残っていない（grep 検査）
- `InventorySyncService` および呼び出し箇所が phase15 Step D 後にすべて削除されている

### inventory_movements の検証（P14-6 / Q14-5 / S14-7 対応）
- 入荷登録時に `inventory_movements` に `movement_type='inbound', quantity=+n` の行が記録される
- 通常購入の注文確定時に `movement_type='sale', quantity=-n` が記録される（`created_by_user_id` は注文者）
- **予約購入の出荷時に `movement_type='sale', quantity=-n` が記録される（`created_by_user_id` は出荷操作の管理者／S14-2）**
- 返品復元時に `movement_type='return', quantity=+n` が記録される
- 各 `movement` の `reference_type` / `reference_id` から元レコードを正しく逆引きできる
- `inventories.quantity` の更新と `inventory_movements` INSERT が同一トランザクションでロールバックされる
- `SUM(quantity GROUP BY product_id)` が現在の `inventories.quantity` と一致する
- **`movement_type` × `quantity` 符号の `CHECK` 制約違反が DB レベルで拒否される**（例：`movement_type='inbound'` で `quantity < 0` を試みて失敗／S14-7）

## Amazia Market / PHPUnit
- カート追加・削除
- セッション → DB カートマージ
- 模擬決済成功
- 模擬決済エラー（バリデーション）
- 配送情報未入力エラー
- 注文確定リクエストに `shipping_method_id` が含まれない場合のエラー
- 注文確定リクエストの `quantity` が 0 以下の場合のエラー
- 購入履歴表示（数量・配送方法を含む）
- 予約ステータス表示（境界値は JST 0:00 基準）

### Console / PHPUnit（S14-10 対応：r3 で追加）
- Console の配送先変更画面で過去住所（`is_active=false`）が選択肢に表示されない（UI フィルタ）
- API 直叩きで `is_active=false` の住所を指定した場合は通る（オーナー検証のみ）

### テスト値の config 経由化
- `payment_methods` / `shipping_methods` / `shipping_statuses` のマスタ ID
- 並行運用のダミー倉庫 ID
- `inventory_movements` の `movement_type` enum 値
- 予約ステータス判定のタイムゾーン（`Asia/Tokyo`）
- 機能フラグ `inventory.read_source` および子キーの値（S14-3）

---

# 🔧 追加で検討するとさらに良くなる点（既存改訂版から維持）

## 1. 予約ステータス判定の「優先順位ルール」明文化

### ■ 予約ステータス判定の優先順位（上から順に適用）
1. **公開前（公開日 > 今日）** → NOT_PUBLIC
2. **予約開始日が設定されており、今日 < 予約開始日** → PRE_ORDER_NOT_STARTED
3. **公開済 & 発売日前（公開日 ≤ 今日 < 発売日）** → PRE_ORDER
4. **発売日以降（今日 ≥ 発売日）**
   - 在庫あり → ON_SALE
   - 在庫なし → BACK_ORDER or SOLD_OUT（予約可否で分岐）
5. **在庫なし & 予約不可** → SOLD_OUT

→ 在庫参照先は完全移行後は `inventories`、判定基準時刻は JST 0:00。

---

## 2. address テーブルの履歴管理方針
住所編集 = 論理削除＋新規 INSERT。注文時の住所オーナー検証は `is_active` を問わない。Console UI では `is_active=false` を選択肢に出さない（S14-10）。

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

# phase15 への要請事項（r3 で確定した事項）

phase15 r4 → r5 が長期シグネチャ・完全移行後の構造で実装着手できるよう、phase14 r3 で確定した以下を phase15 側に反映する：

| 項目 | 内容 |
|------|------|
| 実装段取り（Step A 〜 E） | phase15 r4 は **Step A 完了後に Step B として着手**。長期シグネチャ `createForSales(sales_id)` で実装可能 |
| `sales.shipping_method_id` 追加完了（Step A） | phase15 の `DeliveryCreationService.createForSales` を **長期シグネチャ `(sales_id)` で実装可** |
| 注文確定フローの確定 | phase15 は協調仕様のみで OK |
| **予約購入の在庫減算は出荷時** | phase15 r5 で `deliveries` 状態遷移 Service（PENDING → SHIPPED）に「`sales.is_preorder == true` の場合のみ在庫減算フックを追加」する設計変更を反映 |
| **出荷時の在庫不足挙動（S14-1）** | **方針 (A) 例外＋PENDING 維持**。phase15 r5 のステータス遷移 Service にロック後在庫再確認とエラー時 `PENDING` 維持の処理を実装 |
| **予約購入 `sale` の `created_by_user_id`（S14-2）** | **出荷操作を実行した管理者の `user_id`** を `inventory_movements.created_by_user_id` に記録。phase15 r5 の出荷 Service が管理者 `user_id` を渡す |
| 在庫モデル完全移行 | phase15 の並行運用フック（`InventorySyncService`）は phase14 r3 完了時点（Step C 終了）で削除対象。phase15 r4 → r5（Step D）で削除 |
| `shipping_statuses` マスタ拡張 | CANCELED / DELIVERY_FAILED / RESCHEDULED が利用可。マスタ存在 ≠ 入力許容 |
| `inventory_movements` 利用 | phase15 の入荷登録 Service は `inventory_movements` への INSERT も追加。`quantity` は符号付き、`direction` カラムなし。`movement_type` × 符号の DB CHECK あり（S14-7） |
| 機能フラグ `inventory.read_source` | phase15 の在庫参照箇所も機能フラグで切替可能に実装。子キー `console_display` も含む（S14-3） |
| 共通命名規約 | `docs/ai_context/operation_logs_naming.md` を本書 r1 で参照宣言済み |

---

# レビューコメント対応サマリ（r1 → r2 → r3）

## r1 で対応済み（再掲）
| ID | 由来 | 対応 |
|----|------|------|
| P14-1 〜 P14-3 | phase15 r3 / r4 起点（🔴 必須） | r1 で完了 |
| P14-4 〜 P14-8 | phase15 r3 / r4 起点（🟡 推奨） | r1 で完了 |
| P14-9 〜 P14-17 | phase15 起点 + phase14 単独（🟢 軽微） | r1 で完了 |

## r2 で対応済み（再掲）
| ID | 優先度 | 対応 |
|----|--------|------|
| Q14-1 | 🔴 必須 | 実装段取り Step A 〜 D を冒頭に明記 |
| Q14-2 | 🔴 必須 | 本番リリース前スキーマ確定前提を宣言 |
| Q14-3 | 🔴 必須 | 予約購入の在庫減算を出荷時に確定 |
| Q14-4 | 🟡 推奨 | `shipping_statuses` 機能対応フェーズを明記、Service バリデーション制御 |
| Q14-5 | 🟡 推奨 | `inventory_movements.direction` 廃止 + `quantity` 符号付き化 |
| Q14-6 | 🟡 推奨 | 住所オーナー検証は `is_active` を問わない |
| Q14-7 | 🟡 推奨 | `payment_id` UNIQUE 違反時の冪等処理 |
| Q14-8 | 🟡 推奨 | 完全移行 Step 2 を機能フラグで段階化 |
| Q14-9 〜 Q14-14 | 🟢 任意 | r2 で完了 |

## r3 で新規対応（S14-1 〜 S14-12）
| ID | 優先度 | 対応 |
|----|--------|------|
| S14-1 | 🟡 推奨 | **予約購入の出荷時に在庫不足だった場合は例外＋ `PENDING` 維持**（方針 A 採用）。再構築 SQL も整合 |
| S14-2 | 🟡 推奨 | **予約購入の `sale` 記録の `created_by_user_id` を「出荷操作の管理者」に細分化**。Q14-11 の運用ルール表を `sale`（通常）/ `sale`（予約）に分割 |
| S14-3 | 🟡 推奨 | **機能フラグの粒度に `console_display` を追加**。継承ロジックを擬似コードで明文化 |
| S14-4 | 🟡 推奨 | **Step E（クリーンアップ）を新設**し、`products.stock` DROP と機能フラグ撤去を同時実施。死コード残留を防止 |
| S14-5 | 🟡 推奨 | **`payment_id` UNIQUE 違反時の冪等処理に `user_id` / `product_id` / `quantity` / `amount` 全項目一致チェックを追加**（決済 ID 盗用による他人購入取得の脆弱性を塞ぐ） |
| S14-6 | 🟢 任意 | 注文確定フローの予備バリデーションとロック後再確認を明記。「ステップ 1 は予備、ステップ 3 で最終判定」を擬似コードに反映 |
| S14-7 | 🟢 任意 | `inventory_movements` の `movement_type` × `quantity` 符号の `CHECK` 制約を追加（DB 二重防御） |
| S14-8 | 🟢 任意 | `address.is_active` は r0 相当で追加済と明記。Step A の作業項目から削除 |
| S14-9 | 🟢 任意 | 予約購入の出荷時減算 異常系テストを追加（在庫不足・遷移ガード拒否・トランザクション例外） |
| S14-10 | 🟢 任意 | Console の配送先変更 UI で `is_active=false` の住所をフィルタする運用を追加。テストケースも追加 |
| S14-11 | 🟢 任意 | UUID v7 ライブラリ選定基準（License・GitHub Star・最新リリース日）を技術検討事項に追記 |
| S14-12 | 🟢 任意 | 改訂履歴の「改訂版（r0 相当）」表記は本書命名規則として一貫。指摘認識のみ記載 |
