# Hooks 一覧（phaseX-11 Step 3 + Step 6-1）

## このファイルの位置付け

`.claude/settings.json` に登録した Claude Code hook の発火条件・挙動・脱出ハッチを
一元管理する。「どの Hook が何を発火するか」を AI / 人間の双方が読み取れる形で残し、
過剰発火や TPL 陳腐化を後追いで検知できるようにする。

phaseX-11 では 2 種類の hook を導入済み：
- **PreToolUse（Step 3）**：Edit/Write 時に該当 TPL を AI に思い出させる
- **PostToolUse（Step 6-1）**：Read 時に `_access_log.md` へ追記し、参照頻度を計測

両 hook は環境変数 `AMAZIA_TPL_HOOK_DISABLE=1` で同時に無効化できる。

---

## PreToolUse Hook（Step 3）

### 全体像

| 項目 | 値 |
|------|-----|
| Hook 種別 | `PreToolUse`（実装は `.claude/hooks/tpl_trigger.ps1`） |
| 対象ツール | `Edit` / `Write` / `MultiEdit` |
| 実装方式 | PowerShell 単一スクリプトでファイルパスを正規表現マッチし、該当 TPL の警告を `additionalContext` で AI に伝える |
| 強度 | 「読め」レベル（`permissionDecision: "allow"` 固定でブロックしない） |
| 脱出ハッチ | 環境変数 `AMAZIA_TPL_HOOK_DISABLE=1` を設定すると黙る |

> **設計判断（matcher 統合）：** Claude Code の `matcher` はツール名しか受け付けないため、
> ファイルパスごとに 4 件の独立 Hook を切ると同じスクリプトが 4 回 spawn される。
> 単一 Hook 内でパスマッチングを行う方式に統合し、内部で **4 つの論理トリガー** を判定している。

---

## 論理トリガー一覧（4 件）

| # | 発火条件（ファイルパスの正規表現） | 発火 TPL | 予防 AP | 効果 |
|---|------------------------------------|----------|---------|------|
| 1 | `**/schema.sql` または `**/database/migrations/*.php` | TPL-001 | AP-001 | 新規テーブル追加・スキーマ変更時の事前確認を強制 |
| 2 | `amazia-core/**/test/**/*Helper.java` / `*Factory.java` / `*TestSupport.java` | TPL-003 | AP-002 | テストヘルパー作成時の Entity 制約確認を強制 |
| 3 | `.github/workflows/*.yml` または `ops/**/*.{sh,ps1,yml,yaml}` | TPL-008 | AP-008 | CI/CD・運用機構実装時の保証範囲・検知経路の明示を強制 |
| 4 | `amazia-core/**/src/test/**/*Test.java` | TPL-009 | AP-009 | 件数アサーションテスト追加時の分離確認を強制 |

> 同一ファイルが複数トリガーに該当する場合、該当 TPL すべての警告が結合されて返る。

---

## 発火時の挙動

1. Claude Code が `Edit` / `Write` / `MultiEdit` を呼ぶ前に PreToolUse hook が起動
2. `.claude/hooks/tpl_trigger.ps1` が stdin から `tool_input.file_path` を取り出す
3. パス正規化（`\` → `/`）後、上記 4 トリガーの正規表現を順に評価
4. 該当した TPL の警告本文を結合し、`hookSpecificOutput.additionalContext` に詰めて返す
5. AI（Claude）は `additionalContext` をシステムメッセージとして受け取り、TPL の自己チェックを実施してから実装に入る
6. 該当パターンなしの場合は `permissionDecision: "allow"` のみ返し、AI には何も伝えない（過剰発火回避）

---

## 脱出ハッチ

過剰発火で作業効率が落ちる場合、以下のいずれかで Hook を抑止できる：

| 手段 | 効果 |
|------|------|
| `set AMAZIA_TPL_HOOK_DISABLE=1`（CMD）/ `$env:AMAZIA_TPL_HOOK_DISABLE='1'`（PowerShell） | 当該シェルセッションでのみ Hook が `additionalContext` を返さなくなる |
| `.claude/settings.json` の該当 `hooks.PreToolUse` ブロックを一時コメントアウト | リポジトリ全体で停止（git で戻せる） |
| `.claude/hooks/tpl_trigger.ps1` の該当トリガー正規表現を狭める | 個別トリガーのみ抑止 |

---

## 動作確認手順

PowerShell から以下を実行することで、Claude Code を介さず単体で挙動を検証できる：

```powershell
# TPL-001 トリガーが発火する例
'{"tool_name":"Edit","tool_input":{"file_path":"amazia-core/src/main/resources/schema.sql"}}' `
  | powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/tpl_trigger.ps1

# 該当パターンなしの例（README 編集）
'{"tool_name":"Edit","tool_input":{"file_path":"README.md"}}' `
  | powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/tpl_trigger.ps1

# 脱出ハッチの動作確認
$env:AMAZIA_TPL_HOOK_DISABLE='1'
'{"tool_name":"Edit","tool_input":{"file_path":"amazia-core/src/main/resources/schema.sql"}}' `
  | powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/tpl_trigger.ps1
Remove-Item env:AMAZIA_TPL_HOOK_DISABLE
```

> **PowerShell バージョンについて：** Windows PowerShell 5.1（`powershell.exe`、Windows 標準）を採用。
> PowerShell 7+（`pwsh`）が利用可能な環境では `powershell` を `pwsh` に置き換えても動作する。

期待結果：
- TPL-001 例：`additionalContext` に TPL-001 の警告本文が入る
- README 例：`hookSpecificOutput.additionalContext` 不在（黙る）
- 脱出ハッチ例：環境変数を設定すると schema.sql でも黙る

---

