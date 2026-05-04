
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
