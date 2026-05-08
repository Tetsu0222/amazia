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

---

## 派生節 (2): users / roles / permissions / role_permissions テーブル不在で Console ログイン全員 500（2026-05-08）

### ステータス
✅ 解決済（2026-05-08 — 派生節 (3) と同時対応で schema.sql 書き起こし + Laravel migration 削除を実施）

### 発症箇所
- 画面: Console ログインページ（`http://localhost:5174/login`）
- エンドポイント: Console `POST /api/auth/login` → Core `POST /api/auth/login`
- レスポンス: 500 Internal Server Error（Console UI には「メールアドレスまたはパスワードが正しくありません」と表示）

### 症状
ローカル `docker compose up` 後、シード済の `admin@amazia.example.com / Admin@2024!` でログインしても認証エラー。Core ログに次のスタックトレース：

```
o.h.engine.jdbc.spi.SqlExceptionHelper : SQL Error: 1054, SQLState: 42S22
o.h.engine.jdbc.spi.SqlExceptionHelper : Unknown column 'u1_0.active_flag' in 'field list'
```

実際の DB を覗くと `users` は **Laravel 標準スキーマ**（`id / name / email / password / remember_token / created_at / updated_at`）になっており、Core が期待する `employee_id / role_id / active_flag / failed_attempts / locked_until / password_hash` カラムが一切存在しない。さらに `roles / permissions / role_permissions` テーブル自体が DB に存在していなかった。

### 根本原因
1. **Core の `schema.sql` に認証スキーマ（`users / roles / permissions / role_permissions`）の DDL が一切含まれていない**。これらは `db/migration/V1__create_auth_tables.sql` に定義されているが、本プロジェクトは Flyway 未使用のため V*.sql は名残ファイルで実行されない（[037](037_flyway_misassumed_phase14_tables_missing.md)）。
2. **Console（Laravel）の `0001_01_01_000000_create_users_table.php`** が `Schema::create('users', ...)` で **Laravel 標準スキーマの users テーブル**を作成する。`docker compose up` で Console が先に migrate を走らせると、Core が起動する頃には Laravel 版 users が既存しており、Core 側に「認証用 users 作成 DDL」が存在しないため Core 用スキーマには永遠にならない。
3. `data.sql` の admin seed は `INSERT IGNORE INTO users (employee_id, ...)` を実行するが、Laravel 版 users には `employee_id` カラムが無く INSERT 自体が `Unknown column` で失敗。これが `continue-on-error=true` で潰される。

### なぜ CI で検知できなかったか
- 044 と同様、起動時 `spring.sql.init` の DDL/INSERT 失敗は WARN で潰される。
- `data.sql` の `INSERT IGNORE` を「冪等」として扱っているが、Laravel users との衝突は構造の不整合であり `INSERT IGNORE` で吸収していい類のものではない（観念的なバグ）。
- ローカル E2E は CI の対象外（テスト環境は H2 + Entity 駆動 ddl-auto=create-drop で MySQL の構造的衝突は再現しない）。

### 修正内容
**応急処置（2026-05-08）：** MySQL に直接 Core 用 auth スキーマを生成して seed：

```bash
# amazia.users を Laravel スキーマで作られていたため DROP し、
# Core スキーマで再作成 + roles / permissions / role_permissions も作成 + admin seed
docker cp Amazia/amazia-core/auth_seed.sql amazia-mysql:/tmp/auth_seed.sql
docker exec amazia-mysql sh -c 'mysql -uamazia -pamazia_pass amazia < /tmp/auth_seed.sql'
```

`auth_seed.sql` は本トラブル対応のために `Amazia/amazia-core/auth_seed.sql` に残している（resources 配下に置くと `spring.sql.init` が拾うため意図的に外に置いた）。

ログイン成功確認：
```
$ curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@amazia.example.com","password":"Admin@2024!"}'
{"accessToken":"...","role":"admin","userId":1}
```

**恒久対応（未着手）：**
1. `amazia-core/src/main/resources/schema.sql` に **`CREATE TABLE IF NOT EXISTS users / roles / permissions / role_permissions`** を `BIGINT UNSIGNED` 揃えで追記（V1 と整合）。
2. **Laravel 側の `0001_01_01_000000_create_users_table.php` を削除またはスキップ**。Console は Core の users を見るので Laravel 標準 users は不要。代替として `password_reset_tokens` / `sessions` 用の独立 migration に分離する。
3. `data.sql` の admin seed が Core スキーマに対して INSERT 成功することを起動ログで確認するスモークテストを CD ヘルスチェックに追加する（044 派生節の「主要テーブル存在確認」と統合可能）。

