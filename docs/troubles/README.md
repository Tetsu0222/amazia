# 不具合一覧

詳細分析：
- [20260503_trouble_analysis.md](../analysis/20260503_trouble_analysis.md)（001〜006）
- [20260504_trouble_analysis.md](../analysis/20260504_trouble_analysis.md)（007〜013）
- [20260505_trouble_analysis.md](../analysis/20260505_trouble_analysis.md)（014〜020）
- [20260506_trouble_analysis.md](../analysis/20260506_trouble_analysis.md)（021〜030）
- [20260507_trouble_analysis.md](../analysis/20260507_trouble_analysis.md)（031〜040 + 次フェーズへの再発防止策）

## 横断的な知見

個別トラブルから抽出した横断的な知見は以下に集約：

- [docs/ai_context/ai_collaboration_antipatterns.md](../ai_context/ai_collaboration_antipatterns.md) — AI 協働で踏みやすい落とし穴（AP-001〜008）
- [docs/ai_context/test_insights.md](../ai_context/test_insights.md) — テスト観点の知見
- [docs/ai_context/operational_insights.md](../ai_context/operational_insights.md) — 実装・運用パターンの落とし穴

新規トラブル記録時は [CLAUDE.md](../../CLAUDE.md) のテンプレに従い、`## AI協働観点` セクションも併せて埋めること。

## 一覧

