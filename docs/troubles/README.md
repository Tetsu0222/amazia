# 不具合一覧

詳細分析：
- [20260503_trouble_analysis.md](../analysis/20260503_trouble_analysis.md)（001〜006）
- [20260504_trouble_analysis.md](../analysis/20260504_trouble_analysis.md)（007〜013）
- [20260505_trouble_analysis.md](../analysis/20260505_trouble_analysis.md)（014〜020）
- [20260506_trouble_analysis.md](../analysis/20260506_trouble_analysis.md)（021〜030）

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
| 031 | [Console（Laravel）の Cookie 中継で Set-Cookie が落ちる](031_console_cookie_relay_drops_set_cookie.md) | Guzzle CookieJar 未有効化のため Spring の Set-Cookie がブラウザに届かず本番ログイン後 401・生ヘッダ透過に修正 | ✅ 解決済 | - | - |
| 032 | [JWT 署名アルゴリズム不一致で API が 401](032_jwt_alg_mismatch_console_vs_core.md) | Core が HS512 で発行するトークンを Console が SHA-256 固定で検証していて必ず署名不一致・alg ヘッダ追従に修正 | ✅ 解決済 | - | - |
| 033 | [Console 経由の SKU 画像配信が auth.jwt 配下にあり 401](033_console_image_file_route_under_auth_jwt.md) | `<img src>` は Authorization を運ばないのに画像配信ルートが auth.jwt 内にあり必ず 401・公開ルートに移動 | ✅ 解決済 | - | - |
| 034 | [フェーズ13 トラブル0件の不在分析（メタ）](034_phase13_no_incident_analysis.md) | phase13 でトラブル記録が 0 件だったこと自体を分析。設計成熟（仮説A）／運用薄（仮説B）／観測死角（仮説C）の切り分けを将来課題として残す | 🟡 様子見 | - | - |
| 035 | [Market 購入ボタンで sku_id=undefined](035_market_checkout_sku_id_undefined.md) | Core SkuDetail の getter は `getSkuId()` で JSON は `skuId` だが、Market 側で `selectedSku.id` を参照していて undefined。テストデータも実 JSON 形式と乖離していた | ✅ 解決済 | - | - |
| 036 | [MUI v9 移行漏れ（Grid v1構文 + Stack props 透過）](036_mui_grid_v1_to_v2_migration_missing.md) | フェーズ9・10で書かれた `<Grid item xs={...}>` の廃止 + MUI v9 の Stack で `alignItems`/`justifyContent` を直接 props に渡せず DOM 透過する仕様変更を見逃していた。複数フェーズを跨いで放置（後日メタ評価対象） | ✅ 解決済 | - | - |
| 037 | [Flyway 利用と誤認しフェーズ14テーブルが本番に作成されず](037_flyway_misassumed_phase14_tables_missing.md) | 本プロジェクトは Flyway 未導入で `schema.sql` 方式だが、`db/migration/` ディレクトリの存在から Flyway と外挿。Step 0/A で作った V6-V11 が死ファイル化し、注文確定 API が `payment_methods` 不在で 500 になっていた。035 と同型の「外挿による誤認」の再発（メタ評価対象） | ✅ 解決済 | - | - |
| 038 | [products.price/stock の NOT NULL 残存で Console 商品登録が500](038_products_price_stock_not_null_drift.md) | フェーズ10で SKU 側に移行された旧 `price`/`stock` カラムの NOT NULL 制約が本番 MySQL に残存。Vue ProductForm が price/stock を送らない（=設計通り）リクエストで MySQL 1048 で 500。H2 テストでは Entity 通りに NULL 許容で再生成されるため検知されなかった。027 と同種の H2/本番乖離 | ✅ 解決済 | - | - |
| 039 | [Market Checkout が preorder モード未対応で予約フローでも在庫バリデーションが効く](039_market_checkout_preorder_mode_missing.md) | C-4 で追加した予約フロー導線で `&preorder=1` 付きで checkout に遷移しても、Checkout.jsx 側がクエリを読み取っていなかったため通常注文フォームと同じ動作（在庫超過警告で確定不可）になっていた。Checkout のテスト未整備で画面間契約が漏れた | ✅ 解決済 | - | - |
| 040 | [SKU 価格未登録の商品が Market に出て注文時に 400](040_market_lists_products_without_sku_price.md) | C-4 で在庫 0 商品も Market に表示するようにした副作用で、SKU 価格未登録の商品まで露出。注文画面まで進めても Core が `sku price not registered` で 400 を返す。EC 業界標準は「価格未登録は出品不可」。Service 層に「販売可否」概念が無かった構造的な問題 | ✅ 解決済 | - | - |

## 再発防止アクション（未対応）

分析レポートで特定された再発防止策のうち、まだ実施されていないもの。

| 優先 | 内容 | 関連 | ステータス |
|------|------|------|-----------|
| 高 | デプロイ後ヘルスチェックステップの追加（HTTP 200確認） | 003・005 | ✅ X-2にて対応（[phaseX-2](../design/phaseX/phaseX-2_deploy_pipeline_redesign.md) 完了条件に含む） |
| 中 | 開発環境セットアップ手順書の整備（Dockerボリューム管理・Composer拡張注意事項） | 002・006 | ⬜ 未対応 |
| 低 | デプロイスクリプトへのファイル存在確認ステップの標準化 | 005 | ⬜ 未対応 |

> 優先度「高」のアクションは次の開発サイクルで対応することを推奨。
