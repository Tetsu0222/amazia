# フェーズ15：配送管理（改訂版 r1）

## ステータス
🔲 未着手

## 改訂履歴
| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | - | 初稿 |
| r1 | 2026-05-06 | レビューコメント R-1 〜 R-12 を反映。phase14_shipping.md との整合、tracking_code の責務分離、入荷管理（inbounds）の DB 設計追加、配送ステータスの統一、operation_logs 反映、異常系テスト追加など。 |

## 範囲
- Amazia Console
- Amazia Market
- Amazia Core
- DB設計

## 機能概要
- Amazia Console に配送管理機能（顧客への配送・商品入荷）を追加
- Amazia Core に配送管理テーブルおよび CRUD を実装
- Amazia Market の購入履歴に配送情報を反映
- 将来的な問い合わせ管理（phase18）との連携を見据えた設計とする

---

# 全体方針（R-1 / R-2 対応）

## 🔹 phase14 設計との責務分担

phase14_shipping.md でメインに定義済みのテーブル（`sales` / `address` / `shipping_statuses` / `payment_methods`）と本フェーズで新規追加するテーブルの責務を以下のとおり明文化する。

| テーブル | 定義フェーズ | 役割 |
|---------|-------------|------|
| `sales` | phase14 | **注文時点のスナップショット**（住所マスタID・配送ステータスID・配送日希望など、購入確定時の情報） |
| `address` | phase14 | 住所マスタ。`is_active` で論理削除（履歴管理） |
| `shipping_statuses` | phase14 | 配送ステータスマスタ（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED） |
| `payment_methods` | phase14 | 決済方法マスタ |
| `deliveries` | **phase15（本書）** | **実配送オペレーション**（実際の発送日・配達完了日・配送業者の追跡番号など、配送実体としての情報） |
| `shipping_methods` | **phase15（本書）** | 配送方法マスタ（宅配 / コンビニ受取 / 置き配） |
| `inbounds` | **phase15（本書）** | 商品の入荷（仕入）管理。顧客への配送とは責務を分離 |

### 設計上の原則
- **`sales` は「注文確定」のスナップショット、`deliveries` は「配送実体」**。住所変更や再配達などの配送オペレーション情報は `deliveries` 側で扱う。
- 住所は VARCHAR で再保持せず、`address.id`（phase14）を FK 参照する。
- 配送ステータスは VARCHAR ではなく `shipping_statuses` マスタ（phase14）への FK（`shipping_status_id`）で持つ（規約 3-1「権限・定数・マスタは config／マスタ化」と整合）。

## 🔹 tracking_code の責務分離（R-2 対応）

初版で混在していた「配送追跡番号」と「問い合わせ番号」を分離する。

- `tracking_code VARCHAR(100)` … **配送業者の追跡番号**（純粋に配送用途）。`deliveries` テーブルが保持。
- 問い合わせとの紐付けは **`inquiries` 側で `target_type='delivery' / target_id=deliveries.id` を持つ方式**（phase18 と整合）。`deliveries.inquiry_id` は持たない。
  - 問い合わせ対象は配送以外（商品・売上）にも広がるため、対象側 FK を `deliveries` に持たせるとスケールしない。
  - phase18 設計書側で `inquiries.target_type` / `target_id` の追加を要請する（本書末尾「phase18 への要請事項」参照）。

---

# 機能詳細

---

## 🖥 Amazia Console

### 顧客への配送管理
- 購入商品の配送状況を管理
- 配送ステータス更新（phase14 マスタ：PENDING → SHIPPED → DELIVERED、または RETURN_REQUESTED / RETURNED）
- 配送先情報の確認・変更（住所変更時は phase14 `address` の `is_active` 切替に従う）
- 配送追跡番号（`tracking_code`）の登録・参照
- 将来的に問い合わせ画面と相互リンク（`inquiries.target_type='delivery'` 経由）

