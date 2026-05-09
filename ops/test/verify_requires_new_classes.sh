#!/usr/bin/env bash
# phaseX-11 Step 5: REQUIRES_NEW クラス一覧の検証
#
# `@Transactional(propagation = Propagation.REQUIRES_NEW)` を使う
# amazia-core 本番クラスの実測値と、ops/test/requires_new_classes.txt（期待値）を比較する。
# 不一致なら CI を失敗させ、TPL-009 / AP-009 への申し送り漏れを早期検知する。
#
# 使い方:
#   bash ops/test/verify_requires_new_classes.sh           # 通常検証
#   AMAZIA_VERIFY_DEBUG=1 bash ops/test/...                # 期待・実測の双方を出力
#
# 終了コード:
#   0 — 一致
#   1 — 不一致（出力に diff を表示）
#   2 — 入力ファイル / ディレクトリ不在

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXPECTED_FILE="${REPO_ROOT}/ops/test/requires_new_classes.txt"
SOURCE_DIR="${REPO_ROOT}/amazia-core/src/main"

if [ ! -f "${EXPECTED_FILE}" ]; then
  echo "ERROR: expected file not found: ${EXPECTED_FILE}" >&2
  exit 2
fi
if [ ! -d "${SOURCE_DIR}" ]; then
  echo "ERROR: source dir not found: ${SOURCE_DIR}" >&2
  exit 2
fi

# 期待値: コメント・空行を除き、重複除去してソート
expected="$(grep -vE '^\s*(#|$)' "${EXPECTED_FILE}" | awk 'NF' | sort -u)"

# 実測値: REQUIRES_NEW が含まれる .java ファイルのファイル名（拡張子なし）を抽出
# - grep -rl で該当ファイル一覧を取得
# - basename .java で単純クラス名へ変換
# - sort -u で重複除去・ソート
actual="$(grep -rlE 'Propagation\.REQUIRES_NEW' "${SOURCE_DIR}" --include='*.java' \
  | xargs -I{} basename {} .java \
  | sort -u)"

if [ "${AMAZIA_VERIFY_DEBUG:-}" = "1" ]; then
  echo "=== expected ==="; echo "${expected}"
  echo "=== actual ==="; echo "${actual}"
fi

# 期待にあって実測にない（=削除されたクラスが txt に残置）
missing_in_actual="$(comm -23 <(printf '%s\n' "${expected}") <(printf '%s\n' "${actual}") || true)"
# 実測にあって期待にない（=新規 REQUIRES_NEW 追加で txt 未更新）
extra_in_actual="$(comm -13 <(printf '%s\n' "${expected}") <(printf '%s\n' "${actual}") || true)"

status=0

if [ -n "${extra_in_actual}" ]; then
  echo "❌ 新規 REQUIRES_NEW クラスが ops/test/requires_new_classes.txt に未登録です:" >&2
  echo "${extra_in_actual}" | sed 's/^/  + /' >&2
  echo "" >&2
  echo "対応:" >&2
  echo "  1. 上記クラスを ops/test/requires_new_classes.txt に追記" >&2
  echo "  2. 当該クラス書き込み先テーブルへの cleanup.sql 適用が漏れていないか TPL-009 で確認" >&2
  echo "  3. 関連 AP-009（テスト分離不足）に新規クラスを横展開チェック" >&2
  status=1
fi

if [ -n "${missing_in_actual}" ]; then
  echo "⚠️  ops/test/requires_new_classes.txt に列挙されているが本番コードに存在しません:" >&2
  echo "${missing_in_actual}" | sed 's/^/  - /' >&2
  echo "" >&2
  echo "対応: クラスが廃止されたなら txt から削除してください" >&2
  status=1
fi

if [ "${status}" -eq 0 ]; then
  echo "✅ REQUIRES_NEW クラス一覧と実測が一致しています ($(echo "${actual}" | wc -l | tr -d ' ') 件)"
fi

exit "${status}"
