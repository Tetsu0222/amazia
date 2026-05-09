---
description: AI コンテキストドキュメントの 3 ヶ月定期レビュー（phaseX-11 Step 6-2）。GitHub Actions cron が起こした Issue を起点に呼び出すことを想定。
argument-hint: <レビュー連番 例 001 / 自動採番なら省略>
---

# AI コンテキスト ドキュメント定期レビュー

このコマンドは [docs/design/phaseX/phaseX-11_doc_driven_layering.md](../../docs/design/phaseX/phaseX-11_doc_driven_layering.md) §Step 6-2 を実行可能なチェックリストに変換した skill です。
3 ヶ月ごとに `quarterly-doc-review.yml` が Issue を自動起票し、人間がそれを見て本コマンドを呼び出す運用。

> **引数 `$ARGUMENTS`：** レビュー連番（例：`001`）。省略時は `docs/ai_context/_review_*.md` の最大番号 +1 を採番してください。

---

## 実施タスク（4 件）

### 1. 参照ログから死んだドキュメントを抽出

[`docs/ai_context/_access_log.md`](../../docs/ai_context/_access_log.md) と直近のアーカイブ
（`_access_log_archive_*.md`）を読み、各ファイルの参照回数を集計してください。

判定基準：

- **死亡候補**：直近 3 ヶ月（90 日）で 0 回参照されたファイル
- **要観察**：3 回未満
- **健全**：3 回以上

`docs/ai_context/` 配下のファイル一覧を `Glob` で取得し、参照回数 0 のファイルを差分として明示してください。

### 2. troubles 棚卸しと TPL 陳腐化チェック

直近 3 ヶ月で追加・更新された `docs/troubles/NNN_*.md` を `git log` で抽出し、
各 trouble に対して以下を判定してください：

- 既存 AP-001〜009 / TPL-001〜009 のいずれかでカバーできているか
- カバーできていない場合、新規 AP / TPL の追加候補か、それとも単発トラブルか
- 既存 TPL の本文と本番コードに乖離が無いか（特に TPL-009 の REQUIRES_NEW クラス一覧は
  [`ops/test/requires_new_classes.txt`](../../ops/test/requires_new_classes.txt) で
  CI 検証済だが、それ以外の TPL 内列挙データに乖離がないか目視）

### 3. CLAUDE.md 肥大化兆候の警告

[`CLAUDE.md`](../../CLAUDE.md) の行数を計測し、以下を報告してください：

- 現在の行数
- phaseX-11 Step 2 完了時の 59 行からの増分
- 80 行を超えている場合、再リファクタリング候補節を提案
- AP / TPL 増加に伴う直書きルールの再混入が無いか確認

### 4. Hook / skill / データ化の費用対効果点検

phaseX-11 で導入した 3 レイヤーが機能しているかを点検：

| 機構 | 点検項目 |
|------|---------|
| Hook（Step 3） | `_access_log.md` で `prompt_templates.md` が定常的に参照されているか（=Hook → Read 経由が機能） |
| skill（Step 4） | `/check-phase-completion` の使用記録（実利用ベースは現状追えないが、TPL-007 本文の skill 化済みマークが残っているか） |
| データ化（Step 5） | `verify-data-rules.yml` の最近 3 ヶ月の実行履歴（緑 / 赤）と、追加データ化候補の有無 |

---

## 報告書フォーマット

`docs/ai_context/_review_$ARGUMENTS.md` を新規作成し、以下の書式で結果をまとめてください：

```markdown
# AI コンテキスト ドキュメント定期レビュー（$ARGUMENTS）

## レビュー実施日
YYYY-MM-DD

## レビュー範囲
直近 3 ヶ月（YYYY-MM-DD 〜 YYYY-MM-DD）

---

## 1. 死んだドキュメントの早期検知

### 参照ログ集計結果

| ファイル | 参照回数 | 判定 |
|---------|---------|------|
| prompt_templates.md | NN | 健全 / 要観察 / 死亡候補 |
| ai_collaboration_antipatterns.md | NN | ... |
| ... | ... | ... |

### 死亡候補への対応方針

- <ファイル名>: <統合 / 削除 / 様子見>（理由）

## 2. troubles 棚卸し

### 直近 3 ヶ月で追加・更新された troubles

- NNN_<タイトル>: <既存 AP/TPL カバー状況> / <対応方針>

### TPL 本文の陳腐化

- TPL-NNN: <乖離あり / なし>（あれば内容）

## 3. CLAUDE.md 肥大化監視

- 現在: NN 行
- Step 2 完了時: 59 行
- 増分: +/- N 行
- 判定: 正常 / 要圧縮（80 行超え時）

## 4. 3 レイヤー費用対効果

- Hook: <参照ログから読み取れる発火傾向>
- skill: <`/check-phase-completion` の運用記録 or 推測>
- データ化: <`verify-data-rules.yml` の実行履歴・追加候補の有無>

---

## 次回レビュー予定

YYYY-MM-DD（本レビュー実施日 + 3 ヶ月）

## phaseX-12 申し送り

（あれば記載）

## 付記

- 起点 Issue: <該当 Issue の URL>
- アーカイブされた参照ログ: <_access_log_archive_*.md があれば列挙>
```

---

## 運用ルール

- 本 skill は人間が GitHub Actions cron の Issue 通知を見て手動呼び出しする想定
- レビュー結果ファイル名は `_review_001.md`, `_review_002.md` のように 3 桁ゼロ埋め連番
- レビュー後、起点 Issue にレビュー結果ファイルへのリンクをコメントで返す（手動）
- 死んだドキュメントの統合・削除は当該レビューでは判定のみとし、実施は別 PR で

---

## 関連

- [hooks_inventory.md](../../docs/ai_context/hooks_inventory.md) — Hook の点検対象
- [skills_inventory.md](../../docs/ai_context/skills_inventory.md) — skill の点検対象
- [data_rules_inventory.md](../../docs/ai_context/data_rules_inventory.md) — データ化の点検対象
- [_access_log.md](../../docs/ai_context/_access_log.md) — 参照ログ本体