### 商品の入荷管理
- 商品入荷情報を登録（`inbounds` テーブル）
- 入荷日・数量・倉庫情報・仕入先（任意）を管理
- 在庫テーブルと連動（**Service 層で在庫数を増加**。Model にロジックを書かない／規約 1-1）

### Console 操作の operation_logs 記録（R-8 対応）
phase14 の方針に倣い、以下の管理操作はすべて `operation_logs` に記録する（5W1H 追跡）。

| action | target_type | target_id | comment 例 |
|--------|-------------|-----------|-----------|
| `update_shipping_status` | `deliveries` | deliveries.id | 旧ステータス・新ステータス・理由 |
| `update_shipping_address` | `deliveries` | deliveries.id | 旧 address_id・新 address_id・理由（phase14 と整合） |
| `register_tracking_code` | `deliveries` | deliveries.id | 登録した追跡番号 |
| `register_inbound` | `inbounds` | inbounds.id | 商品ID・数量・倉庫 |

---

## 🧠 Amazia Core

### 配送管理テーブル CRUD
- `deliveries` の CRUD を Core に実装。Console / Market 双方から利用。
- 配送ステータス遷移は Service 層で**遷移ルール**を管理（後述「配送ステータス遷移ルール」参照）。
- 配送予定日の自動計算は `DeliveryScheduleService` に集約（後述「配送予定日の計算仕様」）。

### 入荷テーブル CRUD
- `inbounds` の CRUD を Core に実装。
- 入荷登録時、Service 層で対象商品の在庫数を加算。`inbounds` レコードと在庫加算は同一トランザクションで実行。

---

## 🛒 Amazia Market

### 購入履歴への配送情報反映
- 購入履歴画面に以下を表示
  - 配送ステータス（`shipping_statuses` マスタの表示名）
  - 配送予定日（`scheduled_date`）
  - 配送完了日（`delivered_date`、あれば）
  - 配送方法（`shipping_methods` マスタの表示名）
- 表示元は `deliveries` テーブル（実配送）。`sales` 側は注文時点スナップショットのため、Market 表示は `deliveries` を優先（R-11 対応：二重持ち回避）。
- `deliveries` がまだ生成されていない購入直後の状態では、`sales.shipping_status_id` を表示（フォールバック）。

---

# DB設計（追加）

## deliveries テーブル（新規：配送実体）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| sales_id | BIGINT | NOT NULL | 売上ID（phase14 `sales.id` への FK） |
| user_id | BIGINT | NOT NULL | 配送先ユーザID（**ギフト配送など購入者≠配送先の場合に備えた冗長保持**／R-5 対応：理由を明記） |
| shipping_address_id | BIGINT | NOT NULL | 配送先住所（phase14 `address.id` への FK／R-1 対応） |
| shipping_method_id | BIGINT | NOT NULL | 配送方法マスタID（`shipping_methods.id`／R-9 対応） |
| shipping_status_id | BIGINT | NOT NULL | 配送ステータスマスタID（phase14 `shipping_statuses.id`／R-1・R-4 対応） |
| tracking_code | VARCHAR(100) | NULL | **配送業者の追跡番号**（R-2 対応：問い合わせ番号とは分離） |
| scheduled_date | DATE | NULL | 配送予定日（在庫切れ等で確定不能なときは NULL／R-6 対応） |
| shipped_date | DATE | NULL | 発送日 |
| delivered_date | DATE | NULL | 配達完了日 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- `sales_id` は phase14 `sales.id` を参照（FK）。
- `shipping_address_id` は phase14 `address.id` を参照（FK）。
- `shipping_status_id` は phase14 `shipping_statuses.id` を参照（FK）。
- `shipping_method_id` は本書 `shipping_methods.id` を参照（FK）。
- `user_id` を冗長保持する理由：**ギフト配送（購入者 sales.user_id ≠ 配送先ユーザ）を将来サポートするため**。当面は購入者と一致する運用だが、後追いマイグレーションコストを抑える目的でカラムを先行確保する（R-5 対応）。

