# Phase X-7 Step 1：トラブル50件 AI協働観点トリアージログ

## 目的

`docs/troubles/001`〜`050` を AI 協働観点でタグ付けし、深掘り対象（5〜10件）を選定するための作業ログ。
本ファイルは Phase X-7 Step 1 の成果物であり、Step 2 以降のパターン抽出は本ログを根拠に進める。

完了後も残置し、将来 50 件超のトラブル発生時の再棚卸しの基準として参照可能にする。

## 作成日
2026-05-08（Claude による叩き案）

## レビュー状況
🔲 ユーザーレビュー待ち

---

## タグ定義（再掲）

| タグ | 定義 |
|------|------|
| `[AI起因]` | AIの判断・実装ミスが主因（既存コード/スキーマ未確認・ドキュメント鵜呑み・実機未検証・誤報告・対症療法・過去知見の参照漏れなど） |
| `[協働ミス]` | 人間×AIの協働プロセスで起きたミス（指示曖昧・確認漏れ・スコープ未明示・差分の流し読みマージなど） |
| `[AI無関係]` | インフラ起因・外部要因（AWS/UI ライブラリ仕様変更・物理タイミング・運用判断など）でAI協働の改善余地が薄い |

判定の優先順位：AI起因の要素があれば `[AI起因]` を優先。明確にAI関与が読み取れない場合は `[AI無関係]`。判定保留は `[協働ミス]` に寄せる。

---

## トリアージ結果（全50件）

