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