### インデックス方針（R-10 対応）
| インデックス | 用途 |
|-------------|------|
| `idx_deliveries_sales_id` | sales からの逆引き（購入履歴・売上画面） |
| `idx_deliveries_shipping_status_id` | ステータス別の一覧・集計（Console 配送管理画面） |
| `idx_deliveries_tracking_code` | 追跡番号での問合わせ検索 |
| `idx_deliveries_scheduled_date` | 配送予定日でのバッチ・一覧フィルタ |

---

## shipping_methods テーブル（新規：配送方法マスタ／R-9 対応）

| id | name | description |
|----|------|-------------|
| 1 | home_delivery | 宅配 |
| 2 | konbini_pickup | コンビニ受取 |
| 3 | dropoff | 置き配 |

phase14 の決済方法マスタ（`payment_methods`）と同じ方針で集計容易性を担保する。

---

## inbounds テーブル（新規：商品入荷管理／R-3 対応）

顧客への配送（`deliveries`）と責務を分離するための独立テーブル。

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| product_id | BIGINT | NOT NULL | 商品ID（FK） |
| warehouse_id | BIGINT | NULL | 倉庫ID（倉庫マスタ未整備の場合 NULL 許容。将来マスタ化） |
| supplier_id | BIGINT | NULL | 仕入先ID（将来マスタ化） |
| quantity | INT | NOT NULL | 入荷数量 |
| inbounded_at | DATE | NOT NULL | 入荷日 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

### 制約・備考
- 在庫加算は **Service 層で実装**（規約 1-1）。`inbounds` 登録と在庫加算は同一トランザクションで実行。
- `warehouse_id` / `supplier_id` のマスタ化は本フェーズのスコープ外。マスタ化されるまで NULL 許容。

### インデックス方針
| インデックス | 用途 |
|-------------|------|
| `idx_inbounds_product_id` | 商品別の入荷履歴取得 |
| `idx_inbounds_inbounded_at` | 期間別集計 |

---

# 配送ステータス遷移ルール（R-4 / R-7 対応）

## ステータス（phase14 マスタに統一）
PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED

## 遷移可否（Service 層でガード）

| 現在 → 次 | PENDING | SHIPPED | DELIVERED | RETURN_REQUESTED | RETURNED |
|-----------|---------|---------|-----------|------------------|----------|
| PENDING | - | ✅ | ❌ | ❌ | ❌ |
| SHIPPED | ❌ | - | ✅ | ❌ | ❌ |
| DELIVERED | ❌ | ❌ | - | ✅ | ❌ |
| RETURN_REQUESTED | ❌ | ❌ | ❌ | - | ✅ |
| RETURNED | ❌ | ❌ | ❌ | ❌ | - |

- `DELIVERED → PENDING` のような巻き戻しは Service 層で例外を投げて拒否（規約 4-2 異常系）。
- `RETURN_REQUESTED` への遷移は phase14 `sales_return.status = REQUESTED` と連動して発生する。

---

# 配送予定日の計算仕様（R-6 対応）

| 項目 | 仕様 |
|------|------|
| 計算タイミング | **注文確定時に Core 側で算出**し `deliveries.scheduled_date` を初期値設定。出荷時点で前後する場合は更新 API で上書き。 |
| 入力 | (a) 注文日, (b) phase14 `address.prefecture`（地域マスタ代替）, (c) 在庫有無, (d) `shipping_methods` の標準リードタイム |
| 計算ロジックの所在 | `DeliveryScheduleService.calculate(...)` に集約（規約 1-1） |
| 在庫切れ時 | `scheduled_date = NULL`。Market は「入荷待ち」として表示。入荷時（`inbounds` 登録時）に再計算。 |
| 設定値 | リードタイム（地域別・配送方法別）は `config/app/Delivery.php`（PHP）／ `application.yml`（Java）に定数として定義（規約 3-1） |

---

