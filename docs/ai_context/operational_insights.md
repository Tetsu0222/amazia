# 実装・運用パターンの知見集

過去の作業・トラブルから抽出した、実装時・運用時に参照すべき設計パターンと落とし穴。
テストでは検出しづらいライフサイクル・コンテナ運用・スケジューリング系の知見を集約する。

新規機能実装・フェーズ計画時に参照すること。テスト観点（`test_insights.md`）と相補的に扱う。

---

## カテゴリ1: Spring Boot バッチ起動とコンテナ運用

### Spring Boot 単発バッチを ApplicationRunner で起動する場合（phase13 §14.1 起因）

- Spring Boot は `CommandLineRunner` / `ApplicationRunner` 完了後も Web サーバ運用のため main thread を終了しない
- そのため `--<flag>` 起動の単発バッチを `docker compose run` で foreground 実行するとコンテナがハングする
- 対策パターン：
  - `run -d --name <fixed>` でデタッチ起動 → ログから完了マーカー（例: `[xxx] import done`）を `until ... grep` で検知 → `docker stop` で明示終了
  - SSM Run Command 等のタイムアウト制限のある経路では特に必須（タイムアウト到達でコマンド連鎖が途中で切れ、後続の Web サーバ復帰が走らないため）
- t3.micro 運用下では Web サーバ JVM（`-Xmx384m`）と並走させるとピーク時 OOM リスクがあるため、取込中は Web サーバを `stop` する運用が安全
- 同 Service を将来 `@Scheduled` 化する予定があるなら、外部起動経路（手動コマンド）と内部スケジュール経路の両方で同じ Service を呼ぶ構成にして、片側だけのバグで取り残しが出ないようにする

### イメージ参照と build 定義の二重管理

本番 `docker-compose.yml` が `image: ECR/...` 参照だけで `build:` 定義を持たない場合、ローカル変更は反映できない。
ローカル用 `docker-compose.local.yml` に `build:` 定義を持たせ、`docker compose -f base -f local build` の併用パターンで切り分けるのが基本。

`pom.xml` の `<finalName>` などビルド成果物名を変えた直後は、Docker Buildkit のキャッシュがメタデータベースで判定して全ステージ CACHED になるケースがあるため、初回は `--no-cache` で強制再ビルドする。

### 設計観点
- [ ] CommandLineRunner / ApplicationRunner で起動するバッチ Service は、ログに完了マーカー（例: `[xxx] import done`）を必ず出力するか
- [ ] 完了マーカーを SSM や CI 側で検知して使い捨てコンテナを終了させる手順が、設計書または運用手順書に記載されているか
- [ ] 同 Service が将来 `@Scheduled` 化される予定なら、両起動経路で Service を共通化しているか（Runner / Scheduled は薄いラッパに留める）
- [ ] t3.micro 運用下で外部バッチを実行する場合、Web サーバを一時停止する運用がコマンド化されているか

---

## カテゴリ2: SSM Run Command 経由の長時間ジョブ

### 出力サイズと終端シグナルの設計

- SSM Run Command の出力は **24,000 文字でカット** される。Hibernate の `insert into ...` ログのように大量に出るものを素通しすると、ジョブ自体の成功シグナルが切り落とされて見えなくなる
- コマンド側で `grep -E "成功|失敗"` のように成功/失敗ラインのみ抽出してから出力する
- ジョブ成否は SSM の `Status: Success`（exit code 0）と、出力本文の成功マーカー文字列の **両方** で確認する。片方だけでは「シェルは成功したが内部 Java は失敗」のケースを取りこぼす

### コマンド連鎖（`&&` チェーン）の設計

