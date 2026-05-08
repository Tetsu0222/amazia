# テーブル定義書：monthly_sales_reports

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | monthly_sales_reports |
| 論理名 | 月次売上レポート |
| 所属システム | Core |
| 説明 | 月次バッチが集計する売上の正本。集計軸は商品 / 決済方法 / 配送方法 / 予約区分の4軸（NULL 運用：軸を集約しない場合は NULL を入れる）。`MonthlySalesReportJob` が UPSERT で書き込む |
| 追加フェーズ | フェーズ17（r8 / 5.4 / R-15） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | レポートID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | year | 年 | SMALLINT | - | NOT NULL | - | 例：2026 |
| 3 | month | 月 | TINYINT | - | NOT NULL | - | 1〜12 |
| 4 | product_id | 商品ID | BIGINT | - | NULL | NULL | NULL = 商品軸を集約 |
| 5 | payment_method_id | 決済方法ID | BIGINT | - | NULL | NULL | NULL = 決済軸を集約 |
| 6 | shipping_method_id | 配送方法ID | BIGINT | - | NULL | NULL | NULL = 配送軸を集約 |
| 7 | is_preorder | 予約購入フラグ | BOOLEAN | - | NULL | NULL | NULL = 予約軸を集約 |
| 8 | total_amount | 売上合計 | BIGINT | - | NOT NULL | - | 円（税抜） |
| 9 | total_quantity | 数量合計 | INT | - | NOT NULL | - | |
| 10 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_msr_axes | UNIQUE | (year, month, product_id, payment_method_id, shipping_method_id, is_preorder) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| uk_msr_axes | UNIQUE | 同月二重 INSERT 防止（R-15）。UPSERT で再実行可能化 |

## 設計上の注意

- 4 軸 NULL 運用：軸を集約する集計行は NULL で記録する。MySQL の UNIQUE 制約は NULL を「複数行で同値とみなさない」ため、NULL 軸の組み合わせも一意キーとして機能する点に注意（INSERT 側で軸の一貫した NULL 化が必要）。
- テーブル分割化（軸別の専用テーブル）は r9 候補。本フェーズは NULL 運用 + UNIQUE + UPSERT で同月二重 INSERT を物理防止する。
- 集計 SQL は LIMIT/OFFSET ページングで処理（メモリ事項：Heap `-Xmx384m`）。
- **`year` / `month` は H2 の予約語**のため、Entity 側で `@Column(name = "\`year\`")` / `@Column(name = "\`month\`")` とバッククォート付きで指定し、schema.sql 側もバッククォート付きで `CREATE TABLE` する（Step 1.5 / 2026-05-08 修正）。MySQL 8.0 ではいずれも予約語ではないが、Hibernate の Dialect が方言ごとに適切な引用符で再生成するため両環境で安全に動作する。

## Entity

`com.example.salesreport.entity.MonthlySalesReport`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-5）
