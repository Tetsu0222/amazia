
# # フェーズ14：購入機能（改訂版）

## ステータス
🔲 未着手

## 範囲
- Amazia Console  
- Amazia Market  
- Amazia Core  
- DB設計（新規テーブル・マスタ化含む）

---

# # 全体方針（レビュー反映）

## 🔹 分析画面との連携方針（最低限の方向性を定義）
将来の分析画面を見据え、以下の分析軸を想定しデータ保持方針を決定する：

- **RFM分析**（Recency / Frequency / Monetary）  
- **商品別売上分析**（カテゴリ別・発売日別・予約比率など）  
- **ユーザ行動分析**（購入導線、予約→購入転換率など）

→ これにより、売上テーブルは「過度に正規化しない柔軟構造」を維持しつつ、  
　**マスタテーブル化（決済方法・配送ステータス）** を行う。

---

# # 予約ステータスロジックの一元化（Core に集約）

Console / Market で重複していた予約判定ロジックを **Amazia Core に統合** し、  
以下のステータスを返す API を提供する。

### ■ 予約ステータス一覧（Enum）
| ステータス | 説明 |
|-----------|------|
| NOT_PUBLIC | 公開前（公開日未到達） |
| PRE_ORDER_NOT_STARTED | 公開済・予約開始前 |
| PRE_ORDER | 予約受付中（公開日 < 発売日） |
| ON_SALE | 発売済（公開日 >= 発売日） |
| BACK_ORDER | 発売済・在庫切れだが予約受付可能 |
| SOLD_OUT | 完売（予約不可） |

→ Market / Console はこのステータスを参照して UI 表示を統一。

---

# # 配送ステータスのマスタ化（Enum）

| ステータス | 説明 |
|-----------|------|
| PENDING | 配送準備中 |
| SHIPPED | 配送済 |
| DELIVERED | 配送完了 |
| RETURN_REQUESTED | 返品申請中 |
| RETURNED | 返品完了 |

---

# # Amazia Console（改訂）

## 売上管理画面

### ■ 集計要件（拡張）
- 年/月/日単位  
- **商品別集計**  
- **ユーザ別集計**  
- **決済方法別集計**  
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
- 返品理由（ユーザ入力）  
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

---

# # Amazia Market（改訂）

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

---

## 配送情報（仕様補強）
- 登録住所 or 新規住所  
- コンビニ受け取り：**店舗検索API（将来）を想定**  
- 置き配：**商品属性（allow_dropoff）と連携**

---

## 購入履歴
- 購入日時  
- 商品名  
- 金額  
- 配送予定日  
- 配送ステータス  
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

# # DB設計（改訂）

## sales テーブル（改訂版）

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | 購入ユーザID |
| product_id | BIGINT | 商品ID |
| amount | INT | 金額 |
| payment_method_id | BIGINT | 決済方法マスタID |
| shipping_address_id | BIGINT | 住所マスタID |
| shipping_date | DATE | 配送日 |
| sales_date | DATE | 売上日 |
| shipping_status_id | BIGINT | 配送ステータスマスタID |
| payment_id | VARCHAR(100) | 決済ID（模擬でも発行） |
| is_preorder | BOOLEAN | 予約購入か |
| created_at | DATETIME | 作成日時 |
| updated_at | DATETIME | 更新日時 |

---

## address テーブル（新規）
住所の再利用・履歴管理のため分離。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | 所有ユーザ |
| postal_code | VARCHAR(20) | 郵便番号 |
| prefecture | VARCHAR(50) | 都道府県 |
| city | VARCHAR(100) | 市区町村 |
| address_line | VARCHAR(255) | 住所 |
| building | VARCHAR(255) | 建物名 |
| created_at | DATETIME | 作成日時 |

---

## payment_methods（マスタ）
| id | name |
|----|------|
| 1 | credit_card |
| 2 | d_payment |
| 3 | cash_on_delivery |

---

## shipping_statuses（マスタ）
前述の Enum を格納。

---

## operation_logs（改訂）
| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| user_id | BIGINT | 操作ユーザ |
| action | VARCHAR(100) | 操作内容 |
| target_type | VARCHAR(50) | 対象種別 |
| target_id | BIGINT | 対象ID |
| screen_name | VARCHAR(100) | 画面名 |
| api_name | VARCHAR(100) | API名 |
| comment | TEXT | 任意コメント |
| created_at | DATETIME | 操作日時 |

