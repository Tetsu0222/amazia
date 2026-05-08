# AI協働アンチパターン集

## 目的

本ファイルは Claude Code をはじめとする AI 協働開発において、過去のトラブルから抽出された
「AI 任せで踏みやすい落とし穴」と「人間側の予防機会」を成文化したもの。

AI は実装・計画着手時に本ファイルを参照し、該当パターンに陥らないこと。
人間は不具合分析・レビュー時に本ファイルを参照し、改善のレバーを見つけること。

## 関連ドキュメント

- [test_insights.md](test_insights.md) — テスト観点の知見（成功事例ベース）
- [operational_insights.md](operational_insights.md) — 実装・運用パターンの落とし穴（成功事例ベース）
- [prompt_templates.md](prompt_templates.md) — 作業種別ごとのプロンプトテンプレート集（各 AP に対応する TPL を収録）
- [_triage_log.md](_triage_log.md) — 本ドキュメントの作成根拠（過去 50 件のトラブル棚卸し）

---

## 本ドキュメントの前提（責任の切り分け方針）

本質的には、AI 協働開発で発生したすべてのミスは人間側の責任である。
AI に何を読ませるか、何を確認させるか、どこで止めるかを設計するのは
人間の役割であり、AI の判断ミスはその設計の不備の現れと捉えるべきである。

しかし「すべて人間が悪い」で片付けると改善のレバーが見えなくなる。
そこで本ドキュメントでは便宜的に以下の3軸で切り分ける：

- **AI 側の判断ミス** — AI が陥りやすい認知の偏り。プロンプト設計や
  コンテキスト供給で対策可能
- **人間側の予防機会** — 指示・レビュー・前提共有のどこで止められたか
- **構造的ガード（事前 / 事後）** — 個人の注意力に依存せず仕組みで防ぐ手段
  （CI / lint / テンプレ / ヘルスチェック等）

この切り分けは責任の所在を決めるためではなく、改善の打ち手を見つける
ための分析フレームワークである。AI 側の判断ミスを記述することは AI への
責任転嫁ではなく、「次に同じプロンプトを書くときに何を補えばよいか」を
明らかにする作業である。

---

## 横断観点

個別 AP を読む前に、複数 AP にまたがる共通根を先に把握しておく。
各 AP は単発の落とし穴として記述しているが、実際の現場では複数 AP が
連鎖して発生することが多い。横断観点を理解しておくと、AP を読む際の
補助線として機能する。

### 「単発バグの修正で類似クラスを見落とす」共通根

AP-005（046→048）・AP-006（020/030/041）・AP-001（044→045→049）・AP-008（003/008/023/025/026）には
**「直前のトラブル修正で類似クラスの再発を見落とす」** という共通根がある。

人間側の予防機会としては「不具合対応時に必ず同根の他箇所を grep する」という運用ルールを
[CLAUDE.md](../../CLAUDE.md) の不具合対応ルールに組み込み済み。

新規 AP として独立させるかは継続判断。現時点では各 AP の「人間側の予防機会」
「対応プロンプトスニペット」に横展開確認の指示を組み込むことで対処している。

### 協働プロセス側のアンチパターン（要追加検討）

[_triage_log.md](_triage_log.md) で `[協働ミス]` に分類した 015（Market を CI/CD に乗せる人間側の設計判断ミス）・
029（compose プラグイン消失と運用前提の未共有）・047（UI ライブラリ仕様起因の協働プロセス漏れ）は
本 AP-001〜008 に直接含まれていない。

これらは「人間側の設計判断・前提共有が不十分なまま AI に渡した結果」として共通根を持つ。
今後類似事例が積み上がった段階で、AP-009 として「人間側の前提整備不足」を独立追加するかを検討する。

### 中間プロセスの解釈余地（閉じていない文脈）

troubles → AP → TPL の流れは、スタート（具体トラブルの記録）とゴール（次回作業時の予防）は
形式が揃っているが、**中間の抽象化・再具体化の工程が AI の解釈に委ねられている**。
チェス・将棋のように盤面・駒の動き・勝利条件が完全に定義された閉じた文脈で AI は強いが、
ここでは中間プロセスに文脈の揺れがあり、AI が「察する」必要のある箇所が複数残っている。

具体的には以下の 5 点で解釈余地が生じている：