| 番号 | ファイル | タグ | 根拠（1行） |
|------|---------|------|------------|
| 001 | ssm_connection_lost | [AI無関係] | SSMエージェント起動順序の物理タイミング起因。AI判断とは無関係。 |
| 002 | mysql_host_not_allowed | [AI無関係] | Docker ネットワーク設定の実装手順問題。インフラ側の既知パターン。 |
| 003 | ssm_command_queue_stuck | [AI起因] | CI/CD 設計（--build 除去）を AI が PR 後も検証漏れで重複実装。 |
| 004 | ec2_ip_changed_after_restart | [協働ミス] | Elastic IP 未割り当ての仕様を人間が確認懈怠。AI は CORS 設定対応のみ。 |
| 005 | nginx_console_403 | [協働ミス] | distコピー元パスが Vite 設定と人間が確認懈怠で乖離。SSM キュー詰まり（003 の副次）。 |
| 006 | composer_platform_ext_missing | [協働ミス] | 人間が Dockerfile 拡張有効化を忘れ AI の install コマンド（`--ignore-platform-req`）では根本解決不可。 |
| 007 | excel_import_422 | [AI起因] | AI が Ant Design fileList オブジェクト構造（`.originFileObj`）を読まずに実装した。 |
| 008 | containers_not_restart_after_ec2_reboot | [AI起因] | AI が docker-compose.yml の restart ポリシー漏れを見落とし、systemd unit 導入も後付けになった。 |
| 009 | core_base_url_missing | [AI起因] | AI が新規環境変数を docker-compose.yml 追記漏れ、テスト側もハードコードで乖離。 |
| 010 | market_products_not_displayed | [AI起因] | AI が docker-compose.yml に amazia-market サービス定義を見落とし、nginx プロキシ設定の環境差（127.0.0.1 vs サービス名）も見落とした。 |
| 011 | console_sidebar_not_displayed | [AI起因] | AI が設計書の App.vue レイアウト実装・ルーター登録を忘れた（フロントエンド実装漏れ）。 |
| 012 | publish_period_not_reflected_in_market | [AI起因] | AI が SKU 移行後もフォーム側で price/stock 必須バリデーションを削除忘れ。Entity と設計書乖離。 |
| 013 | console_image_upload_missing | [AI起因] | バックエンド API は実装済みながら、AI が ProductForm.vue 画像アップロード UI を実装していない。 |
| 014 | sku_price_ui_not_implemented | [AI起因] | AI が SkuPriceList.vue をプレースホルダーのまま放置。バックエンド API 実装で満足した。 |
| 015 | amazia_market_ecr_pull_failed | [協働ミス] | 人間が Market を CI/CD に組み込む設計判断をしたが、要求・スコープが中途半端で ECR リポジトリ作成等の前提整備を AI に渡せなかった。人間側の設計ミスが主因。 |
| 016 | sku_image_404_on_ec2 | [協働ミス] | EC2 上のイメージが古く（人間のデプロイ手順漏れ）。AI は「両レイヤーを同時デプロイすべし」と後知恵。 |
| 017 | aws_cost_unused_resources | [AI無関係] | AWS リソース最適化は AI 協働の余地が薄い（無料枠管理は運用判断）。 |
| 018 | core_startup_permissions_table_not_exist | [AI起因] | AI が Flyway / JPA DDL / schema.sql の運用を整理せず、Entity 未定義のテーブル参照を見落とした。 |
| 019 | console_login_500_market_401 | [AI起因] | AI が Guzzle Cookie メソッド名（`isSecure()` vs `getSecure()`）を誤読。市場ルート設計も乖離。 |
| 020 | refresh_cookie_domain_container_name | [AI起因] | AI が core から返された Cookie の domain=コンテナ名を、ブラウザへ転送時に null で上書き処理を忘れた（対症療法不完全）。 |
| 021 | user_creation_422_no_error_detail | [AI無関係] | GlobalExceptionHandler 未実装は架構上の設計漏れ。レスポンスボディ構造は DP 側の責任。 |
| 022 | ssm_undeliverable_during_deploy | [AI無関係] | SSM Agent の ConnectionLost は物理タイミング。AI は接続確認ロジック追加で対応（部分的）。 |
| 023 | docker_compose_name_conflict_orphan | [AI起因] | AI が `--remove-orphans` フラグを deploy.yml に漏記。systemd unit には入っていたのに。 |
| 024 | ssm_failed_no_error_output | [AI無関係] | AWS SSM API の失敗時ログ取得が不十分な設計。AI は stdout/stderr/StatusDetails 出力追加で対応。 |
| 025 | ssm_pending_after_recovery | [AI起因] | AI が Online 判定を甘くしリカバリ後の Pending 滞留が発生。連続検知・待機を後付けで対応した。 |
| 026 | ssm_zombie_online_undeliverable | [AI起因] | AI が PingStatus=Online だけに依存し、MGS セッション死を検知できず。カナリアコマンド方式を導入。 |
| 027 | workflow_test_h2_schema_and_json_payload | [AI起因] | AI が schema.sql を H2 互換性確認なしで記述（MySQL INDEX 構文）。Entity と JSON 型の二重エラー。 |
| 028 | cd_ssm_undeliverable_then_container_crashloop | [AI無関係] | CD 中断・SSM 配信中断で残骸が蓄積。設計側は 029 でプラグイン消失と判明（AI は観測不充分）。 |
| 029 | compose_plugin_lost_and_users_schema_drift | [協働ミス] | 環境変化（compose プラグイン消失・.env 忘却）に加え、人間が運用前提を AI と共有しないまま実装を進めた協働プロセス上のミス。 |
| 030 | https_via_cloudfront_duckdns_single_domain | [AI起因] | AI が DuckDNS プレフィックス付き CNAME 不可を見落とし（実装着手前に検証不足）。ACM / CloudFront 設定は対症療法。 |
| 031 | console_cookie_relay_drops_set_cookie | [AI起因] | AI が Guzzle CookieJar デフォルト無効を見落とし、Set-Cookie 転送が機能しなかった。 |
| 032 | jwt_alg_mismatch_console_vs_core | [AI起因] | AI が JJWT Keys.hmacShaKeyFor() の鍵長依存を読まず、Console は HS256 固定で実装。テストも別々。 |
| 033 | console_image_file_route_under_auth_jwt | [AI起因] | AI が img src 要素が Authorization ヘッダを運ばないこと見落とし、auth.jwt ルート内に画像配信をさせた。 |
| 034 | phase13_no_incident_analysis | [AI無関係] | トラブル 0 件メタ分析。設計知見蓄積（test_insights.md）が効いた可能性は高い。観測解像度評価が主眼。 |
| 035 | market_checkout_sku_id_undefined | [AI起因] | AI が Core DTO getter 名（skuId）を確認せず、Market で id を仮定した（外挿による誤認）。 |
| 036 | mui_grid_v1_to_v2_migration_missing | [AI起因] | AI が MUI v9 アップグレード後も Grid v1 構文・Stack props を直さず（ライブラリ移行手順漏れ）。 |
| 037 | flyway_misassumed_phase14_tables_missing | [AI起因] | AI が Flyway 使用と誤認し V6〜V11 を作成。schema.sql 正本性を確認せず。フェーズ14 設計前の pom.xml 確認漏れ。 |
| 038 | products_price_stock_not_null_drift | [AI無関係] | 本番 MySQL の旧 NOT NULL vs Entity NULL 許容は H2 テストと乖離。スキーマドリフト検知体制不在。 |
| 039 | market_checkout_preorder_mode_missing | [AI起因] | AI が Checkout.jsx に予約モード分岐を実装忘れ（ProductDetail 側は実装済みながら間が取り残された）。 |
| 040 | market_lists_products_without_sku_price | [AI起因] | AI が販売可否（SKU 価格登録状態）の Service 層判定を実装せず、C-4 で「在庫 0 も表示」にしたら価格未定商品まで出た。 |
| 041 | publish_judgement_dual_basis_secs_vs_jst_zero | [AI起因] | AI が公開判定の二重基準（秒単位 vs JST 0:00）を見落とし、Market と注文確定で挙動分岐。 |
| 042 | disk_full_zombie_online_docker_image_pile | [AI無関係] | Docker イメージ蓄積・ディスク満杯は長期的インフラ管理問題。AI は prune 自動化で対応。 |
| 043 | market_purchase_history_shipping_status_not_synced | [AI起因] | AI が読み出し時の参照先（sales.shipping_status_id vs deliveries.shipping_status_id）の真実の所在を定義しなかった。 |
| 044 | operation_logs_table_missing_users_id_unsigned_drift | [AI起因] | AI が users.id BIGINT UNSIGNED と FK 列型の整合性を見落とし、schema.sql の UNSIGNED 統一を忘れた。 |
| 045 | sales_return_table_missing_users_id_unsigned_drift | [AI起因] | 044 修正で users.id 参照先の全 FK 棚卸しを謳いながら、sales_return.approver_id の同型ドリフトを見落とした。 |
| 046 | cd_healthcheck_mysql_root_password_unexpanded | [AI起因] | AI が docker exec ... sh -c "..." でホスト側展開される `$MYSQL_ROOT_PASSWORD` をシングルクォートで保護せず。 |
| 047 | console_product_delete_popconfirm_layout_broken | [協働ミス] | UI ライブラリ仕様起因だが、人間が UI 確認観点を AI に明示せず実装レビューでも見逃した協働プロセス上のミス。 |
| 048 | cd_healthcheck_sql_quote_break_inside_sh_c | [AI起因] | AI が `sh -c '...'` 内の SQL リテラル `'` をエスケープ（`'\''`）せず、046 修正後も破壊。クラス問題を見落とした。 |
| 049 | password_histories_table_missing_in_schema_sql | [AI起因] | AI が schema.sql に password_histories DDL を記載忘れ（Flyway V1 だけ）。設計書「要検討事項」を未消化のまま実装。 |
| 050 | h2_unique_constraint_test_helper_collision | [AI起因] | AI が @UniqueConstraint(product_id, color, size) を読まずテストヘルパーで色をハードコード。同一 product 複数 SKU で衝突。 |

