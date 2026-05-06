# トラブル分析レポート（021〜030）

## 概要

`docs/troubles/` 配下の021〜030のトラブルドキュメントを「テストケース不足に起因するもの」と「環境設定上の課題」の2軸で分析する。
分析フレームワークは [20260503_trouble_analysis.md](20260503_trouble_analysis.md)・[20260504_trouble_analysis.md](20260504_trouble_analysis.md)・[20260505_trouble_analysis.md](20260505_trouble_analysis.md) を踏襲する。

> 注：No.020 は前回レポート（20260505）で分析済みのため本レポートでは扱わない。代わりに No.030 までを範囲とした。

---

## 分類サマリー

| # | ファイル | タイトル | 分類 | 根本カテゴリ |
|---|---------|---------|------|------------|
| 021 | 021_user_creation_422_no_error_detail.md | 社員登録 422 のエラー詳細不明 | テスト不足 | グローバル例外ハンドラ未整備・APIエラー契約の検証なし |
| 022 | 022_ssm_undeliverable_during_deploy.md | SSM Undeliverable でデプロイ失敗 | 環境設定 | デプロイ前の SSM 健全性検査の未整備 |
| 023 | 023_docker_compose_name_conflict_orphan.md | docker-compose 孤児コンテナの name conflict | 環境設定 | デプロイ経路の `--remove-orphans` 抜け |
| 024 | 024_ssm_failed_no_error_output.md | SSM Failed 時にエラー出力が空 | テスト不足 + 環境設定 | 失敗時ログ収集の不足（StandardErrorContent のみ） |
| 025 | 025_ssm_pending_after_recovery.md | SSM リカバリ直後 Pending 滞留 | 環境設定 | PingStatus=Online を1回検知で即配信判定 |
| 026 | 026_ssm_zombie_online_undeliverable.md | ゾンビOnline（Online でも Undeliverable） | 環境設定 | 配信パス健全性の観測手段が PingStatus 1指標のみ |
| 027 | 027_workflow_test_h2_schema_and_json_payload.md | フェーズ12 ワークフロー導入で CI 全滅 | テスト不足 + 環境設定 | H2/MySQL DDL 互換 + JPA `columnDefinition=JSON` 不整合 |
| 028 | 028_cd_ssm_undeliverable_then_container_crashloop.md | CD 中の SSM 配信不能 → restart loop | 環境設定 | CD 中断時の Docker 残骸 + compose プラグイン消失（029連動） |
| 029 | 029_compose_plugin_lost_and_users_schema_drift.md | compose plugin 消失 + users スキーマ齟齬 | 環境設定 + テスト不足 | 3層複合（OS パッケージ管理 / .env 不在 / Laravel-Spring DB スキーマ齟齬） |
| 030 | 030_https_via_cloudfront_duckdns_single_domain.md | HTTPS化 構成判断の経緯（CloudFront + desec.io） | 設計判断記録（テスト不足/環境設定の枠外） | コスト方針との整合のための構成変更 |

---

## 詳細分析

### 021 — 社員登録 422 のエラー詳細不明（テスト不足）

**分類：テストケース不足（API エラー契約の未整備）**

Spring Boot プロジェクトに `@RestControllerAdvice` のグローバル例外ハンドラが存在しなかったため、Bean Validation 失敗時のレスポンスが Spring デフォルトの簡素なボディに留まり、フロントが原因を表示できなかった。

- API 統合テストで「422 時のレスポンスボディに `errors[].field` `errors[].message` が含まれること」を assert する観点が抜けていた
- 019 と同根：API 契約（特に異常系）が PHPUnit / JUnit の検証対象になっていない

**再発防止**：Spring Boot プロジェクト初期化時に `shared/exception/GlobalExceptionHandler` を雛形として作成する。422/400/404 のレスポンス契約をテストケース上で必ず assert する。フロント側でも `errors[].message` を form 上に表示する規約を作る。

---

### 022 — SSM Undeliverable でデプロイ失敗（環境設定）

**分類：環境設定上の課題（デプロイ前検査の不備）**

`deploy.yml` のリカバリ機構が「15分以上 InProgress のコマンドが残っている場合のみ」リカバリを発動する設計だったため、`PingStatus=ConnectionLost`（キューは空だが Agent 死亡）のケースを素通りさせていた。

