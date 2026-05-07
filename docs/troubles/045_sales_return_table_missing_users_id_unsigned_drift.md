# 045: sales_return テーブル不在 + users.id UNSIGNED ドリフトで返品管理が500

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- 画面: Console 返品管理一覧（`https://www.amazia-portfolio.dedyn.io/console/sales-returns`）
- エンドポイント: Console `GET /console/api/sales-returns` → Core `GET /api/sales-returns`
- レスポンス: 500 Internal Server Error

## 症状
ブラウザで返品管理ページを開くと API 呼び出しが 500 で返り、画面はエラー表示のまま。
Core コンテナログに以下のスタックトレースが残る:

```
o.h.engine.jdbc.spi.SqlExceptionHelper : SQL Error: 1146, SQLState: 42S02
o.h.engine.jdbc.spi.SqlExceptionHelper : Table 'amazia.sales_return' doesn't exist

java.sql.SQLSyntaxErrorException: Table 'amazia.sales_return' doesn't exist
  at com.example.salesreturn.service.ListSalesReturnService.list(ListSalesReturnService.java)
  at com.example.salesreturn.controller.ListSalesReturnController.list(ListSalesReturnController.java:34)
```

`SHOW TABLES LIKE 'sales_return%'` を本番 MySQL で実行すると 0 件返却で、テーブル自体が作られていないことを確認。

## 根本原因
本番 MySQL に **`sales_return` テーブルが作成されていなかった**。044 と完全に同型。

`amazia-core/src/main/resources/schema.sql` には以下の DDL が含まれていた:

```sql
CREATE TABLE IF NOT EXISTS sales_return (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    sales_id        BIGINT       NOT NULL,
    ...
    approver_id     BIGINT       NULL,                    -- ← 問題箇所
    ...
    CONSTRAINT fk_sales_return_approver FOREIGN KEY (approver_id) REFERENCES users(id)
);
```

本番の `users.id` は `bigint **unsigned**`（Laravel の `bigIncrements` 由来）であり、`sales_return.approver_id BIGINT`（signed）と FK 制約上の型互換が無いため、Core 起動時の `spring.sql.init` 実行で MySQL が次のエラーを出して DDL が失敗していた:

```
ERROR 3780 (HY000): Referencing column 'approver_id' and referenced column 'id'
in foreign key constraint 'fk_sales_return_approver' are incompatible.
```

`schema.sql` は `continue-on-error=true` で実行されるため Core 自体は起動成功する。結果として「Core は正常起動／`sales_return` だけ作成失敗／呼ばれた瞬間に 1146 で 500」という静かな状態になっていた。

なお `fk_sales_return_sales` は `sales(id) BIGINT (signed)` を参照しており、こちらは型整合上問題ない（FK 不成立は `approver_id` 側の 1 制約のみ）。MySQL は CREATE TABLE 内のいずれかの FK が不成立になると DDL 全体を失敗させるため、テーブルそのものが作られなかった。

## なぜ 044 で気付けなかったか
2026-05-07 の 044 修正時、`operation_logs.user_id` だけを UNSIGNED に揃え、同じ `users.id` を参照する **`sales_return.approver_id` の同型ドリフトには波及確認していなかった**。

044 の再発防止欄（「Entity の `Long` フィールドを MySQL に DDL する際、参照先（特に `users.id`）の signed/unsigned を毎回確認する」）は `users.id` を参照する全 FK の棚卸しを謳っていたが、実際の修正では発症した 1 列のみを直して終わっていた。修正範囲を **「同じ参照先を持つ全 FK 列」** に拡張しなかったのが直接の取りこぼし。

phaseX-6 の「主要テーブル存在確認」ヘルスチェックは `required_tables.txt` に `sales_return` を含めて運用予定だが、設計時点（2026-05-07）でユーザー側にライフサイクル設定と故意失敗テストデプロイが残タスクであり、本不具合の本番デプロイ時点ではまだ機能していなかった可能性が高い（時系列はメタ評価対象）。

## なぜ CI で検知できなかったか
044 と同じ構造的な問題:

