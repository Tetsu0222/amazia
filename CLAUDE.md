# Amazia プロジェクト AIコンテキスト

このファイルは **薄い参照ハブ** として保つ。詳細ルール本文は外部ファイルに切り出し、
ここでは「いつ何を参照すべきか」の 1 行トリガー条件のみを置く（phaseX-11 Step 2 で再構成）。

## 実装・計画時に必ず読むファイル

### コーディング規約
`docs/coding_guidelines.md` — 命名・コメント・テスト方針など。実装前に必ず参照。

### テスト設計の知見
`docs/ai_context/test_insights.md` — 過去トラブルから抽出したテスト観点。
テストケース作成・フェーズ実装計画時に参照。フェーズ完了の定義はここの「まとめ」が正本。

### 実装・運用パターンの知見
`docs/ai_context/operational_insights.md` — Spring Boot ライフサイクル / コンテナ運用 /
SSM 経由ジョブなど、テストでは検出しづらい設計パターン。新規機能実装・フェーズ計画時に参照。

### AI協働アンチパターン
`docs/ai_context/ai_collaboration_antipatterns.md` — AP-001〜009。新規スキーマ追加・
ライブラリ採用・テストヘルパー作成・バグ修正・フェーズ完了確認の **着手前** に該当 AP の
「対応プロンプトスニペット」を確認。本質的にはすべて人間側の責任である前提で、
改善のレバーを見つけるための分析フレームワークとして使う。

### プロンプトテンプレート集
`docs/ai_context/prompt_templates.md` — TPL-001〜009。各作業種別の着手前に AI 自身が
自己チェックとして使う。各 TPL は対応する AP-* と双方向リンクされている。

## 不具合対応時のルール

ユーザーから不具合・障害の調査・修正を依頼されたときは、コード修正に加えて
[docs/ai_context/trouble_doc_template.md](docs/ai_context/trouble_doc_template.md) の
手順とテンプレートに従う。特に以下を **必ず** 行う：

- 既存 `docs/troubles/` の同根トラブルを先に確認（連番乱立を避け、派生節を優先）
- 修正後、`test_insights.md` / `ai_collaboration_antipatterns.md` / `troubles/README.md` の追記漏れチェック
- 同根の他箇所を grep する（AP-001/005/006/008/009 の共通根：単発修正で閉じない）

詳細手順・ドキュメント雛形・AP-NNN 関連表は上記ファイル参照。

## DB / API 設計書のメンテナンスルール

DB スキーマ変更や API エンドポイント追加を伴うフェーズでは、**実装と同フェーズ内で**
[docs/ai_context/design_doc_maintenance.md](docs/ai_context/design_doc_maintenance.md) に
従って設計書を更新する。後追いで漏れた実例：P12〜P14 で 14 テーブル / 29 エンドポイント未文書化。

トリガー条件：

- `schema.sql` / `database/migrations/` を編集したとき → `docs/database_design/` 更新
- `@RestController` / `routes/*.php` / Market 側 API を編集したとき → `docs/api_design/` 更新
- 新規テーブル追加時 → `ops/healthcheck/required_tables.txt` も同フェーズで更新（044 起因）
- フェーズ完了判定時 → 設計書反映が完了条件に含まれる

## 基本ルール

- 新規環境変数を追加するときは `docker-compose.yml` と `phpunit.xml` を**必ずセット**で更新する
- テスト内で URL・ホスト名をハードコードせず `config()` 経由で取得する
- フェーズ完了の定義は `docs/ai_context/test_insights.md` の「まとめ」セクションに従う
- フェーズで DB / API を触ったら同フェーズ内で `docs/database_design/` `docs/api_design/` を更新する（[design_doc_maintenance.md](docs/ai_context/design_doc_maintenance.md) 参照）