1. **トラブル → 根因抽出** — 根本原因をどの粒度で書くかが自由記述（表層症状か構造欠陥か）
2. **根因 → AP 昇格判断** — 既存 AP に統合 / 新規 AP / 派生節 の判定基準が文章
3. **AP → 作業種別への写像** — どの作業がどの AP を踏むかが AI の察し（N 対 N でよい、と書かれている）
4. **作業種別 → TPL 選択** — 着手前に「これは TPL-XXX 案件だ」と気づく決定論的な契機がない
5. **TPL の `<...>` 穴埋め** — スロットの型・参照元が未定義で、自由記述になっている

将来的な「閉じ方」の候補：

- **判定木（決定論的トリガー）** — 「`schema.sql` を編集 → TPL-001」「`pom.xml` に新規 dep 追加 → TPL-002」のように、ファイル / コマンドパターンから TPL を機械的に引ける表を整備する。`.claude/settings.json` の hooks で構造的ガードに昇格できる
- **根因タグの語彙固定** — AP の根因を「型不整合」「シグネチャ未確認」「クォート展開」「横展開漏れ」のような有限の語彙タグで分類。troubles の根因節も同タグで閉じれば、AP 昇格判断が「既存タグに該当 → AP 既存に追記 / 該当タグなし → 新 AP 候補」と決定論化される
- **TPL スロットの仕様化** — TPL ごとに「埋めるべきスロット名・型・参照元」を定義（例：TPL-006 なら `kw: 概念名 (string, 出典:troubleの症状節から抽出)`）

この観点は観測された不具合ではなく、プロセス自体の構造的弱さである。
具体トラブル発生時は troubles ドキュメントに記録するが、本観点はそこに馴染まないため
横断観点として記録する。新規 AP として独立させるかは、同種の違和感が再度顕在化した段階で判断する。

---

## アンチパターン一覧

### AP-001: 既存スキーマ・既存DDLを読まずに新規追記

**症状：**
Core 起動時の schema.sql 実行で既存の users.id（BIGINT UNSIGNED）との FK 型不互換により
operation_logs テーブルの作成が失敗。`continue-on-error=true` で WARN として潰され、
本番 MySQL に該当テーブルが存在しないまま稼働、API 呼び出し時に 500 エラー発生。

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
本番デプロイ後、ユーザー操作（API 呼び出し時）で初めて検知。CI（H2）では型概念が無く、
`continue-on-error` で WARN になるため CD でも気付けない。

**事前ガード：**
- operational_insights.md / coding_guidelines.md に「users.id は BIGINT UNSIGNED、参照する全 FK 列も同一型で統一」を明記
- 新規テーブル DDL 作成時のテンプレ化（FK 宣言行に参照先型のコメント必須）
- Entity と schema.sql の差分を CI で自動検出（@JoinColumn の参照先型と FK 列型の照合）

**事後検知：**
- phaseX-6 で導入済みの主要テーブル存在確認（`ops/healthcheck/required_tables.txt` 照合）
- 起動ログから schema.sql の WARN を CD ジョブログに `grep` 抽出し可視化

**対応プロンプトスニペット：**
> `<テーブル名>` を追加する前に：
> 1. `amazia-core/src/main/resources/schema.sql` を読み、参照先となる既存テーブル（特に users.id）の型を確認
> 2. FK 列が同一型（BIGINT UNSIGNED）になっているか
> 3. 既存の同概念テーブルがないか grep
>
> を順に実行し、結果を報告してから DDL を書いてください。

