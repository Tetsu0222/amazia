# テスト設計のための知見集

過去のトラブル履歴（`docs/troubles/`）から抽出したテスト観点。
新規機能実装・テストケース作成時に参照すること。

---

## カテゴリ1: 環境変数・設定管理

### 新規環境変数追加時のルール（009 起因）
- `docker-compose.yml` と `phpunit.xml` を**必ずセットで**更新する
- テストは URL を `http://localhost:8080` のようにハードコードせず `config()` 経由で取得する
- PHPUnit テストが CI グリーンでも、環境変数が本番環境に届いていない可能性がある

### テスト観点
- [ ] 新環境変数が `docker-compose.yml`・`phpunit.xml`・`.env.example` に揃っているか
- [ ] テスト内で `config('app.xxx')` が phpunit.xml の値を参照しているか
- [ ] 実際にコンテナ内で `echo $ENV_VAR` して値が届いているか確認する

---

## カテゴリ2: APIバリデーション・ルート定義

### ファイルアップロード系（007 起因）
- `mimes:xlsx,xls` はOSによってMIMEタイプが揺れる（Alpine Linux で特に不安定）
- 代わりに `mimetypes:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,...` と明示する
- 静的ルート（`/products/import`）は動的ルート（`/products/{id}`）より前に定義する
- Ant Design の `a-upload` は `fileList[0]` ではなく `fileList[0].originFileObj` が実 File オブジェクト

### テスト観点
- [ ] ファイルアップロードで `content-length` が実ファイルサイズと一致しているか
- [ ] MIMEタイプ検証が Docker 環境（Alpine）でも正しく動作するか
- [ ] ルート定義の順序が正しいか（静的 > 動的）

### Spring Boot エラーレスポンス詳細（021 起因）
- `@RestControllerAdvice` がないと `MethodArgumentNotValidException` / `ResponseStatusException` のエラー詳細がレスポンスに含まれない
- Spring Boot プロジェクト初期に `shared/exception/GlobalExceptionHandler` を作成する
- API テストで 422 時は `errors[].message` または `message` フィールドの内容まで検証する

---

## カテゴリ3: フロントエンドUI完全性

### バックエンド先行実装のUI漏れ（011・013・014 起因）
- API が完成してもフロントに**UI導線がなければユーザーは使えない**
- プレースホルダーコンポーネント（「フェーズXXで実装予定」）はフェーズ完了時に必ず差し替える
- ルート登録（`router/index.js`）・メニュー追加（`App.vue`）・実コンポーネントの3点セットが必要

### フェーズ完了チェックリスト
- [ ] `router/index.js` に新ページのルートが登録されているか
- [ ] `App.vue` のサイドメニューに新ページへのリンクがあるか
- [ ] Vue コンポーネントにプレースホルダーが残っていないか（`TODO:` コメントで管理）
- [ ] ブラウザで実際にクリックして動作確認 → スクリーンショットをPRに添付

---

## カテゴリ4: フェーズ移行時のフィールド廃止

### 設計変更とコードの乖離（012 起因）
- フェーズ移行でエンティティからフィールドを廃止するときは、以下をすべて同時に削除する:
  - Java エンティティの `@NotNull` アノテーション・フィールド定義
  - Vue フォームのフィールド・バリデーションルール
  - Laravel コントローラーのリクエスト受付定義
- 片方だけ削除すると「フォームが送信できない」「400 Bad Request」が発生する

### テスト観点
- [ ] 廃止フィールドを送信せずに API を叩いて 2xx が返るか
- [ ] フォームから廃止フィールドの入力欄が消えているか
- [ ] Core エンティティに廃止フィールドが残っていないか

---

## カテゴリ5: Docker・コンテナ環境

### コンテナ間通信の罠（002・010 起因）
- Docker Compose 内では `127.0.0.1` はコンテナ自身を指す（他コンテナには届かない）
- 他コンテナへの参照はサービス名（例: `http://amazia-core:8080`）を使う
- 本番EC2（同一ホスト）と Docker Compose では nginx の `proxy_pass` が異なる構成が必要
- Docker Compose 専用の nginx 設定ファイルを別途用意し、volumes でマウントする

### EC2再起動後のコンテナ自動起動（008 起因）
- `docker-compose.yml` の全サービスに `restart: unless-stopped` を設定する
- Nginx は systemd 自動起動 → バックエンドコンテナが Exited でも 502 が返るため検知が遅れる

### テスト観点
- [ ] `docker-compose.yml` の全サービスに `restart: unless-stopped` があるか
- [ ] Docker Compose 環境と本番環境で異なる nginx 設定が分離管理されているか
- [ ] `docker-compose up` 直後に全サービスが `Up` 状態になるか（`docker ps` で確認）
- [ ] EC2 再起動後に全エンドポイントが応答するか

---

## カテゴリ6: デプロイ・ビルドアーティファクト

### ビルドパスの認識ミス（005 起因）
- Vite の `outDir` 設定（`vite.config.js`）と deploy.yml のコピー元パスが一致しているか確認する
- ビルド後に `ls` で dist ディレクトリの存在を確認してから zip・コピーを実行する

