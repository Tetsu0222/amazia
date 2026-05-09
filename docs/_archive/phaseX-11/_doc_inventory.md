# phaseX-11 Step 1：ドキュメント棚卸しと役割再定義

## 作成日
2026-05-09

## 目的

phaseX-11（ドキュメント駆動の多層化）の Step 2 以降（CLAUDE.md 参照ハブ化 / Hook 設計 / skill 化 / データ化）の **判断根拠** として、現在の AI コンテキスト系ドキュメント資産が「誰が・いつ・何のために読むか」を一文で定義し、重複・欠落・矛盾を洗い出す。

設計書の Step 1 完了条件：

- 全対象ファイルについて 5 項目（想定読者 / 読むタイミング / 主な役割 / 重複・課題）が埋まっている
- 重複・課題が少なくとも 3 件抽出されている

---

## 対象ファイルの棚卸し

| ファイル | 想定読者 | 読むタイミング | 主な役割 | 重複・課題 |
|---------|---------|--------------|---------|----------|
| [CLAUDE.md](../../../CLAUDE.md) | AI（自動ロード） | 会話開始時に自動ロード | 参照ハブ + 不具合対応テンプレ + DB/API メンテルール + 基本ルール | (a) 不具合対応のドキュメントテンプレ本文（30行）が直書きで肥大化要因。(b) DB/API メンテ詳細（40行）も直書き。(c) 「フェーズ完了の定義」が CLAUDE.md・test_insights.md・各 phase 設計書・AP-007/TPL-007 の **4 箇所** に重複。(d) AP-007/TPL-007 の双方向リンクが CLAUDE.md には張られておらず、不具合対応時の AP 参照が AI の能動判断頼み。 |
| [docs/ai_context/test_insights.md](../../ai_context/test_insights.md) | AI（実装計画時）/ 人間（レビュー） | 新規機能実装・テストケース作成・フェーズ計画時 | 過去 50+ 件のトラブルから抽出したテスト観点（カテゴリ 1〜15 + まとめ） | (e) 800 行超の巨大ファイルで、AI が能動 Read しても全文を活用しづらい。(f) 「フェーズ完了の定義」がここにも書かれており CLAUDE.md と二重管理。(g) カテゴリ番号体系（1〜15 + 7-2 + 11-2 + 12 が末尾）が整理されておらず、新規追記時の差し込み位置が不明瞭。(h) 構造的な再発パターン表が単独で価値ある資産だが、ファイル末尾に埋もれて参照されにくい。 |
| [docs/ai_context/operational_insights.md](../../ai_context/operational_insights.md) | AI（実装計画時）/ 人間（設計レビュー） | 新規機能実装・フェーズ計画時（特にコンテナ運用 / SSM / schema.sql 編集 / スコープ撤退判断） | 実装・運用パターンの落とし穴（カテゴリ 1〜5）。テストでは検出しづらい設計パターンを補完 | (i) test_insights.md と「補完関係」と書かれているが、両者の境界（どちらに何を書くか）の判断基準が文章でしか書かれていない。(j) カテゴリ3 の schema.sql 編集時 3 点観点が CLAUDE.md §DB/API メンテルール § 主要テーブル定数の同期と内容重複。(k) ファイル末尾のカテゴリ5「スコープ撤退の判断ログ」は知見というより事例記録で、性格が他カテゴリと異なる。 |
| [docs/ai_context/ai_collaboration_antipatterns.md](../../ai_context/ai_collaboration_antipatterns.md) | AI（着手前チェック）/ 人間（不具合分析・レビュー） | 新規スキーマ追加・ライブラリ採用・テストヘルパー作成・バグ修正・フェーズ完了確認の **着手前** | AI 協働で踏みやすい落とし穴 9 件（AP-001〜009）と「対応プロンプトスニペット」 | (l) 各 AP の「対応プロンプトスニペット」と prompt_templates.md の TPL 本文が重複（AP×TPL の N 対 N 関係で 81 通りの整合性が要管理）。(m) 横断観点で「中間プロセスの解釈余地」など未閉じの文脈が記述され、運用ドキュメントとしては読み手を迷わせる。(n) AP の追加基準（2 件累積で新規 AP 化）が文章で書かれているのみで、判定が AI / 人間の主観に委ねられている。 |
| [docs/ai_context/prompt_templates.md](../../ai_context/prompt_templates.md) | AI（自己チェック用）/ 人間（指示文ベース） | 各作業種別の **着手前** に AI が能動的に Read | 作業種別ごとの定型プロンプト 9 件（TPL-001〜009） | (o) 着手前に「これは TPL-XXX 案件だ」と気づく決定論的トリガーが無く、AI の能動判断頼み（横断観点 §中間プロセス §4 で自認済）。(p) AP との同期管理コスト（双方向リンク）が手作業。(q) TPL の `<...>` スロット仕様が型・参照元未定義で自由記述。 |
| [docs/ai_context/operation_logs_naming.md](../../ai_context/operation_logs_naming.md) | AI（実装時）/ 人間（設計レビュー） | 新規 Controller / Service / 画面追加で `operation_logs` を記録する場合 | `operation_logs.screen_name` / `api_name` / `action` の命名規約 | (r) 性質が他 ai_context ファイルと異なる（ad-hoc な「規約定義」であり、知見抽出やプロンプトテンプレートではない）。配置場所として ai_context 配下が妥当か再検討余地あり（`docs/coding_guidelines.md` 配下や `docs/database_design/` 配下も候補）。(s) phase14 r4 / phase15 r4 起因で書かれた version 固定の規約だが、後フェーズでの追記（phase15 r5 で `shipping_blocked_insufficient_stock` 追加）が改訂履歴で管理されており、規約と履歴が混在。 |
| [docs/troubles/README.md](../../troubles/README.md) | 人間（不具合棚卸し）/ AI（同根トラブル探索） | 新規不具合発生時に同型を grep する起点 / 不具合対応完了時に表へ追記 | troubles 50+ 件のインデックス + 雛形枠 + 再発防止アクション一覧 | (t) 表が 50+ 行に達し、概要列が 200 字級の長文（054 など）で「目次」としての機能を超えている。(u) 「再発防止アクション（未対応）」テーブルが ⬜ 未対応 / ✅ 対応済 で混在し、消化済を整理する運用ルールが無い。(v) 各トラブルの AP/TPL 関連付けが書かれていないため、AP/TPL 側からの逆引きはあるが README 側からの順引きが弱い。 |