---

## タグ別集計

| タグ | 件数 | 比率 |
|------|------|------|
| [AI起因] | 30 | 60% |
| [協働ミス] | 7 | 14% |
| [AI無関係] | 13 | 26% |
| 合計 | 50 | 100% |

---

## 深掘り候補（Step 2 へ送る代表ケース）

`[AI起因]` `[協働ミス]` 計 34 件から、**パターンの異なる代表 8 件** を選定する叩き案。
重複パターンは 1 件に集約し、最も再発被害が大きいもの／最も典型的なものを代表とした。

| # | 出典 | 仮アンチパターン名 | 代表理由・他の同型件 |
|---|------|---------------------|---------------------|
| 1 | 044 / 045 / 049 | **既存スキーマ・既存DDLを読まずに新規追記** | 044→045 で同型再発した教訓的事例。049 も schema.sql 全体を読まずに記載漏れた同根。最頻出かつ最被害大。 |
| 2 | 050 / 027 | **Entity / 制約を読まずにテストヘルパーをハードコード** | @UniqueConstraint や Entity 注釈を読まずテストを書いた事例。H2 / MySQL 互換性とも絡む。 |
| 3 | 037 | **存在しない技術スタックを仮定（Flyway 誤認）** | pom.xml を確認せずFlyway 使用と仮定し V6〜V11 を作成。技術選定の前提誤認パターン。 |
| 4 | 035 / 019 / 031 / 032 | **API/SDK のシグネチャ・仕様を確認せず外挿** | DTO getter 名・Guzzle メソッド名・JJWT 鍵長制約・CookieJar デフォルト。コード/ドキュメント未確認の外挿が共通根。 |
| 5 | 046 / 048 | **シェル/SQL のクォート・エスケープを読まず実装** | `sh -c "..."` 内のホスト変数展開と SQL リテラルクォート。046 修正で類似問題を見落とし 048 で再発。 |
| 6 | 020 / 030 / 041 | **対症療法で本質根因を残す** | 020 は Cookie domain 上書き忘れ、030 は DuckDNS 仕様検証不足、041 は公開判定の二重基準温存。「動いた」で満足した事例。 |
| 7 | 011 / 013 / 014 / 039 | **設計書の機能を部分実装で完了報告** | バックエンド API だけ／親画面だけ／プレースホルダーで「動く」と判定し、UI 全体や横断機能の実装漏れに気付けず。 |
| 8 | 003 / 023 / 008 / 025 / 026 | **CI/CD・運用設計の検証不足で副次トラブル誘発** | --build 除去・--remove-orphans 漏れ・restart ポリシー漏れ・SSM Online 判定甘さ。実機運用の長期挙動を予見できなかった共通根。 |

