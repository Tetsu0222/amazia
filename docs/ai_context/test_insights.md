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
6. **Docker 初回起動**: `docker compose down -v && docker compose up --build` で DB 初期化からエラーなく起動する（018 起因）
7. **認証フロー E2E**: ログイン → リロード → 自動リフレッシュのフローが通る（019・020 起因）
8. **両レイヤー疎通**: デプロイ後に Console（8000）と Core（8080）を直接 curl して、新エンドポイントが両方とも応答する（016 起因）

---

## 構造的な再発パターン（001〜020 通算）

[20260503_trouble_analysis.md](../analysis/20260503_trouble_analysis.md)・[20260504_trouble_analysis.md](../analysis/20260504_trouble_analysis.md)・[20260505_trouble_analysis.md](../analysis/20260505_trouble_analysis.md) の合算で、5 つの再発パターンが認められる。新規実装・修正の際は、自分の作業がどのパターンを再生産しないかを意識する。

| パターン | 該当 |
|---------|------|
| デプロイ後ヘルスチェック不在 | 003, 005, 008, 010, 011, 013, 014, 016 |
| docker-compose / 環境変数の管理漏れ | 002, 004, 008, 009, 010, 015, 018 |
| フロント / UI の実装検証不在 | 007, 011, 012, 013, 014 |
| 認証・Cookie・プロキシ層の設計検証不在 | 019, 020 |
| AWS 運用（コスト・ディスク・デプロイ統合）の未整備 | 016, 017 |