### サービス配信方式の混在（010・015 起因）
- `amazia-market` は Vite の静的ファイルを Nginx が直接 serve する構成であり、**Docker コンテナ管理外**
- `docker-compose.yml` に `amazia-market` を追加するのは誤り（ECR リポジトリが存在しない）
- 各サービスの配信構成を以下に整理:

| サービス | 配信方式 | ECR | docker-compose |
|---|---|---|---|
| amazia-core | Docker + ECR | ✅ | ✅ |
| amazia-console | Docker + ECR | ✅ | ✅ |
| amazia-market | Nginx 静的配信 | ❌ | ❌ |

### テスト観点
- [ ] `vite.config.js` の `outDir` と deploy.yml のパスが一致しているか
- [ ] ビルドアーティファクトが存在することを確認してからデプロイステップを実行しているか
- [ ] docker-compose.yml に ECR リポジトリが存在しないイメージを参照していないか

---

## カテゴリ7-2: Spring Boot テスト環境（H2）と本番（MySQL）の DDL 互換（027 起因）

### schema.sql の DB 方言混入
- `spring.sql.init.mode=always` の場合、`classpath:schema.sql` はテストでも自動で拾われる
- MySQL 専用構文（`CREATE TABLE` 内インライン `INDEX`、`ON UPDATE CURRENT_TIMESTAMP`、`ADD COLUMN IF NOT EXISTS` など）は H2 で爆発し、`ApplicationContext` ロードが失敗する
- 一度ロードに失敗すると Spring Boot のデフォルト閾値で **以降の同一 Context のテストは全て自動 Fail** され、根本原因が読み取りづらくなる
- 対策:
  - テスト側 `application-test.properties` で `spring.sql.init.schema-locations=` を空指定して schema.sql を除外
  - または schema.sql を H2/MySQL 両対応に書き直す（インライン INDEX を `CREATE INDEX IF NOT EXISTS` に分離など）

### JSON 列の Hibernate マッピング
- `String` フィールドに `@Column(columnDefinition = "JSON")` を付けると、保存時に文字列リテラルとして二重エスケープされ、`ObjectMapper.readValue` が失敗する
- JSON として扱うなら `@JdbcTypeCode(SqlTypes.JSON)` か `AttributeConverter` を使う。単純なテキスト保存で良いなら `@Lob` で十分

### テスト観点
- [ ] フェーズ追加で `schema.sql` / `application-{profile}.properties` を変更した場合、必ずローカルで `mvn test` を流してから push する
- [ ] JSON 列に保存した payload が読み取り側で `ObjectMapper.readValue` できるかを実テストで通す（往復検証）
- [ ] 過去に Phase 11 でも同種の ApplicationContext 失敗（76b2dd23 / a3c565cc）があり、Phase 導入時の H2 互換性は再発パターンとして要警戒

### Entity と本番 MySQL の NOT NULL 制約乖離（038 起因）
- H2 + `ddl-auto=create-drop` のテストは Entity から都度スキーマ生成するため、本番 MySQL に残る旧 NOT NULL 制約は再現されない
- Entity に `@Column(nullable=false)` が無く本番 MySQL だけが NOT NULL の場合、テストは緑でも UI 経由の最小ペイロード送信時に MySQL 1048（`Column 'X' cannot be null`）で 500 を起こす
- 設計書で「廃止／NULL 許容に移行」と書かれた旧カラムは、**実際の ALTER MODIFY が schema.sql に書かれているか**を確認する。書かれていなければ本番 DB だけ古い制約で取り残される
- Console PHPUnit は `Http::fake` で Core を偽装するため、この種の DB 制約違反は Console テストでも検知できない
- 対策:
  - 旧カラム廃止／NULL 許容変更は schema.sql 末尾に冪等な `ALTER TABLE ... MODIFY COLUMN ...` を必ず追記
  - DB 設計書の「NULL」記述と Entity の `@Column` を双方向で確認する規律
  - 重要 UI 画面（商品登録など）はフェーズ完了時にブラウザから実機リクエストを通して確認する

### テスト観点（追加）
- [ ] Entity が NULL 許容しているフィールドが本番 MySQL でも NULL 許容か（`DESCRIBE table` で確認）
- [ ] 設計書 §カラム定義で「NULL」と書かれた旧カラムについて、schema.sql に MODIFY が書かれているか
- [ ] フェーズの完了条件として、UI フォームから最小ペイロード（必須項目のみ）で送信して 2xx が返ることを実機確認

---

## カテゴリ7: Core API 依存の異常系テスト

### Console → Core のプロキシ（009 起因）
- Console は Core に対してプロキシ API を持つ。Core が停止・エラーのとき Console のレスポンスを検証する
- `Http::fake()` でモックすると「実際のURLを叩くか」が検証できないため、config() 経由の URL を確認する

### 追加すべきテストケース
- [ ] Core が 500 を返すとき Console が適切なエラーレスポンスを返すか
- [ ] Core が 404 を返すとき Console が 404 または適切なエラーを返すか
- [ ] Core の URL が環境変数経由で正しく設定されているか（直接 `assert` して確認）

---

## カテゴリ8: 認証・Cookie・プロキシ層の検証（019・020 起因）

