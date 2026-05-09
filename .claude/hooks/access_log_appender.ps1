#!/usr/bin/env pwsh
# phaseX-11 Step 6-1: 参照ログ Hook
#
# Claude Code の PostToolUse hook から呼ばれ、Read tool が docs/ai_context/ 配下を
# 読んだときに docs/ai_context/_access_log.md へ 1 行追記する。
#
# 仕様:
# - 入力: stdin に Claude Code から JSON（tool_name / tool_input.file_path / tool_response 等）
# - 出力: 何も返さない（ログ追記のみ。Claude には影響しない）
# - 対象: tool_name == "Read" かつ file_path が docs/ai_context/ 配下
# - 追記内容: 日付 / ファイル名 / 推定発火元（プレースホルダ "—"）
# - ローテーション: 行数 1000 超で先頭 500 行を _access_log_archive_YYYYMM.md へ退避
# - プライバシー: tool_response は読まない。ファイル名のみ記録
# - 脱出ハッチ: AMAZIA_TPL_HOOK_DISABLE=1 で全 hook 無効化（Step 3 と共通）

$ErrorActionPreference = 'Stop'

# 何があっても Claude を止めない方針：例外は全て握りつぶしてゼロを返す
trap {
    exit 0
}

if ($env:AMAZIA_TPL_HOOK_DISABLE -eq '1') { exit 0 }

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }

try {
    $hookInput = $raw | ConvertFrom-Json
} catch { exit 0 }

if ([string]$hookInput.tool_name -ne 'Read') { exit 0 }

$filePath = [string]$hookInput.tool_input.file_path
if (-not $filePath) { exit 0 }

$normalized = $filePath -replace '\\', '/'

# docs/ai_context/ 配下のみ対象（_access_log.md / _archive 自体は除外）
if ($normalized -notmatch '(^|/)docs/ai_context/[^/]+\.md$') { exit 0 }
if ($normalized -match '(^|/)_access_log\.md$') { exit 0 }
if ($normalized -match '(^|/)_access_log_archive_') { exit 0 }

# 表示用の短縮パス（docs/ai_context/ 以降）
$shortPath = $normalized -replace '.*?docs/ai_context/', 'docs/ai_context/'

# リポジトリルート推定：このスクリプトの親の親（.claude/hooks → リポルート）
$repoRoot = (Get-Item $PSScriptRoot).Parent.Parent.FullName
$logFile = Join-Path $repoRoot 'docs\ai_context\_access_log.md'
$logDir  = Split-Path $logFile -Parent

if (-not (Test-Path $logDir)) { exit 0 }

# 初回作成時はヘッダーを書く（UTF-8 BOM 付きで一貫させる）
if (-not (Test-Path $logFile)) {
    $header = @(
        '# AI コンテキスト参照ログ',
        '',
        '<!-- phaseX-11 Step 6-1 自動生成。docs/ai_context/ 配下の Read を PostToolUse hook で記録 -->',
        '<!-- 1000 行超で _access_log_archive_YYYYMM.md にローテーション。プライバシー：パスのみ記録 -->',
        '',
        '| 日付 | ファイル | 発火元（推定） |',
        '|------|---------|--------------|'
    ) -join [System.Environment]::NewLine
    $utf8Bom = [System.Text.UTF8Encoding]::new($true)
    [System.IO.File]::WriteAllText($logFile, $header + [System.Environment]::NewLine, $utf8Bom)
}

$date = (Get-Date).ToString('yyyy-MM-dd')
$line = "| $date | $shortPath | — |"

# 追記（並行アクセス対策で FileStream を Append + Read で開く）
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$stream = [System.IO.File]::Open($logFile, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
try {
    $bytes = $utf8NoBom.GetBytes($line + [System.Environment]::NewLine)
    $stream.Write($bytes, 0, $bytes.Length)
} finally {
    $stream.Dispose()
}

# ローテーション判定：行数が 1000 を超えたら先頭 500 行を archive へ退避
$lines = [System.IO.File]::ReadAllLines($logFile)
if ($lines.Count -gt 1000) {
    $headerLineCount = 7  # 上の $header と空行
    $archiveStart = $headerLineCount
    $archiveEnd   = $headerLineCount + 500 - 1
    if ($archiveEnd -lt ($lines.Count - 1)) {
        $archiveLines = $lines[$archiveStart..$archiveEnd]
        $keepLines    = $lines[0..($headerLineCount - 1)] + $lines[($archiveEnd + 1)..($lines.Count - 1)]

        $stamp = (Get-Date).ToString('yyyyMM')
        $archiveFile = Join-Path $logDir "_access_log_archive_$stamp.md"
        $archiveHeader = @(
            "# AI コンテキスト参照ログ アーカイブ ($stamp)",
            '',
            '<!-- _access_log.md からローテーションされた古い行 -->',
            '',
            '| 日付 | ファイル | 発火元（推定） |',
            '|------|---------|--------------|'
        )

        $utf8Bom = [System.Text.UTF8Encoding]::new($true)
        if (-not (Test-Path $archiveFile)) {
            [System.IO.File]::WriteAllLines($archiveFile, $archiveHeader, $utf8Bom)
        }
        # アーカイブはタイムスタンプ毎に追記
        Add-Content -Path $archiveFile -Value $archiveLines -Encoding UTF8

        # 本体は保持分のみで上書き
        [System.IO.File]::WriteAllLines($logFile, $keepLines, $utf8Bom)
    }
}

exit 0