### 再発防止

| 観点 | 対策 |
|------|------|
| Core / Console の users テーブル責務分離 | 認証ドメイン（users / roles / permissions / role_permissions）は **Core 側が正本**で、Console は Laravel 標準 users migration を持たない設計に統一する。`docs/database_design/README.md` に「認証スキーマは Core が所有」を明記 |
| schema.sql の網羅性 | 037 で「V*.sql は名残」と決めた以上、認証スキーマ（V1）も `schema.sql` に書き起こす必要があった。本プロジェクトの schema.sql 完成度監査を1度行う（V1 〜 V13 のうち IF NOT EXISTS 版で未移植のものを洗い出し） |
| `continue-on-error` の盲点（再掲） | 044 と同型。起動時 DDL 失敗を健全性チェックで検知する仕組みが必要。`ops/healthcheck/required_tables.txt` に `users / roles / permissions / role_permissions` を追加し、デプロイ後 1 分以内に検知できるようにする |
| ローカル E2E スモーク | `docker compose down -v && docker compose up` 直後に `curl /api/auth/login` で 200 が返ることをチェックする 1 行スクリプトを `ops/local_smoke.sh` 等に整備する |

### AI協働観点
- AI の判断ミス：本トラブルは Step 6 着手時の前提として Console ログインが動くことを暗黙に仮定していた。実際は本タスクとは独立した既存不具合だが、フェーズ実装の途中でログインが落ちる前提を見抜けず、`docker compose up` 直後の動作確認を省略していた。
- 人間が止めるべきだった点：該当なし（フェーズ着手より前の段階で発生していた既存問題）。
- 該当アンチパターン：AP-001 系（schema.sql / V*.sql の二重管理）の派生。044 と同型のため「同根トラブルは派生節」運用（[user memory: trouble_doc_consolidation]）に従って本ファイルへ追記。

---

## 派生節 (3): products / SKU / inquiries 系も schema.sql に未定義 — ローカル DB ボリューム再作成で全消失（2026-05-08）

### ステータス
✅ 解決済（2026-05-08）

### 発症箇所
- 画面: Console 商品一覧（`http://localhost:5174/`）
- エンドポイント: Console `GET /api/products` → Core `GET /api/products`
- レスポンス: 500 Internal Server Error

### 症状
ログイン直後に商品一覧を開くと 500。Core ログ：

```
SQL Error: 1146, SQLState: 42S02
Table 'amazia.products' doesn't exist
java.sql.SQLSyntaxErrorException: Table 'amazia.products' doesn't exist
```

調査の結果、`products` のみならず **`product_skus / product_sku_prices / product_sku_stocks / product_sku_stock_transactions / product_images / product_sku_images / product_statuses` 等 SKU/Product ドメインのテーブルが一つも存在しない**ことが判明。

### 根本原因（派生節 (2) と同じ構造）
1. **`amazia-core/src/main/resources/schema.sql` に Product / SKU / inquiries 等のビジネステーブル DDL が一切含まれていない**。これらは古い時代に Hibernate `ddl-auto=update` で永続ボリュームに自動生成されていた可能性が高い（V*.sql にも DDL が無いため、Flyway 由来でもない）。
2. `application-local.properties` は `spring.jpa.hibernate.ddl-auto=none` で固定されていたため、ローカル環境では「Entity が更新されてもテーブルが追加されない」状態。
3. ユーザの環境で **MySQL ボリュームが再作成された瞬間（2026-05-08 の早朝、原因不明 — `docker compose down -v` か Docker Desktop の再構築の可能性）に、schema.sql に DDL が無いビジネステーブル群が永遠に消失**。schema.sql に DDL があるテーブル（market_*, postal_addresses, cart, sales, deliveries 等）のみが再生成された。
4. Console (Laravel) は migrate で users / sessions / cache / personal_access_tokens を作るため、**起動時に Laravel 標準スキーマで users を上書きしていた**（派生節 (2) の問題）。これが認証 500 の原因。

### なぜ気づかなかったか
- ユーザの体感「Step 6 着手前は動いていた」は、ボリューム再作成より前のセッションのこと。
- ログイン画面は Core を経由せず Console まででエラーが出るが、`docker compose up` 直後に商品一覧を開く E2E スモークが運用に組み込まれていなかった。
- フェーズ計画（Step 6）の最中に発覚したが、本タスクのスコープ（バッチ管理 UI）とは独立した既存の構造的欠陥。