### Console（Laravel）↔ Core（Spring）の Cookie 転送
- Core が発行する Cookie の `domain` 属性は Docker コンテナ名（例: `amazia-core`）になることがある
- そのままブラウザに渡すと `localhost`（開発）や本番ドメインと一致せず Cookie が**保存されない**
- Console がプロキシ転送する際は **`domain=null`** を必須とする（リバースプロキシ層で domain を上書きする原則）
- Core 側で発行する Cookie の `domain` も空または省略にしておくのが望ましい

### Guzzle Cookie API の落とし穴（019 起因）
- Guzzle の `SetCookie` は `isSecure()` / `isHttpOnly()` を持たない（メソッド未定義で 500 になる）
- 正しいメソッドは `getSecure()` / `getHttpOnly()`
- Cookie 転送ヘルパー関数を切り出し、テストでメソッド名の固定化を保証する

### 公開エンドポイントとミドルウェアグループ（019 起因）
- マーケット向け公開 API（`/products/market` 等）が `routes/api.php` の `Route::middleware('auth.jwt')->group()` 内に include されると 401 になる
- 公開ルートは auth グループの**外**に明示的に配置する
- 「未認証で 200 が返るか」のテストを公開ルートごとに必ず1本書く

### Cookie ライフサイクル E2E
- API テストは Bearer トークン直付与で済ませがちだが、ブラウザ実装では Cookie が主役
- 「ログイン → アクセストークン期限切れ → 自動リフレッシュ → 操作継続」のシナリオを Playwright/E2E でカバーする
- 「ログイン → リロード → 認証維持」も最低限の E2E ケース

### フロント側シナリオ E2E（Phase 13 で採用）
- Playwright を新規導入する前に、`Vitest + jsdom + MemoryRouter` で **App.jsx と同じ Routes 構成 + AuthProvider + ProtectedRoute + AppHeader** を結合してフローを通すだけでも、認証画面間の遷移バグや CSRF / customer 状態の連携漏れは大半検出できる
- 例: `amazia-market/src/test/auth_flow.e2e.test.jsx` で「登録 → ログイン → マイページ → ログアウト → パスリセ」を1ファイルで通している
- 注意：`api/customer.js` を `vi.mock` で全関数モック化したうえで、`vi.clearAllMocks()` を `beforeEach` で呼ぶ場合は **`mockResolvedValue` も毎回再設定**する（`clearAllMocks` で実装が消えるため、商品 API などの背景モックも各 `beforeEach` で初期化が必要）

### テスト観点
- [ ] Core から受け取った `Set-Cookie` を Console が転送する際、`domain` が `null` で送出されているか
- [ ] Guzzle `SetCookie` 操作箇所で `getSecure()/getHttpOnly()` を使っているか（`isSecure/isHttpOnly` の grep が 0 件）
- [ ] 公開ルートが `auth.jwt` グループ外に定義されているか（未認証 curl で 200 を確認）
- [ ] ログイン → リロード → 自動リフレッシュの E2E が通るか

---

## カテゴリ9: Docker 初回起動・JPA/Laravel 共存 DB（018 起因）

### `docker compose down -v` からの初回起動シナリオ
- H2 インメモリでは `data.sql` 用のテーブルが test スキーマで作られるため CI は通るが、Docker MySQL の**初回起動（`-v` で volume 削除後）**ではテーブル不在で爆発するパターンが頻出
- フェーズ完了時に必ず `docker compose down -v && docker compose up --build` を実施し、DB 初期化からの起動を確認する

### JPA Entity とテーブル定義の一致
- テーブル定義 SQL（Flyway / `schema.sql` / 手動 DDL）に対応する `@Entity` クラスを必ずセットで作成する
- Entity 不在のテーブルは `ddl-auto=update` でも自動生成されない
- `data.sql` の INSERT 先テーブルが Entity 不在だと、Spring 起動時に `Table doesn't exist` で Restarting ループ

### `data.sql` 実行順の制御
- `spring.jpa.defer-datasource-initialization=true` は **`application.properties`（共通）**に置く
- プロファイル別ファイル（`application-local.properties` 等）にしか書かないと、Spring Boot 3.x で確実には機能しない

### Laravel ↔ Spring JPA の DB 共有
- Laravel migration 由来の `users.id` は `bigint unsigned`、JPA 自動 FK は `bigint signed` になり型不一致 WARN が発生
- 対策：JPA 側 FK カラムも `BIGINT UNSIGNED` を明示する。または `ddl-auto=none` にして DDL は SQL/migration で一元管理する

### テスト観点
- [ ] フェーズ完了時に `docker compose down -v && docker compose up --build` で起動完了するか
- [ ] テーブル定義 SQL に対応する `@Entity` が存在するか
- [ ] `spring.jpa.defer-datasource-initialization=true` が `application.properties` 側にあるか
- [ ] Laravel と Spring JPA が共有する DB で型不一致 WARN が出ていないか

---

## カテゴリ10: AWS 運用（コスト・ディスク・デプロイ統合）（016・017 起因）