- Core 単体テストは H2 + `spring.jpa.hibernate.ddl-auto=create-drop` で Entity から都度スキーマを生成。`@JoinColumn` で型を強制していないので H2 は signed BIGINT で `users.id` も `sales_return.approver_id` も生成し、FK は問題なく成立する
- Console の PHPUnit は `Http::fake` で Core 応答を偽装するため Core の DB 制約に到達しない
- 本番 MySQL のみが「`users.id` が UNSIGNED」という Laravel migration 由来の特性を持つ

[027](027_workflow_test_h2_schema_and_json_payload.md) / [038](038_products_price_stock_not_null_drift.md) / [044](044_operation_logs_table_missing_users_id_unsigned_drift.md) と同種の「H2 / 本番 MySQL のスキーマ乖離」系不具合の **4 例目**。

## 修正内容
1. **本番ホットフィックス**: SSM 経由で MySQL コンテナに直接 DDL を発行（`approver_id` を `BIGINT UNSIGNED` に変更して CREATE）
   ```sql
   CREATE TABLE IF NOT EXISTS sales_return (
       id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
       sales_id        BIGINT          NOT NULL,
       status          VARCHAR(50)     NOT NULL,
       reason          TEXT            NULL,
       quantity        INT             NOT NULL,
       approver_id     BIGINT UNSIGNED NULL,
       approved_at     DATETIME        NULL,
       notified_user   BOOLEAN         NOT NULL DEFAULT FALSE,
       notified_admin  BOOLEAN         NOT NULL DEFAULT FALSE,
       created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
       updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
       CONSTRAINT chk_sales_return_quantity_positive CHECK (quantity > 0),
       INDEX idx_sales_return_sales_id (sales_id),
       INDEX idx_sales_return_status (status),
       CONSTRAINT fk_sales_return_sales FOREIGN KEY (sales_id) REFERENCES sales(id),
       CONSTRAINT fk_sales_return_approver FOREIGN KEY (approver_id) REFERENCES users(id)
   );
   ```
2. **コード側修正**: `amazia-core/src/main/resources/schema.sql` の同 DDL を `BIGINT UNSIGNED` に揃える。次回クリーン起動でも FK が通るようにする
3. **設計書更新**: `docs/database_design/TBL_sales_return.md` の `approver_id` 型を `BIGINT UNSIGNED` に修正、注釈に trouble 045 を明記
4. **動作確認**:
   - `docker exec amazia-console curl http://amazia-core:8080/api/sales-returns` → `200 / []`
   - `SHOW CREATE TABLE sales_return` で FK 2 本が作成済みであることを確認

## 再発防止

| 観点 | 対策 |
|------|------|
| 同型ドリフトの取りこぼし | **`users.id` を参照する全 FK 列の棚卸し**を 044 で実施しておくべきだった。今後 `users.id` を参照する FK が増える際は schema.sql 内を全件 grep して UNSIGNED 揃えを必ず確認する |
| H2 と本番 MySQL の乖離 | 027・038・044 に続き 4 例目。H2 では UNSIGNED 概念が無いため FK 互換性エラーが原理的に発生し得ない構造的な盲点は phaseX-6（主要テーブル存在確認 + 起動ログ WARN 抽出）で補う方針 |
| 044 で導入予定の検知が間に合わなかった | phaseX-6 のヘルスチェックは `required_tables.txt` に `sales_return` を含めて運用するため、phaseX-6 完全運用後は同型不具合がデプロイ直後に検知される。phaseX-6 ライフサイクル設定 / 故意失敗テストデプロイの早期完了を推奨 |
| テスト観点追加 | `docs/ai_context/test_insights.md` に「`users.id` を参照する FK 列を新設・移動するときは schema.sql 全体を grep して UNSIGNED 揃えを横展開確認する」観点を追記 |
| 修正範囲ルール | 不具合修正時に schema.sql で型ドリフトを発見したら、**当該列だけでなく同一参照先を持つ全 FK 列を一括点検する**ことを CLAUDE.md「DB / API 設計書のメンテナンスルール」に追記検討 |