代表 8 パターンで `[AI起因]` 30 件の大半をカバーできる見込み。Step 2 で具体化する。

---

## 注記

- 判定は文書記載のみから推定。当時の実際のやり取り（プロンプトログ）は未参照のため、ユーザーレビュー時に「実態と判定がズレる」ものは修正してください。
- 「AI起因」と判定したものでも、人間側の予防機会（指示の出し方・レビュー観点）は必ず存在します。Step 2 で必ず両側面を抽出します。
- `[AI無関係]` と判定したものでも、AI が初動対応や事後分析で関与している場合があります。これらは「AI は協働者として有用だった」事例として、Step 3 のアンチパターン集の前段に「AI 協働が機能した事例」節を設けるかどうかは要検討。

---

# Step 2：深掘り対象のパターン抽出（構造化メモ）

## 目的

Step 1 で選定した深掘り 8 パターンについて、5 観点（症状・AI側の判断ミス・人間側の予防機会・検知タイミング・事前ガード/事後検知）を抽出する。
本セクションは Step 3 でアンチパターン集本体（`ai_collaboration_antipatterns.md`）を書く際のドラフト元として使う。

## 作成日
2026-05-08（Claude による叩き案）

## レビュー状況
🔲 ユーザーレビュー待ち

---

## AP-001: 既存スキーマ・既存DDLを読まずに新規追記

**症状：**
Core 起動時の schema.sql 実行で既存の users.id（BIGINT UNSIGNED）との FK 型不互換により operation_logs テーブルの作成が失敗。`continue-on-error=true` で WARN として潰され、本番 MySQL に該当テーブルが存在しないまま稼働、API 呼び出し時に 500 エラー発生。

**AI 側の判断ミス：**
- 既存 users テーブルの型（UNSIGNED）を確認せずに新規テーブルの FK 列を signed BIGINT で実装
- H2 テスト環境では UNSIGNED 概念が無いため FK 互換性チェックが通り、本番固有の不整合を見落とし
- schema.sql の continue-on-error 挙動を織り込まず、DDL 失敗が WARN で潰されることを認識していなかった
- 044 修正後に「users.id 参照先の全 FK 棚卸し」を謳いながら 045（sales_return.approver_id）で同型再発、049 では schema.sql 全体を見ずに password_histories の DDL 自体を記載漏れ

**人間側の予防機会：**
- 新規テーブル追加時に「既存スキーマの参照先テーブル（特に users.id）の型を確認」をチェックリスト化して指示文に明示
- 044 修正時に「users.id 参照先 FK の全棚卸し」を AI 任せにせず grep 結果を一緒に検証
- 設計レビューで「FK 列と参照先の型が整合するか」を必ず確認