- `&&` チェーンは前段失敗で停止するため、終端処理（コンテナ削除・Web サーバ復帰）が走り残しになりやすい
- 復帰系のステップは `;` で繋いで「前段が失敗しても必ず実行」にするか、明示的なリカバリ手順を別途用意する
- 例：取込ジョブが `timeout` で打ち切られたとき、使い捨てコンテナが残ると次回 `--name <fixed>` の重複で失敗するため、コンテナ削除は `; docker rm -f <name> 2>/dev/null` のようにエラー無視で末尾に置く

### 設計観点
- [ ] SSM 経由で実行するコマンドは、本処理の出力を `grep` で要約してから返しているか
- [ ] 終端処理（停止・削除・サービス復帰）が前段失敗でも実行されるよう `;` または明示的リカバリで担保されているか
- [ ] 失敗時に残骸（コンテナ・ロックファイル等）が残った場合のリカバリ手順がドキュメント化されているか

### `docker exec ... sh -c '<INNER>'` に持ち込む文字列の3不変条件（[046](../troubles/046_cd_healthcheck_mysql_root_password_unexpanded.md) / [048](../troubles/048_cd_healthcheck_sql_quote_break_inside_sh_c.md) 起因）

SSM RunShellScript から EC2 ホスト bash を経由して docker コンテナ内 sh まで届く `docker exec ... sh -c '<INNER>'` パターンは、**3層のシェル解釈**（SSM JSON ペイロード → ホスト bash → コンテナ内 sh）を通るためクォートが容易に壊れる。046 と 048 は同じ構造に対する別軸の不具合だった。**個別事象の対症療法ではなく、INNER に持ち込む文字列がクラスとして満たすべき不変条件で考える**。

| 不変条件 | 守らなかった結果 | 起因 |
|---|---|---|
| ① 外側を `'<INNER>'` シングルクォートで包む。`"<INNER>"` ダブルクォートは禁止。 | INNER 中の `$VAR` がホスト側で空展開され、コンテナまで届かない（例：`$MYSQL_ROOT_PASSWORD` → `mysql -p amazia` 相当に劣化） | [046](../troubles/046_cd_healthcheck_mysql_root_password_unexpanded.md) |
| ② INNER 中に動的に埋め込む文字列の `'` は **`'\''` にエスケープ** してから埋める。 | INNER 中の `'` がホスト bash の外側シングルクォートを閉じ、その後の文字列が裸トークンとしてホスト解釈される（例：SQL 中の `'amazia'` が `=amazia` に劣化して `Unknown column` で失敗） | [048](../troubles/048_cd_healthcheck_sql_quote_break_inside_sh_c.md) |
| ③ INNER 中に動的に埋め込む文字列の `\` は別途検討する。`'\''` エスケープでは守れず、用途によっては `\\` への二重化が必要。 | （現時点で再現事例なし。将来 INNER に Windows パス・正規表現リテラル等を埋め込むと踏みうる） | 将来課題 |

#### 推奨実装（GitHub Actions の `shell: bash` を前提）

bash パラメータ展開で `'` を `'\''` に置換するのが最も堅い。`sed`/`printf` を経由するとシェルエスケープと sed エスケープの噛み合わせで実機での復元結果が崩れる事例を確認している（[048](../troubles/048_cd_healthcheck_sql_quote_break_inside_sh_c.md) で発覚）。

```bash
# SQL（あるいは任意のリテラル）を INNER に埋める前に必ずエスケープ
APOS="'"
ESC_APOS="'\\''"
SQL_ESC="${SQL//$APOS/$ESC_APOS}"
INNER_SH="mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" amazia -N -e \"$SQL_ESC\""