---

## 重複・課題サマリ（最低 3 件 → 計 22 件抽出）

設計書 Step 1 の完了条件は「重複・課題が少なくとも 3 件抽出」。本棚卸しでは a〜v の 22 件を抽出した。phaseX-11 Step 2〜6 で対応するもの・phaseX-12 以降に申し送るものを以下に整理する。

### Step 2（CLAUDE.md 参照ハブ化）で対応

| 課題 | 内容 | 対応方針 |
|------|------|---------|
| (a) | 不具合対応テンプレ本文 30 行が CLAUDE.md 直書き | `docs/ai_context/trouble_doc_template.md` へ切り出し |
| (b) | DB/API メンテ詳細 40 行が CLAUDE.md 直書き | `docs/ai_context/design_doc_maintenance.md` へ切り出し |
| (c) | 「フェーズ完了の定義」が CLAUDE.md / test_insights.md / 各 phase 設計書 / AP-007 / TPL-007 の 4 箇所重複 | CLAUDE.md は 1 行ポインタに集約。test_insights.md「まとめ」を正本として明示 |
| (d) | CLAUDE.md に AP/TPL 双方向リンクが弱い | 切り出し先の `trouble_doc_template.md` に AP-NNN 関連表を追加 |

### Step 3（Hook 機械的トリガー）で対応

| 課題 | 内容 | 対応方針 |
|------|------|---------|
| (o) | TPL 着手前トリガーが AI の能動判断頼み | PreToolUse hook（`Edit\|Write` × `**/schema.sql` 等）で機械的発火 |

### Step 4（skill 化）で対応