**検知タイミング：**
本番デプロイ後、ユーザー操作（API 呼び出し時）で初めて検知。CI（H2）では型概念が無く、`continue-on-error` で WARN になるため CD でも気付けない。

**事前ガード：**
- operational_insights.md / coding_guidelines.md に「users.id は BIGINT UNSIGNED、参照する全 FK 列も同一型で統一」を明記
- 新規テーブル DDL 作成時のテンプレ化（FK 宣言行に参照先型のコメント必須）
- Entity と schema.sql の差分を CI で自動検出（@JoinColumn の参照先型と FK 列型の照合）

**事後検知：**
- phaseX-6 で導入済みの主要テーブル存在確認（`required_tables.txt` 照合）
- 起動ログから schema.sql の WARN を CD ジョブログに `grep` 抽出し可視化

**出典：**
- メイン: docs/troubles/044_operation_logs_table_missing_users_id_unsigned_drift.md
- 関連: docs/troubles/045_sales_return_table_missing_users_id_unsigned_drift.md, docs/troubles/049_password_histories_table_missing_in_schema_sql.md

---

## AP-002: Entity / 制約を読まずにテストヘルパーをハードコード

**症状：**
ProductSku の `@UniqueConstraint(product_id, color, size)` を確認せずテストヘルパーで color/size をハードコード値に固定。同一 product に複数 SKU を作成するテストで制約違反となり関連テストが全滅。

**AI 側の判断ミス：**
- Entity の `@UniqueConstraint` を確認せずにテストヘルパーを実装
- 同一テーブルに複数行を作るテストケースのスコープを考慮せず、color を単一固定値に設定
- H2 が Entity 定義から UNIQUE 制約を生成することを織り込まず、テスト作成時に手元での検証を省略

**人間側の予防機会：**
- テストヘルパー作成・改修時に「対象 Entity の制約注釈を確認」をプロンプトに含める
- 既存テストで同テーブルの複数行作成パターンがあれば参照するようレビューで指摘
- ヘルパーの設計原則（制約カラムは引数化または `nanoTime` 等で一意化）を test_insights.md に明文化

**検知タイミング：**
CI（mvn test）で即座に検知。ただしヘルパーが他テストに連鎖するため、影響範囲が広く失敗件数が多い。

**事前ガード：**
- test_insights.md に「@UniqueConstraint を持つ Entity のヘルパーは制約カラムを引数化／一意化する」観点を追加
- ヘルパー実装時のテンプレ（Entity 注釈をコメントで併記）

**事後検知：**
- CI 失敗時に UNIQUE 違反メッセージを検出したら、同 Entity の他テストとヘルパー共有を自動チェック
- ヘルパー追加 PR で「対象 Entity の `@UniqueConstraint` 一覧」を PR 本文に貼る運用

**出典：**
- メイン: docs/troubles/050_h2_unique_constraint_test_helper_collision.md
- 関連: docs/troubles/027_workflow_test_h2_schema_and_json_payload.md

---

## AP-003: 存在しない技術スタックを仮定（Flyway 誤認）

**症状：**
Flyway を使用していないプロジェクトで `db/migration/` ディレクトリの存在から Flyway 利用を誤認。V6〜V11.sql を作成したが本番では実行されず、必要テーブル 6 個が本番 MySQL に作成されないまま phase14 の注文確定 API が 500 エラー多発。

**AI 側の判断ミス：**
- `db/migration/` の存在から「Flyway を使用している」と外挿し、`pom.xml` の flyway 依存有無を確認せず
- `schema.sql` + `spring.sql.init.mode=always` の運用実態を直接読まず、「V*.sql が起動時に流れる」と仮定
- 設計書に「Flyway で管理」と書いた段階で実装と整合を取らず、フェーズ着手前の前提検証を省略

**人間側の予防機会：**
- 新フェーズ着手時に「マイグレーション機構の確定」を最初のステップにする指示を出す
- 設計書の「前提」を書く際は、その前提を裏付ける設定ファイル（pom.xml 等）を AI に確認させる
- 既存トラブル（018 で Entity 未定義 / Flyway 不在の議論があった）の知見を AI に再参照させる

**検知タイミング：**
本番デプロイ後、ユーザー操作（API 呼び出し時）で 500 エラーで検知。テスト（H2）は Entity から自動生成のため気付けない。