# 技術検討事項
- 配送ステータスの遷移ルール（上記表で確定）
- 配送予定日の自動計算（上記仕様で確定。地域マスタは phase14 `address.prefecture` を流用）
- `tracking_code` の生成方式：**配送業者の発行番号をそのまま登録**（自動採番しない）。手動入力 or 外部 API 連携を想定。
- 入荷管理と在庫管理の連動：Service 層・同一トランザクションで実施
- 配送情報の Market 反映タイミング：`deliveries` 更新時に同期反映（ポーリング不要。購入履歴 API が `deliveries` を直接 JOIN）
- 将来的な問い合わせ管理画面との統合：`inquiries.target_type='delivery'` 経由（phase18 と協議）
- 外部配送 API 連携（phase14 と同方針）

---

# TDDテストケース

## Amazia Core / JUnit

### 正常系
- 配送情報（`deliveries`）が正しく登録・更新・取得できる
- 配送ステータスの更新が正しく反映される（PENDING → SHIPPED → DELIVERED）
- 入荷情報（`inbounds`）登録時に在庫が正しく増加する（同一トランザクション）
- `tracking_code` が正しく保存・取得できる
- 配送予定日が地域・配送方法・在庫状況を入力に正しく算出される
- 在庫切れ時に `scheduled_date = NULL` で登録される

### 異常系（R-7 対応）
- **不正な配送ステータス遷移を拒否**：DELIVERED → PENDING、SHIPPED → PENDING など
- **存在しない `sales_id` での配送登録を拒否**（FK 違反 / Service 層チェック）
- **存在しない `product_id` での入荷登録を拒否**
- **`tracking_code` の重複登録**：仕様としては許容（配送業者側で同番号が異なる配送に振られるケースは現実にあるため）。ただし「同一 `sales_id` に対する重複」は警告ログに記録する
- 入荷登録の在庫加算が失敗した場合、`inbounds` レコードもロールバックされる（トランザクション境界の検証）
- `shipping_status_id` に存在しないマスタ ID を指定した場合の拒否
- `shipping_method_id` に存在しないマスタ ID を指定した場合の拒否

## Amazia Console / PHPUnit
- 配送管理画面で配送情報が正しく表示される
- 配送ステータス更新時に `operation_logs` にレコードが記録される（action / target_type / target_id / screen_name / api_name）
- 配送先変更時に `operation_logs` にレコードが記録される（旧住所・新住所が comment に含まれる）
- 入荷登録時に `operation_logs` にレコードが記録される
- 不正な遷移を Console から要求した場合、エラーレスポンスが返る

## Amazia Market / PHPUnit
- 購入履歴に配送情報（ステータス・予定日・完了日・方法）が正しく表示される
- 配送ステータスが phase14 マスタの表示名で表示される
- 配送方法（home_delivery / konbini_pickup / dropoff）が正しく表示名で表示される
- `deliveries` 未生成時は `sales` 側のステータスにフォールバックする
- 在庫切れ商品の購入履歴で `scheduled_date` が NULL のとき「入荷待ち」と表示される

---

# phase18（問い合わせ管理）への要請事項

R-2 を実現するため、phase18 設計書の `inquiries` テーブルに以下の追加を要請する。

| カラム名 | 型 | 説明 |
|---------|-----|------|
| target_type | VARCHAR(50) | 問い合わせ対象種別（`delivery` / `product` / `sales` ...） |
| target_id | BIGINT | 対象ID（`target_type` と組み合わせて参照） |

- 配送に関する問い合わせ：`target_type='delivery' / target_id=deliveries.id`
- 既に phase18 の `inquiry_messages` がスレッド管理を担うため、`deliveries` 側に `inquiry_id` は持たせない。

---

# レビューコメント対応サマリ