| # | ドキュメント | 概要 | ステータス | 優先順位 | 分析状況 |
|---|-------------|------|-----------|---------|---------|
| 001 | [SSM ConnectionLost](001_ssm_connection_lost.md) | EC2 reboot後にSSMエージェントが回復しない | ✅ 解決済 | - | ✅ 済 |
| 002 | [MySQL host not allowed](002_mysql_host_not_allowed.md) | Dockerコンテナ間のMySQL接続拒否 | ✅ 解決済 | - | ✅ 済 |
| 003 | [SSM コマンドキュー詰まり](003_ssm_command_queue_stuck.md) | docker-compose --build によるSSMキュー詰まり | ✅ 解決済 | - | ✅ 済 |
| 004 | [EC2 IP変更によるCORSエラー](004_ec2_ip_changed_after_restart.md) | stop/start後のIP変動でCORS設定が無効化 | ✅ 解決済 | - | ✅ 済 |
| 005 | [Nginx 403 Forbidden](005_nginx_console_403.md) | Console UIへのアクセスが403を返す | ✅ 解決済 | - | ✅ 済 |
| 006 | [Composer platform ext missing](006_composer_platform_ext_missing.md) | ローカルでcomposer updateがext-gd/zipエラーで失敗 | ✅ 解決済 | - | ✅ 済 |
| 007 | [Excel一括インポート 422エラー](007_excel_import_422.md) | Excelアップロード時に422 Unprocessable Content | ✅ 解決済 | - | ✅ 済 |
| 008 | [EC2再起動後にコンテナが自動起動しない](008_containers_not_restart_after_ec2_reboot.md) | stop/start後にDockerコンテナがExitedのまま→502 | ✅ 解決済 | - | ✅ 済 |
| 009 | [CORE_BASE_URL未設定による500エラー](009_core_base_url_missing.md) | Phase8デプロイ後に/api/admin/productsが500（環境変数漏れ） | ✅ 解決済 | - | ✅ 済 |
| 010 | [Market商品未表示](010_market_products_not_displayed.md) | amazia-marketがdocker-compose.ymlに未定義のため商品0件表示 | ✅ 解決済 | - | ✅ 済 |
| 011 | [Consoleサイドバー未表示](011_console_sidebar_not_displayed.md) | App.vueにレイアウト未実装・SKUルート未登録 | ✅ 解決済 | - | ✅ 済 |
| 012 | [公開期間がMarketに反映されない](012_publish_period_not_reflected_in_market.md) | ProductFormのprice/stock必須バリデーションで送信不可 | ✅ 解決済 | - | ✅ 済 |
| 013 | [Console画像登録の導線なし](013_console_image_upload_missing.md) | Phase9でAPI実装済みだがVue UIが未追加 | ✅ 解決済 | - | ✅ 済 |
| 014 | [SKU価格管理ページのUI未実装](014_sku_price_ui_not_implemented.md) | SKU・価格管理のVue UIが未実装 | ✅ 解決済 | - | ✅ 済 |
| 015 | [amazia-market ECR pull失敗](015_amazia_market_ecr_pull_failed.md) | docker-compose.ymlに存在しないECRリポジトリへの参照が残りデプロイ失敗 | ✅ 解決済 | - | ✅ 済 |
| 016 | [SKU画像エンドポイント EC2で404](016_sku_image_404_on_ec2.md) | ECRイメージが古くSKU画像ルート未反映・EC2ディスク満杯が重なり発生 | ✅ 解決済 | - | ✅ 済 |
| 017 | [AWS課金：不要リソースの蓄積](017_aws_cost_unused_resources.md) | 停止中EC2・ECR古イメージ・t3.smallによる想定外課金 | ✅ 解決済 | - | ✅ 済 |
| 018 | [amazia-core 起動失敗 permissions テーブル不在](018_core_startup_permissions_table_not_exist.md) | amazia-coreがRestartingループ—data.sql実行時にpermissionsテーブルが存在しない | ✅ 解決済 | - | ✅ 済 |
| 019 | [Consoleログイン500 / Market商品取得401](019_console_login_500_market_401.md) | ConsoleログインでLaravel 500・Market商品一覧で401 | ✅ 解決済 | - | ✅ 済 |
| 020 | [リロードでログアウト／refresh_token Cookie未保存](020_refresh_cookie_domain_container_name.md) | ログイン後リロードでログアウト・アクセストークン期限切れ後に再認証失敗 | ✅ 解決済 | - | ✅ 済 |
| 021 | [社員登録422エラーの詳細不明](021_user_creation_422_no_error_detail.md) | POST /api/users が422を返すがエラー詳細が表示されない | ✅ 解決済 | - | ✅ 済 |
| 022 | [SSM Undeliverable でデプロイ失敗](022_ssm_undeliverable_during_deploy.md) | SSM Agent が ConnectionLost のままデプロイが走りコマンド配信不能で失敗 | ✅ 解決済 | - | ✅ 済 |
| 023 | [docker-compose name conflict](023_docker_compose_name_conflict_orphan.md) | 孤児コンテナ残留で `amazia-core` 作成時に name conflict・デプロイ失敗 | ✅ 解決済 | - | ✅ 済 |
| 024 | [SSM Failed 時のエラー出力が空](024_ssm_failed_no_error_output.md) | デプロイ Failed 時に stderr のみ取得していて根本原因が掴めない | ✅ 解決済 | - | ✅ 済 |
| 025 | [SSM 自動リカバリ直後 Pending 滞留](025_ssm_pending_after_recovery.md) | EC2 stop/start 直後の Online 検知が早すぎて send-command が Pending のまま落ちる | ✅ 解決済 | - | ✅ 済 |
| 026 | [SSM ゾンビOnline (Undeliverable)](026_ssm_zombie_online_undeliverable.md) | PingStatus=Online でも MGS セッション死亡で Undeliverable になる事象。カナリア方式で検知 | ✅ 解決済 | - | ✅ 済 |
| 027 | [フェーズ12 ワークフロー導入で CI 全滅](027_workflow_test_h2_schema_and_json_payload.md) | schema.sql の MySQL 専用構文 + payload の JSON 列が H2 テストで爆発・連鎖失敗 | ✅ 解決済 | - | ✅ 済 |
| 028 | [CD 中の SSM 配信不能 → コンテナクラッシュループ](028_cd_ssm_undeliverable_then_container_crashloop.md) | CD 中断 + EC2 stop/start 後に compose の残骸でコンテナが restart loop、SSM カナリアも InProgress 滞留 | ✅ 解決済 | - | ✅ 済 |
| 029 | [docker compose plugin 消失 + users スキーマ齟齬](029_compose_plugin_lost_and_users_schema_drift.md) | systemd の compose 起動が exit 125、users 業務カラム欠落で Spring が data.sql 失敗、SSM 応答阻害も連鎖 | ✅ 解決済 | - | ✅ 済 |
| 030 | [HTTPS化を CloudFront + desec.io 1ドメイン構成で実装](030_https_via_cloudfront_duckdns_single_domain.md) | フェーズ11 §3 ALB 案を無料枠完走方針と整合させるため CloudFront + desec.io（www サブドメイン）に切替・本番 HTTPS で主要動作確認まで完了 | ✅ 解決済 | - | ✅ 済 |
| 031 | [Console（Laravel）の Cookie 中継で Set-Cookie が落ちる](031_console_cookie_relay_drops_set_cookie.md) | Guzzle CookieJar 未有効化のため Spring の Set-Cookie がブラウザに届かず本番ログイン後 401・生ヘッダ透過に修正 | ✅ 解決済 | - | ✅ 済 |
| 032 | [JWT 署名アルゴリズム不一致で API が 401](032_jwt_alg_mismatch_console_vs_core.md) | Core が HS512 で発行するトークンを Console が SHA-256 固定で検証していて必ず署名不一致・alg ヘッダ追従に修正 | ✅ 解決済 | - | ✅ 済 |
| 033 | [Console 経由の SKU 画像配信が auth.jwt 配下にあり 401](033_console_image_file_route_under_auth_jwt.md) | `<img src>` は Authorization を運ばないのに画像配信ルートが auth.jwt 内にあり必ず 401・公開ルートに移動 | ✅ 解決済 | - | ✅ 済 |
| 034 | [フェーズ13 トラブル0件の不在分析（メタ）](034_phase13_no_incident_analysis.md) | phase13 でトラブル記録が 0 件だったこと自体を分析。設計成熟（仮説A）／運用薄（仮説B）／観測死角（仮説C）の切り分けを将来課題として残す | 🟡 様子見 | - | ✅ 済 |
| 035 | [Market 購入ボタンで sku_id=undefined](035_market_checkout_sku_id_undefined.md) | Core SkuDetail の getter は `getSkuId()` で JSON は `skuId` だが、Market 側で `selectedSku.id` を参照していて undefined。テストデータも実 JSON 形式と乖離していた | ✅ 解決済 | - | ✅ 済 |
| 036 | [MUI v9 移行漏れ（Grid v1構文 + Stack props 透過）](036_mui_grid_v1_to_v2_migration_missing.md) | フェーズ9・10で書かれた `<Grid item xs={...}>` の廃止 + MUI v9 の Stack で `alignItems`/`justifyContent` を直接 props に渡せず DOM 透過する仕様変更を見逃していた。複数フェーズを跨いで放置（後日メタ評価対象） | ✅ 解決済 | - | ✅ 済 |
| 037 | [Flyway 利用と誤認しフェーズ14テーブルが本番に作成されず](037_flyway_misassumed_phase14_tables_missing.md) | 本プロジェクトは Flyway 未導入で `schema.sql` 方式だが、`db/migration/` ディレクトリの存在から Flyway と外挿。Step 0/A で作った V6-V11 が死ファイル化し、注文確定 API が `payment_methods` 不在で 500 になっていた。035 と同型の「外挿による誤認」の再発（メタ評価対象） | ✅ 解決済 | - | ✅ 済 |
| 038 | [products.price/stock の NOT NULL 残存で Console 商品登録が500](038_products_price_stock_not_null_drift.md) | フェーズ10で SKU 側に移行された旧 `price`/`stock` カラムの NOT NULL 制約が本番 MySQL に残存。Vue ProductForm が price/stock を送らない（=設計通り）リクエストで MySQL 1048 で 500。H2 テストでは Entity 通りに NULL 許容で再生成されるため検知されなかった。027 と同種の H2/本番乖離 | ✅ 解決済 | - | ✅ 済 |
| 039 | [Market Checkout が preorder モード未対応で予約フローでも在庫バリデーションが効く](039_market_checkout_preorder_mode_missing.md) | C-4 で追加した予約フロー導線で `&preorder=1` 付きで checkout に遷移しても、Checkout.jsx 側がクエリを読み取っていなかったため通常注文フォームと同じ動作（在庫超過警告で確定不可）になっていた。Checkout のテスト未整備で画面間契約が漏れた | ✅ 解決済 | - | ✅ 済 |
| 040 | [SKU 価格未登録の商品が Market に出て注文時に 400](040_market_lists_products_without_sku_price.md) | C-4 で在庫 0 商品も Market に表示するようにした副作用で、SKU 価格未登録の商品まで露出。注文画面まで進めても Core が `sku price not registered` で 400 を返す。EC 業界標準は「価格未登録は出品不可」。Service 層に「販売可否」概念が無かった構造的な問題 | ✅ 解決済 | - | ✅ 済 |
| 041 | [公開期間判定の二重基準（秒単位 vs JST 0:00）で予約商品の注文確定が 400](041_publish_judgement_dual_basis_secs_vs_jst_zero.md) | `Product#isPublished()`（秒単位 LocalDateTime）と C-2 で追加した `PreorderStatusService`（JST 0:00 基準 LocalDate）の判定基準が食い違い、同日 17 時公開予定の商品が Market では PRE_ORDER として表示されるが Checkout では「非公開」で 400。公開判定を PreorderStatusService に統一して解消 | ✅ 解決済 | - | - |
| 042 | [EC2ディスクフル起因のゾンビOnline・カナリア配信失敗](042_disk_full_zombie_online_docker_image_pile.md) | 旧 Docker イメージが `/var/lib/docker` に蓄積（33個・3.08GB）してルートボリューム 8GB を枯渇。systemd-journald がジャーナルを書けず無限エラー、SSM Agent も `echo canary-ok` を実行できず CD の `canary_check` が `Failed` で停止。EBSを16GBに拡張＋`docker system prune -af`で復旧、deploy.ymlに `docker image prune -af` 自動化と canary_check 失敗時の3点セットログ出力を追加 | ✅ 解決済 | - | - |
| 043 | [Market 購入履歴の配送状況が Console の更新を反映しない](043_market_purchase_history_shipping_status_not_synced.md) | 配送ステータスが `sales.shipping_status_id` と `deliveries.shipping_status_id` の二重ソースになっており、Console の状態遷移は `deliveries` のみ更新する一方で Market 購入履歴 API は `sales` 側を読んでいたため、Console で「配送済」にしても Market 購入履歴は永続的に「配送準備中」表示。読み出し側を deliveries 優先（旧 sales のみフォールバック）に修正 | ✅ 解決済 | - | - |
| 044 | [operation_logs テーブル不在 + users.id UNSIGNED ドリフトで操作履歴が500](044_operation_logs_table_missing_users_id_unsigned_drift.md) | schema.sql の `operation_logs.user_id BIGINT` が本番 `users.id BIGINT UNSIGNED` と FK 型互換せず Core 起動時の DDL が `continue-on-error` で潰されてテーブル未作成。呼び出された瞬間 1146 で 500。本番ホットフィックス＋schema.sql を UNSIGNED に修正。027・038 に続く H2／本番 MySQL 乖離系の3例目（メタ評価対象） | ✅ 解決済 | - | - |
| 045 | [sales_return テーブル不在 + users.id UNSIGNED ドリフトで返品管理が500](045_sales_return_table_missing_users_id_unsigned_drift.md) | 044 と完全に同型。`sales_return.approver_id BIGINT` が本番 `users.id BIGINT UNSIGNED` と FK 型互換せず DDL が `continue-on-error` で潰されてテーブル未作成、呼び出された瞬間 1146 で 500。044 修正時に「同じ参照先を持つ全 FK 列の棚卸し」を行わなかったために 2 日後に同型再発。phaseX-6 のヘルスチェック完全運用後は同型不具合がデプロイ直後に検知される（メタ評価対象） | ✅ 解決済 | - | - |
| 046 | [CD ヘルスチェックの MySQL 認証が `Access denied (using password: NO)` で失敗](046_cd_healthcheck_mysql_root_password_unexpanded.md) | phaseX-6 で追加した「主要テーブル存在確認」ステップが初回デプロイで `exit 1`。SSM 経由 `docker exec` の外側ダブルクォートにより EC2 ホスト側 bash で `$MYSQL_ROOT_PASSWORD` が空展開され `mysql -p amazia` 相当に劣化していた。`sh -c '...'` のシングルクォート方式に修正（同型バグの DIFF クエリ・mysqldump も同時修正）| ✅ 解決済 | - | - |
| 047 | [Console 商品マスタの削除確認ポップアップのレイアウト崩れ](047_console_product_delete_popconfirm_layout_broken.md) | 商品一覧の行「削除」押下時、`a-popconfirm` の中身が縦積み・タイトル左はみ出しで崩れる。画面右端列で `placement` 未指定（デフォルト `top`）が画面外押し出され、加えて `@click.stop` を Popconfirm 自体に付けていてイベント伝播停止が効いていなかった。`placement="topRight"` 指定とボタン側への `@click.stop` 移動で解消 | ✅ 解決済 | - | - |
| 048 | [CD ヘルスチェックの SQL シングルクォートが `sh -c '<INNER>'` を閉じて `Unknown column 'amazia'` で失敗](048_cd_healthcheck_sql_quote_break_inside_sh_c.md) | 046 修正で `sh -c '<INNER>'` 方式に切り替えたが、INNER に埋め込んだ SQL 本文中の `'amazia'` `IN ('users','products',...)` のシングルクォートが外側を閉じてしまい、ホスト bash で `WHERE table_schema=amazia` に劣化して MySQL が `Unknown column 'amazia'` を返す。SQL 中の `'` を `'\''` にエスケープしてから INNER に埋め込む方式（bash パラメータ展開）に修正。**046 が「`$VAR` だけ守った点の対症療法」になっており、`sh -c '<INNER>'` クラス全体の不変条件を整理しなかったための同型再発**。同ドキュメントに派生節として `tr -d '[:space:]'` が `\n` まで削って `EXPECTED_COUNT=1` 誤検知になっていた件も併記（メタ評価対象） | ✅ 解決済 | - | - |
| 049 | [password_histories テーブルが schema.sql 未記載のまま本番に存在せず（044・045 同型・phaseX-6 で事前検知）](049_password_histories_table_missing_in_schema_sql.md) | 046・048 が解消されて phaseX-6 ヘルスチェックが本来の役割で初動した瞬間、`password_histories` テーブルが本番 MySQL に存在しないことを検知。DDL は Flyway 名残ファイル `V1__create_auth_tables.sql` にしか無く、schema.sql には未記載のまま運用に入っていた（設計書では「要検討事項」と既知扱い）。schema.sql に CREATE TABLE IF NOT EXISTS を追加（FK 列を BIGINT UNSIGNED に揃え）。**044・045 と同じ schema.sql ドリフト系統だが、本件は phaseX-6 ヘルスチェックの初の「事前検知」事例**（メタ評価対象） | ✅ 解決済 | - | - |
| 050 | [テストヘルパーのハードコードで `@UniqueConstraint(product_id, color, size)` に衝突し CI 全滅（H2 ドリフト系統の "逆向き" 顕在化）](050_h2_unique_constraint_test_helper_collision.md) | `ListPreorderProductsServiceTest` の `createSku` が `color="赤"` `size="M"` をハードコードしたまま同一 product に対して 3 回呼ばれる新規テストを追加。`skuCode` のみ `nanoTime` で一意化していたが UNIQUE 制約は `(product_id, color, size)` にかかっており 2 件目で H2 が UNIQUE 違反を返して CI 全滅。`color` を `nanoTime` で一意化して解消。**027/037/038/044/045/049 と同じ H2/本番 MySQL 乖離系統だが本件は方向が逆（H2 が Entity の `@UniqueConstraint` を忠実に DDL 化するから落ちる）**。二次リスクとして `product_skus` の `CREATE TABLE` が schema.sql に未記載（049 同型）の課題を次フェーズに送り（メタ評価対象） | ✅ 解決済 | - | - |