**事前ガード：**
- operational_insights.md カテゴリ3 に「マイグレーション機構の特定方法」を明記（`pom.xml` 依存 + 起動ログ検証）
- フェーズ設計書テンプレに「マイグレーション機構」セクションを必須項目化
- `db/migration/V*.sql` 配下に「本番未実行・名残ファイル」を示す README を置く

**事後検知：**
- デプロイ直後のログで `Flyway` 文字列を grep（動いていなければ起動ログに出ない）
- phaseX-6 の主要テーブル存在確認で本番テーブルの過不足を検知

**出典：**
- メイン: docs/troubles/037_flyway_misassumed_phase14_tables_missing.md

---

## AP-004: API/SDK のシグネチャ・仕様を確認せず外挿

**症状：**
Market の Checkout 画面で SKU 選択時、Core API レスポンスの getter 名（`skuId`）と異なる仮定フィールド（`id`）を参照したため `sku_id=undefined` で遷移失敗。同型は Console/Core 間でも頻発（Guzzle Cookie メソッド誤読・JJWT 鍵長制約見落とし・CookieJar デフォルト無効見落としなど）。

**AI 側の判断ミス：**
- Core DTO の実コードを読まず、`id` フィールド名を推測で仮定
- リスト API（軽量サマリ）と詳細 API のフィールド差分を確認せず同一構造と想定
- ライブラリの既知挙動（Guzzle CookieJar デフォルト無効・JJWT `Keys.hmacShaKeyFor()` の鍵長制約）を確認せず外挿

**人間側の予防機会：**
- フロントから API を参照する実装時に「Core DTO（getter 名）を直接読んでから書く」を指示文に含める
- ライブラリ採用時に「公式ドキュメントの該当節を AI に引用させる」運用
- 「リスト API ≠ 詳細 API」の前提を共有し、フィールド差分の確認をレビュー観点に固定

**検知タイミング：**
手動 E2E テスト（ブラウザでクリック → URL/Network 確認）で検知。CI のフロントテストはモックデータが理想化されているため検知不可。

**事前ガード：**
- フロント側ユニットテストのモックデータを Core 実 API レスポンスから自動抽出する仕組み
- Core DTO に対する型定義ファイル（OpenAPI / TypeScript 型）の自動生成
- 新規画面追加時の PR テンプレに「参照する全 API DTO 名・フィールド一覧」を必須項目化

**事後検知：**
- 実機ブラウザでの貫通テスト（Market → Checkout → Core 注文確定）をフェーズ完了条件に含める
- フロント側テストでモックではなく実 Core API（テスト用 DB）を経由させる E2E テストの導入

**出典：**
- メイン: docs/troubles/035_market_checkout_sku_id_undefined.md
- 関連: docs/troubles/019_console_login_500_market_401.md, docs/troubles/031_console_cookie_relay_drops_set_cookie.md, docs/troubles/032_jwt_alg_mismatch_console_vs_core.md

---

## AP-005: シェル / SQL のクォート・エスケープを読まず実装

**症状：**
CD パイプラインのヘルスチェック SQL が `docker exec ... sh -c '<INNER>'` の内側にシングルクォート非エスケープで埋め込まれ、SQL リテラルの `'` が外側を閉じてしまい MySQL が「`amazia` はカラム名」と解釈して Unknown column エラー。046（ホスト側 `$MYSQL_ROOT_PASSWORD` 展開漏れ）の修正直後に同類別の問題で再失敗。

**AI 側の判断ミス：**
- `docker exec ... sh -c` のクォート構造（ホスト bash → コンテナ sh）を二重展開の観点から整理せず
- SQL リテラル（`'amazia'`）がシェル層で二次変換されることを考慮せず、テンプレートに直埋め込み
- 046 修正で「クォート問題のクラス」に気付かず単発バグ扱いし、関連 SQL リテラルの確認を省略

**人間側の予防機会：**
- 046 修正時に「クォート問題のクラス全体」を AI に検査させ、再発防止策のスコープを広げる指示
- シェル構築の設計レビューで複層クォート構造（ホスト → コンテナ → スクリプト）を図示し、各層の展開タイミングを明文化
- 故意失敗テストデプロイを PR マージ前に必ず実施する運用化