| ID | 優先度 | 対応 |
|----|--------|------|
| R-1 | 🔴 必須 | `deliveries` を「実配送」、`sales` を「注文スナップショット」と責務分離。住所は `address.id` FK、ステータスは `shipping_statuses.id` FK 化 |
| R-2 | 🔴 必須 | `tracking_code` を配送追跡番号に限定。問い合わせ連携は `inquiries.target_type/target_id` 方式（phase18 へ要請） |
| R-3 | 🔴 必須 | `inbounds` テーブル新規追加。在庫加算は Service 層・同一トランザクション |
| R-4 | 🟡 推奨 | 配送ステータスを phase14 マスタ（PENDING/SHIPPED/DELIVERED/RETURN_REQUESTED/RETURNED）に統一 |
| R-5 | 🟢 任意 | `deliveries.user_id` 保持理由（ギフト配送）を本文に明記 |
| R-6 | 🟡 推奨 | 配送予定日の計算仕様（タイミング・入力・所在・在庫切れ時挙動）を明文化 |
| R-7 | 🟡 推奨 | 異常系テスト（不正遷移・FK 違反・トランザクション境界・マスタ不整合）を追加 |
| R-8 | 🟡 推奨 | `operation_logs` 記録対象（ステータス更新・住所変更・追跡番号登録・入荷登録）を明記 |
| R-9 | 🟢 任意 | `shipping_methods` マスタ化。`deliveries.shipping_method_id` で参照 |
| R-10 | 🟢 任意 | インデックス方針（`sales_id` / `shipping_status_id` / `tracking_code` / `scheduled_date`）を追記 |
| R-11 | 🟢 任意 | Market 表示は `deliveries` を主、`sales` をフォールバックに整理 |
| R-12 | 🟢 任意 | 冒頭の余分な空行を削除（本書 r1 で整形済み） |

---

# 再レビューコメント（r1 に対して）

レビュー日：2026-05-06
レビュー対象：phase15_delivery_management_r1.md
参照：phase14_shipping.md / phase18_inquiry_management.md / docs/coding_guidelines.md
前回指摘：R-1 〜 R-12

---

## 総評

初版で指摘した R-1 〜 R-12 はすべて反映されている。特に **R-1（phase14 との責務分担）**、**R-2（tracking_code の責務分離）**、**R-3（inbounds 追加）** の重大3項目は、責務分離テーブル・phase18 への要請事項・Service 層集約の明文化まで含めて丁寧に対応されている。**この方向性で実装フェーズに進んで概ね問題ない**。

ただし、r1 で新たに具体化された箇所（ステータス遷移表・配送予定日計算・フォールバック仕様）に、追加で詰めておきたい論点が残っている。以下、優先度順に再指摘する。

---

## 🟡 中程度（実装前に詰めたい）

### RR-1. ステータス遷移表に「キャンセル」「配達失敗」「再配達」のパスがない

r1 の遷移表は正常進行＋返品系のみで、現実の配送オペレーションで頻出する以下のパスが未定義：

- **発送前キャンセル**（PENDING → ?）：phase14 に CANCELED 系ステータスがないが、注文取消時の `deliveries` の扱いは？レコード削除か、論理削除か、ステータス追加か
- **配達失敗・持ち戻り**（SHIPPED → SHIPPED に戻す or DELIVERY_FAILED）
- **再配達**（SHIPPED 中の配達日変更）

**推奨対応：**
- 本フェーズのスコープに含めないなら「キャンセル・配達失敗・再配達は対象外。将来フェーズで対応」と**明示的に範囲外宣言**する
- 含めるなら phase14 の `shipping_statuses` マスタへ追加ステータスを要請（phase14 を r2 化する必要あり）

どちらでも構わないが、現状は「決めていない」状態に見えるので明文化が必要。

### RR-2. 配送予定日の更新タイミングと operation_logs の関係が抜けている

r1 では：
- 注文確定時に算出 → `scheduled_date` 初期値
- 出荷時点で前後する場合は「更新 API で上書き」
- 在庫切れ時は `inbounds` 登録時に「再計算」

と書かれているが、**この再計算・上書きが operation_logs に残らない**。配送予定日の変更は顧客への影響が大きく、いつ・誰が・何の理由で変えたかを追跡できないと運用上のクレーム対応で困る。

**推奨対応：** R-8 の operation_logs 記録対象表に以下を追加。