# 外側 sh -c は必ずシングルクォート
PARAMS_JSON=$(jq -n --arg inner "$INNER_SH" '{
  commands: [ ("docker exec amazia-mysql sh -c '\''" + $inner + "'\''") ]
}')
```

#### 横展開の正しい粒度

046 修正時、3箇所のテンプレート（COUNT クエリ・DIFF クエリ・mysqldump）に展開したのは「同ファイル内の同パターン」としては合格だが、**「INNER に SQL リテラルを埋め込むテンプレート全体が満たすべき条件」**まで踏み込んでいなかった。新しい SSM 経由 docker exec ステップを追加する PR では、**点（ファイル内コピペ）ではなくクラス（不変条件3点）でレビューする**。

### 設計観点（追加）
- [ ] SSM 経由 `docker exec ... sh -c '<INNER>'` を新規追加・改修する PR で、INNER に持ち込む全リテラル（SQL 本文・S3 キー・ファイルパス等）に対し3不変条件を確認したか
- [ ] 動的にリテラルを埋め込むテンプレートでは、埋め込み直前にシングルクォートを `'\''` でエスケープしているか（`sed`/`printf` ではなく bash パラメータ展開を推奨）
- [ ] CD ヘルスチェック等で「故意失敗テストデプロイ」を行うとき、認証エラー経路（[046](../troubles/046_cd_healthcheck_mysql_root_password_unexpanded.md)）と SQL クォートエラー経路（[048](../troubles/048_cd_healthcheck_sql_quote_break_inside_sh_c.md)）を**両方**通るシナリオで検証しているか

---

## カテゴリ3: 設計書作成時の既存実装と環境設定の棚卸し（phase14 r4 / 037 起因）

### 既存実装と新設計の重複検出

新規フェーズの設計書を起こすときは、**「既存実装で同等の役割を持つテーブル / Service / カラムがないか」を必ず棚卸し**する。汎用的な概念（在庫・売上・住所・通知・履歴ログなど）ほど、過去フェーズで実装済みの可能性が高い。

棚卸しを怠ると、同じ役割の機能を別の粒度で **二重実装** する設計に流れる。例：

- **phase14 r1〜r3 の設計** では `inventories(product_id, warehouse_id)` / `inbounds` / `inventory_movements` / `warehouses` を新設し、`InventorySyncService` で並行運用、`products.stock` を最終的に廃止する **大規模な在庫モデル移行（Step C〜E）** を計画していた。
- しかし **フェーズ10で既に** `product_skus` / `product_sku_stocks(sku_id, quantity, version)` / `product_sku_stock_transactions` / `ReceiveProductSkuStockService` が **SKU 単位の正本** として実装・運用されていた。
- 設計書はこの既存資産を把握せずに書かれていたため、r3 の Step C〜E は「すでに到達済みのゴールへ向かうための移行計画」になっていた。
- r4 で大幅縮小：在庫モデル新設・並行運用・段階移行を **すべて削除**、既存 `product_sku_stocks` を正本として採用、Step を 0/A/B の3段階に圧縮。

### 棚卸しの具体手順（設計書ドラフト着手前に実施）

1. **テーブル全件確認**: `@Table(name=...)` を Core 全体で grep、Console の `database/migrations/` を全件閲覧、`docs/database_design/TBL_*.md` を一度通読
2. **Service 全件リスト**: `Get-ChildItem -Recurse */service/*.java` で Core の Service クラスを一覧化、Console の `app/*/Service/` も同様
3. **粒度の確認**: 同じ役割を別の粒度で実装する（商品単位 vs SKU 単位、ユーザ単位 vs 会員単位など）前に、粒度をどう揃えるかを設計書冒頭で決定
4. **設計書冒頭に「既存活用テーブル一覧」を明記**: 新設テーブルとの責務境界を表で整理する。例：r4 では「フェーズ10で実装済みの SKU 在庫モデルを正本として採用」「フェーズ13で実装済みの `market_customers` を `sales.user_id` の参照先として採用」を冒頭に明示
5. **ペアフェーズ設計書の整合**: phase14 と phase15 のように相互参照する設計書がある場合、片方の改訂は他方にも波及する。改訂時は両設計書の整合性を必ず確認

### 設計観点
- [ ] 設計書の「DB設計」セクションを書く前に、既存テーブル全件と既存 Service 全件を確認したか
- [ ] 既存資産があれば、設計書冒頭に「既存活用テーブル一覧」を明記したか
- [ ] 同じ役割を別の粒度で実装しようとしていないか（特に在庫・在庫増減・履歴系）
- [ ] ペアフェーズ設計書（phase14↔phase15 等）の改訂で、片方の改訂が他方の前提を崩していないか
- [ ] r1 → r2 → r3 と改訂を重ねた設計書が、最初から既存実装を見落としていないか（改訂のたびに前提の再点検をする）

---

### 環境設定の棚卸し（037 起因）

「実装」だけでなく **「環境設定／ビルド構成／DB 初期化方式」も棚卸し対象**にする。035（DTO の getter 名を外挿）と 037（Flyway 利用と外挿）は、レイヤーは違うが **「実ファイルを直接読まずに外挿した」という同型の誤認**。

037 の具体例：
- **誤認**：`amazia-core/src/main/resources/db/migration/V*.sql` の存在から「Flyway を使っている」と推測
- **実態**：pom.xml に flyway 依存なし。本番は `schema.sql` を `spring.sql.init.mode=always` で起動時実行する方式。`db/migration/` は過去の名残ファイルで何も動かない
- **結果**：Step 0 / Step A で作成した V6〜V11 が死ファイル化し、本番 DB に payment_methods テーブルが作られず注文確定 API が 500 エラー

### 棚卸しの具体手順（設計書ドラフトに「Xを使う」と書く前に実施）

1. **依存関係の直接確認**：pom.xml / package.json / composer.json で「使っていると主張するライブラリ」が実際に依存に入っているか確認
2. **起動時ログの確認**：「動いているはずの仕組み」が実際に起動時ログに出ているか確認（Flyway なら "Migrating schema..." ログが出る）
3. **設定ファイルの直接確認**：spring の application.properties / Laravel の config / Vite の vite.config.js など、その仕組みを有効化する設定値があるかを確認
4. **「ディレクトリの存在」だけで仕組みの利用を判断しない**：`db/migration/` / `tests/` / `migrations/` のようなディレクトリは過去の名残でも残ることがある

### 設計観点（環境設定）
- [ ] 設計書の前提セクションに「X を使う」と書く前に、pom.xml / package.json / 起動ログを直接確認したか
- [ ] テスト環境と本番環境の **DB 初期化方式が異なる** ことを意識しているか（H2 + ddl-auto vs MySQL + schema.sql は **緑＝本番動作とは限らない**）
- [ ] Step 完了確認に「ローカル本番（docker-compose.local.yml）での実 API 呼び出し」を含めているか
- [ ] 035 / 037 のように **「外挿による誤認」** をしていないか。設計書／実装計画に書く「前提」は **すべて実コード/設定ファイルへの参照** で裏付ける
- [ ] r4 のような大規模改訂の場合、新設計が依存する **環境設定の前提（ビルド・起動・DB 初期化など）** も棚卸し対象に含めたか

---

### schema.sql 編集時の3点観点（phaseX-6 / 027・038・044 起因）

`amazia-core/src/main/resources/schema.sql` は本番 MySQL の DDL を `spring.sql.init.mode=always` + `continue-on-error=true` で起動時実行する。`continue-on-error` は H2/MySQL 互換差や重複実行を吸収する一方、**DDL 失敗を WARN に潰す**ため、編集時は以下3点を必ず確認する。

#### 1. FK 列の signed/unsigned を参照先と一致させる（044 起因）

- 本プロジェクトの `users.id` は Laravel の `bigIncrements` 由来で **`BIGINT UNSIGNED`**。Spring の `Long` フィールドを `@JoinColumn` で参照する場合、schema.sql 側は `BIGINT UNSIGNED` を**明示**する
- `BIGINT NOT NULL` のまま FK を張ると本番 MySQL は `ERROR 3780 Referencing column ... incompatible` で DDL を拒否し、`continue-on-error` で WARN 化されたまま該当テーブルが作られない
- H2 には UNSIGNED 概念が無く Entity 通りに signed BIGINT で生成されるため CI では検知不能（044 が顕在化までユーザー操作待ちだった理由）
- 確認手順：`grep -nE 'REFERENCES users\(id\)' schema.sql` で参照箇所を洗い出し、対応する FK 列がすべて `BIGINT UNSIGNED` か目視確認する

#### 2. 冪等性の担保

- 新規テーブルは必ず `CREATE TABLE IF NOT EXISTS`
- マスタデータは `INSERT IGNORE` または `ON DUPLICATE KEY UPDATE`
- 既存テーブルへのカラム追加は `ALTER TABLE ... ADD COLUMN ...` を **`continue-on-error=true` 前提で再実行されても無害**な形に書く（MySQL は `ADD COLUMN IF NOT EXISTS` を ANSI 準拠でサポートしないため、再実行時の "Duplicate column" エラーは continue-on-error で吸収する設計）
- インデックス・FK 追加も同様：`CREATE INDEX` / `ALTER TABLE ... ADD CONSTRAINT` を分離で書き、重複は continue-on-error で吸収
- `MODIFY COLUMN` は冪等（同じ定義を再実行してもエラーにならない）。NOT NULL → NULL 許容の移行（038）は MODIFY で書く

#### 3. H2 / MySQL 互換性（027 起因）

- H2 は本プロジェクトのテストで使う（`spring.jpa.hibernate.ddl-auto=create-drop` だが H2 プロファイルでは schema.sql を読まない設定）
- ただし schema.sql が H2 で読まれてしまう経路ができた場合に備え、以下は **書かない**：
  - `CREATE TABLE` 内インライン `INDEX ...` 句 → `CREATE INDEX` で分離（H2 で「不明なデータ型」エラー）
  - `ON UPDATE CURRENT_TIMESTAMP` を H2 で読ませる経路があれば NG（MySQL 専用）
  - `ADD COLUMN IF NOT EXISTS` のような MySQL 8 系の拡張構文
- JSON 列を扱うなら `@JdbcTypeCode(SqlTypes.JSON)` か AttributeConverter で対応（`columnDefinition = "JSON"` は文字列リテラル二重エスケープを起こす）
- `DATETIME(6)` 等の精度指定は MySQL 固有なので、必要ならテストでも H2 互換であることを確認

#### schema.sql 編集後の確認手順

1. `mvn test` を流して H2 経路でテストが緑になることを確認（schema.sql 自体は test プロファイルで読まれないが、Entity の互換性を検査）
2. ローカル本番（`docker-compose.local.yml` + MySQL）で `docker compose down -v && docker compose up --build` から再起動し、Core 起動ログに DDL 関連の WARN が出ていないかを `docker logs amazia-core 2>&1 | grep -iE 'WARN.*(schema|DDL|ALTER|CREATE TABLE|FOREIGN KEY)'` で確認
3. 主要テーブルが期待通り作成されたことを `information_schema.tables` で件数確認（CD の改善① と同じ SQL）
4. 本番 DB に新規テーブルを追加した場合は **`ops/healthcheck/required_tables.txt` への追記**を忘れない（CLAUDE.md §主要テーブル定数の同期）

#### 設計書「前提」セクションへの裏付け参照ファイル要求（037 派生）

設計書に「前提」として書く事実は、**裏付け参照ファイルを 1 つ以上引用**する。

- 例：「本プロジェクトは Flyway 未使用」と書くなら `amazia-core/pom.xml` の依存ブロックを引用する
- 例：「DB 初期化は schema.sql + data.sql」と書くなら `application-local.properties` の `spring.sql.init.mode=always` 行を引用する
- 「ディレクトリの存在」だけを根拠にせず、**動作している設定値**を一次ソースとして示す（037 で `db/migration/` の存在から Flyway を外挿した経緯への対策）

---

## カテゴリ4: 既存 DB / API 定義書を最初に読む（フェーズ15以降の設計書ドラフト前提）

### なぜこのカテゴリが要るか

カテゴリ3（既存実装の棚卸し）は **「@Table 全件 grep / Service 全件リスト / migration 通読」** という、コードに対する直接探索を求めている。これは正確だが時間がかかり、結果を毎回ゼロから組み立て直すことになる。

DB / API の定義書（`docs/database_design/TBL_*.md` / `docs/api_design/*_API.md`）は **その探索結果の縮約版** として整備されている。設計書ドラフト着手前にこれらを通読すれば、棚卸しの初手を10倍速く回せる。

ただし定義書は「最後に整備された時点のスナップショット」であり、コードの方が常に正本。**定義書の記述を信じて疑わずに前提化するのは新たな外挿**（035 / 037 と同型）になる。定義書はあくまで「最初の地図」として使い、設計の前提に組み込む際は実コード（`@Entity` / `@RestController` / migration / schema.sql）で裏付けを取る。

### 棚卸しの初手（カテゴリ3 を補強する手順）

1. **DB の全体像を掴む**
   - [`docs/database_design/README.md`](../database_design/README.md) のテーブル一覧を通読（フェーズごとに分類済み）
   - [`docs/database_design/ER_diagram.md`](../database_design/ER_diagram.md) で関連を把握（§1〜§5 のサブダイアグラムに分割済み）
   - 関連しそうな `TBL_*.md` を 2〜3 本ピックアップして詳細を確認

2. **API の既存エンドポイントを掴む**
   - [`docs/api_design/Core_API.md`](../api_design/Core_API.md) で Spring Boot 側の実装済 API を確認
   - [`docs/api_design/Console_API.md`](../api_design/Console_API.md) で Laravel 側のプロキシ・独自 API を確認
   - [`docs/api_design/Market_API.md`](../api_design/Market_API.md) で React 側からの呼び出し対応を確認

3. **設計書冒頭の「既存活用一覧」に反映**
   - 新設テーブル / API と既存資産の責務境界を表で書く（カテゴリ3 §4 と統合）
   - 既存テーブル拡張で済む箇所は新設しない

4. **コードで裏付け**
   - 定義書の記述を前提化する前に、`@Entity` / migration / `@RestController` のいずれか一次ソースで現在値を確認する
   - 定義書と実装が乖離していたら、**コードを正、定義書を更新**（フェーズ完了の定義に「設計書反映」を含めるルールは `Amazia/CLAUDE.md` 参照）

### 設計観点

- [ ] 設計書ドラフトの最初に `docs/database_design/README.md` と `docs/api_design/*_API.md` を通読したか
- [ ] 関連する既存 `TBL_*.md` を 2〜3 本以上具体的に挙げ、責務境界を整理したか
- [ ] 「定義書にこう書いてある」を最終根拠にせず、`@Entity` / `@RestController` / migration で裏付けたか
- [ ] 当該フェーズで触る DB / API について、フェーズ完了時に設計書を更新する計画を立てたか（実装と同一フェーズ内で更新する。後追いだと P12〜P14 で 14テーブル / 29エンドポイントが未文書化のまま放置された実例あり）

---

## カテゴリ5: スコープ撤退の判断ログ（phase14 r4 → phase14.5 分離 / 2026-05-06 起因）

### なぜこのカテゴリが要るか

設計書を r1 → r2 → r3 → r4 と詳細化していくと、**「設計書通り全部やる」が暗黙の規範**になりやすい。だが個人開発・短期スパン（4〜5 日）のポートフォリオ性格に対しては、設計書の網羅性とプロジェクトの工数規模が乖離することがある。

このカテゴリは **「設計書の途中で線を引いて締める判断」自体を再現可能なパターンとして残す** ためのもの。撤退判断は「失敗」ではなく **意思決定の記録**として明示的に書き残す。

### phase14 r4 → phase14.5 分離の経緯（事例）

#### 完了範囲（phase14 r4）
- Step 0（前提整備）/ Step A（スキーマ拡張）/ Step B-1〜B-6
- B-5 は B-5-1〜B-5-8 に細分化して全て完了（返品ワークフロー全層 + operation_logs 横断記録）
- Core 234 件 / Console 80 件 / Market 53 件 全グリーン
- 本番（CloudFront + EC2）で end-to-end 動作確認済み

#### 分離した範囲（phase14.5）
- B-7（予約ステータス判定 API：6 種類の Enum 判定 + Console 商品 UI 改修）
- B-8（phase15 r5 への要請整理）

#### 分離判断のトリガー
B-7 着手前の調査で **Product Entity に release_date / preorder_start_date / accept_preorder / accept_backorder の 4 カラムが不足** していると判明。これらを追加して 6 種類のステータスを判定するには：

1. schema.sql 冪等 ALTER 追加
2. Product Entity / Repository 拡張
3. Console 商品登録 UI に 4 カラム入力欄追加
4. PreorderStatusService 新設 + 境界値テスト網羅
5. Market `ProductDetail.jsx` / `ProductMarketList` の表示分岐実装
6. Vitest / phpunit / JUnit の追加テスト

「単一の API を追加する」見た目に反して、UI 改修まで含めると **独立フェーズ規模**。phase14 r4 完了の核（購入・返品・売上・操作履歴）はすでに動いており、B-7 は「設計書 r4 の章としては存在するが、phase14 完了に必須ではない」と判定できた。

### 撤退判断のチェックリスト

設計書の Step が **過剰スコープではないか**を着手前に問うチェック：

- [ ] **着手前に範囲の規模感を見積もったか**：「単一 API」と思った機能が、Entity 拡張 / UI 改修 / テスト網羅まで含めて何ファイル動くかを Service / Controller / DTO / 画面 / テスト の単位で数えたか
- [ ] **既に動いている範囲は十分か**：未着手 Step を残しても「動くものとして締まる範囲」が end-to-end で完結しているか
- [ ] **必須 vs 望ましい の区別**：当該 Step は完了の核（コア導線が回る）か、付加価値（既にあるものを強化する）か
- [ ] **依存関係の確認**：当該 Step が後続 Step を実装可能にする前提になっているか（前提なら撤退不可、独立なら撤退可）
- [ ] **撤退判断のリスクは何か**：分離した結果として「半分実装で動かない箇所」が残らないか。残るなら撤退ではなく完了まで進める

### 撤退時に必ずやること（phase14.5 分離での実例）

1. **元設計書に「分離」を明示**：phase14_shipping.md の改訂履歴に `r4 実装完了` の版を追加し、§予約機能 章の冒頭に「phase14.5 へ分離」の注記を入れる
2. **元実装計画に「7. phase14.5 への分離」セクション追加**：分離理由・分離範囲表・完了の再定義を書く
3. **新フェーズの設計書を即作成**（鉄は熱いうちに打て）：分離した内容は記憶が薄れる前に独立した md として残す。設計書の文体・章立ては元設計書を踏襲すると整合が取れる
4. **operational_insights.md（本書）に判断ログを残す**：次に同じ判断局面が来たときに参照できる形で

### 設計観点

- [ ] 設計書 r4 のような網羅版に対して、フェーズ着手前に **「全部やる」の規範を一度疑う** プロセスを置いているか
- [ ] Step 単位で分離可能性（独立性 / 依存関係）を整理しているか
- [ ] 撤退判断時に元設計書・実装計画・新設計書・本書（operational_insights）の **4 箇所セットで反映** しているか（1 箇所だけ更新すると後で齟齬の元になる）
- [ ] 「鉄は熱いうちに打て」を意識し、分離決定から新設計書作成までを連続作業で済ませているか（時間が空くと記載漏れが増える）

---
