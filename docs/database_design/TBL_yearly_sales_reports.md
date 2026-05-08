# テーブル定義書：yearly_sales_reports

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | yearly_sales_reports |
| 論理名 | 年次売上レポート |
| 所属システム | Core |
| 説明 | 年次バッチが集計する売上の正本。`monthly_sales_reports` と同じ4軸（商品 / 決済 / 配送 / 予約区分）を NULL 運用で持ち、`YearlySalesReportJob` が UPSERT で書き込む |
| 追加フェーズ | フェーズ17（r8 / 5.4 / R-15） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | レポートID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | year | 年 | SMALLINT | - | NOT NULL | - | 例：2026 |
| 3 | product_id | 商品ID | BIGINT | - | NULL | NULL | NULL = 商品軸を集約 |
| 4 | payment_method_id | 決済方法ID | BIGINT | - | NULL | NULL | NULL = 決済軸を集約 |
| 5 | shipping_method_id | 配送方法ID | BIGINT | - | NULL | NULL | NULL = 配送軸を集約 |
| 6 | is_preorder | 予約購入フラグ | BOOLEAN | - | NULL | NULL | NULL = 予約軸を集約 |
| 7 | total_amount | 売上合計 | BIGINT | - | NOT NULL | - | 円（税抜） |
| 8 | total_quantity | 数量合計 | INT | - | NOT NULL | - | |
| 9 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_ysr_axes | UNIQUE | (year, product_id, payment_method_id, shipping_method_id, is_preorder) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| uk_ysr_axes | UNIQUE | 同年二重 INSERT 防止（R-15）。UPSERT で再実行可能化 |

## 設計上の注意

- 4 軸 NULL 運用：`monthly_sales_reports` と同様。
- 集計 SQL は LIMIT/OFFSET ページングで処理（メモリ事項：Heap `-Xmx384m`）。
- **`year` は H2 の予約語**のため、Entity 側で `@Column(name = "\`year\`")` バッククォート付き指定／schema.sql も同様（Step 1.5 / 2026-05-08 修正）。詳細は `TBL_monthly_sales_reports.md` の同節を参照。

## Entity

`com.example.salesreport.entity.YearlySalesReport`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-5）