---

## sales_return テーブル（新規）
返品ワークフロー管理。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| sales_id | BIGINT | 対象売上 |
| status | VARCHAR(50) | REQUESTED / APPROVED / REJECTED / REFUNDED |
| reason | TEXT | 返品理由 |
| approver_id | BIGINT | 承認者 |
| approved_at | DATETIME | 承認日時 |
| created_at | DATETIME | 作成日時 |

---

# # 技術検討事項（補強）

- 予約ステータスは **リアルタイム判定**（バッチ不要）  
- 売上集計のインデックス  
  - sales_date  
  - product_id  
  - user_id  
  - payment_method_id  
- 決済インターフェースは将来の本番決済APIに差し替え可能な構造にする  
- 配送ステータスはマスタ化し、外部配送API連携も想定  

---

# # TDDテストケース（異常系追加）

## Amazia Core / JUnit
- 売上登録・更新・取得  
- 集計処理（年/月/日 + 商品別/ユーザ別/決済方法別）  
- 操作履歴の記録  
- 予約ステータス判定（境界値：公開日＝今日、発売日＝今日）  
- **異常系：不正ステータス・不正日付**

## Amazia Market / PHPUnit
- カート追加・削除  
- セッション → DB カートマージ  
- 模擬決済成功  
- **模擬決済エラー（バリデーション）**  
- 配送情報未入力エラー  
- 購入履歴表示  
- 予約ステータス表示（Core API 連携）  

以下に、あなたが挙げてくれた **「追加で検討するとさらに良くなる点（軽微）」** を、  
既存の改訂版設計書に **追記すべき内容として整理した md 形式の改善案** をまとめました。

既存設計書にそのまま追記できる粒度で書いています。

---

# 🔧 追加で検討するとさらに良くなる点（軽微・追記案）

## 1. 予約ステータス判定の「優先順位ルール」明文化

予約ステータスは複数の条件（公開日・予約開始日・発売日・在庫）が絡むため、  
**優先順位ルールを Core に明示的に定義**する。

### ■ 予約ステータス判定の優先順位（上から順に適用）
1. **公開前（公開日 > 今日）**  
   → NOT_PUBLIC

2. **予約開始日が設定されており、今日 < 予約開始日**  
   → PRE_ORDER_NOT_STARTED

3. **公開済 & 発売日前（公開日 ≤ 今日 < 発売日）**  
   → PRE_ORDER

4. **発売日以降（今日 ≥ 発売日）**  
   - 在庫あり → ON_SALE  
   - 在庫なし → BACK_ORDER or SOLD_OUT（予約可否で分岐）

5. **在庫なし & 予約不可**  
   → SOLD_OUT

→ この優先順位を API 仕様として Core に固定し、Console / Market はこの結果のみを利用する。

---

## 2. address テーブルの履歴管理方針

現状は「最新住所」を保持する構造だが、  
**過去住所の扱いを明確化**することで運用が安定する。

### ■ 履歴管理の選択肢
| 方針 | 説明 |
|------|------|
| 論理削除（is_active フラグ） | 過去住所も保持し、sales から参照可能。履歴追跡が容易。 |
| 物理削除 | 最新住所のみ保持。過去住所は sales にコピーされるため問題なし。 |

### ■ 推奨方針
- **論理削除（is_active = false）で過去住所を保持**  
- sales.shipping_address_id は過去住所を参照し続けるため、  
  **購入時点の住所が正しく保持される**

### ■ address テーブルに追記
| カラム名 | 型 | 説明 |
|---------|-----|------|
| is_active | BOOLEAN | 現在利用中の住所か（false なら過去住所） |

---

## 3. 返品ワークフローの通知設計（軽微だが重要）

返品・返金ワークフローは承認制のため、  
**通知設計を追加することで UX と運用性が向上**する。

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

### ■ sales_return テーブルに追記すべき項目
| カラム名 | 型 | 説明 |
|---------|-----|------|
| notified_user | BOOLEAN | ユーザ通知済みか |
| notified_admin | BOOLEAN | 管理者通知済みか |