| action | target_type | target_id | comment 例 |
|--------|-------------|-----------|-----------|
| `update_scheduled_date` | `deliveries` | deliveries.id | 旧予定日・新予定日・変更理由（手動 / 入荷再計算 / 出荷遅延） |

### RR-3. `tracking_code` 重複登録の「警告ログ」が曖昧

r1 異常系テストに『「同一 `sales_id` に対する重複」は警告ログに記録する』とあるが、

- 「警告ログ」とは何か？ アプリログ？ operation_logs？ 別の監査テーブル？
- そもそも同一 `sales_id` に複数の `deliveries` が紐づくのは正常ケース（分割配送・ギフトで配送先複数）か、異常ケース（オペミス）か

がはっきりしない。**ここの判断はビジネス要件に直結**するので、設計書段階で決めるべき。

**推奨対応：** 以下のどちらかを明文化。

- (A) `sales:deliveries = 1:1` 前提 → DB レベルで `UNIQUE(sales_id)` 制約を入れる
- (B) `sales:deliveries = 1:N` を許容（分割配送等） → 重複検知不要。代わりに「同一 sales 内での tracking_code 重複」のみチェック

### RR-4. Market のフォールバック仕様に競合状態の説明がない

r1 で「`deliveries` 未生成時は `sales` 側のステータスにフォールバック」と決まったが、

- `deliveries` 生成のタイミングは？（注文確定時？ 管理者の出荷準備操作時？）
- 注文直後〜`deliveries` 生成までの間、`sales.shipping_status_id` の初期値は何か（PENDING？ NULL？）

が未定義。Market 表示の一貫性に直結するため明文化したい。

**推奨対応：** 以下のいずれかに決める。

- (A) **注文確定と同時に `deliveries` を生成**（`sales` と1:1で同期作成）。フォールバックは理論上発生せず、ロジックは保険として残す
- (B) **管理者の出荷準備操作時に `deliveries` を生成**。それまで `sales.shipping_status_id = PENDING` を保証

個人的には (A) が単純で運用事故が少ないと考える。Market のロジックに分岐を持ち込むと、Vue/PHPUnit テストのケースも増える。

---

## 🟢 軽微（改善提案）

### RR-5. `deliveries.user_id` 冗長保持の妥当性は要再検討

r1 で「ギフト配送（購入者 ≠ 配送先）を想定した先行確保」と理由が明記されたのは良い。ただし、

- ギフト配送は phase14 の sales / address 設計にも影響する（受取人の `address` をどう扱うか、`address.user_id` 制約をどうするか）
- 本フェーズ単独で `deliveries.user_id` だけ先行確保しても、phase14 側が対応していなければ機能しない

**推奨対応：** どちらか。

- (A) 本当に将来要件として確度が高いなら、phase14 r2 で sales / address も合わせて拡張する設計を提案する
- (B) 確度が低いなら **YAGNI 原則で削除**。必要になったらマイグレーションで追加する（規約「不要な抽象を作らない」と整合）

私としては (B) を推奨。phase18 の問い合わせ連携と違い、ギフト配送は機能要件としてどこにも宣言されていない。

### RR-6. 在庫テーブルとの連動仕様が未定

r1 で「Service 層で在庫加算」「同一トランザクション」と決まったのは良いが、**「在庫テーブル」が何を指すかが曖昧**。

- 既存の `products.stock` カラムを更新するのか
- 別途 `inventories` テーブルがあるのか（ある場合は phase14 までに既存定義があるか確認が必要）
- 倉庫別在庫（`inbounds.warehouse_id` がある以上、在庫も倉庫別になるはず）はどこで管理するか

**推奨対応：** 「在庫の現在値を保持するテーブル／カラム」を本書内で明示する。倉庫別在庫を将来扱うなら、`inventories(product_id, warehouse_id, quantity)` のような構造を本フェーズで先行定義しておくと、後から `inbounds.warehouse_id` を活かしやすい。

### RR-7. phase18 への要請事項の調整プロセスが不明

「phase18 設計書側で `inquiries.target_type` / `target_id` の追加を要請する」とあるが、