### EC2 ディスク管理
- 旧イメージ・ビルドキャッシュが蓄積するとルートディスクが満杯になり、`docker pull` が**途中失敗**してデプロイ全体が壊れる
- `docker system prune -af` を週次 cron 化、または最低限デプロイ前に空き容量を確認する
- ディスク不足は副次的に ECR コスト超過（無料枠 500MB）にも繋がる

### ECR ライフサイクルポリシー
- タグなしイメージを 1 日後に自動削除するライフサイクルポリシーを**全リポジトリで標準化**する
- 設定なしで放置すると、タグなしイメージが各リポジトリ数十個単位で蓄積し、ストレージ課金が発生

### amazia-console と amazia-core の同時デプロイ
- 片方だけ ECR にプッシュ・EC2 で pull すると、Console 側ルート追加 → Core 側未対応で 404 という連鎖障害が起きる
- 1 つの GitHub Actions ワークフローで両方をビルド＆プッシュ＆EC2 pull するよう統合する
- デプロイ後は両レイヤー（8000/8080）に直接 `curl` して 404 がないことを確認する

### コスト監視
- Billing アラート（月 $1 / $5 / $10 等）を必ず設定する
- 学習・デモ用途のインスタンスタイプは t3.micro（無料枠内）を基本とする
  - t3.small への一時昇格は `phaseX-4`（Spring Heap 制限 + Swap）完了判定とセットで運用
- 停止中（stopped）インスタンスでも EBS は課金される。不要なら **terminate** する（停止ではなく削除）

### テスト観点（運用観点）
- [ ] EC2 デプロイ前に `df -h` で空き容量を確認しているか（最低 1GB 以上）
- [ ] 全 ECR リポジトリにライフサイクルポリシー（タグなし 1 日削除）が設定されているか
- [ ] amazia-console と amazia-core が 1 ワークフローで同時デプロイされる構成になっているか
- [ ] デプロイ後に両レイヤー（`http://127.0.0.1:8000/...` と `http://127.0.0.1:8080/...`）を curl で確認しているか
- [ ] AWS Billing アラートが設定されており、Cost Explorer を月次確認しているか

---

## カテゴリ11: コンテナへのコード反映（019 起因）

### イメージ COPY 方式 vs ボリュームマウント方式
- `amazia-console` は Dockerfile の `COPY` でコードを焼き込んでおり、ホスト編集はそのままでは反映されない
- 反映方法は 2 通り：
  - **即時**：`docker cp` でコンテナに直接コピー（その場限り、再起動で消える）
  - **永続**：`docker compose build` で再ビルドしてから `up -d` で再起動

### テスト観点
- [ ] ホスト編集後にコンテナへ反映する手順（cp / build）が手順書化されているか
- [ ] 「修正したのに動かない」と感じた際、コンテナ内のファイルを `docker exec ... cat` で実際に確認するフローがあるか

---

## カテゴリ11-2: 認証ルートと公開ルートの分離（033 起因）

### `<img src>` は Authorization を運ばない
- ブラウザの `<img src="...">` / `<video src="...">` / `<a download>` は **fetch API ではない** ため、`axios.defaults.headers.common['Authorization']` を設定しても **Authorization ヘッダが付かない**
- これらから呼ばれるルートを `auth.jwt` ミドルウェアの中に置くと、必ず 401 で弾かれる
- Console 経由・Market 経由のいずれでも、画像実体配信は `auth.jwt` の**外**に明示的に置く規約とする
- UUID ファイル名 + 公開を前提とする画像であれば、認証不要でも実害はない（情報差ゼロ）

### JWT 署名アルゴリズムの揃え方（032 起因）
- Spring (JJWT) の `Keys.hmacShaKeyFor(secret.getBytes())` は **鍵バイト長で alg を自動選択**する
  - 32 byte → HS256 / 48 byte → HS384 / **64 byte 以上 → HS512**
- ローカルの短い `JWT_SECRET` で動いていても、本番の長い鍵では HS512 になり、検証側が SHA-256 ハードコードだと **必ず 401**
- 検証側（Console / 他言語）は **JWT ヘッダの `alg` クレームに追従**する実装を規約化する。固定アルゴリズムでハードコードしない
- `JWT_SECRET` は本番標準として 64 byte 以上を `setup.md` 等で明示し、ローカルと本番の鍵長乖離による「動いていたのに本番で死ぬ」を回避する

### テスト観点
- [ ] `<img src>` から呼ばれるルートが `auth.jwt` グループ外にあるか（未認証 curl で 200）
- [ ] 新規ルート追加時に「ブラウザの非 fetch コンテキスト（`<img>` `<video>` `<a download>`）から呼ばれる可能性があるか」をレビュー
- [ ] JWT 検証側が alg ヘッダに追従しているか（`hash_hmac('sha256', ...)` のようなハードコード grep が 0 件）
- [ ] ローカルと本番で `JWT_SECRET` のバイト長が同等（≥64）であるか