**検知タイミング：**
本番 CD のヘルスチェック実行時に検知。SSM RunShellScript 固有の挙動でローカル/CI では再現不可。

**事前ガード：**
- operational_insights.md に「`sh -c '<INNER>'` の不変条件：①外側シングルクォート ②`<INNER>` 中の `'` は `'\''` エスケープ ③`\` を含むケースは別検討」を明記
- シェル文字列構築のヘルパー関数を用意し、自動エスケープを通す
- PR で `sh -c` を含むコミットを自動検出してレビュー観点を提示

**事後検知：**
- 故意失敗テストデプロイ（schema.sql から 1 テーブル削除して `exit 1` を確認）
- CD ステップで `set -x` 相当のシェル展開結果ダンプを残し、ログで実 SQL を確認できるようにする

**出典：**
- メイン: docs/troubles/048_cd_healthcheck_sql_quote_break_inside_sh_c.md
- 関連: docs/troubles/046_cd_healthcheck_mysql_root_password_unexpanded.md

---

## AP-006: 対症療法で本質根因を残す

**症状：**
公開期間判定が秒単位（LocalDateTime）と日付単位（LocalDate JST 0:00）の二重基準のまま温存され、Market では「予約可」だが Checkout 確定時は「公開日未到達」で 400 エラー。020（Cookie domain 上書き忘れ）・030（DuckDNS プレフィックス CNAME 不可の事前検証漏れ）も「動いた箇所だけ直して根因を残した」同型。

**AI 側の判断ミス：**
- 公開判定ロジックが複数 Service / Entity に分散していることに気付かず、目の前の表示バグだけ直して終了
- 設計書「JST 0:00 基準」と既存 `Product#isPublished()` の矛盾を見つけても即座に統一せず
- 「動作した」を解決とみなし、同概念の他箇所への横展開確認を省略

**人間側の予防機会：**
- 不具合修正の指示時に「同概念のロジックが他所にないか必ず grep」を明示
- 修正完了の判断基準を「症状解消」ではなく「根因の単一化・横展開確認完了」に置く
- レビューで「直した箇所」だけでなく「直さなかった箇所の根拠」を AI に説明させる

**検知タイミング：**
ユーザー操作の貫通テスト（Market 表示 → Checkout 確定）で初めて検知。単独画面のテストでは緑のまま。

**事前ガード：**
- 判定ロジックは 1 つの Service に集約。Entity に Bean 注入できないロジックを置かない
- 設計書の「判定基準」をコード側にコメント・定数として転記し、他の実装箇所と同期しているか CI で確認
- 「同一概念の判定が複数箇所」アンチパターンを operational_insights.md に明記

**事後検知：**
- 境界値テスト（公開日が今日の未来時刻 / JST 0:00 直前）の必須化
- 実機ブラウザでの貫通確認をフェーズ完了条件に含める
- 同根バグの再発を troubles の「派生節」として親トラブルに追記し、横展開確認の漏れを可視化

**出典：**
- メイン: docs/troubles/041_publish_judgement_dual_basis_secs_vs_jst_zero.md
- 関連: docs/troubles/020_refresh_cookie_domain_container_name.md, docs/troubles/030_https_via_cloudfront_duckdns_single_domain.md

---

## AP-007: 設計書の機能を部分実装で完了報告

**症状：**
phase10 の設計書「SKU 価格管理機能」はバックエンド API / DB / Service が完全実装されているが、Vue コンポーネント（SkuPriceList.vue）がプレースホルダーのまま残置。画面を開くと「フェーズ10で実装予定です」という空テンプレートが表示。011（Console サイドバー未表示）・013（画像アップロード UI 漏れ）・039（予約モード分岐漏れ）も同型。

**AI 側の判断ミス：**
- 「API が実装された = 機能完了」と判断し、フロント UI 実装の漏れに気付かず
- 設計書の「機能全体」と「実装層の分業（フロント/バック/DB）」を分ける概念がなく、バック完了 = フェーズ完了と誤認
- フェーズ完了の判断を「CI グリーン」だけで行い、ブラウザでの実機確認をスキップ

**人間側の予防機会：**
- フェーズ完了の定義を「CI 緑 + 実機ブラウザでの全画面操作確認」と明示し、AI に同条件で完了報告させる
- 設計書に「フロント / バック / DB の実装期限」対応表を持たせる
- プレースホルダーコンポーネントは TODO コメント + フェーズ番号を必須化し、フェーズ完了時に grep で残置検出