- 001 と同根のSSM Agent ハングがデプロイ動線で再現
- 失敗時のレスポンス（`StandardErrorContent` 空）が原因特定を阻害（→ 024 が直接派生）

**再発防止**：デプロイ前に `PingStatus=Online` を必須条件として確認するステップを `deploy.yml` に組み込み、`ConnectionLost` 検知時は stop/start を自動実行する。本対応は同日完了。

---

### 023 — docker-compose 孤児コンテナの name conflict（環境設定）

**分類：環境設定上の課題（デプロイ経路の compose 操作不備）**

systemd unit 側には 008 で `--remove-orphans` を入れていたが、**デプロイ経路の `docker-compose down` / `up` には抜けていた**。compose 管理外の旧コンテナがネットワーク参照を握ったままで、新規 `up` が name conflict を起こしていた。

- 「修正したつもりが片側の経路だけ」というインフラ修正の典型
- ローカル CI では再現不能（クリーンな Docker デーモンを毎回使うため）

**再発防止**：compose 系の操作は **`down`/`up` の両方で `--remove-orphans` を付ける**ことを規約化。systemd unit と CD ワークフローでオプションを揃える。即時復旧手順（`docker rm -f` でコンテナを束で消す）をドキュメント化済み。

---

### 024 — SSM Failed 時にエラー出力が空（テスト不足 + 環境設定）

**分類：テストケース不足 ＋ 環境設定上の課題（失敗時可観測性）**

Failed 時のエラー収集ロジックが `StandardErrorContent` のみを取得していたため、`docker pull` の stdout エラーや Undeliverable で stderr が空になるケースを取りこぼしていた。

- 「ジョブが落ちたが原因不明」という診断系の不具合は通常運用では表面化しない
- 023 / 022 の真因切り分けが本問題のせいで遅延した

**再発防止**：失敗時は `StatusDetails` / `StandardOutputContent` / `StandardErrorContent` の **3点セット** を必ず出力する。`Failed` `TimedOut` に加えて `Cancelled` ステータスも明示的にハンドリング対象に入れる。SSM 経路の他ステップでも同パターンを揃える。phaseX-3 のデプロイで本機構が想定通り稼働することを実環境で確認済み（→ ✅ 解決済）。

---

### 025 — SSM リカバリ直後 Pending 滞留（環境設定）

**分類：環境設定上の課題（リカバリ完了判定の甘さ）**

EC2 stop/start 後の `PingStatus=Online` は SSM Agent → SSM サービスのハートビート成立のみを示す指標で、コマンド受信ワーカー / MGS セッション起動完了は別タイミング。Online を1回検知して即 `send-command` すると、Agent が温まりきらず Pending 滞留 → TimedOut。

- 022 で「リカバリを自動化」した結果、リカバリ完了判定の甘さが新たな隙間として顕在化（典型的な連鎖）

**再発防止**：リカバリ後の Online 確認は **連続3回 + 60秒の安定化待機**を必須化。ループ上限を 18→30 に拡張し、ストリークが途切れたらカウンタをリセットする。phaseX-3 のデプロイで安定動作を確認（→ ✅ 解決済）。

---

### 026 — ゾンビOnline（Online でも Undeliverable）（環境設定）

**分類：環境設定上の課題（観測手段の根本不足）**

`PingStatus=Online` はハートビートのみを保証し、MGS セッション（コマンド配信パス）の健全性は別。AWS API には MGS セッション状態を直接照会する手段がない。

- 022 / 025 で「PingStatus=Online なら正常」と仮定していた前提を崩すケース
- 「リカバリ機構を強化するたびに次の隙間が露呈する」連鎖の到達点

**再発防止**：実コマンドを送る前に軽量な `echo canary-ok` を発行する **カナリア方式** を導入し、配信成功を実証してから本コマンドを送る。リカバリ前後で関数共通化し、無限ループを回避するため最大2回でジョブを失敗させる。本機構は AWS SSM サービス側障害下でも「明示的に失敗」することが実環境で確認できており、phaseX-3 の本番デプロイで安定稼働を確認済み（→ ✅ 解決済）。

> 補足：本対応中、調査の最序盤で AWS Health Dashboard の確認が漏れていたという反省があり、以後「最初の5分で並行確認すべき項目」がメモリとしても固着化された。

---

### 027 — フェーズ12 ワークフロー導入で CI 全滅（テスト不足 + 環境設定）

