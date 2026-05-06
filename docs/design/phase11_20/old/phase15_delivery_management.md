
# フェーズ15：配送管理

## ステータス
🔲 未着手

## 範囲
- Amazia Console  
- Amazia Market  
- Amazia Core  
- DB設計

## 機能概要
- Amazia Console に配送管理機能（顧客への配送・商品入荷）を追加  
- Amazia Core に配送管理テーブルおよび CRUD を実装  
- Amazia Market の購入履歴に配送情報を反映  
- 将来的な問い合わせ管理との連携を見据えた設計とする

---

# 機能詳細

---

## 🖥 Amazia Console

### 顧客への配送管理
- 購入商品の配送状況を管理  
- 配送ステータス更新（例：準備中 → 発送済み → 配達完了）  
- 配送先情報の確認・変更  
- 将来的に「問い合わせ番号」と紐づけ、問い合わせ画面と相互リンク可能にする

### 商品の入荷管理
- 商品入荷情報を登録  
- 入荷日・数量・倉庫情報などを管理  
- 在庫テーブルと連動（在庫数の増加）

---

## 🧠 Amazia Core

### 配送管理テーブル設計 & CRUD
- 配送情報を保持するテーブルを新規作成  
- Console・Market 双方から利用  
- 将来的な問い合わせ管理との連携を考慮し、問い合わせ番号（tracking_code）を保持可能にする

---

## 🛒 Amazia Market

### 購入履歴への配送情報反映
- 購入履歴画面に以下を表示  
  - 配送ステータス  
  - 配送予定日  
  - 配送完了日（あれば）  
  - 配送方法（宅配 / コンビニ受取 / 置き配）  
- 配送ステータスは Core の配送管理テーブルと連動

---

# DB設計（追加）

### deliveries テーブル（新規：配送管理）

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT | PK |
| sales_id | BIGINT | 売上ID（紐づく購入情報） |
| user_id | BIGINT | 配送先ユーザID |
| shipping_address | VARCHAR(255) | 配送先住所 |
| shipping_method | VARCHAR(50) | 配送方法（宅配 / コンビニ / 置き配） |
| shipping_status | VARCHAR(50) | 配送ステータス（pending / shipped / delivered / returned） |
| tracking_code | VARCHAR(100) | 問い合わせ番号（将来の問い合わせ画面と連携） |
| scheduled_date | DATE | 配送予定日 |
| shipped_date | DATE | 発送日 |
| delivered_date | DATE | 配達完了日 |
| created_at | DATETIME | 作成日時 |
| updated_at | DATETIME | 更新日時 |

---

# 技術検討事項
- 配送ステータスの定義と状態遷移  
- 配送予定日の自動計算ロジック（地域・在庫状況による）  
- 問い合わせ番号（tracking_code）の生成方式  
- 入荷管理と在庫管理の連動  
- 配送情報の Market 反映タイミング（ポーリング or Webhook 的処理）  
- 将来的な問い合わせ管理画面との統合

---

# TDDテストケース

## Amazia Core / JUnit
- 配送情報が正しく登録・更新・取得できる  
- 配送ステータスの更新が正しく反映される  
- 入荷情報登録時に在庫が正しく増加する  
- tracking_code が正しく保存・取得できる  

## Amazia Market / PHPUnit
- 購入履歴に配送情報が正しく表示される  
- 配送ステータスが正しく反映される  
- 配送方法（宅配 / コンビニ / 置き配）が正しく表示される  

---

# レビューコメント

レビュー日：2026-05-06  
レビュー対象：phase15_delivery_management.md  
参照：phase14_shipping.md / phase18_inquiry_management.md / docs/coding_guidelines.md

---

## 🔴 重大（設計の整合性に関わる指摘）

### R-1. フェーズ14と DB 設計が二重定義になっている

phase14_shipping.md ではすでに以下が定義済み。

- `sales` テーブルに `shipping_address_id` / `shipping_status_id` / `shipping_date` を持たせる
- `address` テーブル（住所マスタ／is_active で履歴管理）
- `shipping_statuses` マスタ（PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED）

これに対し、本フェーズ15の `deliveries` テーブルは `shipping_address` を VARCHAR(255) で再保持し、`shipping_status` を VARCHAR(50) で持つなど、フェーズ14の設計と**重複・矛盾**している。

**推奨対応：**
- `deliveries` は sales に対する「配送イベント／配送実体」を表すテーブルと位置づけ、住所はフェーズ14の `address.id` を FK 参照する
- `shipping_status` は VARCHAR ではなく `shipping_statuses` マスタへの FK（`shipping_status_id`）にする（規約 3-1 「権限・定数・マスタは config / マスタ化」と整合）
- そもそも sales に配送関連カラムが既にあるため、「sales が持つ配送情報」と「deliveries が持つ配送情報」の責務分担をフェーズ14と合わせて明文化する必要がある（例：sales は注文時点のスナップショット、deliveries は実配送オペレーション）

### R-2. tracking_code の意味が「問い合わせ番号」と混在している

本書では `tracking_code` を「問い合わせ番号（将来の問い合わせ画面と連携）」と説明しているが、これは一般的に **配送会社の追跡番号**を指す名称。一方で phase18_inquiry_management.md の問い合わせは `inquiries.id` でスレッド管理されており、配送と直接の FK 関係はない。

このまま実装すると「配送追跡番号なのか、問い合わせ ID なのか」が読み手に伝わらず、AI 駆動開発時の文脈理解コストが上がる（規約 2-1 の趣旨に反する）。

**推奨対応：**
- カラムを目的別に分離する  
  - `tracking_code VARCHAR(100)` … 配送業者の追跡番号（純粋に配送用途）  
  - `inquiry_id BIGINT NULL` … 問い合わせと紐付けたい場合の FK（NULL 許容）
