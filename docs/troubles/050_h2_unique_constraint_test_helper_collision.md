# 050: テストヘルパーのハードコードで `@UniqueConstraint(product_id, color, size)` に衝突し CI 全滅（H2 ドリフト系統の "逆向き" 顕在化）

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- テスト: [ListPreorderProductsServiceTest.java](../../amazia-core/src/test/java/com/example/product/ListPreorderProductsServiceTest.java) `minPriceとmaxPriceが_SKU価格の最小最大で集計される`（`createSku` ヘルパー L187-195）
- Entity: [ProductSku.java:9-10](../../amazia-core/src/main/java/com/example/sku/entity/ProductSku.java#L9) `@UniqueConstraint(columnNames = {"product_id", "color", "size"})`
- 顕在化: GitHub Actions `Deploy to EC2` ワークフロー / `amazia-core テスト` ジョブ

## 症状
フェーズ16 関連実装を push した CI で `mvn test` が以下のエラーで失敗し、`BUILD FAILURE` で CD ステージまで届かない:

```
ListPreorderProductsServiceTest.minPriceとmaxPriceが_SKU価格の最小最大で集計される:146->createSku:194
  » DataIntegrityViolation could not execute statement
  [Unique index or primary key violation:
   "PUBLIC.CONSTRAINT_INDEX_C85 ON PUBLIC.PRODUCT_SKUS(PRODUCT_ID NULLS FIRST, COLOR NULLS FIRST, SIZE NULLS FIRST)
    VALUES ( /* key:1 */ CAST(1 AS BIGINT), U&'\8d64', 'M')"
  [insert into product_skus (color,...,product_id,size,...) values (?,?,?,?,?,?,?,default)]
Tests run: 339, Failures: 0, Errors: 1, Skipped: 0
```

`U&'\8d64'` は `'赤'` の Unicode エスケープ。テストヘルパーが `color="赤"` `size="M"` をハードコードしているため、同一 product に `createSku(pid)` を 3 回呼んだ時点で `(product_id=1, color='赤', size='M')` が重複して H2 の UNIQUE 制約違反になっていた。

## 根本原因
[ListPreorderProductsServiceTest.java:187-195](../../amazia-core/src/test/java/com/example/product/ListPreorderProductsServiceTest.java#L187-L195) の `createSku` ヘルパーで:

```java
private Long createSku(Long productId) {
    ProductSku sku = new ProductSku();
    sku.setProductId(productId);
    sku.setSkuCode("SKU-" + System.nanoTime());  // ← 一意化
    sku.setColor("赤");                           // ← ハードコード
    sku.setSize("M");                            // ← ハードコード
    sku.setStatus("ACTIVE");
    return skuRepository.save(sku).getId();
}
```

`skuCode` は `nanoTime` で一意化されているが、UNIQUE 制約はコードではなく `(product_id, color, size)` にかかっている。`minPriceとmaxPrice...` テストは同 product に対して `createSku(pid)` を 3 回呼ぶため 2 回目で衝突する。

## 027/037/038/044/045/049 と共通する根本

本件は **H2 (`ddl-auto=create-drop`) と本番 MySQL (`schema.sql`) のスキーマ初期化方式の違い** を、テストを書く側が織り込んでいなかったケースであり、027/037/038/044/045/049 と同じ「テスト環境と本番環境のスキーマ生成方式が違うことに起因する事故」系統に属する。ただし**方向は逆**:

| トラブル | 方向 |
|---|---|
| 027 | schema.sql の MySQL 拡張が H2 で動かず ApplicationContext 失敗 |
| 037 | Flyway 名残ファイルが本番未適用 → 本番にテーブルなし／H2 は Entity から自動生成され緑 |
| 038 | 本番 NOT NULL／Entity NULL 許容 → H2 は Entity から再生成され緑、本番で 500 |
| 044/045 | 本番 `users.id UNSIGNED` と FK 列の型不整合で本番のみ `continue-on-error` 潰し |
| 049 | DDL が schema.sql に未記載で本番未作成（H2 は Entity から自動生成され緑） |
| **050** | **H2 が Entity の `@UniqueConstraint` を忠実に反映するため、テスト側が同制約に対して重複データを作るとテスト時点で落ちる**。設計書/Entity を読まずヘルパーを書いたため衝突した |

過去（044/045/049）が「H2 は Entity 通りに生成して緑、本番で初めて壊れる」だったのに対し、050 は「H2 が Entity 通りに生成してくれることを織り込まずヘルパーを書き、テストで爆発する」。**「テストが H2 で何を生成しているか／本番が何を持っているかをテスト作成者が必ず確認する」という規律の欠如**が共通根本。

## なぜ CI 以前で検知できなかったか
- ローカルでも `mvn test` を流していなかったため、push 直後の CI で初めて検知（[027](027_workflow_test_h2_schema_and_json_payload.md) 「ローカル門番」と同じ反省）
- テスト追加時、同 product に複数 SKU を作る処理を書く前に `ProductSku` Entity の `@UniqueConstraint` を確認していなかった
- `SkuAggregateControllerTest` など **既存テストでは color/size を変えて衝突を回避していた**前例があるが、それを参照せず孤立してヘルパーを書いた

## 修正内容
[ListPreorderProductsServiceTest.java](../../amazia-core/src/test/java/com/example/product/ListPreorderProductsServiceTest.java) の `createSku` で `color` を `nanoTime` サフィックスで一意化（呼び出し側を変えずに済む最小修正）:

```java
private Long createSku(Long productId) {
    // ProductSku は (product_id, color, size) UNIQUE 制約を持つため、
    // 同一 product に複数 SKU を作るテスト（min/max 価格集計など）で衝突しないよう color を一意化する。
    String suffix = String.valueOf(System.nanoTime());
    ProductSku sku = new ProductSku();
    sku.setProductId(productId);
    sku.setSkuCode("SKU-" + suffix);
    sku.setColor("赤-" + suffix);
    sku.setSize("M");
    sku.setStatus("ACTIVE");
    return skuRepository.save(sku).getId();
}
```

横展開チェックとして、`amazia-core/src/test` 全体を `setColor\(|new ProductSku\(` で grep。同 product に複数 SKU を作るパターンは `SkuAggregateControllerTest`（color: Red/Blue/Green、size: M/L/S と変えて回避済）と `ListSalesServiceTest` 系（引数で `color` を渡す形）のみで、現状のヘルパーで衝突するのは本件のテストだけと確認した。

## 二次リスク（次フェーズ送り）
[ProductSku.java:9-10](../../amazia-core/src/main/java/com/example/sku/entity/ProductSku.java#L9) で `@UniqueConstraint(product_id, color, size)` が宣言されているのに、`product_skus` テーブルの `CREATE TABLE` は **`schema.sql` に存在しない**（grep で 0 件、FK 参照のみ存在）。`required_tables.txt` には登録されており本番にはテーブルがある＝049 のヘルスチェックは通る、しかし**本番 DB の UNIQUE 制約・カラム型が Entity 宣言と一致しているかは未検証**。

049 と完全に同型の「DDL が schema.sql に未記載のまま運用に入っているテーブル」リスクであり、次フェーズで `mysqldump` の本番スナップショットと突合して schema.sql に DDL を追記する必要がある。本トラブルの直接原因（テストヘルパー）とは独立した課題なので、本件では修正せず再発防止欄に「次フェーズ送り」として記録する。

## 再発防止
| 観点 | 対策 |
|------|------|
| 同一テーブルの複数行を作るテストヘルパー作成時の規律 | `@UniqueConstraint` を持つ Entity のテストヘルパーは、**制約カラムを毎回ユニーク化**する（or 引数で受け取れる形にする）。`docs/ai_context/test_insights.md` に「`@UniqueConstraint` を持つ Entity のテストヘルパー設計観点」を追加 |
| ローカル門番の徹底 | 027 でも同じ反省を書いたが、テストヘルパー追加時にもローカル `mvn test` を必ず通す。Push 直後の CI で初めて UNIQUE 違反を踏むのは構造的に再発する |
| H2/本番乖離系の "逆向き" の存在を明文化 | 044/045/049 は「H2 緑→本番赤」だが、本件は「H2 赤」で表面化する。`test_insights.md` の「H2/本番 MySQL 乖離」観点に**両方向（緑→赤／赤→未到達）が起こりうる**ことを明記する |
| `product_skus` の DDL 未記載 | 二次リスクとして 049 同型の課題を残す。次フェーズで本番 `mysqldump` と Entity 宣言を突合し、`product_skus` の `CREATE TABLE` を schema.sql に追記する。phaseX-6 で導入したスキーマスナップショット（S3 日次）が活用できる |
| 同 PR で複数行を作る既存テストの参照 | テストヘルパー新設時、`SkuAggregateControllerTest` のように既に **色違い・サイズ違いで複数行を作る前例**を grep で確認してから書く。孤立したヘルパー設計を避ける |

## 関連トラブル
- [027](027_workflow_test_h2_schema_and_json_payload.md): H2／本番 MySQL のスキーマ生成方式違いに起因する初発例。ローカル門番の反省も同じ
- [037](037_flyway_misassumed_phase14_tables_missing.md) / [038](038_products_price_stock_not_null_drift.md) / [044](044_operation_logs_table_missing_users_id_unsigned_drift.md) / [045](045_sales_return_table_missing_users_id_unsigned_drift.md) / [049](049_password_histories_table_missing_in_schema_sql.md): すべて H2 緑／本番赤の方向。本件は逆向きだが系統は同じ
- [049](049_password_histories_table_missing_in_schema_sql.md): 「DDL が schema.sql に未記載」という二次リスクを次フェーズに引き継ぐ点で直接の親