**分類：テストケース不足 ＋ 環境設定上の課題**

2段の問題が連動した複合障害。

1. **環境設定（DDL 互換性）**：`schema.sql` 内の `INDEX ...` インライン句が H2 で「不明なデータ型」と解釈され ApplicationContext がロードできず、`@SpringBootTest` の閾値で連鎖失敗
2. **テスト不足（JPA 列定義）**：①修正後に露見した 422 — `WorkflowRequest.payload` を `String` 型なのに `columnDefinition=JSON` で書いていたため、Hibernate が JSON リテラルとして再エスケープ → `objectMapper.readValue` が失敗

- 過去にも phase11 導入時の H2 / Spring Context 不整合（`a3c565cc`）が記録されており、**ローカル門番（`mvn test`）をフェーズ完了の必須条件として運用できていない**ことが繰り返し露呈

**再発防止**：`schema.sql` を書く際は H2 互換性を意識する／テスト環境では `spring.sql.init.schema-locations=` を空にして `ddl-auto=create-drop` に寄せる。`String` フィールドに `columnDefinition=JSON` を付けないルールを coding_guidelines に明記。phaseX-3 / X-4 期も含めて push 前に `mvn -pl amazia-core test` を必ず通す運用を徹底。

---

### 028 — CD 中の SSM 配信不能 → restart loop（環境設定）

**分類：環境設定上の課題（CD 中断時の残骸と systemd 連動の盲点）**

GitHub Actions の CD が `ECR pull` 中に Cancel された結果、新旧イメージが宙ぶらりんで残骸となり、その後の EC2 stop/start で systemd の `amazia.service` が `docker compose up -d` を実行しても起動失敗 → restart loop に陥った。`veth` インターフェイスが約20秒間隔で生成・破棄されるカーネルログがコンテナ再作成ループの動かぬ証拠。

- 028 ドキュメント当初の推定（「CD 残骸 + メモリ枯渇」）は **後日 029 で真因が compose プラグイン消失と判明**し更新済み
- AWS Health Dashboard を **並行**確認したことで「自分側の問題」と即時切り分けできたのは 026 補足の教訓を実践した好例

**再発防止**：systemd unit と CD 経路の compose 操作整合（`--remove-orphans` 等）／カナリアの InProgress 滞留時は restart loop を疑い `console-output` で veth ループ確認／メモリ逼迫の根本対策は phaseX-4 で個別タスク化し完了済み。

---

### 029 — compose plugin 消失 + users スキーマ齟齬（環境設定 + テスト不足）

**分類：環境設定上の課題（複数）＋ テストケース不足**

3層が同時に崩れた重大障害。

1. **OS パッケージ管理（環境設定）**：Amazon Linux 2023 標準リポジトリでは `docker compose` v2 プラグインが提供されないため、何かのタイミングで `/usr/libexec/docker/cli-plugins/docker-compose` が消失していた。systemd unit が `docker compose ...` を呼ぶと `compose` がサブコマンドと認識されず exit 125 → restart loop の真因
2. **環境変数管理（環境設定）**：systemd unit が参照する `.env.production` が EC2 上に存在せず（`-` プレフィックスでエラー抑止だったため気付きにくい）、`docker-compose.yml` の `${JWT_SECRET}` が空文字に解決されていた
3. **DB スキーマ齟齬（テスト不足）**：`users` テーブルが Laravel 標準カラムのみで業務カラム（`employee_id` 等）が欠落、`roles`/`permissions`/`role_permissions` も不在。018 で対処したはずの状態が再発

- 「Laravel migration と Spring data.sql が同じ DB を共有する」構造的弱点（018 と同根）が再び顕在化
- compose プラグイン消失は CI（GitHub Actions ランナー）では再現不能、`.env.production` 不在も CI と本番で渡し方が違うため検知不能

**再発防止**：compose プラグインを EC2 user data か setup スクリプトで Docker 公式バイナリ経由でインストール（標準リポ非依存）／`JWT_SECRET` を GitHub Secrets で管理し `deploy.yml` で `.env` 生成／users 業務カラムの Laravel migration を起こすか Spring `data.sql` 側で `CREATE TABLE IF NOT EXISTS` 相当を担う／メモリ逼迫対策は phaseX-4 で完了済。

---

### 030 — HTTPS化 構成判断の経緯（設計判断記録）