---

# レビューコメント（phase14 r1 化に向けて）

レビュー日：2026-05-06
レビュー対象：phase14_shipping.md（現行版＝改訂版）
参照：phase15_delivery_management_r3.md / phase18_inquiry_management.md / docs/coding_guidelines.md

## 経緯と本セクションの位置付け

phase15（配送管理）の設計レビューを r1 〜 r3 まで繰り返す中で、phase15 単独では解決できない論点が **phase14 r2 への要請事項** として積み上がっている。phase15 r3 が実装着手レベルに到達するためには、phase14 側を r1 化（または r2 化）して以下を確定する必要がある。

本セクションでは、phase14 r1 で対応すべき論点を **phase15 起点の要請** と **phase14 単独で見直すべき論点** に分けて整理する。

---

## 🔴 重大（phase15 r3 の実装着手前に確定が必要）

### P14-1. `products.stock` 廃止と `inventories` への完全移行（phase15 RRR-1 由来）

**現状：** phase14 では商品の在庫を `products.stock`（カラム）で保持している前提で、購入処理・予約ステータス判定・返品時の在庫戻しが書かれている。

**phase15 r3 での決定：** `inventories(product_id, warehouse_id, quantity)` テーブルを新規導入。phase15 では並行運用にとどめ、`products.stock` 廃止と完全移行は **phase14 r2 のスコープ** に切り出された。

**phase14 r1（または r2）で確定すべきこと：**
- 販売処理（在庫減算）を `products.stock` から `inventories.quantity` 参照に書き換える時期と手順
- 予約ステータス判定（`BACK_ORDER` / `SOLD_OUT`）の在庫参照先を `inventories` に切り替える
- `sales_return` 承認時の在庫戻し処理も `inventories` 加算に切り替える
- `products.stock` カラムをいつ DROP するか（並行運用→完全移行の切替タイミング）

**並行運用期の整合性リスク（phase15 RRRR-2 由来）：**
phase15 r3 は入荷時に `inventories` と `products.stock` を両方加算する。一方、販売側の在庫減算が `products.stock` のみだと、時間が経つほど両者が乖離する。

→ phase14 r1 で **販売側の在庫減算 Service にも `inventories.quantity` 減算フックを追加**する（並行運用期間中の整合性担保）か、**`inventories` を「入荷累積記録」と割り切って完全移行時に再構築する**かを決定する必要がある。

### P14-2. 注文確定フローの確定（phase15 RRR-2 由来）

**現状：** phase14 の購入処理は「模擬決済 → sales 登録」程度しか書かれておらず、トランザクション境界・在庫減算順序・バリデーション順が未定義。

**phase15 r3 からの要請：**
- `DeliveryCreationService.createForSales(sales_id)` を注文確定 Service から呼び出す
- 通常購入で在庫切れの場合は注文を拒否する（予約購入＝`is_preorder=true` の場合のみ在庫切れでも成立）
- バリデーション・在庫減算・`sales` INSERT・`deliveries` 生成は同一トランザクション

**phase14 r1 で確定すべきこと：** 以下の擬似コードレベルでフローを明文化する。

```
1. validateOrder(sales_request)
   - is_preorder == false かつ stock <= 0 なら拒否
2. begin transaction
3. stock 減算（並行運用期は products.stock、完全移行後は inventories）
4. INSERT sales
5. DeliveryCreationService.createForSales(sales.id)
6. commit
```

### P14-3. `sales` テーブルへの `shipping_method_id` 追加（phase15 RRRR-3 由来）

**現状：** phase14 の `sales` テーブルには `payment_method_id` はあるが、配送方法を保持するカラムが無い（200-211 行目）。

**問題：** phase15 r3 の `DeliveryCreationService.createForSales(sales_id)` が `deliveries.shipping_method_id` の値を決められない。

**phase14 r1 で確定すべきこと：** いずれかを選択。

