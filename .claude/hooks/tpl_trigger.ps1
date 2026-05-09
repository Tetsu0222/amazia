#!/usr/bin/env pwsh
# phaseX-11 Step 3: TPL 機械的トリガー Hook
#
# Claude Code の PreToolUse hook から呼ばれ、Edit/Write のファイルパスに応じて
# 該当 TPL（`docs/ai_context/prompt_templates.md` 内の自己チェック）を AI に思い出させる。
#
# 仕様:
# - stdin に Claude Code から hook の JSON が渡される（tool_name, tool_input.file_path 等）
# - stdout に { hookSpecificOutput: { hookEventName: "PreToolUse",
#       permissionDecision: "allow", additionalContext: "<警告本文>" } } を返す
# - 該当パターンなし or AMAZIA_TPL_HOOK_DISABLE=1 のときは allow のみ（黙る）
# - 警告は「読め」レベル。ブロックはしない（設計書 §注意点）

$ErrorActionPreference = 'Stop'

function Write-AllowResponse {
    param([string]$Context)
    $payload = @{
        hookSpecificOutput = @{
            hookEventName      = 'PreToolUse'
            permissionDecision = 'allow'
        }
    }
    if ($Context) {
        $payload.hookSpecificOutput.additionalContext = $Context
    }
    $payload | ConvertTo-Json -Depth 6 -Compress
}

# 脱出ハッチ：環境変数で完全無効化
if ($env:AMAZIA_TPL_HOOK_DISABLE -eq '1') {
    Write-AllowResponse
    exit 0
}

# stdin から JSON を読む
$raw = [Console]::In.ReadToEnd()
if (-not $raw) { Write-AllowResponse; exit 0 }

try {
    $hookInput = $raw | ConvertFrom-Json
} catch {
    Write-AllowResponse
    exit 0
}

$toolName = [string]$hookInput.tool_name
if ($toolName -notin @('Edit', 'Write', 'MultiEdit', 'NotebookEdit')) {
    Write-AllowResponse; exit 0
}

$filePath = [string]$hookInput.tool_input.file_path
if (-not $filePath) { Write-AllowResponse; exit 0 }

# パス区切りを正規化（Windows / POSIX 両対応）
$normalized = $filePath -replace '\\', '/'

$messages = New-Object System.Collections.Generic.List[string]

# TPL-001: 新規テーブル追加 / schema.sql 編集
if ($normalized -match '(^|/)schema\.sql$' -or $normalized -match '(^|/)database/migrations/[^/]+\.php$') {
    $messages.Add(@'
[TPL-001 trigger] schema.sql / Laravel migration を編集しようとしています。
着手前に docs/ai_context/prompt_templates.md#tpl-001 の自己チェックを実施してください。
要点: (1) 参照先 ID 列の型抽出 / (2) FK 列の型整合 / (3) 同概念テーブルの grep /
(4) docs/database_design/ の命名・index 規約整合。
新規テーブルなら ops/healthcheck/required_tables.txt の更新も必要（design_doc_maintenance.md 参照）。
'@)
}

# TPL-003: テストヘルパー作成・改修（Java/Spring Boot 側）
if ($normalized -match '(^|/)amazia-core/.+/test/.+Helper\.java$' -or
    $normalized -match '(^|/)amazia-core/.+/test/.+Factory\.java$' -or
    $normalized -match '(^|/)amazia-core/.+/test/.+TestSupport\.java$') {
    $messages.Add(@'
[TPL-003 trigger] テストヘルパー / ファクトリ / TestSupport を編集しようとしています。
着手前に docs/ai_context/prompt_templates.md#tpl-003 の自己チェックを実施してください。
要点: (1) Entity の @UniqueConstraint / @Column(unique=true) の有無 /
(2) 制約カラムの一意化戦略（System.nanoTime() / カウンタ等） /
(3) 既存ヘルパーのパターン整合。AP-002（Entity 制約未読でのハードコード）を予防。
'@)
}

# TPL-008: CI/CD・運用機構（GitHub Actions / SSM 関連スクリプト等）
if ($normalized -match '(^|/)\.github/workflows/[^/]+\.ya?ml$' -or
    $normalized -match '(^|/)ops/.+\.(sh|ps1|yml|yaml)$') {
    $messages.Add(@'
[TPL-008 trigger] CI/CD ワークフロー / 運用スクリプトを編集しようとしています。
着手前に docs/ai_context/prompt_templates.md#tpl-008 の自己チェックを実施してください。
要点: (1) 何を検証し何を保証していないかの明文化 / (2) 過去 troubles の grep
（SSM:003/008/022-026/028 / schema:027/037/038/044/045/049 / shell-sql:046/048） /
(3) カナリア戦略 / (4) 失敗時検知経路。「動いた」だけで完了としない。
'@)
}

# TPL-009: 件数アサーションを伴うテスト（Spring Boot Test）
# Java の **Test.java で amazia-core 配下を対象。Repository/Service/Job が含まれることが多い
if ($normalized -match '(^|/)amazia-core/.*src/test/.+Test\.java$') {
    $messages.Add(@'
[TPL-009 trigger] amazia-core のテストクラスを編集しようとしています。
件数アサーション（count() / findAll().size() / size() == N）を伴う場合は
docs/ai_context/prompt_templates.md#tpl-009 の自己チェックが必須。
要点: (1) @SpringBootTest なら H2 共有 DB を考慮 / (2) 既知 REQUIRES_NEW 経由クラス
（FaultInjectionLogger / BatchAlertNotifier / BatchExecutionRecorder）の残置確認 /
(3) 自テスト所有 ID でフィルタしたカウントを使う / (4) push 前に
mvn test -Dsurefire.runOrder=random で分離検証。AP-009 を予防。
'@)
}

if ($messages.Count -gt 0) {
    $combined = ($messages -join "`n`n---`n`n")
    Write-AllowResponse -Context $combined
} else {
    Write-AllowResponse
}

exit 0