- 「将来の問い合わせ管理と相互リンク」という要件は、`deliveries.inquiry_id` ではなく `inquiries` 側に `target_type='delivery' / target_id` を持たせる方が、phase18 の設計とも整合する（問い合わせ対象は配送以外にも商品・売上に広がるため）

### R-3. 入荷管理（inventory inbound）の DB 設計が欠落

「商品入荷情報を登録／在庫テーブルと連動」と機能要件に書かれているが、対応するテーブル設計がない。`deliveries` テーブルだけでは入荷を扱えないし、概念的に「顧客への配送」と「商品の入荷（仕入）」を同じテーブルに混在させるのは責務違反。

**推奨対応：**
- `inbounds`（または `stock_inbounds`）テーブルを新規追加
  - `id / product_id / warehouse_id / quantity / inbounded_at / supplier_id(任意) / created_at / updated_at`
- 在庫増加処理は Service 層で実装（規約 1-1）。Model に書かない
- もしくは本フェーズのスコープから入荷管理を外し、別フェーズに切り出す判断もあり得る（フェーズ責務の肥大化を避ける）

---

## 🟡 中程度（明確化が必要）

### R-4. 配送ステータスの定義がフェーズ14と不一致

- 本書：`pending / shipped / delivered / returned`（小文字スネークケース・4種）
- phase14：`PENDING / SHIPPED / DELIVERED / RETURN_REQUESTED / RETURNED`（大文字・5種）

マスタ化を前提にするなら**フェーズ14の定義に統一**するべき。返品中（RETURN_REQUESTED）が抜けると、phase14 の `sales_return` ワークフロー（REQUESTED → APPROVED → REFUNDED）と配送ステータスが噛み合わなくなる。

### R-5. sales_id と user_id の二重保持に関する説明不足

`deliveries.sales_id` があれば、`user_id` は `sales` 経由で辿れる。それでも `deliveries.user_id` を冗長に持つなら、その理由（例：配送先ユーザー≠購入者となるギフト配送を想定）を本文に明記すべき。理由がないなら削除を推奨（正規化）。

### R-6. 配送予定日の自動計算ロジック未定義

「技術検討事項」に「配送予定日の自動計算ロジック（地域・在庫状況による）」とあるが、実装に踏み込むには情報不足：

- 計算対象は注文時点 / 出荷時点 のいずれか
- 地域マスタは存在するか（フェーズ14の `address.prefecture` を使うのか）
- 在庫切れ時の `scheduled_date` は NULL か、暫定値か

設計書段階で最低限「いつ・何を入力に・どこで計算するか」を決めておかないと、TDD のテストケースも書けない。

### R-7. テストケースに異常系が不足

規約 4-2「異常系テストを書く」に対して、本書のテストはほぼ正常系のみ。最低限以下を追加：

- 不正な配送ステータス遷移（例：delivered → pending への巻き戻し）の拒否
- 入荷登録時に商品が存在しない場合の異常系
- tracking_code の重複登録時の挙動
- sales が存在しない `sales_id` での配送登録の拒否

### R-8. 操作履歴（operation_logs）への記録が未言及

phase14 では「配送先変更は operation_logs に記録」と明示されている。本フェーズで追加する「配送ステータス更新」「配送先変更」「入荷登録」も同様に operation_logs 対象とすべきだが、本書には記載がない。Console から行う管理操作はすべて 5W1H で追跡できる必要がある。

---

## 🟢 軽微（改善提案）

### R-9. `shipping_method` もマスタ化の検討

`shipping_method` を VARCHAR(50) 直書きにすると、将来の集計（決済方法別と同様の「配送方法別売上」など）でやりにくくなる。phase14 が決済方法をマスタ化している方針と揃え、`shipping_methods` マスタ＋`shipping_method_id` を推奨。

### R-10. インデックス方針の記載がない

phase14 ではインデックス（sales_date / product_id 等）に言及があるが、本書には無い。`deliveries.sales_id` / `deliveries.shipping_status_id` / `deliveries.tracking_code` あたりは検索頻度が高くなるので、インデックス方針を追記すると親切。

### R-11. Market 側の表示要件と DB の対応が一部不明

「配送方法（宅配 / コンビニ受取 / 置き配）」の表示が要件にあるが、`deliveries.shipping_method` は配送実績を表すのか、注文時点の希望を表すのかが曖昧。phase14 の `sales` 側との二重持ちにならないか確認が必要。

### R-12. ドキュメント整形の軽微な問題

- 4行目「🔲 未着手」のステータスは継続でよい（指摘なし）
- ファイル冒頭に空行が1行ある（他フェーズ書も同様だが、phase14 と揃えるなら整える）

---

## ✅ 良い点

- 「将来的な問い合わせ管理との連携」を初期段階で見据えている設計姿勢は良い（ただし R-2 のとおり実装の落とし込みは要再検討）
- DB 設計を表形式で簡潔にまとめており可読性が高い
- TDD テストケースが Core / Market でレイヤーごとに整理されている

---

## まとめ：対応優先度

| 優先度 | 項目 |
|--------|------|
| 🔴 必須 | R-1（phase14 との整合）, R-2（tracking_code 命名分離）, R-3（入荷テーブル設計） |
| 🟡 推奨 | R-4（ステータス統一）, R-6（配送予定日仕様）, R-7（異常系テスト）, R-8（operation_logs） |
| 🟢 任意 | R-5, R-9, R-10, R-11, R-12 |

最重要は **R-1（phase14 と二重定義）** と **R-2（tracking_code の意味の混在）**。これらを解決しないまま実装に入ると、フェーズ14 の sales / address / shipping_statuses 周りに後戻り修正が発生する可能性が高い。実装着手前に phase14 設計書とのすり合わせを推奨する。