**検知タイミング：**
ローカル / ステージングでのブラウザ操作で即座に検知可能。CI ではルーティングのみ通るため検知不可。

**事前ガード：**
- フェーズ完了 PR テンプレに「対象画面をブラウザで開き、全フローを 1 周実行した」を必須チェック項目に
- プレースホルダー検出 lint（`<!-- TODO: フェーズXで実装 -->` 残置をフェーズ完了時に検知）
- 設計書の「実装層対応表」に対する未着手検出スクリプト

**事後検知：**
- E2E テスト（Playwright / Cypress）で画面遷移 → 実データ表示を自動検証
- デプロイ後スモークテストで全新規画面をブラウザで 1 周
- コンポーネント render 結果に placeholder 文字列を含むと自動警告

**出典：**
- メイン: docs/troubles/014_sku_price_ui_not_implemented.md
- 関連: docs/troubles/011_console_sidebar_not_displayed.md, docs/troubles/013_console_image_upload_missing.md, docs/troubles/039_market_checkout_preorder_mode_missing.md

---

## AP-008: CI/CD・運用設計の検証不足で副次トラブル誘発

**症状：**
SSM コマンド配信が PingStatus=Online でも Undeliverable になる「ゾンビ Online」現象が発生。キャンセル直後の再実行で MGS セッションが破壊される。既存リカバリ機構（PingStatus チェック → stop/start）では検知できず、複数回デプロイ失敗。同根は 003（--build 除去後の検証漏れ）・023（--remove-orphans 漏れ）・008（restart ポリシー漏れ）・025（リカバリ後 Pending 滞留）でも繰り返された。

**AI 側の判断ミス：**
- PingStatus=Online が「コマンド配信パスの健全性」を保証していないことを見落とし
- 「前のリカバリが効いた → 正常」と判断し、隙間（ゾンビ Online）への対策を入れず
- 実機運用での長期挙動（再起動・キャンセル直後・disk fill 等）の予見を省略

**人間側の予防機会：**
- CI/CD・運用機構を入れる際に「何を検証していて、何を保証していないか」を明文化させる
- 既存リカバリ機構の改修時に「過去 SSM 系トラブル一覧（003/008/023/025/026）」を AI に再参照させる
- リカバリの追加実装時、本番でしか再現しない挙動を検証するためのカナリア戦略を要求

**検知タイミング：**
本番デプロイ時にのみ再現。複数回デプロイで偶発的に検知。

**事前ガード：**
- カナリアコマンド（軽量 echo 等）で実配信可否を事前確認
- リカバリ前後で同一テストコマンドを流し、配信可否を双方確認
- 無限リカバリループ防止（最大リトライ回数制限）

**事後検知：**
- デプロイ後の標準チェックリスト：①PingStatus 確認 ②カナリア確認 ③実コマンド実行 ④成功ログ確認
- SSM コマンド失敗時に StatusDetails を自動分類ログ出力（Undeliverable / TimedOut / Pending）
- 複数回リカバリ発動時に運用者通知

**出典：**
- メイン: docs/troubles/026_ssm_zombie_online_undeliverable.md
- 関連: docs/troubles/003_ssm_command_queue_stuck.md, docs/troubles/008_containers_not_restart_after_ec2_reboot.md, docs/troubles/023_docker_compose_name_conflict_orphan.md, docs/troubles/025_ssm_pending_after_recovery.md

---

## Step 2 注記

- 「人間側の予防機会」は責任転嫁ではなく、AI への指示・レビュー観点の改善案として記載した
- 「事前ガード」「事後検知」は出典トラブルの「再発防止」節を出発点に、汎用観点へ書き換えている。具体策は出典トラブル側を参照する想定
- AP-006・AP-008 は「単発バグの修正で類似クラスを見落とした」点で他パターンとも横断する。Step 3 で扱う際、横断観点として独立節を立てるか議論の余地あり
- Step 1 で `[協働ミス]` に再分類した 015（Market を CI/CD に乗せる人間側の設計判断ミス）は本 8 パターンに直接含まれていない。Step 3 で「協働プロセス側のアンチパターン」セクションを別建てし、015 を代表事例として扱う案を引き続き検討する
