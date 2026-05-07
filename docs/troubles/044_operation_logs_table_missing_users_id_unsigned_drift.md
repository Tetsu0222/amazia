# 044: operation_logs テーブル不在 + users.id UNSIGNED ドリフトで操作履歴が500

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- 画面: Console 操作履歴一覧（`https://www.amazia-portfolio.dedyn.io/console/operation-logs`）
- エンドポイント: Console `GET /console/api/operation-logs` → Core `GET /api/operation-logs`
- レスポンス: 500 Internal Server Error

## 症状
ブラウザで操作履歴ページを開くと API 呼び出しが 500 で返り、画面はエラー表示のまま。
Core コンテナログに以下のスタックトレースが残る:

```
o.h.engine.jdbc.spi.SqlExceptionHelper : SQL Error: 1146, SQLState: 42S02
o.h.engine.jdbc.spi.SqlExceptionHelper : Table 'amazia.operation_logs' doesn't exist

java.sql.SQLSyntaxErrorException: Table 'amazia.operation_logs' doesn't exist
  at com.example.operationlog.service.ListOperationLogService.list(ListOperationLogService.java:45)
  at com.example.operationlog.controller.ListOperationLogController.list(ListOperationLogController.java:43)
```

## 根本原因
本番 MySQL に **`operation_logs` テーブルが作成されていなかった**。

`amazia-core/src/main/resources/schema.sql` には以下の DDL が含まれていた:

```sql
CREATE TABLE IF NOT EXISTS operation_logs (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,                    -- ← 問題箇所
    ...
    CONSTRAINT fk_operation_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

ところが本番の `users.id` は `bigint **unsigned**`（Laravel の `bigIncrements` 由来と推定）であり、`operation_logs.user_id BIGINT`（signed）と FK 制約上の型互換が無いため、Core 起動時の `spring.sql.init` 実行で MySQL が次のエラーを出して DDL が失敗していた:

```
ERROR 3780 (HY000): Referencing column 'user_id' and referenced column 'id'
in foreign key constraint 'fk_operation_logs_user' are incompatible.
```

`schema.sql` は `continue-on-error` で実行されるため、Core 自体は起動成功する。結果として「Core は正常起動／`operation_logs` だけ作成失敗／呼ばれた瞬間に 1146 で 500」という静かな状態になっていた。

CI/CD が長期間コケていた間に B-6 系の commit が積まれていたため、ユーザー側の「CI/CDが原因かも」という直観も部分的には合っていた（DDL 失敗を見逃した期間が伸びた）。

## なぜ CI で検知できなかったか
- Core 単体テストは H2 + `spring.jpa.hibernate.ddl-auto=create-drop` で Entity から都度スキーマを生成。`@JoinColumn` を使っていないので H2 は signed BIGINT で `users.id` も `operation_logs.user_id` も生成し、FK は問題なく成立する
- Console の PHPUnit は `Http::fake` で Core 応答を偽装するため Core の DB 制約に到達しない
- 本番 MySQL のみが「`users.id` が UNSIGNED」という Laravel migration 由来の特性を持っており、本番固有の不整合だった

[027_workflow_test_h2_schema_and_json_payload.md](027_workflow_test_h2_schema_and_json_payload.md) / [038_products_price_stock_not_null_drift.md](038_products_price_stock_not_null_drift.md) と同種の「H2 / 本番 MySQL のスキーマ乖離」系不具合。

なお Core ログに `continue-on-error` で潰された DDL エラーは ERROR ではなく WARN で出ていた可能性が高く、起動ログを真面目に追わない限り気付けない構造でもあった。

## 修正内容
1. **本番ホットフィックス**: SSM 経由で MySQL コンテナに直接 DDL を発行（`user_id` を `BIGINT UNSIGNED` に変更）
   ```sql
   CREATE TABLE IF NOT EXISTS operation_logs (
       id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
       user_id     BIGINT UNSIGNED NOT NULL,
       action      VARCHAR(100) NOT NULL,
       target_type VARCHAR(50)  NULL,
       target_id   BIGINT       NULL,
       screen_name VARCHAR(100) NULL,
       api_name    VARCHAR(100) NULL,
       comment     TEXT         NULL,
       created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
       INDEX idx_operation_logs_user_id (user_id),
       INDEX idx_operation_logs_action (action),
       INDEX idx_operation_logs_target (target_type, target_id),
       INDEX idx_operation_logs_created_at (created_at),
       INDEX idx_operation_logs_screen_name (screen_name),
       INDEX idx_operation_logs_api_name (api_name),
       CONSTRAINT fk_operation_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
   );
   ```
2. **コード側修正**: `amazia-core/src/main/resources/schema.sql` の同 DDL を `BIGINT UNSIGNED` に揃える。次回クリーン起動でも FK が通るようにする
3. **動作確認**:
   - `docker exec amazia-console curl http://amazia-core:8080/api/operation-logs` → `200 / []`
   - 公開エンドポイント `https://www.amazia-portfolio.dedyn.io/console/api/operation-logs` → `401`（auth.jwt 経路でルーティング自体は正常）

## 再発防止

| 観点 | 対策 |
|------|------|
| FK 型ドリフト | Entity の `Long` フィールドを MySQL に DDL する際、参照先（特に `users.id`）の signed/unsigned を毎回確認する。本プロジェクトでは Laravel migration 由来で `users.id` が UNSIGNED である事実を `docs/database_design/TBL_users.md` 等に明記する（後続フェーズで対応） |
| H2 と本番 MySQL の乖離 | 027・038 に続き 3 例目。H2 では UNSIGNED 概念自体が無いため FK 互換性エラーは発生し得ない。スキーマ系トラブルは「本番 MySQL に対する起動時 DDL の WARN/ERROR を CI のスモークで取る」体制が無いと再発し続ける |
| `continue-on-error` の盲点 | 起動時 schema.sql の DDL 失敗は WARN で潰され、その後の API 呼び出しまで気付けない。デプロイ後ヘルスチェックに「主要テーブル存在確認 SQL（SHOW TABLES）」を1ステップ追加する案を検討（`docs/troubles/README.md` 再発防止アクション中の「デプロイ後ヘルスチェック」と統合可能） |
| CI/CD 停止期間中の変更追跡 | CI/CD が連続失敗していた期間に積まれたコミットは、復旧後にまとめてデプロイされる。各コミットの schema 変更は個別に検証されないまま本番に届く。CI/CD 復旧時のチェックリストに「停止期間中の schema.sql 変更を本番起動ログで grep」を加える |
| テスト観点追加 | `test_insights.md` に「`users.id` が `BIGINT UNSIGNED` のため FK 列も UNSIGNED で揃える」観点を追記 |