## 再発防止アクション（未対応）

分析レポートで特定された再発防止策のうち、まだ実施されていないもの。

| 優先 | 内容 | 関連 | ステータス |
|------|------|------|-----------|
| 高 | デプロイ後ヘルスチェックステップの追加（HTTP 200確認） | 003・005 | ✅ X-2にて対応（[phaseX-2](../design/phaseX/phaseX-2_deploy_pipeline_redesign.md) 完了条件に含む） |
| 高 | **デプロイ後ヘルスチェックに「主要テーブル存在確認 SQL」を追加**。`continue-on-error` で潰された WARN を**デプロイ後に検知**する仕組みを次回品質改善フェーズで導入（H2/本番MySQLスキーマ乖離の系統的盲点への対策） | 027・037・038・044 | ✅ X-6 にて対応（[phaseX-6](../design/phaseX/phaseX-6_post_deploy_schema_healthcheck.md) §改善① — deploy.yml + `ops/healthcheck/required_tables.txt`） |
| 高 | Core 起動ログの WARN を CD ジョブログに `grep` 抽出するステップを deploy.yml に追加（`continue-on-error` の盲点対策） | 037・038・044 | ✅ X-6 にて対応（[phaseX-6](../design/phaseX/phaseX-6_post_deploy_schema_healthcheck.md) §改善② — deploy.yml「起動ログの schema 関連 WARN 抽出」ステップ） |
| 高 | フェーズ完了条件に「本番環境での E2E（ログイン → リロード維持 → 画像表示）まで」を必須化 | 030・031・032・033 | ⬜ 未対応（CLAUDE.md 規約 / `test_insights.md` カテゴリ8 への接続） |
| 中 | クロスサービス回帰テスト（Console 状態遷移 → Market 読み出しの往復）の観点を `test_insights.md` に追記 | 043 | ⬜ 未対応 |
| 中 | Spring 発行 JWT を Console PHPUnit でフィクスチャ検証する E2E 寄りテスト導入。`JWT_SECRET` 64バイト以上を `setup.md` に明記 | 032 | ⬜ 未対応 |
| 中 | フロントの Vitest モックデータを Core DTO の実 JSON 形式に揃える運用（getter 名から外挿しない） | 035・039・040 | ⬜ 未対応 |
| 中 | `product_skus` の `CREATE TABLE` を schema.sql に追記（049 同型の二次リスク。phaseX-6 の本番 mysqldump スナップショットと突合して移植） | 050 | ⬜ 未対応（次フェーズ送り） |
| 中 | `@UniqueConstraint` を持つ Entity のテストヘルパーは制約カラムを毎回ユニーク化／引数化する規律を `test_insights.md` カテゴリ7-2 に追記済（横展開時にレビュー観点として参照） | 050 | ✅ 050 修正時に対応（`test_insights.md` カテゴリ7-2「`@UniqueConstraint` を持つ Entity のテストヘルパー設計」） |
| 中 | URL クエリ・ナビゲーション state 経由の画面遷移は遷移先画面でも単体テストで「クエリ解釈」を検証する観点を `test_insights.md` の「画面間契約テスト」項目に追加 | 039 | ⬜ 未対応 |
| 中 | UI ライブラリのメジャーバージョンアップ手順（Migration Guide 確認 → 旧構文 grep → ブラウザ Console 警告目視）を `docs/ai_context/` に整備 | 036 | ⬜ 未対応 |
| 中 | 設計書「前提」セクションに **裏付け参照ファイル** を必須項目として明記する規約を CLAUDE.md に追加検討 | 037 | ⬜ 未対応 |
| 中 | 開発環境セットアップ手順書の整備（Dockerボリューム管理・Composer拡張注意事項） | 002・006 | ⬜ 未対応 |
| 低 | `console.error` を fail に昇格させる Vitest 設定（`@testing-library/react`）の導入を将来課題として記録 | 036 | ⬜ 未対応 |
| 低 | `operational_insights.md` カテゴリ3 を「既存実装と環境設定の棚卸し」に拡張 | 037 | ✅ X-6 にて対応（[phaseX-6](../design/phaseX/phaseX-6_post_deploy_schema_healthcheck.md) §改善③ — `operational_insights.md` カテゴリ3 に schema.sql 編集時の3点観点を追記） |
| 低 | 本番 MySQL スキーマスナップショットを S3 に日次保存（schema.sql との週次差分レビュー用） | 027・037・038・044 | ✅ X-6 にて対応（[phaseX-6](../design/phaseX/phaseX-6_post_deploy_schema_healthcheck.md) §改善④ — deploy.yml「本番 DB スキーマスナップショット保存」ステップ。S3 ライフサイクル設定は別途手動） |
| 低 | phase13 の観測網の死角（CSRF 403 件数 / ロック解除ログ / SES バウンス通知 / `market_sessions` GC）の整備 | 034 | ⬜ 未対応（仮説 B/C 切り分けの将来課題） |
| 低 | デプロイスクリプトへのファイル存在確認ステップの標準化 | 005 | ⬜ 未対応 |

> 優先度「高」のアクションは次の開発サイクルで対応することを推奨。
> 特に「主要テーブル存在確認」「Core 起動 WARN 抽出」の2つは、027・037・038・044 と同型の **H2/本番MySQLスキーマ乖離** の継続再発を断ち切るための中核的施策。詳細設計は [20260507_trouble_analysis.md](../analysis/20260507_trouble_analysis.md) §「次の品質改善フェーズに送る再発防止策」を参照。
