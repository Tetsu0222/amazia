# 038: products.price / stock の NOT NULL 制約が本番に残存し Console 商品登録が 500

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- 画面: Console 商品登録（`http://localhost:5174/products/new`）
- エンドポイント: Console `POST /api/products` → Core `POST /api/products`
- レスポンス: 500 Internal Server Error

## 症状
ブラウザの商品登録フォームから商品名のみを入力して送信すると Console が 500 を返す。
DevTools Console には `:5174/api/products:1 Failed to load resource: the server responded with a status of 500 (Internal Server Error)` が表示される。
Core 側のコンテナログに以下のスタックトレースが残る：

```
Column 'price' cannot be null
DataIntegrityViolationException: could not execute statement
[insert into products (..., price, stock, ...) values (?,?,...)]
SQLState: 23000, MySQL error 1048
```

## 根本原因
本番 MySQL の `products` テーブルで `price` / `stock` が **NOT NULL（既定値なし）** のまま残っていた。

設計書（[TBL_products.md](../database_design/TBL_products.md) §カラム定義 #4-5）では「フェーズ10以降は SKU 側で管理。NULL 許容」が正で、Product Entity も `@Column(nullable=false)` を付けていない（NULL 許容のつもりで実装）。
ところが本番 MySQL の旧スキーマには NOT NULL 制約が残っており、Vue ProductForm から `price`/`stock` が送信されない（=設計書通り SKU 側で管理する想定）リクエストで INSERT 時に 1048 で落ちていた。

カラムの履歴：
- フェーズ10: 価格・在庫を SKU 側（`product_sku_prices` / `product_sku_stocks`）に移行
- 旧 `products.price` / `products.stock` は documentation 上は NULL 許容に変更したが、実 DB の ALTER は実行されないまま放置
- 結果として「設計書では NULL 許容、Entity では NULL 許容、本番 DB だけ NOT NULL」という三層スキーマドリフトが発生

## なぜ CI で検知できなかったか
- Core 単体テストは H2 + `spring.jpa.hibernate.ddl-auto=create-drop` で Entity から都度スキーマ生成。Entity は `@Column(nullable=false)` を付けていないので NULL 許容で生成される → テストでは `price=null` でも通過
- Console PHPUnit は `Http::fake` で Core の応答を偽装するため、Core の DB 制約に到達しない
- フェーズ14.5 C-3 で追加した「予約発売 4 カラム既定値テスト」も同じ理由でグリーン
- 本番 MySQL のみが旧 NOT NULL を保持しており、UI 経由のリクエストでだけ顕在化した

[027_workflow_test_h2_schema_and_json_payload.md](027_workflow_test_h2_schema_and_json_payload.md) と同種の「H2 / 本番 MySQL のスキーマ乖離」系不具合。

## 修正内容
1. `amazia-core/src/main/resources/schema.sql` 末尾に冪等な `ALTER TABLE products MODIFY COLUMN price/stock INT NULL` を追記。次回 Core 起動時に自動適用される
2. ホットフィックスとして本番 MySQL に同じ ALTER を即時実行：
   ```sql
   ALTER TABLE products MODIFY COLUMN price INT NULL;
   ALTER TABLE products MODIFY COLUMN stock INT NULL;
   ```
3. 動作確認: `curl -X POST http://localhost:8080/api/products -d '{"name":"...","statusCode":"ON_SALE"}'` → 201 / `price=null, stock=null` でレスポンス
4. [TBL_products.md](../database_design/TBL_products.md) 変更履歴に「フェーズ14.5 P2」として追記

## 再発防止

| 観点 | 対策 |
|------|------|
| スキーマドリフト検知 | Entity の `@Column` 制約と本番 MySQL の NOT NULL 制約を機械的に比較する仕組みは未整備。当面は schema.sql に冪等 ALTER を追記する運用を徹底し、`docs/database_design/` の更新と同時に schema.sql も触る規律を維持 |
| H2 と本番 MySQL の乖離 | 037 と本件で 2 例目。H2 のみのテストでは「カラムが本番で NOT NULL」「型が DATE/DATETIME」「インデックス制約」などの不一致が検知できない。重要画面は実機ブラウザで動作確認するフェーズ完了条件を堅持 |
| UI 入力欄と Entity の不一致 | Vue ProductForm が `price`/`stock` を持たないこと自体は SKU 移行設計どおりで意図した姿。Entity 側の旧カラムは「読み取り専用 / レガシー」と位置付け、設計書 §カラム定義に「旧」と明記済 |
| テスト観点追加 | `test_insights.md` に「本番 MySQL の NOT NULL 制約が H2 に伝わらない」観点を追記済 |
| 削除案 | `price` / `stock` カラム自体を将来的に廃止する案は別フェーズで検討（既存データ・既存 API 互換のため即時削除はしない） |
