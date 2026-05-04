
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
