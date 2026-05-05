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

## カテゴリ7: Core API 依存の異常系テスト

### Console → Core のプロキシ（009 起因）
- Console は Core に対してプロキシ API を持つ。Core が停止・エラーのとき Console のレスポンスを検証する
- `Http::fake()` でモックすると「実際のURLを叩くか」が検証できないため、config() 経由の URL を確認する

### 追加すべきテストケース
- [ ] Core が 500 を返すとき Console が適切なエラーレスポンスを返すか
- [ ] Core が 404 を返すとき Console が 404 または適切なエラーを返すか
- [ ] Core の URL が環境変数経由で正しく設定されているか（直接 `assert` して確認）

---

## まとめ: フェーズ完了の定義

以下をすべて満たしてフェーズ完了とする:

1. **バックエンド**: PHPUnit / JUnit テストがグリーン
2. **フロントエンド**: 新ページの router 登録・メニュー登録・コンポーネント実装が揃っている
3. **ブラウザ確認**: 実際にアクセスして画面が動作し、スクリーンショットをPRに添付
4. **環境変数**: `docker-compose.yml`・`phpunit.xml`・`.env.example` がセット更新済み
5. **廃止フィールド**: 設計変更に伴うフィールド削除がエンティティ・フォーム・コントローラーで揃っている
