# Skills 一覧（phaseX-11 Step 4）

## このファイルの位置付け

`.claude/commands/` 配下に置いた slash command（skill）の一覧と、各 skill の発火タイミング・
TPL との対応関係・拡張候補を一元管理する。

phaseX-11 Step 4 では「TPL を **参照する** 形から **呼び出す** 形へ移行する」実験として、
TPL-007（フェーズ完了確認）を最初の skill 化対象とした。

---

## 全体像

| 項目 | 値 |
|------|-----|
| 配置場所 | `.claude/commands/<name>.md`（プロジェクトレベル slash command） |
| 呼び出し | `/<name> <引数>` |
| 引数展開 | 本文中の `$ARGUMENTS` が渡された引数で置換される |
| 中身 | YAML frontmatter（description / argument-hint）+ Markdown 本文。本文がそのまま Claude へのプロンプトとして投入される |

---

## 登録済み skill

| skill 名 | 由来 | 役割 |
|---------|------|------|
| `/check-phase-completion` | phaseX-11 Step 4（TPL-007 置換） | フェーズ完了確認の 6 チェック |
| `/review-docs` | phaseX-11 Step 6-2 | AI コンテキストドキュメントの 3 ヶ月定期レビュー |

### `/check-phase-completion`

| 項目 | 値 |
|------|-----|
| ファイル | [.claude/commands/check-phase-completion.md](../../.claude/commands/check-phase-completion.md) |
| 呼び出し例 | `/check-phase-completion phaseX-7` |
| 引数 | フェーズ番号（例：`phaseX-7` / `phase16`） |
| 置換対象 | [TPL-007 フェーズ完了確認時](prompt_templates.md#tpl-007-フェーズ完了確認時) |
| 予防 AP | [AP-007 設計書の機能を部分実装で完了報告](ai_collaboration_antipatterns.md#ap-007-設計書の機能を部分実装で完了報告) |
| 発火タイミング | フェーズ完了報告（design/phase*.md のステータスを「完了」にする）の **直前** |

**skill が実施する 6 つのチェック：**

1. 設計書の機能一覧 vs 実装状況の○×表（画面 / API / DB / 運用）
2. プレースホルダー残置の grep（`フェーズXで実装` / `TODO` / 空テンプレート）
3. 実機ブラウザ確認（ローカル + 本番ドメイン、認証絡みは本番必須）
4. CI グリーン単独では完了としない（テスト + ランダム順序 + デプロイ健全性）
5. DB / API 設計書の同フェーズ更新（`design_doc_maintenance.md` に従う）
6. `test_insights.md` の「まとめ：フェーズ完了の定義」10 項目との照合

**最終報告：** 規定のテンプレートで完了判定レポートを提示。

---

### `/review-docs`

| 項目 | 値 |
|------|-----|
| ファイル | [.claude/commands/review-docs.md](../../.claude/commands/review-docs.md) |
| 呼び出し例 | `/review-docs 001` |
| 引数 | レビュー連番（例：`001` / `002`）。省略時は既存最大番号 +1 を採番 |
| 起点 | `.github/workflows/quarterly-doc-review.yml` が 3 ヶ月ごとに自動起票する Issue（[1, 4, 7, 10] 月の 9 日 09:00 JST） |
| 役割 | AI コンテキストドキュメントの定期棚卸し（phaseX-11 Step 6-2） |

**skill が実施する 4 タスク：**

1. **死んだドキュメントの早期検知** — `_access_log.md` と `_access_log_archive_*.md` から参照頻度を集計し、3 ヶ月で 0 回参照のファイルを抽出
2. **troubles 棚卸しと TPL 陳腐化チェック** — 直近 3 ヶ月で更新された `docs/troubles/NNN_*.md` を AP/TPL カバー状況と突合
3. **CLAUDE.md 肥大化兆候の警告** — 行数を計測し、Step 2 完了時の 59 行 / 80 行上限と比較
4. **3 レイヤー費用対効果点検** — Hook / skill / データ化が機能しているかをログ・履歴ベースで点検

**最終報告：** `docs/ai_context/_review_NNN.md` を新規作成し、規定書式（skill 内に同梱）でレポートを残す。

**運用フロー：**

1. cron が `[Doc Review] YYYY-MM-DD 四半期 AI コンテキストレビュー` Issue を自動起票
2. 人間が Issue 通知を受け、ローカルで `/review-docs <NNN>` を呼び出す
3. AI が 4 タスクを実施し `_review_NNN.md` を作成 → PR
4. PR マージ後、Issue にレビュー結果ファイルへのリンクをコメントしてクローズ

---

## 設計判断と運用ルール

### なぜ TPL-007 を最初の skill 化対象に選んだか（設計書 Step4 §理由）

- **明確なタイミング**：フェーズ末という決定的なトリガーがあり、いつ呼び出すかが曖昧でない
- **チェックリスト型**：確認項目が 5〜6 個に固定されており、自由記述より構造化に向いている
- **失敗の影響が大きい**：部分実装で完了報告するリスクは過去 4 件（011/013/014/039）顕在化しており、費用対効果が高い

### TPL 本文との二重管理を避ける運用

skill 化したことで TPL-007 本文と skill 本文に重複が生じる。リスクを抑えるため：

- TPL-007 本文の冒頭に「skill 化済み（`/check-phase-completion`）」マークを付与し、
  運用上は skill を呼び出すことを推奨する旨を明記する
- TPL-007 本文は **AP-007 との双方向リンク用の参照点** として残置する
  （AP-007 → TPL-007 のリンクは保ち、TPL-007 → skill のリンクで実体に飛ぶ）
- 内容変更が必要になった場合は、まず skill 側を更新し、TPL-007 側を「skill が正本」として薄く同期する

### skill 化しない TPL（現時点）

| TPL | 理由 |
|-----|------|
| TPL-001 / 003 / 008 / 009 | Hook（Step 3）でファイル編集時に自動発火する方が決定論的 |
| TPL-002 | ライブラリ採用は判断系で、ファイル編集タイミングと直結しない |
| TPL-004 | 着手前の構成確認系で、新フェーズ計画時に AI が能動 Read で良い |
| TPL-005 | シェル / SQL 構築は登場頻度が低く、AP-005 のスニペット参照で十分 |
| TPL-006 | バグ修正の能動判断系で skill 引数化が難しい |

設計書 §注意点：「全 TPL を skill 化しない（過剰実装になる）」を尊重し、
TPL-007 の運用結果を見て次フェーズで対象拡張するかを判断する。

---

## 動作確認手順

Claude Code 内で：

```
/check-phase-completion phaseX-7
```

期待結果：
- `$ARGUMENTS` が `phaseX-7` に置換され、6 つのチェックリストが Claude のプロンプトに展開される
- Claude が `docs/design/phaseX/phaseX-7*.md` を読み始め、設計書の機能一覧と実装状況を○×で報告する
- 最終報告フォーマットに沿ったレポートが返る

引数なしで呼び出した場合：
- skill 内の指示に従い、Claude は対象フェーズをユーザーに確認してから進める

---

## 拡張候補（次フェーズ以降）

skill 化が次に費用対効果が高そうな候補：

| 候補 | 想定 skill 名 | 期待効果 |
|------|--------------|---------|
| TPL-006（バグ修正時の横展開確認） | `/check-bug-horizontal-spread` | AP-001/005/006/008/009 の共通根「単発修正で類似クラス見落とし」を呼び出し化 |
| 設計書の DB / API 一覧棚卸し | `/audit-design-doc-coverage` | `design_doc_maintenance.md` の更新漏れを能動的にチェックするコマンド |
| troubles 一覧の同根検索 | `/find-similar-trouble` | 不具合報告を受けた際に同根を grep で機械的に列挙 |

phaseX-11 内では追加実装せず、phaseX-12 以降で TPL-007 skill の運用結果を見て判断する。

---

## 関連ドキュメント

- [prompt_templates.md](prompt_templates.md) — TPL-007 本文（skill 化済み旨を記載）
- [ai_collaboration_antipatterns.md](ai_collaboration_antipatterns.md) — AP-007（skill が予防する落とし穴）
- [hooks_inventory.md](hooks_inventory.md) — Hook（Step 3 / Step 6-1）と skill（Step 4 / Step 6-2）の使い分け
- [_access_log.md](_access_log.md) — `/review-docs` が読む参照ログ（Step 6-1 Hook が追記）
- [../../.claude/commands/check-phase-completion.md](../../.claude/commands/check-phase-completion.md) — `/check-phase-completion` 実装本体
- [../../.claude/commands/review-docs.md](../../.claude/commands/review-docs.md) — `/review-docs` 実装本体
- [../../.github/workflows/quarterly-doc-review.yml](../../.github/workflows/quarterly-doc-review.yml) — 3 ヶ月 cron + Issue 起票
- [../design/phaseX/phaseX-11_doc_driven_layering.md](../design/phaseX/phaseX-11_doc_driven_layering.md) — Step 4 / Step 6-2 の設計書