**分類：本件は不具合ではなく設計判断記録**

ALB 採用案 → CloudFront + desec.io への切替経緯と、構築過程で踏み抜いた DNS / CloudFront / ACM の制約集。

主な踏み抜き：
- DuckDNS が ACM 検証用のプレフィックス付き CNAME を作れず詰まり → desec.io に移行
- CloudFront はオリジンに IP 直接指定不可 → `origin.amazia-portfolio.dedyn.io` に分離（永久ループも回避）
- ルートドメイン CNAME は DNS RFC 違反 → `www.amazia-portfolio.dedyn.io` に正規化（amazon.com と同じ構成）
- CloudFront のオリジンプロトコル既定値が HTTPS only で 504 → HTTP only へ修正
- ACM 証明書は CloudFront 用には us-east-1 必須（最初うっかり ap-southeast-2 で発行）

派生発見：
- 画像永続化の不在（本番 docker-compose に bind mount なし）→ ホストパス + symlink で恒久対応
- Cookie 中継不備（→ 031）／JWT alg 不一致（→ 032）／画像配信ルートが auth 配下（→ 033）

**再発防止**：「無料枠で完走できる構成か」を着手前のチェックリスト化（`feedback_free_tier_first` メモリで記録済み）／Cookie は最小スコープ原則（refresh エンドポイント直下）／フィーチャーフラグ（`HTTPS_ENABLED`）で AWS 側未完でもデプロイを止めない構造を採用／009 教訓どおり新規環境変数は docker-compose.yml + phpunit.xml + application-test.properties をセット更新（履行済み）。

---

## 全体傾向と考察（021〜030）

### テストケース不足に起因するもの（4件）

| # | テストの空白 |
|---|------------|
| 021 | API 異常系のレスポンス契約（`errors[]` 構造）が未検証 |
| 024 | 失敗時ログ取得の不足（stdout/stderr/StatusDetails 3点未取得） |
| 027 | H2/MySQL DDL 互換性の検証不足 + JPA `columnDefinition=JSON` 不整合 |
| 029 | 本番 MySQL での users スキーマ齟齬（CI は H2 で検知不能） |

### 環境設定上の課題（7件）

| パターン | 該当 |
|---------|------|
| デプロイ前 SSM 健全性検査の不備 | 022 |
| compose 操作の経路間不整合 | 023 |
| SSM リカバリ完了判定の甘さ | 025 |
| SSM 配信パスの観測手段不足（PingStatus だけでは足りない） | 026 |
| CD 中断時の Docker 残骸処理 | 028 |
| OS パッケージ（compose プラグイン）の運用不安定性 | 029 |
| `.env.production` 不在による Spring/Laravel 設定空文字化 | 029 |

### 設計判断記録（1件）

| # | 内容 |
|---|------|
| 030 | コスト方針整合のため ALB → CloudFront + desec.io へ構成変更（不具合ではない） |

---

## 021〜030 の構造的パターン

### パターンA：SSM デプロイ機構の連鎖補強（022 → 024 → 025 → 026）

```
022: PingStatus=ConnectionLost を事前検知できない → 事前検査追加
 ↓
024: Failed 時にエラーが空で原因不明 → ログ3点出力
 ↓
025: リカバリ後 Online 検知が早すぎて Pending 滞留 → 連続3回+60秒待機
 ↓
026: Online でも Undeliverable になる「ゾンビOnline」 → カナリア方式
```

「リカバリ機構を強化するたびに次の隙間が露呈する」典型的な連鎖。026 のカナリア方式に到達して **配信そのものを実証してから本コマンドを送る** 段階となり、phaseX-3 の本番デプロイで安定動作を確認できたため、022/024/025/026 はすべて ✅ 解決済とした。

### パターンB：Laravel + Spring が同じ DB を共有する構造的弱点（018 → 029 で再発）

`users` テーブルを Laravel migration と Spring data.sql の双方が触る前提で組まれているが、

- Laravel migration は標準カラムのみ
- Spring data.sql は業務カラムを期待
- mysql volume を介して片方の状態が残ると他方の起動が破綻

という弱点があり、018 で応急対処したのに 029 で再発。**022〜026 が SSM 配信問題、028〜029 が Docker / DB 構成問題と、再発系の分布が「インフラ運用の慢性的弱点」に偏っている** ことが本期間の特徴。

### パターンC：CD と systemd の経路不整合（023・028・029）

