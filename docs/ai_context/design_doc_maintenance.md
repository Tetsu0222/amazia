# DB / API 設計書のメンテナンスルール

## このファイルの位置付け

DB スキーマ変更や API エンドポイント追加を伴うフェーズで、実装と同フェーズ内に
設計書を更新するための手順を定める。CLAUDE.md からポインタ参照される本体ファイル。

phaseX-11 Step 2 で CLAUDE.md 本文（直書き 40 行超）から切り出し。

## いつ参照するか（トリガー条件）

- フェーズ計画時にスキーマ変更（新規テーブル / カラム追加 / リネーム / 廃止）が含まれるとき
- 新規 `@RestController` / Laravel ルート / Market 側の API 呼び出しが発生するとき
- フェーズ完了確認時（TPL-007）に「設計書反映」が完了条件として含まれるとき
- `schema.sql` / `database/migrations/` / `routes/*.php` を Edit/Write 対象とする操作の **着手前**

---

## 基本方針

DB スキーマ変更や API エンドポイント追加を伴うフェーズでは、**実装と同じフェーズ内で**
設計書を更新する。後追いでまとめてやるとフェーズを跨いで漏れる
（実例：P12〜P14 で 14 テーブル / 29 エンドポイントが未文書化のまま放置された）。

---

## DB スキーマを変更したとき

対象ファイル：`docs/database_design/`

### 1. 新規テーブル追加時

- `TBL_<テーブル名>.md` を新規作成（既存定義書のフォーマットを踏襲：概要 / カラム表 / FK / Index / マイグレーション対応）
- `README.md` のファイル一覧表にエントリを追加（所属システム・出現フェーズを明記）
- `ER_diagram.md` の Mermaid 図にテーブルとリレーションを追加

### 2. 既存テーブルへのカラム / インデックス追加時

- 該当 `TBL_*.md` のカラム表・インデックス節を更新
- マイグレーション対応セクション（または変更履歴）に schema.sql / V*.sql の出所を明記

### 3. マイグレーション方式の前提（037 起因 — `operational_insights.md` カテゴリ3 参照）

- 本番 Core は Flyway 未使用。`amazia-core/src/main/resources/schema.sql` を `spring.sql.init.mode=always` で起動時実行する方式
- `db/migration/V*.sql` は名残ファイルで本番では実行されない。新規スキーマ変更は schema.sql に IF NOT EXISTS / continue-on-error で追記するのが正
- Console は Laravel マイグレーション（`amazia-console/database/migrations/`）

---

## API エンドポイントを追加 / 変更したとき

対象ファイル：`docs/api_design/`

- Core（Spring Boot `@RestController`）の追加 → `Core_API.md` に追記
- Console（Laravel `routes/*.php`）の追加 → `Console_API.md` に追記
- Market（React からの呼び出し）に関わる場合 → `Market_API.md` の対応エンドポイントも更新
- パス変更・廃止は「実装済」「予定」「廃止済」の状態を明確に書き分ける（Market_API.md が「予定」のまま乖離した実例あり）

---

## 主要テーブル定数の同期（phaseX-6 / 044 起因）

`ops/healthcheck/required_tables.txt` は CD の「主要テーブル存在確認」ステップが参照する、
本番 DB に必ず存在すべき Core テーブルの一覧（出典：`docs/database_design/README.md` Core システム）。
`continue-on-error` で潰された DDL 失敗（044）をデプロイ後 1 分以内に検知するための定数。

- **テーブルを追加するとき**：同フェーズ内で `TBL_*.md` 新設・`docs/database_design/README.md` 追記・`schema.sql` 追記・`ER_diagram.md` 反映に加え、**`ops/healthcheck/required_tables.txt` にも追記**する
- **テーブルを廃止／リネームするとき**：同様に `required_tables.txt` から削除／更新する。残置すると次回デプロイで「不足テーブル」検知に引っかかり CD が `exit 1` で止まる
- **対象範囲**：Core システムのテーブルのみ（Console の `sessions` / `personal_access_tokens` 等 Laravel 標準テーブルは対象外）
- **更新が漏れた場合の挙動**：テーブル追加で更新を忘れても CD は通る（過大検知しない設計）。一方で本来検知すべき不足は検知されないため、PR レビューで `TBL_*.md` 追加と `required_tables.txt` 追加が両方あるかを必ず確認

---

## フェーズ完了の定義に組み込む

フェーズ完了とみなすには、`docs/ai_context/test_insights.md` のまとめセクションに加え、
**当該フェーズで触った DB / API がすべて設計書に反映されていること**を確認する。
設計書未更新のままフェーズを閉じない。

判定の詳細は [test_insights.md](test_insights.md) の「まとめ」セクション、ならびに
TPL-007（フェーズ完了確認）の自己チェック手順 [prompt_templates.md](prompt_templates.md) を参照。

---

## 関連ドキュメント

- [operational_insights.md](operational_insights.md) — カテゴリ3「schema.sql 編集時の 3 点観点」
- [ai_collaboration_antipatterns.md](ai_collaboration_antipatterns.md) — AP-001（既存スキーマ未読での追記）/ AP-007（部分実装での完了報告）
- [prompt_templates.md](prompt_templates.md) — TPL-001（新規テーブル追加）/ TPL-007（フェーズ完了確認）
- [../database_design/README.md](../database_design/README.md) — DB 設計書の索引
- [../api_design/](../api_design/) — Core_API.md / Console_API.md / Market_API.md
- [../../ops/healthcheck/required_tables.txt](../../ops/healthcheck/required_tables.txt) — phaseX-6 由来の主要テーブル定数
- [../../CLAUDE.md](../../CLAUDE.md) — 本ファイルへのポインタ起点