### Cookie 中継は生ヘッダ透過で（031 起因）
- Laravel Guzzle ラッパーはデフォルトで CookieJar が無効化されており、`$response->cookies()` が空配列になる
- Cookie を再構築する方式は属性（Domain/Path/Secure/HttpOnly）の組み立てミスが入り込みやすい
- 規約：**Spring の `Set-Cookie` 生ヘッダをそのまま透過**する。属性の唯一の正本は Core 側 `application.properties`（環境変数）
- Console PHPUnit で「Spring からの `Set-Cookie` をモックして、ブラウザ転送時にそのままヘッダに含まれること」を assert する

### テスト観点
- [ ] Cookie 中継ハンドラが `$response->getHeaders()['Set-Cookie']` ベースの透過になっているか
- [ ] Cookie 属性は環境変数で外部化されているか（コードにハードコードしない）

---

## カテゴリ13: SSM デプロイ機構（022・024・025・026 起因）

### PingStatus = Online は「使える」を意味しない
- `describe-instance-information` の `PingStatus=Online` は **SSM Agent → SSM サービスのハートビート成立**を示すだけ
- コマンド受信ワーカー / MGS（Message Gateway Service）セッションの健全性は別レイヤで、AWS API には直接照会する手段がない
- 結果として 2 種類の「Online だが配信不能」状態が発生する：

| 状態 | 原因 | 対処 |
|------|------|------|
| 一時的 Pending 滞留（リカバリ直後） | EC2 stop/start 後 Agent が温まりきっていない | Online 連続検知 + 安定化待機 |
| ゾンビ Online（永続的 Undeliverable） | キャンセル直後再実行などで MGS セッション死亡 | カナリアコマンドで実配信を実証 |

### リカバリの確度を上げる4段ロケット
1. **事前検知**：デプロイ前に `PingStatus=Online` を必須条件として確認（`ConnectionLost` なら stop/start）
2. **リカバリ完了判定**：`Online` を **連続 3 回**（10秒間隔・最低20秒連続）検知してから 60 秒の安定化待機を入れる
3. **配信実証**：本コマンド前に `echo canary-ok` を発行し、Success が返ることを確認
4. **失敗時ログ**：Failed 時は `StatusDetails` / `StandardOutputContent` / `StandardErrorContent` の **3 点セット**を必ず出力し、`Failed` `TimedOut` に加えて `Cancelled` も拾う

リカバリは最大 2 回までに制限し、それ以上は exit 1 で明示的に失敗させる（無限ループ回避）。

### キャンセル直後の再実行を抑止する運用
- GitHub Actions のジョブを Cancel すると、未完の SSM コマンドが MGS セッションを破壊することがある
- キャンセル後は **最低 5 分待つ**運用ルールを明記。それでも詰まったら `cancel-command` でキューから外してから再実行

### 切り分けの最初の 5 分で並行確認すべき項目（026 補足の教訓）

「自分側を疑い尽くしてから外を疑う」は姿勢としては正しいが、**並行で疑える項目は並行で確認**することで切り分け時間を 10〜20 分短縮できる。

| 項目 | 確認方法 |
|------|---------|
| AWS Service Health Dashboard | `https://health.aws.amazon.com/health/status` で該当リージョン・サービス |
| EC2 状態 | コンソールで running + 3/3 OK |
| SSM PingStatus | `aws ssm describe-instance-information` |
| セキュリティグループ Outbound | コンソールで 443/0.0.0.0/0 の有無 |
| 最近のデプロイ・設定変更 | git log / CloudTrail |

### コンテナ restart loop の決定的シグナル
- カナリアが Pending → InProgress まで進んだのに完了しない場合、メモリ逼迫 → restart loop を疑う
- `aws ec2 get-console-output --latest` のカーネルログで **異なる `veth` 名が約20秒間隔で生成・破棄を繰り返している**痕跡が決定的証拠
- 同時に SSM Agent も応答阻害される（Agent もホスト OS のリソースで動いているため）

### テスト観点
- [ ] `deploy.yml` のリカバリ機構が「事前 PingStatus 検査 → リカバリ → Online 連続検知 + 60秒待機 → カナリア → 本コマンド」の4段で構成されているか
- [ ] 失敗時に `StatusDetails` / `StandardOutputContent` / `StandardErrorContent` の 3 点を出力するロジックが SSM 経由の全ステップで揃っているか
- [ ] `Cancelled` ステータスもエラーハンドラの分岐に入っているか
- [ ] リカバリは最大 2 回までで、それ以上は exit 1 で明示的失敗するか
- [ ] 切り分けチェックリスト（AWS Health / EC2 / PingStatus / SG / 直近変更）が `docs/troubles/` または README に記載され参照可能か

---

## カテゴリ14: CD と systemd の compose 経路整合（023・028・029 起因）

### 同じ compose を複数経路で呼ぶ場合の整合
- Amazia は **CD（GitHub Actions → SSM → docker-compose）** と **systemd unit（EC2 起動時 → docker compose）** の 2 経路で同じ compose 構成を扱う
- 経路間で記法・オプションが揃っていないと、片方の修正が片方に反映されず潜在不具合になる

| 整合項目 | 過去のミス |
|---------|----------|
| `--remove-orphans` | systemd 側のみ付与・CD 側に抜けて 023 |
| `docker-compose` v1（ハイフン）/ `docker compose` v2（スペース） | systemd は v2、deploy.yml は v1 で混在（029 で復旧時に表面化） |
| `.env` ファイル | systemd の `EnvironmentFile=-/path/.env.production` が `-` 接頭辞で「不在でもエラーなし」になっており、不在に気付きにくい（029） |