- 023：`docker-compose down/up` の `--remove-orphans` がデプロイ経路だけ抜け
- 028：CD 中断 → systemd 起動 のタイミングで残骸が起動失敗を誘発
- 029：systemd `docker compose` v2 を呼ぶのに deploy.yml は `docker-compose` v1 を呼んでいる

「CD と systemd で同じ compose を扱うのに記法・オプションが揃っていない」根の同じ問題が3件で顕在化。

### パターンD：phaseX-3 動作確認で連鎖発見された潜在不具合（030 → 031・032・033）

030 はそれ自体は構成変更だが、「**本番で初めて Console ログイン以降を実機検証した**」ことで Cookie 中継 / JWT alg / 画像配信ルートという **過去のローカル開発で見過ごされていた潜在不具合が同時に出現**。フェーズ完了の定義に「本番環境で E2E まで」を含めるべきという教訓を強化した。

---

## 001〜030 通算の構造的パターン

5月3日（001-006）／5月4日（007-013）／5月5日（014-020）／5月6日（021-030）の4レポート合算で 30 件全体に下記のパターンが継続している。

| パターン | 001-006 | 007-013 | 014-020 | 021-030 |
|---------|---------|---------|---------|---------|
| デプロイ後ヘルスチェック不在 | 003, 005 | 008, 010, 011, 013 | 014, 016 | （028 で派生） |
| docker-compose / 環境変数管理漏れ | 002, 004 | 008, 009, 010 | 015, 018 | 023, 029 |
| フロント / UI の実装検証不在 | — | 007, 011, 012, 013 | 014 | — |
| 認証・Cookie・プロキシ層の設計検証不在 | — | — | 019, 020 | 021（API契約）／031〜033は次回レポート対象 |
| AWS 運用（コスト・ディスク・配信統合） | — | — | 016, 017 | （X-3 の bind mount 設計が 030 補遺で対応） |
| **SSM デプロイ機構の連鎖補強**（新規） | 001 | — | — | 022, 024, 025, 026 |
| **本番 MySQL と Laravel/Spring の DB 共有問題**（再発） | — | — | 018 | 029 |
| **CD と systemd の経路不整合**（新規） | — | 008（systemd 導入） | — | 023, 028, 029 |

021〜030 で新たに浮上したのは：
1. **SSM デプロイ機構の連鎖補強**（022・024・025・026 のカスケード）
2. **CD と systemd で同じ compose を扱うのに記法・オプションが揃っていない**（023・028・029）
3. **phaseX-3 で「本番初動」確認が連鎖的な潜在不具合を露出**（030 補遺と派生 031-033）

---

## 推奨アクション（021〜030 追加分）

| 優先 | 内容 | 関連 |
|------|------|------|
| 高 | SSM カナリア + リカバリの安定性を継続観察し、3か月無事故なら別系統を疑える状態を作る | 022, 024, 025, 026 |
| 高 | Laravel migration に users 業務カラム追加 migration を新設、または Spring 側で `CREATE TABLE IF NOT EXISTS` を担う | 018, 029 |
| 高 | CD と systemd で扱う compose 記法（v1/v2）を統一する。`deploy.yml` を `docker compose`（v2 スペース形式）に寄せる | 023, 028, 029 |
| 高 | フェーズ完了の定義に「本番環境での E2E（ログイン → リロード → 画像表示）まで」を必須化 | 030 派生 |
| 中 | Spring Boot プロジェクト雛形に `GlobalExceptionHandler` を含める運用を coding_guidelines に明記 | 021 |
| 中 | `JWT_SECRET` を GitHub Secrets で管理し `deploy.yml` で `.env` を生成する手順を実装 | 029 |
| 中 | EC2 user data か setup スクリプトで `docker compose` v2 プラグインを Docker 公式バイナリで配置する手順を恒久化（AL2023 リポ非依存） | 029 |
| 中 | `schema.sql` 編集時の H2 互換チェックリストを coding_guidelines に追加（`INDEX` インライン句禁止など） | 027 |
| 低 | 「無料枠で完走できる構成か」のフェーズ着手前チェックリストを `docs/ai_context/` に Web 化（メモリと2系統で固定） | 030 |
| 低 | 失敗時 SSM コマンドを `cancel-command` でキューから外す手順を即時復旧手順テンプレートに統合 | 025, 026 |