| 案 | メリット | デメリット |
|----|---------|-----------|
| (A) `sales` に `shipping_method_id` を追加 | 責務分担がきれい。注文時点の選択が `sales` に残り集計しやすい | phase14 のスキーマ変更が必要 |
| (B) 注文確定 API リクエストで受け取り、phase15 が直接 `deliveries` に書く | phase14 のスキーマ変更不要 | `sales` から配送方法が辿れず、売上集計に「配送方法別」を追加しにくい |
| (C) `DeliveryCreationService.createForSales(sales_id, shipping_method_id)` に拡張 | 中間案 | `deliveries` の生成入力が分散する |

→ 売上分析画面（RFM 分析・決済方法別集計）と整合させる観点では **(A) を推奨**。phase14 の `sales` テーブル定義に `shipping_method_id BIGINT NOT NULL` を追加する。

---

## 🟡 中程度（phase14 r1 で整理推奨）

### P14-4. `shipping_statuses` マスタの拡張（phase15 RR-1 由来）

**phase15 r3 でスコープ外宣言された機能：**
- 発送前キャンセル
- 配達失敗・持ち戻り
- 再配達

これらは `shipping_statuses` マスタに以下のステータス追加が前提となる。phase14 r1 で追加するか、別フェーズに切り出すかを決定する。

| 追加ステータス | 説明 |
|-------------|------|
| CANCELED | 発送前キャンセル |
| DELIVERY_FAILED | 配達失敗・持ち戻り |
| RESCHEDULED | 再配達手配中（必要なら） |

**推奨：** マスタ拡張だけ phase14 r1 で実施し、機能としての「キャンセル UI」「配達失敗フロー」は別フェーズに切り出してもよい。マスタは将来の拡張余地を残しておく方が安全。

### P14-5. `operation_logs` のスキーマ拡張（phase15 RRR-5 由来）

**phase15 r3 の決定：** `update_scheduled_date` の `comment` 先頭に `[manual]` / `[inbound_recalc]` / `[shipping_delay]` をプレフィックス固定。

**将来課題：** 集計需要が高まった段階で `operation_logs` に `reason_code VARCHAR(50)` カラムを追加し、プレフィックスから切り出す。

**phase14 r1 で確定すべきこと：** 上記 `reason_code` 追加を phase14 r1 の `operation_logs` 設計に **先行して入れるか**、**将来課題として残すか**を判断。先行で入れる場合のスキーマ：

| カラム名 | 型 | 説明 |
|---------|-----|------|
| reason_code | VARCHAR(50) | 操作の理由コード（NULLABLE。フリーテキストの `comment` と併用） |

→ 先行追加するメリットは「最初から構造化された集計ができる」、デメリットは「phase15 の `[manual]` プレフィックスとの二重持ちになる」。私としては **将来課題として残す**（phase15 のプレフィックス方式で十分）方を推奨。

### P14-6. `inventory_movements` テーブル新設の判断（phase15 RRR-6 由来）

**現状：** 在庫増減の履歴は `inbounds`（入荷）でしか追跡できない。販売・キャンセル・返品復元・棚卸補正の履歴は分散している。

**phase15 r3 からの要請：** `inventory_movements(id, product_id, warehouse_id, movement_type, quantity, reference_type, reference_id, created_at)` のような構造で在庫増減ログを一元化。

**phase14 r1 で確定すべきこと：**
- このテーブルを phase14 r1 で導入するか、別フェーズ（在庫管理フェーズ）に切り出すか
- 命名衝突の確認（Odoo / ERPNext 等の他 ERP 慣習との整合）
- `movement_type` の enum 定義（`inbound` / `sale` / `cancel` / `return` / `adjustment` など）

**推奨：** P14-1（`inventories` 完全移行）と密接に関連するため、**phase14 r1 で同時設計**することを推奨。完全移行後に履歴を再構築するのは現実的ではない。

### P14-7. `address.user_id` 制約と「自分の住所のみ参照可能」のバリデーション（phase15 RRRR-7 由来）

**phase15 r3 の決定：** `sales.shipping_address_id` は `sales.user_id` が所有する `address` のみ参照可能であることを Service 層で強制（phase15 RRRR-7）。

**phase14 r1 で確定すべきこと：**
- `address.user_id` が「住所の所有者」を表すという定義を明文化
- 同 user_id 以外の `address.id` を `sales.shipping_address_id` に指定できないバリデーションを **phase14 の購入 Service** に組み込む（phase15 ではなく phase14 の責務）
- ギフト配送（購入者 ≠ 配送先）はスコープ外（phase15 r3 と整合）