**関連 TPL：** [TPL-001 新規テーブル追加時](prompt_templates.md#tpl-001-新規テーブル追加時)

**出典：**
- メイン: [docs/troubles/044_operation_logs_table_missing_users_id_unsigned_drift.md](../troubles/044_operation_logs_table_missing_users_id_unsigned_drift.md)
- 関連: [045_sales_return_table_missing_users_id_unsigned_drift.md](../troubles/045_sales_return_table_missing_users_id_unsigned_drift.md), [049_password_histories_table_missing_in_schema_sql.md](../troubles/049_password_histories_table_missing_in_schema_sql.md)

---

### AP-002: Entity / 制約を読まずにテストヘルパーをハードコード

**症状：**
ProductSku の `@UniqueConstraint(product_id, color, size)` を確認せずテストヘルパーで
color/size をハードコード値に固定。同一 product に複数 SKU を作成するテストで制約違反となり
関連テストが全滅。

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

**対応プロンプトスニペット：**
> `<Entity名>` 用のテストヘルパーを作成・改修する前に、Entity クラスを読み、
> `@UniqueConstraint` `@Column(unique=true)` の有無を報告してください。
> 制約カラムが存在する場合、ヘルパーで複数インスタンスを作る際の一意化戦略
> （引数化 / nanoTime 付与など）を提示してから実装してください。

**関連 TPL：** [TPL-003 テストヘルパー作成・改修時](prompt_templates.md#tpl-003-テストヘルパー作成改修時)

**出典：**
- メイン: [docs/troubles/050_h2_unique_constraint_test_helper_collision.md](../troubles/050_h2_unique_constraint_test_helper_collision.md)
- 関連: [027_workflow_test_h2_schema_and_json_payload.md](../troubles/027_workflow_test_h2_schema_and_json_payload.md)

---

### AP-003: 存在しない技術スタックを仮定（Flyway 誤認）

**症状：**
Flyway を使用していないプロジェクトで `db/migration/` ディレクトリの存在から Flyway 利用を誤認。
V6〜V11.sql を作成したが本番では実行されず、必要テーブル 6 個が本番 MySQL に作成されないまま
phase14 の注文確定 API が 500 エラー多発。

**AI 側の判断ミス：**
- `db/migration/` の存在から「Flyway を使用している」と外挿し、`pom.xml` の flyway 依存有無を確認せず
- `schema.sql` + `spring.sql.init.mode=always` の運用実態を直接読まず、「V*.sql が起動時に流れる」と仮定
- 設計書に「Flyway で管理」と書いた段階で実装と整合を取らず、フェーズ着手前の前提検証を省略

**人間側の予防機会：**
- 新フェーズ着手時に「マイグレーション機構の確定」を最初のステップにする指示を出す
- 設計書の「前提」を書く際は、その前提を裏付ける設定ファイル（pom.xml 等）を AI に確認させる
- 既存トラブル（018 で Entity 未定義 / Flyway 不在の議論があった）の知見を AI に再参照させる

**検知タイミング：**
本番デプロイ後、ユーザー操作（API 呼び出し時）で 500 エラーで検知。
テスト（H2）は Entity から自動生成のため気付けない。

**事前ガード：**
- operational_insights.md カテゴリ3 に「マイグレーション機構の特定方法」を明記（`pom.xml` 依存 + 起動ログ検証）
- フェーズ設計書テンプレに「マイグレーション機構」セクションを必須項目化
- `db/migration/V*.sql` 配下に「本番未実行・名残ファイル」を示す README を置く

**事後検知：**
- デプロイ直後のログで `Flyway` 文字列を grep（動いていなければ起動ログに出ない）
- phaseX-6 の主要テーブル存在確認で本番テーブルの過不足を検知

**対応プロンプトスニペット：**
> 新フェーズの実装計画を立てる前に、以下を確認・報告してください：
> 1. `amazia-core/pom.xml` の dependency にマイグレーション機構（flyway-core / liquibase 等）が含まれるか
> 2. 含まれない場合、本番でスキーマが流れる経路は何か（`schema.sql` + `spring.sql.init.mode` の値）
> 3. `db/migration/V*.sql` のファイル群が本番で実行されるか否か
>
> 確認結果を踏まえてから、新規スキーマ変更の方針を提案してください。

**関連 TPL：** [TPL-004 マイグレーション機構の確認](prompt_templates.md#tpl-004-マイグレーション機構の確認)

**出典：**
- メイン: [docs/troubles/037_flyway_misassumed_phase14_tables_missing.md](../troubles/037_flyway_misassumed_phase14_tables_missing.md)

---

### AP-004: API/SDK のシグネチャ・仕様を確認せず外挿

**症状：**
Market の Checkout 画面で SKU 選択時、Core API レスポンスの getter 名（`skuId`）と異なる仮定
フィールド（`id`）を参照したため `sku_id=undefined` で遷移失敗。同型は Console/Core 間でも頻発
（Guzzle Cookie メソッド誤読・JJWT 鍵長制約見落とし・CookieJar デフォルト無効見落としなど）。

**AI 側の判断ミス：**
- Core DTO の実コードを読まず、`id` フィールド名を推測で仮定
- リスト API（軽量サマリ）と詳細 API のフィールド差分を確認せず同一構造と想定
- ライブラリの既知挙動（Guzzle CookieJar デフォルト無効・JJWT `Keys.hmacShaKeyFor()` の鍵長制約）を確認せず外挿

**人間側の予防機会：**
- フロントから API を参照する実装時に「Core DTO（getter 名）を直接読んでから書く」を指示文に含める
- ライブラリ採用時に「公式ドキュメントの該当節を AI に引用させる」運用
- 「リスト API ≠ 詳細 API」の前提を共有し、フィールド差分の確認をレビュー観点に固定

**検知タイミング：**
手動 E2E テスト（ブラウザでクリック → URL/Network 確認）で検知。
CI のフロントテストはモックデータが理想化されているため検知不可。

**事前ガード：**
- フロント側ユニットテストのモックデータを Core 実 API レスポンスから自動抽出する仕組み
- Core DTO に対する型定義ファイル（OpenAPI / TypeScript 型）の自動生成
- 新規画面追加時の PR テンプレに「参照する全 API DTO 名・フィールド一覧」を必須項目化

**事後検知：**
- 実機ブラウザでの貫通テスト（Market → Checkout → Core 注文確定）をフェーズ完了条件に含める
- フロント側テストでモックではなく実 Core API（テスト用 DB）を経由させる E2E テストの導入

**対応プロンプトスニペット：**
> `<API名>` または `<ライブラリ名>` の `<機能>` を使う前に、以下を報告してください：
> - API の場合: 該当 DTO クラスの全フィールド名（getter 名）と型／リスト API と詳細 API のフィールド差分
> - ライブラリの場合: 採用するメソッド/クラスの正確なシグネチャ／デフォルト挙動／制約（鍵長・型・バージョン依存）
>
> 推測ではなく実コード／公式ドキュメントの引用で回答してください。

**関連 TPL：** [TPL-002 ライブラリ・API 採用時](prompt_templates.md#tpl-002-ライブラリapi採用時)

**出典：**
- メイン: [docs/troubles/035_market_checkout_sku_id_undefined.md](../troubles/035_market_checkout_sku_id_undefined.md)
- 関連: [019_console_login_500_market_401.md](../troubles/019_console_login_500_market_401.md), [031_console_cookie_relay_drops_set_cookie.md](../troubles/031_console_cookie_relay_drops_set_cookie.md), [032_jwt_alg_mismatch_console_vs_core.md](../troubles/032_jwt_alg_mismatch_console_vs_core.md)

---

### AP-005: シェル / SQL のクォート・エスケープを読まず実装

**症状：**
CD パイプラインのヘルスチェック SQL が `docker exec ... sh -c '<INNER>'` の内側に
シングルクォート非エスケープで埋め込まれ、SQL リテラルの `'` が外側を閉じてしまい
MySQL が「`amazia` はカラム名」と解釈して Unknown column エラー。
046（ホスト側 `$MYSQL_ROOT_PASSWORD` 展開漏れ）の修正直後に同類別の問題で再失敗。

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

**対応プロンプトスニペット：**
> `sh -c '<INNER>'` または `docker exec ... sh -c` を含むコマンドを実装する前に、以下を整理して報告してください：
> 1. ホスト bash → コンテナ sh の各層で何が展開されるか
> 2. `<INNER>` 中に含まれる `'` `"` `$` `\` の各文字が、どの層で問題になり得るか
> 3. ホスト変数を保護したい場合は外側シングルクォート、コンテナ内変数なら外側ダブルクォート
> 4. SQL リテラルの `'` は `'\''` でエスケープが必要か
>
> 上記を整理してから最終コマンドを提示してください。

**関連 TPL：** [TPL-005 シェル / SQL 文字列構築時](prompt_templates.md#tpl-005-シェル--sql-文字列構築時)

**出典：**
- メイン: [docs/troubles/048_cd_healthcheck_sql_quote_break_inside_sh_c.md](../troubles/048_cd_healthcheck_sql_quote_break_inside_sh_c.md)
- 関連: [046_cd_healthcheck_mysql_root_password_unexpanded.md](../troubles/046_cd_healthcheck_mysql_root_password_unexpanded.md)

---

### AP-006: 対症療法で本質根因を残す

**症状：**
公開期間判定が秒単位（LocalDateTime）と日付単位（LocalDate JST 0:00）の二重基準のまま
温存され、Market では「予約可」だが Checkout 確定時は「公開日未到達」で 400 エラー。
020（Cookie domain 上書き忘れ）・030（DuckDNS プレフィックス CNAME 不可の事前検証漏れ）も
「動いた箇所だけ直して根因を残した」同型。

**AI 側の判断ミス：**
- 公開判定ロジックが複数 Service / Entity に分散していることに気付かず、目の前の表示バグだけ直して終了
- 設計書「JST 0:00 基準」と既存 `Product#isPublished()` の矛盾を見つけても即座に統一せず
- 「動作した」を解決とみなし、同概念の他箇所への横展開確認を省略

**人間側の予防機会：**
- 不具合修正の指示時に「同概念のロジックが他所にないか必ず grep」を明示
- 修正完了の判断基準を「症状解消」ではなく「根因の単一化・横展開確認完了」に置く
- レビューで「直した箇所」だけでなく「直さなかった箇所の根拠」を AI に説明させる

**検知タイミング：**
ユーザー操作の貫通テスト（Market 表示 → Checkout 確定）で初めて検知。
単独画面のテストでは緑のまま。

**事前ガード：**
- 判定ロジックは 1 つの Service に集約。Entity に Bean 注入できないロジックを置かない
- 設計書の「判定基準」をコード側にコメント・定数として転記し、他の実装箇所と同期しているか CI で確認
- 「同一概念の判定が複数箇所」アンチパターンを operational_insights.md に明記

**事後検知：**
- 境界値テスト（公開日が今日の未来時刻 / JST 0:00 直前）の必須化
- 実機ブラウザでの貫通確認をフェーズ完了条件に含める
- 同根バグの再発を troubles の「派生節」として親トラブルに追記し、横展開確認の漏れを可視化

**対応プロンプトスニペット：**
> `<不具合の症状>` を修正する前に、以下を実施・報告してください：
> 1. 同概念のロジック（例：判定基準・データ参照先・エラーハンドリング）が他のファイルに存在しないか grep で確認
> 2. 見つかった全箇所の現状ロジックを列挙し、修正対象と「直さない箇所」の判断根拠を提示
> 3. 修正完了の判断基準を「症状解消」ではなく「根因の単一化・横展開確認完了」とする
>
> 修正後は「直した箇所」と「直さなかった箇所の根拠」を併記して報告してください。

**関連 TPL：** [TPL-006 バグ修正時の横展開確認](prompt_templates.md#tpl-006-バグ修正時の横展開確認)

**出典：**
- メイン: [docs/troubles/041_publish_judgement_dual_basis_secs_vs_jst_zero.md](../troubles/041_publish_judgement_dual_basis_secs_vs_jst_zero.md)
- 関連: [020_refresh_cookie_domain_container_name.md](../troubles/020_refresh_cookie_domain_container_name.md), [030_https_via_cloudfront_duckdns_single_domain.md](../troubles/030_https_via_cloudfront_duckdns_single_domain.md)

---

### AP-007: 設計書の機能を部分実装で完了報告

**症状：**
phase10 の設計書「SKU 価格管理機能」はバックエンド API / DB / Service が完全実装されているが、
Vue コンポーネント（SkuPriceList.vue）がプレースホルダーのまま残置。画面を開くと
「フェーズ10で実装予定です」という空テンプレートが表示。011（Console サイドバー未表示）・
013（画像アップロード UI 漏れ）・039（予約モード分岐漏れ）も同型。

**AI 側の判断ミス：**
- 「API が実装された = 機能完了」と判断し、フロント UI 実装の漏れに気付かず
- 設計書の「機能全体」と「実装層の分業（フロント/バック/DB）」を分ける概念がなく、バック完了 = フェーズ完了と誤認
- フェーズ完了の判断を「CI グリーン」だけで行い、ブラウザでの実機確認をスキップ

**人間側の予防機会：**
- フェーズ完了の定義を「CI 緑 + 実機ブラウザでの全画面操作確認」と明示し、AI に同条件で完了報告させる
- 設計書に「フロント / バック / DB の実装期限」対応表を持たせる
- プレースホルダーコンポーネントは TODO コメント + フェーズ番号を必須化し、フェーズ完了時に grep で残置検出

**検知タイミング：**
ローカル / ステージングでのブラウザ操作で即座に検知可能。
CI ではルーティングのみ通るため検知不可。

**事前ガード：**
- フェーズ完了 PR テンプレに「対象画面をブラウザで開き、全フローを 1 周実行した」を必須チェック項目に
- プレースホルダー検出 lint（`<!-- TODO: フェーズXで実装 -->` 残置をフェーズ完了時に検知）
- 設計書の「実装層対応表」に対する未着手検出スクリプト

**事後検知：**
- E2E テスト（Playwright / Cypress）で画面遷移 → 実データ表示を自動検証
- デプロイ後スモークテストで全新規画面をブラウザで 1 周
- コンポーネント render 結果に placeholder 文字列を含むと自動警告

**対応プロンプトスニペット：**
> `<フェーズ番号>` の完了報告をする前に、以下を実施してください：
> 1. 設計書に記載された画面・API・DB を一覧化し、それぞれの実装状況を○×で報告
> 2. プレースホルダー（"フェーズXで実装予定" 等の残置）が無いか grep
> 3. 対象画面をブラウザで開き、全フローを 1 周実行した結果を記述
> 4. CI グリーンだけでは完了とせず、上記 3 つすべてが揃って初めて「完了」とする
>
> 上記を満たさない状態で完了報告しないでください。

**関連 TPL：** [TPL-007 フェーズ完了確認時](prompt_templates.md#tpl-007-フェーズ完了確認時)

**出典：**
- メイン: [docs/troubles/014_sku_price_ui_not_implemented.md](../troubles/014_sku_price_ui_not_implemented.md)
- 関連: [011_console_sidebar_not_displayed.md](../troubles/011_console_sidebar_not_displayed.md), [013_console_image_upload_missing.md](../troubles/013_console_image_upload_missing.md), [039_market_checkout_preorder_mode_missing.md](../troubles/039_market_checkout_preorder_mode_missing.md)

---

### AP-008: CI/CD・運用設計の検証不足で副次トラブル誘発

**症状：**
SSM コマンド配信が PingStatus=Online でも Undeliverable になる「ゾンビ Online」現象が発生。
キャンセル直後の再実行で MGS セッションが破壊される。既存リカバリ機構
（PingStatus チェック → stop/start）では検知できず、複数回デプロイ失敗。
同根は 003（--build 除去後の検証漏れ）・023（--remove-orphans 漏れ）・008（restart ポリシー漏れ）・
025（リカバリ後 Pending 滞留）でも繰り返された。

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

**対応プロンプトスニペット：**
> CI/CD・運用機構（リカバリ・ヘルスチェック・自動化スクリプト等）を実装・改修する前に、以下を報告してください：
> 1. この機構が「何を検証していて」「何を保証していないか」を明文化
> 2. 関連する過去トラブル（troubles/ 配下を grep）の知見を引用
> 3. 本番でしか再現しない挙動を検証するためのカナリア戦略の有無
> 4. 失敗時の検知経路（ログ・通知・リトライ上限）
>
> 「動いた」だけで完了とせず、上記の保証範囲を明示してから実装してください。

**関連 TPL：** [TPL-008 CI/CD・運用機構導入時](prompt_templates.md#tpl-008-cicd運用機構導入時)

**出典：**
- メイン: [docs/troubles/026_ssm_zombie_online_undeliverable.md](../troubles/026_ssm_zombie_online_undeliverable.md)
- 関連: [003_ssm_command_queue_stuck.md](../troubles/003_ssm_command_queue_stuck.md), [008_containers_not_restart_after_ec2_reboot.md](../troubles/008_containers_not_restart_after_ec2_reboot.md), [023_docker_compose_name_conflict_orphan.md](../troubles/023_docker_compose_name_conflict_orphan.md), [025_ssm_pending_after_recovery.md](../troubles/025_ssm_pending_after_recovery.md)

---

## 運用ルール

### 新規トラブル発生時の AI 協働観点記録

[CLAUDE.md](../../CLAUDE.md) の不具合対応ルールに従い、新規トラブル記録時に
`## AI協働観点` セクションを必ず埋める。フォーマット：

```markdown
## AI協働観点
- AI の判断ミス：<あれば記載／無ければ「該当なし」>
- 人間が止めるべきだった点：<あれば記載／無ければ「該当なし」>
- 該当アンチパターン：AP-NNN <or 新規パターンとして追加 / なし>
```

### 新規 AP の追加基準

既存 AP-001〜008 に該当しないパターンが 2 件以上累積した時点で、新規 AP として本ファイルに
追記する。1 件のみの段階では各トラブルドキュメント側に記録するのみとし、
パターン化は早すぎないこと（汎化しすぎると実用性を欠く）。

### AP と prompt_templates.md の同期

各 AP に「対応プロンプトスニペット」と「関連 TPL」を併記している。
AP を追加・更新する際は、対応する TPL も `prompt_templates.md` 側で更新すること。
双方向リンクを保つ。