### CD 中断時の Docker 残骸対策
- `ECR pull` 中に GitHub Actions Cancel すると、新旧イメージや dangling network が中途半端に残る
- その状態で stop/start すると systemd 起動の `docker compose up -d` が name conflict / network in-use で失敗 → restart loop（028）
- `down`/`up` の双方で `--remove-orphans` を使い、即時復旧時は `docker rm -f` でコンテナを束で消す

### `.env` 不在の検知（029 起因）
- `docker-compose.yml` の `${JWT_SECRET}` のような **デフォルト値なしの参照**は `.env` 不在で空文字に解決される
- Spring/Laravel が空文字で起動しようとするため、症状が「ログインできない」「セッションが効かない」など多岐にわたり原因特定が難しい
- 本番デプロイ時は `.env` 生成を CD 側で担保し、systemd unit の `EnvironmentFile` も先頭 `-` を外して「不在ならエラー」にすることを検討（少なくとも検出する手段を別途用意する）

### Amazon Linux 2023 で `docker compose` v2 プラグインが標準提供されない
- `dnf install docker` だけでは `/usr/libexec/docker/cli-plugins/docker-compose` が入らない
- 何らかのタイミングで（OS アップデート等）プラグインが消失すると `docker compose ...` が `compose is not a docker command` で exit 125
- **EC2 user data または setup スクリプトで Docker 公式バイナリを `curl` 取得してインストール**する手順を恒久化する（標準リポジトリ非依存）

### テスト観点
- [ ] CD と systemd で `docker-compose` v1/v2 の記法が統一されているか（`docker compose` v2 に寄せるのが推奨）
- [ ] `down`/`up` の両方で `--remove-orphans` を使っているか
- [ ] EC2 上で `docker compose version` が成功するか（プラグイン存在確認）を CI / デプロイ前ヘルスチェックに含めるか
- [ ] systemd unit の `EnvironmentFile` が指すファイルの存在を、デプロイ前にチェックする手段があるか
- [ ] EC2 setup スクリプト / user data に `docker compose` v2 プラグインの公式バイナリ取得が含まれているか

---

## カテゴリ15: Laravel migration と Spring data.sql の DB 共有齟齬（018・029 再発）

### 構造的弱点
- Amazia は MySQL を Laravel と Spring の双方が触る前提で組まれている
- Laravel migration は標準カラムのみを定義し、Spring の業務カラム（`employee_id` / `password_hash` / `role_id` / `active_flag` / `failed_attempts` / `locked_until` 等）を含まない
- Spring `data.sql` 側は `INSERT IGNORE` 等で業務カラムにデータを入れる前提だが、テーブル/カラム自体の作成責務が曖昧
- mysql volume を介して片方の状態が崩れると、他方の起動が破綻する（029 で 018 と同種の事象が再発）

### どこに責務を持たせるか（規約案）
1. **Laravel migration に業務カラム追加 migration を新設**するのが最も明示的
2. もしくは **Spring `data.sql` 側で `CREATE TABLE IF NOT EXISTS` 相当を担う**
3. いずれにせよ「`users` テーブルのカラム集合の正本はどこか」を決め、両側で同じものが見える状態を保つ

### `users.id` の型整合
- Laravel の `users.id` は `BIGINT UNSIGNED`、Spring JPA の自動生成 FK はデフォルト `BIGINT SIGNED`
- `roles` / `permissions` / `role_permissions` の id・FK も `BIGINT UNSIGNED` に揃える必要がある
- 不一致のまま FK 制約を張ると ALTER 失敗や JPA の警告が出る

### `mysql volume` を吹き飛ばすリスク
- `docker compose down -v` でローカル DB を消すのは開発で頻発する操作
- 本番でも、検証目的で同操作をすると ALTER 直打ちで応急処置した状態（業務カラム）も消える
- 応急処置は **必ず migration 化または `data.sql` の冪等 DDL に反映**して、`-v` で消えても再現するようにする

### テスト観点
- [ ] `users` テーブルのカラム集合の正本（Laravel migration / Spring data.sql / 手動 ALTER のいずれか）が決まり、ドキュメント化されているか
- [ ] `docker compose down -v && up --build` 後に Spring が data.sql 実行を成功させられるか（018 のフェーズ完了条件と整合）
- [ ] Laravel migration と Spring data.sql に重複した DDL がないか（あればどちらが正本か明示）
- [ ] FK 型（`BIGINT UNSIGNED`）が両側で揃っているか

---

## カテゴリ12: アップロードファイルの永続化（030 起因）

### 本番とローカルの docker-compose 思想を揃える
- ローカル (`docker-compose.local.yml`) でファイル永続化していても、本番 (`docker-compose.yml`) で同じマウントがなければデプロイの度にファイルが消える
- アップロード機能（SKU 画像など）を実装した時点で、本番側のマウントも設計すること