### P14-8. 在庫の同時実行制御方針（phase15 RRRR-8 由来）

**phase15 r3 の決定：** `inventories.quantity` 減算は `SELECT ... FOR UPDATE`（悲観ロック）。`CHECK (quantity >= 0)` 制約も付与。

**phase14 r1 で確定すべきこと：** 販売側の在庫減算（`products.stock` も並行運用期は対象）の同時実行制御方針を phase14 にも明記。phase15 と方針を揃える：

- 販売 Service の在庫減算は `SELECT ... FOR UPDATE` で行を取得してから減算
- 並行運用期は `products.stock` も同様にロック
- 完全移行後は `inventories.quantity` のみ

---

## 🟢 軽微（任意）

### P14-9. マイグレーション仕様の記載（phase15 RRRR-1 由来）

phase15 r3 で並行運用開始時のマイグレーション（`inventories` の初期データ投入＝`products.stock` から複製）が必要となった。phase14 r1 で完全移行する際にも以下のマイグレーションが必要：

- 並行運用期に乖離した `inventories.quantity` を販売・返品履歴から再構築する手順
- 切替時の整合性チェック（`SUM(inventories.quantity) == SUM(products.stock)` を確認）
- `products.stock` を DROP する SQL の実行タイミング

**推奨対応：** phase14 r1 に「完全移行マイグレーション仕様」セクションを追加。

### P14-10. 共通命名規約の参照（phase15 RR-10 / RRR-9 由来）

phase15 r3 で「`screen_name` / `api_name` の命名規約」を共通 ai_context（`docs/ai_context/operation_logs_naming.md`）に定義することが提案された。phase14 の `operation_logs` 設計でも同じ規約を参照することを明記すべき。

**推奨対応：** phase14 r1 の `operation_logs` セクションに「`screen_name` / `api_name` の命名規約は `docs/ai_context/operation_logs_naming.md` に従う」と1行追記。

### P14-11. 改訂履歴セクションが無い

phase14 は冒頭に「フェーズ14：購入機能（改訂版）」とあるが、改訂履歴テーブルがない。phase15 r3 のように `| 版 | 日付 | 内容 |` 形式の改訂履歴を r1 化のタイミングで追加することを推奨。トレーサビリティが向上する。

### P14-12. 冒頭の `# #` 表記揺れ（軽微）

ファイル冒頭が `# # フェーズ14：購入機能（改訂版）` と Markdown の `#` が分離している。`# フェーズ14：購入機能（改訂版）` に統一すべき（他のセクション見出しも `# #` で書かれている箇所が多数あり、Markdown レンダラーによってはレベル1 + 文字列 `#` として表示される）。r1 化に合わせて整形推奨。

---

## phase14 単独の追加観点（phase15 起点ではない）

### P14-13. 模擬決済の `payment_id` 採番方式が未定義

「決済 API モックでも `payment_id（UUID）を発行` し sales に保存」（156 行目）とあるが、UUID v4 か v7 か、どこで採番するか（Core / Market）が未定義。本番決済 API に差し替えた際、`payment_id` の長さや形式が外部システム由来になることを考慮するなら、`VARCHAR(100)` 程度を確保すべき。スキーマでは `payment_id VARCHAR(100)` と既に定義されているので問題ないが、採番ロジックの所在を本書に明記しておくと実装ブレがない。

### P14-14. `sales_return` テーブルの返品理由分類

「返品理由（ユーザ入力）」とあるがフリーテキストのみ。返品分析（不良品 / イメージ違い / サイズ違い等）を将来扱うなら、`return_reason_code` のマスタ化を検討すべき。本フェーズでは入れないとしても、技術検討事項に1行残すと将来の拡張がスムーズ。

### P14-15. `address` テーブルの履歴管理と `is_active` の運用

「論理削除（is_active = false）で過去住所を保持」（357 行目）と決まっているが、

- ユーザーが住所を「編集」した場合、旧住所は `is_active=false` にして新住所を `is_active=true` で INSERT するのか、それとも UPDATE で上書きするのか
- `sales.shipping_address_id` は過去住所を参照し続けるので、編集時は INSERT しないと購入時点の住所が壊れる