- 誰がいつ phase18 設計書を更新するのか
- phase15 実装着手時点で phase18 が未着手の場合、`deliveries.id` を `target_id` として参照する **接合テストはどこで書くのか**

が不明確。設計書の依存関係としてリスクを残さないため、以下のどちらかが望ましい。

- (A) phase18 設計書 r1 を**本フェーズ着手前に**作成する（フェーズ間の前提として固定）
- (B) phase15 着手時点では `inquiries.target_type/target_id` がまだ無いことを前提に、配送 ↔ 問い合わせ連携の動作確認は phase18 実装まで保留する旨を明記

### RR-8. 配送予定日リードタイム設定の管理粒度

「リードタイム（地域別・配送方法別）は `config/app/Delivery.php` に定数定義」とあるが、

- 47都道府県 × 3配送方法 = 141 エントリ。本当に config に書くのか、それともマスタテーブル化するか
- リードタイムが頻繁に変わる業務要件（季節要因・キャンペーン等）があるなら、config よりマスタ＋管理画面が適切

**推奨対応：** 「初期は固定値で config 駆動。将来的に頻繁な変更需要が出てきたらマスタ化」と段階的方針を本書に明記。規約 3-2「条件付きで config 化してよいもの」の範囲内かを判断する観点を残しておく。

### RR-9. 改訂履歴に「初版」の日付が `-` になっている

| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | - | 初稿 |

軽微だが、トレーサビリティのため初版日付も入れた方がよい。

### RR-10. テストの `screen_name` / `api_name` の検証範囲

phase14 で `operation_logs` に `screen_name / api_name` が追加されたことに本書も追従しているが、テストケースは「`screen_name`・`api_name` が記録される」までに留まっている。**実際にどの値が入るか**（例：`screen_name='ConsoleDeliveryListPage'`、`api_name='POST /api/console/deliveries/{id}/status'`）の命名規約が phase14 にも本書にも無いと、テストでアサートする値が揺れる。

**推奨対応：** phase14 / phase15 共通で「`screen_name` / `api_name` の命名規約」を別途定める（本書スコープ外でも、共通 ai_context に追記を提案する形でよい）。

---

## まとめ：再レビュー優先度

| 優先度 | 項目 | 概要 |
|--------|------|------|
| 🟡 推奨 | RR-1 | キャンセル・配達失敗・再配達の扱いを明示的に定義 or スコープ外宣言 |
| 🟡 推奨 | RR-2 | 配送予定日の変更を operation_logs に記録 |
| 🟡 推奨 | RR-3 | sales:deliveries の多重度を確定。UNIQUE 制約 or 1:N 許容を選ぶ |
| 🟡 推奨 | RR-4 | `deliveries` 生成タイミングを (A)/(B) で確定 |
| 🟢 任意 | RR-5 | `deliveries.user_id` 冗長保持を YAGNI で再検討 |
| 🟢 任意 | RR-6 | 在庫テーブル／カラムを明示。倉庫別在庫の方針も整理 |
| 🟢 任意 | RR-7 | phase18 との依存関係解決のプロセスを明記 |
| 🟢 任意 | RR-8 | 配送予定日リードタイムの管理粒度（config or マスタ）を段階方針として明記 |
| 🟢 任意 | RR-9 | 改訂履歴に初版日付を入れる |
| 🟢 任意 | RR-10 | `screen_name` / `api_name` の命名規約を別途定義 |

---

## 結論

初版指摘 R-1 〜 R-12 はすべて適切に反映されており、**設計品質は実装着手レベルに到達している**。

ただし RR-1（ステータス遷移の網羅）、RR-3（多重度）、RR-4（生成タイミング）は実装時に必ずぶつかる論点であり、ここを r1 のまま残すと実装フェーズで「この場合どうするんだっけ？」が再発する。可能であれば r2 で詰めてから着手することを推奨する。

それ以外（RR-5 〜 RR-10）は実装と並行して詰めても致命的ではない。