### 採用方式の比較
| 方式 | 永続性 | 一貫性 |
|------|--------|--------|
| 名前付きボリューム (`core_storage:/app/storage`) | ⭕ docker volume | ❌ local とパス記述が異なる |
| バインドマウント (`./amazia-core/storage:/app/storage`) + ホスト側 symlink | ⭕ EBS 直接 | ⭕ local と同じ記述 |
| S3 連携 | ⭕ クラウド永続 | アプリ側の SDK 改修必要 |

### symlink 方式の落とし穴
- deploy で `unzip -o amazia.zip` した時に zip 内にダミーディレクトリがあると symlink が破壊される
- `unzip -x "amazia/amazia-core/storage/*"` で除外する必要がある
- ホスト側の権限（owner/group）はコンテナ内 UID と一致させる

### テスト観点
- [ ] 本番 docker-compose.yml にアップロードファイル用の volume が定義されているか
- [ ] ローカルと本番で同じパス記述（`./amazia-core/storage:/app/storage` 等）になっているか
- [ ] deploy スクリプトが symlink を保護する unzip 除外を含んでいるか
- [ ] デプロイ後にアップロード → コンテナ再起動 → 画像が残るかの E2E 確認手順が用意されているか

---

## まとめ: フェーズ完了の定義

以下をすべて満たしてフェーズ完了とする:

1. **バックエンド**: PHPUnit / JUnit テストがグリーン
2. **フロントエンド**: 新ページの router 登録・メニュー登録・コンポーネント実装が揃っている
3. **ブラウザ確認**: 実際にアクセスして画面が動作し、スクリーンショットをPRに添付
4. **環境変数**: `docker-compose.yml`・`phpunit.xml`・`.env.example` がセット更新済み
5. **廃止フィールド**: 設計変更に伴うフィールド削除がエンティティ・フォーム・コントローラーで揃っている
6. **Docker 初回起動**: `docker compose down -v && docker compose up --build` で DB 初期化からエラーなく起動する（018・029 起因）
7. **認証フロー E2E**: ログイン → リロード → 自動リフレッシュのフローが通る（019・020 起因）
8. **両レイヤー疎通**: デプロイ後に Console（8000）と Core（8080）を直接 curl して、新エンドポイントが両方とも応答する（016 起因）
9. **本番環境での実機確認**: 認証絡み・Cookie 絡み・画像配信絡みの機能は **本番ドメインで** ログイン → リロード → 画像表示まで通す（030・031・032・033 起因）。ローカル動作確認だけでは Cookie/JWT/プロキシの不整合は検知できない
10. **デプロイ機構の健全性**: `deploy.yml` のリカバリ + カナリア機構が動いている前提で、過去 N 回のデプロイで Failed なく走っているか（022・024・025・026 起因）

---

## 構造的な再発パターン（001〜030 通算）

[20260503](../analysis/20260503_trouble_analysis.md)・[20260504](../analysis/20260504_trouble_analysis.md)・[20260505](../analysis/20260505_trouble_analysis.md)・[20260506](../analysis/20260506_trouble_analysis.md) の合算で、以下の再発パターンが認められる。新規実装・修正の際は、自分の作業がどのパターンを再生産しないかを意識する。

| パターン | 該当 |
|---------|------|
| デプロイ後ヘルスチェック不在 | 003, 005, 008, 010, 011, 013, 014, 016 |
| docker-compose / 環境変数の管理漏れ | 002, 004, 008, 009, 010, 015, 018, 029 |
| フロント / UI の実装検証不在 | 007, 011, 012, 013, 014 |
| 認証・Cookie・プロキシ層の設計検証不在 | 019, 020, 021, 031, 032, 033 |
| AWS 運用（コスト・ディスク・デプロイ統合）の未整備 | 016, 017 |
| **SSM デプロイ機構の連鎖補強**（022 → 024 → 025 → 026） | 022, 024, 025, 026 |
| **CD と systemd の compose 経路不整合**（v1/v2・`--remove-orphans`・`.env`） | 023, 028, 029 |
| **Laravel migration と Spring data.sql の DB 共有齟齬の再発** | 018, 029 |
| **本番初動でのみ顕在化する潜在不具合**（ローカルで通っていても本番で踏む） | 030, 031, 032, 033 |
| **API レスポンス契約とフロント参照名の乖離**（テストデータが実 JSON 形式と異なるためテストでは検知できない） | 035 |
| **「外挿による誤認」**（実ファイル/設定を直接読まずに表面上の手がかりから前提を立てる、異レイヤーで同型再発） | 035, 037 |
| **テスト DB 初期化方式と本番 DB 初期化方式の差異**（H2 + ddl-auto では通るが本番 MySQL + schema.sql では通らない） | 027, 037, 038 |
| **Entity と本番 MySQL の NOT NULL 制約乖離**（旧カラムの ALTER MODIFY 漏れにより設計書通り NULL を送ると 1048 で 500） | 038 |

---

## API レスポンス契約のテスト観点（035 起因）

新規画面でフロントから API レスポンスを参照する際の落とし穴：