→ 「住所編集 = 論理削除＋新規 INSERT」と運用ルールを明文化推奨。

### P14-16. operation_logs の `comment` カラム長制限

`comment TEXT` で定義されているが、フリーテキスト + プレフィックスを格納するなら問題なし。ただし、**ログ検索のインデックス**を考えると、本格運用時にフルテキスト検索やパーティショニングが必要になる可能性がある。技術検討事項に1行追記推奨。

### P14-17. 予約ステータスの境界値テスト範囲

「予約ステータス判定（境界値：公開日＝今日、発売日＝今日）」（294 行目）とあるが、タイムゾーンの扱いが未定義。「今日」が JST 0 時基準か UTC 基準かでテストの結果が変わる。日本国内のみのサービスでも、サーバ時刻と DB 時刻の整合は明記したい。

---

## まとめ：phase14 r1 での対応優先度

| 優先度 | 項目 | 概要 | 由来 |
|--------|------|------|------|
| 🔴 必須 | P14-1 | `products.stock` 廃止と `inventories` 完全移行の段取り。並行運用期の販売側 `inventories` 減算フックの方針 | phase15 RRR-1 / RRRR-2 |
| 🔴 必須 | P14-2 | 注文確定フローの確定（バリデーション・トランザクション境界・`DeliveryCreationService` 呼び出し） | phase15 RRR-2 |
| 🔴 必須 | P14-3 | `sales.shipping_method_id` の追加 | phase15 RRRR-3 |
| 🟡 推奨 | P14-4 | `shipping_statuses` マスタ拡張（CANCELED / DELIVERY_FAILED 等）の判断 | phase15 RR-1 |
| 🟡 推奨 | P14-5 | `operation_logs.reason_code` 追加の判断 | phase15 RRR-5 |
| 🟡 推奨 | P14-6 | `inventory_movements` テーブル新設の判断 | phase15 RRR-6 |
| 🟡 推奨 | P14-7 | `address.user_id` 制約と「自分の住所のみ参照可能」バリデーション | phase15 RRRR-7 |
| 🟡 推奨 | P14-8 | 在庫の同時実行制御方針（悲観ロック）を phase14 にも明記 | phase15 RRRR-8 |
| 🟢 任意 | P14-9 | 完全移行マイグレーション仕様 | phase15 RRRR-1 |
| 🟢 任意 | P14-10 | 共通命名規約の参照 | phase15 RR-10 / RRR-9 |
| 🟢 任意 | P14-11 | 改訂履歴セクション追加 | phase14 単独 |
| 🟢 任意 | P14-12 | 冒頭 `# #` 表記揺れの整形 | phase14 単独 |
| 🟢 任意 | P14-13 | `payment_id` 採番方式の明記 | phase14 単独 |
| 🟢 任意 | P14-14 | `return_reason_code` マスタ化の検討（将来課題） | phase14 単独 |
| 🟢 任意 | P14-15 | 住所編集時の論理削除＋新規 INSERT 運用の明文化 | phase14 単独 |
| 🟢 任意 | P14-16 | `operation_logs.comment` の検索性に関する技術検討 | phase14 単独 |
| 🟢 任意 | P14-17 | 予約ステータスのタイムゾーン明記 | phase14 単独 |

---

## 結論

phase15 r3（実装着手レベル）を真に着手可能にするには、**P14-1（在庫モデル完全移行）／ P14-2（注文確定フロー）／ P14-3（`sales.shipping_method_id`）** の3点を phase14 r1 で確定する必要がある。これらは phase15 単独では解決不能で、phase14 のスキーマ・購入処理・在庫モデルに踏み込む必要があるため。

P14-4 〜 P14-8 は phase15 起点の中程度論点で、phase14 r1 のタイミングで方針だけ決めておくと後続フェーズの設計が安定する。

P14-9 以降は phase14 単独でも気付くべき軽微な論点で、r1 化の作業に合わせて同時に整える程度でよい。

**phase14 r1 のスコープ目安：** 🔴 必須3件 + 🟡 推奨5件 = 計8件を最低限カバーすれば、phase15 r3 の実装着手と整合する。🟢 任意は r1 化のタイミング次第で選択的に取り込めばよい。

