# テーブル定義書：payment_methods

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | payment_methods |
| 論理名 | 決済方法マスタ |
| 所属システム | Core |
| 説明 | 注文時に選択可能な決済方法を保持するマスタ。schema.sql で初期データを INSERT IGNORE 投入する |
| 追加フェーズ | フェーズ14 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 決済方法ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | name | コード名 | VARCHAR | 50 | NOT NULL | - | UNIQUE。`credit_card` / `d_payment` / `cash_on_delivery` |
| 3 | description | 表示名 | VARCHAR | 255 | NULL | NULL | UI 表示用の日本語名 |
| 4 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| name | UNIQUE | name |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| sales | 1:N | 注文の決済方法として参照される |

## 初期データ

schema.sql で `INSERT IGNORE` により以下を投入する。

| id | name | description |
|----|------|-------------|
| 1 | credit_card | クレジットカード |
| 2 | d_payment | d払い |
| 3 | cash_on_delivery | 代引き |

## 設計上の注意

- レコードは Console 画面からの登録対象ではなく、schema.sql 起動時のシード投入のみで管理する（マスタ追加時は schema.sql の INSERT 行に追記する）。
- `name` がコード値、`description` が UI 表示文言。アプリ側でコードから表示文言にマップせず、DB 値を直接使うことで多言語化や文言変更を schema.sql で完結できる。
- `market_customers.payment_method`（VARCHAR）は会員プロフィール上の「希望決済方法」だが、注文時の決済方法は `sales.payment_method_id` で別途確定する。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ14 / V6 相当）