### 修正内容
**応急処置（2026-05-08）：** `docker-compose.local.yml` の Core 環境変数に `SPRING_JPA_HIBERNATE_DDL_AUTO: update` を追加。本番（`docker-compose.yml`）には付加しない。

```yaml
# docker-compose.local.yml
amazia-core:
  environment:
    SPRING_PROFILES_ACTIVE: local
    SPRING_JPA_HIBERNATE_DDL_AUTO: update   # ← 追加
```

これにより、Core 起動時に Hibernate が Entity と DB スキーマを比較し、不足テーブル/カラムを自動的に CREATE / ALTER する。`update` モードのため既存データには影響しない。

確認：
```
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/products
200
```

**恒久対応（実施済 2026-05-08）：**
1. ✅ **`schema.sql` に認証 4 表（users / roles / permissions / role_permissions）と Product/SKU 9 表（products / product_skus / product_sku_prices / product_sku_price_history / product_sku_stocks / product_sku_stock_transactions / product_images / product_sku_images / product_statuses）の DDL を `IF NOT EXISTS` 付きで書き起こした**（schema.sql 冒頭に 2 セクション追加）。Hibernate が ddl-auto=update で生成していた DDL から正規化して移植。本番含めて schema.sql 駆動で完結。
2. ✅ **Console（Laravel）の `0001_01_01_000000_create_users_table.php` から users / password_reset_tokens の Schema::create を削除**。Core が schema.sql で定義する正本テーブルなので Laravel 側で重複作成しない。`sessions` のみ Console 専用として残す。
3. ✅ **`data.sql` に商品サンプル seed を追加**（products 3 件 / product_skus 3 件 / product_sku_prices 3 件 / product_sku_stocks 3 件 / product_statuses 3 件）。`docker compose down -v` 後の初回起動でも商品一覧画面が空で 200 応答するようにする。
4. ✅ **`docker-compose.local.yml` から応急処置の `SPRING_JPA_HIBERNATE_DDL_AUTO=update` を削除**。schema.sql 駆動で完結するため不要。

**残課題：**
- `ops/healthcheck/required_tables.txt` に新規追加した認証 4 表 / Product 9 表を追記（次タスク）
- 開発再開後、本番デプロイ前に schema.sql の完成度監査（V*.sql 全行のうち IF NOT EXISTS 版が schema.sql に揃っているか）

### 再発防止

| 観点 | 対策 |
|------|------|
| 永続ボリューム前提の設計欠陥 | 「Hibernate が過去に作ったテーブルが永続ボリュームに残っている」という見えない依存をやめる。schema.sql / V*.sql のいずれかで DDL を必ず明示する。本プロジェクトでは前者に統一する方針が決まっている（CLAUDE.md / 037） |
| ローカル E2E スモーク | `docker compose -f docker-compose.yml -f docker-compose.local.yml down -v && up -d` 直後に主要画面 5〜6 個（商品 / SKU / 売上 / 配送 / 操作履歴 / バッチ履歴）を curl で 200 返却を確認するスクリプトを `ops/local_smoke.sh` に整備 |
| ddl-auto=update の警告化 | 本番（docker-compose.yml）には `SPRING_JPA_HIBERNATE_DDL_AUTO=update` を**絶対に**付加しない。`BatchProductionValidator` 系の起動時 Validator に「production プロファイルで ddl-auto=none 以外を弾く」チェックを追加検討 |
| schema.sql 完成度監査 | 037 で「V*.sql は名残」と決めた以上、V1〜V13 の DDL はすべて schema.sql に書き起こされている必要がある。次フェーズ着手前に 1 度監査を行い、未移植のテーブルを洗い出す |

### AI協働観点
- AI の判断ミス：ユーザが「以前は動いていた」と告げた時に、私は「Step 6 で何かが壊れた」と仮定せず「schema.sql の元々の欠陥」と決め打ちで応急処置的なテーブル直作成を提案した。これは対症療法と指摘を受け、根本解決（`ddl-auto=update`）に切り替えた。
- 人間が止めるべきだった点：該当なし（AI が原因切り分けの段階で立ち止まらなかったことを正しく指摘した）。
- 該当アンチパターン：AP-001 系の派生 + 「対症療法に流れる癖」。次回以降、ユーザが「以前は動いていた」と言った時は、まず**直前の差分とボリューム状態を時系列で並べる**ことから始める。