- **テストデータがフロント側で書き起こされる場合**、実際の Core/Console API レスポンス JSON 形式と乖離していてもテストは通ってしまう。
- 035 では Core `SkuDetail` の getter が `getSkuId()` のため JSON フィールドが `skuId` だったが、フロントは `id` を参照し、テストデータも `{ id: 101, ... }` で書かれていた。両者が同じ虚構を共有しているのでテストは緑、実環境では undefined。

### 設計観点（自分のコードがこれを再生産していないか）

- [ ] **新規 API のフロント実装着手時に、まず Core/Console 側の DTO / getter 名を直接読んで JSON フィールド名を確定**したか（getter 名から外挿しない）
- [ ] **フロント側ユニットテストのモックデータは、実 API レスポンスと同じフィールド名**で書かれているか
- [ ] **手動 E2E（ブラウザで URL バー確認・ネットワークタブで実 JSON 確認）** を新画面実装後に 1 周しているか
- [ ] **将来課題**: バックエンド DTO のサンプル JSON を Core 側のテストで出力し、それをフロント側のテストデータの参照元にする「コントラクトテスト」の導入

---

## 並行運用フェーズの整合性テスト観点（フェーズ15 r5 / RRRR-7 起因）

複数テーブルへの同期更新（`product_sku_stocks` と `inventories` の双方更新）が発生する並行運用フェーズでは、以下の観点でリグレッションを抑える。

### 不変条件テスト

- [ ] **任意時点で SUM(SKU stock by productId) == inventories.quantity (warehouse=1)** が成立することを各経路後に検証する
  - 経路：販売（注文確定）/ 入荷登録 / 返品復元（REFUNDED 遷移）/ 予約購入の出荷時減算（PENDING→SHIPPED）
  - **複数経路の組合せ**（販売→入荷→販売 など）でも維持されることを必ず確認する。1 経路だけ通っても、フックの呼び忘れや符号反転は組合せで初めて顕在化することがある

### 例外時のロールバック

- [ ] フック対象の Service（販売 / 返品 / 入荷）で **途中例外発生時に「先に書き込んだ inbounds・SKU stock 加算・inventories 加算」がすべてロールバック**することを検証
  - `@Transactional(propagation=REQUIRED)` で外側 TX に参加するフックは、外側がロールバックすれば一緒に巻き戻される
  - **テスト方法**：例外を投げる前後で `count` / `SUM` を取って一致を確認

### auto-provision の落とし穴

- [ ] **テスト環境で `inventories` 行が事前投入されていない場合の挙動**を意識する。本番は schema.sql の起動時マイグレーションで全商品に行が複製されるが、H2 テスト環境では `setUp` で明示投入が必要
- [ ] auto-provision の初期値ソース（`products.stock` か `SUM(product_sku_stocks.quantity)`）が **phase10 移行後の実態と整合**しているか（phase10 で在庫は SKU 側に移行済みのため、`products.stock` ではなく SUM が真の値）
- [ ] **呼出元が SKU 在庫を先に減算してから applyDelta を呼ぶ場合、SUM は減算後の値を返す**。auto-provision がこの値を初期値に採用すると、加算時に「SUM(減算後)+delta」となり期待値とずれる可能性がある。**事前投入で auto-provision 経路を回避する**のが整合性検証としては最も確実

### 出荷時在庫不足の挙動（P5-4）

- [ ] **予約購入の SHIPPED 遷移で在庫不足の場合、409 を返してステータスは PENDING のまま維持**されることを検証
- [ ] その際、**`operation_logs.action='shipping_blocked_insufficient_stock'` が `REQUIRES_NEW` で別 TX 記録**されるため、外側のロールバックの影響を受けないことを確認
- [ ] テストは **`@Transactional` を付けず実 DB に書く形**にして、ステータス維持と log 記録の両方を再 fetch で検証する

---

## 同一概念が複数テーブルに分散しているときの読み書き整合（trouble 043 起因）

フェーズ移行で「ある属性の保有テーブル」を移し替えたが、旧カラムを残したまま運用が続くと、**書き手が新テーブル・読み手が旧テーブルを見続けて永続的に同期が破れる**。

trouble 043 の場合：
- `shipping_status_id` が `sales`（旧）と `deliveries`（新／フェーズ15 r5 で導入）の双方に残存
- Console の状態遷移は `deliveries` のみ更新する一方、Market 購入履歴 API は `sales` 側を読んでいた
- 注文確定直後の単体テスト（PENDING のまま）では検出できず、Console 経由の状態遷移を介した「読み書き往復」テストが欠落

### テスト観点

- [ ] **同一意味の属性が複数テーブルにあるとき、「真の所在」を 1 か所に定め、コード／設計書で明文化**したか
- [ ] **書き手 Service と読み手 Service が同じテーブルを参照**しているか（grep で `set<Field>Id` と `get<Field>Id` の参照テーブルを突合）
- [ ] **Console の状態更新 → Market 購入履歴 API の読み出し**のような「サービス境界をまたぐ読み書き往復」を検証する結合テストを置いているか（単一サービス内の単体テストでは検知できない）
- [ ] フェーズ移行で **テーブル間でカラムを「移動」した場合、旧カラムが本当に不要なら削除**するか、残すなら「初期値専用・読まない」を契約として明記する