| 課題 | 内容 | 対応方針 |
|------|------|---------|
| (c)（再掲） | フェーズ完了確認 = TPL-007 が能動判断依存 | `/check-phase-completion` skill 化で「呼び出し」化 |

### Step 5（データ化）で対応候補

| 課題 | 内容 | 対応方針 |
|------|------|---------|
| (j) | schema.sql 3 点観点と CLAUDE.md §主要テーブル定数の同期が内容重複 | `ops/healthcheck/required_tables.txt` の前例に倣い、検証可能ルールはデータ化（`ops/config/required_env_vars.yml` 等） |

### Step 6（参照ログ・定期レビュー）で対応

| 課題 | 内容 | 対応方針 |
|------|------|---------|
| (e) | test_insights.md が 800 行超で活用しづらい | 参照ログ（`_access_log.md`）で実際の参照頻度を計測。低頻度カテゴリは phaseX-12 以降で再構成検討 |
| (h) | 「構造的な再発パターン表」が末尾に埋もれる | 同上の参照ログで活用度を計測 |
| (u) | 再発防止アクション表の消化済整理ルール不在 | 定期レビュー（3 ヶ月毎の schedule タスク）で棚卸し |

### phaseX-11 スコープ外（phaseX-12 以降に申し送り）

| 課題 | 内容 | 申し送り理由 |
|------|------|------------|
| (f) | test_insights.md の「フェーズ完了の定義」と CLAUDE.md の二重管理 | (c) の解消で間接的に改善するが、test_insights.md 側の構造改修は phaseX-12 |
| (g) | test_insights.md カテゴリ番号体系の不整合（7-2 / 11-2 / 12 が末尾） | 全面リライトはスコープ外（設計書 §対象外） |
| (i) | test_insights / operational_insights の境界判断基準が文章 | 「役割再定義」の文書追加は phaseX-12 |
| (k) | operational_insights.md カテゴリ5（スコープ撤退判断ログ）の性格不一致 | 別ファイル化検討は phaseX-12 |
| (l) | AP × TPL の N 対 N 整合性管理 | 双方向リンク自動化は別途 skill / lint で検討 |
| (m) | AP の横断観点に未閉じ文脈が記述され読み手を迷わせる | 構造改修は phaseX-12（メタフェーズが肥大化しないよう本フェーズでは触らない） |
| (n) | AP 追加基準が文章のみで主観依存 | 判定木化は phaseX-12 候補 |
| (p) | AP-TPL 双方向リンク同期が手作業 | 自動同期は別途 |
| (q) | TPL スロット型・参照元未定義 | TPL スロット仕様化は phaseX-12 候補 |
| (r) | operation_logs_naming.md の配置妥当性 | 移設判断は phaseX-12（設計書 §対象外：全面リライトしない） |
| (s) | operation_logs_naming.md の規約と履歴混在 | 同上 |
| (t) | troubles/README.md 表の 200 字級概要 | troubles ドキュメント全面リライトは phaseX-12 候補 |
| (v) | troubles → AP/TPL の順引き弱い | 双方向リンク自動化と併せて検討 |

---

## 補足：本棚卸しが Step 2〜6 にどう接続するか

設計書 phaseX-11 の各 Step は本棚卸し結果を以下のように利用する：

- **Step 2**: 課題 (a)(b)(c)(d) を解消する切り出し対象を決定済み
- **Step 3**: 課題 (o) の Hook 候補（4 件）は設計書の表に列挙済み。本棚卸しでは「TPL-001 / TPL-003 / TPL-008 / TPL-009 が能動判断依存」と裏付けた
- **Step 4**: 課題 (c) の再掲。TPL-007（フェーズ完了確認）を skill 化対象とする裏付け
- **Step 5**: 課題 (j) を起点に required_env_vars / requires_new_classes / auth_routing をデータ化候補として継承
- **Step 6**: 課題 (e)(h)(u) を参照ログ・定期レビューの初期観測対象とする

本ファイルは Step 2 以降の判断根拠として保持し、phaseX-12 完了時点で再棚卸しの基準として参照する。