## 故意失敗テスト（設計書 Step 3 §完了条件）

以下を実行し、検知が機能することを確認する：

1. 適当な `schema.sql` 風ファイルを作成し（例：`amazia-core/src/main/resources/schema.sql` の編集）
2. Claude Code 内で Edit/Write を試みる
3. AI 側に TPL-001 の警告が見えること（`additionalContext` 経由）
4. `$env:AMAZIA_TPL_HOOK_DISABLE='1'` 設定後に同じ操作で警告が消えること

---

## 注意点・設計の意図

- **「読め」レベルに留めている**：ブロック（`deny`）ではなく `allow + additionalContext`。AI の判断余地を残し、過剰発火による作業停止を回避（設計書 §注意点）
- **ファイルパターンを慎重に絞っている**：`*.java` 全部に発火させると過剰なので、テストヘルパーは `*Helper.java` / `*Factory.java` / `*TestSupport.java` 限定。本物の Entity / Service クラスには発火しない
- **AP-006（対症療法）の Hook 化は見送り**：バグ修正というイベントはファイル種別から判定できない（同じファイルでも新機能 / バグ修正の区別がつかない）。これは Step 4 の skill 化、または Step 6 の参照ログで補う
- **TPL-002/004/005/006/007 の Hook 化も見送り**：着手タイミングがファイル編集と直結しないため、Hook ではなく skill 化（Step 4）または AI の能動 Read で運用する

---

---

## PostToolUse Hook（Step 6-1）

### 全体像

| 項目 | 値 |
|------|-----|
| Hook 種別 | `PostToolUse`（実装は `.claude/hooks/access_log_appender.ps1`） |
| 対象ツール | `Read`（matcher で絞り込み） |
| 役割 | `docs/ai_context/` 配下のファイルが Read されたら `_access_log.md` に 1 行追記。死んだドキュメントの早期検知が目的 |
| 出力 | 何も返さない（Claude 側に影響なし。ログ追記のみ） |
| プライバシー | 記録するのはファイルパスと日付のみ。`tool_response`（読まれた内容）には触れない |
| 脱出ハッチ | 環境変数 `AMAZIA_TPL_HOOK_DISABLE=1` を設定すると黙る（PreToolUse と共通） |

### 追記対象の判定

以下のすべてを満たす場合に追記：

- `tool_name == "Read"`
- `tool_input.file_path` が `docs/ai_context/<name>.md` パターンに一致（相対 / 絶対両対応）
- `_access_log.md` 自身、`_access_log_archive_*` ではない

### 追記書式

```markdown
| YYYY-MM-DD | docs/ai_context/<filename>.md | — |
```

「発火元（推定）」列は現状常に `—`（プレースホルダ）。Hook 側からは Read の発端が
TPL 適用なのか自発参照なのかを区別できないため、推定が必要な場合は
レビュー時に手動で書き加える運用とする。

### ローテーション

行数が **1000** を超えたら、ヘッダー部（先頭 7 行）を保持したまま **先頭 500 行** を
`_access_log_archive_YYYYMM.md` に退避し、本体は残りで上書きする。
アーカイブは月次で蓄積され、定期レビュー（Step 6-2）で参照される。

### 動作確認手順

```powershell
# 通常パスでの追記
'{"tool_name":"Read","tool_input":{"file_path":"docs/ai_context/prompt_templates.md"}}' `
  | powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/access_log_appender.ps1

# 対象外（追記されないこと）
'{"tool_name":"Read","tool_input":{"file_path":"README.md"}}' `
  | powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/access_log_appender.ps1

# 自己参照（無限ループ防止：追記されないこと）
'{"tool_name":"Read","tool_input":{"file_path":"docs/ai_context/_access_log.md"}}' `
  | powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/access_log_appender.ps1

# 脱出ハッチ
$env:AMAZIA_TPL_HOOK_DISABLE='1'
'{"tool_name":"Read","tool_input":{"file_path":"docs/ai_context/test_insights.md"}}' `
  | powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/access_log_appender.ps1
Remove-Item env:AMAZIA_TPL_HOOK_DISABLE
```

### 注意点

- **AI 側に影響しない**：標準出力は空。Claude のセッションに `additionalContext` として
  介入することはない（Step 3 の PreToolUse とは異なる）
- **例外は握りつぶす**：Hook 側で何が起きても Claude を止めないよう `trap { exit 0 }`
  でガード。万一スクリプトが壊れてもセッションが進行不能にならない
- **Windows 5.1 と UTF-8 BOM**：日本語を含む PowerShell スクリプトは UTF-8 BOM 付きで保存
  （Step 3 と同じ罠。BOM 無しだと構文エラー）

---

## 関連ドキュメント

- [prompt_templates.md](prompt_templates.md) — TPL-001〜009 本文（PreToolUse Hook が指し示す自己チェック手順）
- [ai_collaboration_antipatterns.md](ai_collaboration_antipatterns.md) — AP-001〜009（Hook が予防する落とし穴）
- [_access_log.md](_access_log.md) — PostToolUse Hook の追記先
- [skills_inventory.md](skills_inventory.md) — `/review-docs`（参照ログを使う定期レビュー skill）
- [../../.claude/settings.json](../../.claude/settings.json) — Hook の登録元
- [../../.claude/hooks/tpl_trigger.ps1](../../.claude/hooks/tpl_trigger.ps1) — PreToolUse Hook 実装
- [../../.claude/hooks/access_log_appender.ps1](../../.claude/hooks/access_log_appender.ps1) — PostToolUse Hook 実装
- [../design/phaseX/phaseX-11_doc_driven_layering.md](../design/phaseX/phaseX-11_doc_driven_layering.md) — Step 3 / Step 6-1 の設計書
