# Amazia プロジェクト AIコンテキスト

## 実装・計画時に必ず読むファイル

### コーディング規約
`docs/coding_guidelines.md` — 命名・コメント・テスト方針など。実装前に必ず参照。

### テスト設計の知見
`docs/ai_context/test_insights.md` — 過去トラブルから抽出したテスト観点。
テストケース作成・フェーズ実装計画時に参照すること。

### 実装・運用パターンの知見
`docs/ai_context/operational_insights.md` — 過去の実装・運用作業から抽出した落とし穴。
Spring Boot ライフサイクル / コンテナ運用 / SSM 経由ジョブなど、テストでは検出しづらい設計パターン。
新規機能実装・フェーズ計画時に参照すること。

## 不具合対応時のルール

ユーザーから不具合・障害の調査・修正を依頼された場合、コード修正に加えて以下を**必ず**行う。

### トラブルドキュメントの作成・更新
1. `docs/troubles/` にある既存ファイルを確認し、同一・類似の問題がすでに記録されていないか確認する
2. 新規の不具合であれば `docs/troubles/NNN_<概要>.md` を新規作成する（NNN は連番）
3. 既存ファイルに追記すべき内容があれば更新する
4. 修正完了後、`docs/ai_context/test_insights.md` に新たなテスト観点が抽出できる場合は追記する
5. 新規作成時、不具合対応完了時、`docs/troubles/README.md` の更新も忘れない。

### ドキュメントに記載する内容
```
# NNN: <タイトル>

## ステータス
🔴 調査中 / 🟡 対応中 / ✅ 解決済（YYYY-MM-DD）

## 発症箇所
<URL・画面名・エンドポイントなど>

## 症状
<ユーザーが観察した現象>

## 根本原因
<調査で判明した原因>

## なぜ CI で検知できなかったか
<テストで防げなかった理由>

## 修正内容
<実施した変更の概要>

## 再発防止
| 観点 | 対策 |
|------|------|
| ... | ... |
```

## DB / API 設計書のメンテナンスルール

DB スキーマ変更や API エンドポイント追加を伴うフェーズでは、**実装と同じフェーズ内で**設計書を更新する。後追いでまとめてやるとフェーズを跨いで漏れる（実例：P12〜P14 で 14テーブル / 29エンドポイントが未文書化のまま放置された）。

### DB スキーマを変更したとき

対象ファイル：`docs/database_design/`

1. **新規テーブル追加時**
   - `TBL_<テーブル名>.md` を新規作成（既存定義書のフォーマットを踏襲：概要 / カラム表 / FK / Index / マイグレーション対応）
   - `README.md` のファイル一覧表にエントリを追加（所属システム・出現フェーズを明記）
   - `ER_diagram.md` の Mermaid 図にテーブルとリレーションを追加
2. **既存テーブルへのカラム / インデックス追加時**
   - 該当 `TBL_*.md` のカラム表・インデックス節を更新
   - マイグレーション対応セクション（または変更履歴）に schema.sql / V*.sql の出所を明記
3. **マイグレーション方式の前提**（037 起因 — `operational_insights.md` カテゴリ3 参照）
   - 本番 Core は Flyway 未使用。`amazia-core/src/main/resources/schema.sql` を `spring.sql.init.mode=always` で起動時実行する方式
   - `db/migration/V*.sql` は名残ファイルで本番では実行されない。新規スキーマ変更は schema.sql に IF NOT EXISTS / continue-on-error で追記するのが正
   - Console は Laravel マイグレーション（`amazia-console/database/migrations/`）

### API エンドポイントを追加 / 変更したとき

対象ファイル：`docs/api_design/`

- Core（Spring Boot `@RestController`）の追加 → `Core_API.md` に追記
- Console（Laravel `routes/*.php`）の追加 → `Console_API.md` に追記
- Market（React からの呼び出し）に関わる場合 → `Market_API.md` の対応エンドポイントも更新
- パス変更・廃止は「実装済」「予定」「廃止済」の状態を明確に書き分ける（Market_API.md が「予定」のまま乖離した実例あり）

### 主要テーブル定数の同期（phaseX-6 / 044 起因）

`ops/healthcheck/required_tables.txt` は CD の「主要テーブル存在確認」ステップが参照する、本番 DB に必ず存在すべき Core テーブルの一覧（出典：`docs/database_design/README.md` Core システム）。`continue-on-error` で潰された DDL 失敗（044）をデプロイ後 1 分以内に検知するための定数。

- **テーブルを追加するとき**：同フェーズ内で `TBL_*.md` 新設・`docs/database_design/README.md` 追記・`schema.sql` 追記・`ER_diagram.md` 反映に加え、**`ops/healthcheck/required_tables.txt` にも追記**する
- **テーブルを廃止／リネームするとき**：同様に `required_tables.txt` から削除／更新する。残置すると次回デプロイで「不足テーブル」検知に引っかかり CD が `exit 1` で止まる
- **対象範囲**：Core システムのテーブルのみ（Console の `sessions` / `personal_access_tokens` 等 Laravel 標準テーブルは対象外）
- **更新が漏れた場合の挙動**：テーブル追加で更新を忘れても CD は通る（過大検知しない設計）。一方で本来検知すべき不足は検知されないため、PR レビューで `TBL_*.md` 追加と `required_tables.txt` 追加が両方あるかを必ず確認

### フェーズ完了の定義に組み込む

フェーズ完了とみなすには、`docs/ai_context/test_insights.md` のまとめセクションに加え、**当該フェーズで触った DB / API がすべて設計書に反映されていること**を確認する。設計書未更新のままフェーズを閉じない。

## 基本ルール

- 新規環境変数を追加するときは `docker-compose.yml` と `phpunit.xml` を**必ずセット**で更新する
- テスト内で URL・ホスト名をハードコードせず `config()` 経由で取得する
- フェーズ完了の定義は `docs/ai_context/test_insights.md` の「まとめ」セクションに従う
- フェーズで DB / API を触ったら同フェーズ内で `docs/database_design/` `docs/api_design/` を更新する（上記「DB / API 設計書のメンテナンスルール」参照）
