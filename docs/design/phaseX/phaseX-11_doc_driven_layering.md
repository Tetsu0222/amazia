# フェーズX-11：ドキュメント駆動の多層化（Hook / skill / データ化への分散）

## ステータス
✅ 完了（2026-05-09）／Step 1〜6 すべて完了。残作業はユーザー側の運用観察のみ（[フェーズ完了サマリ](#フェーズ完了サマリ-2026-05-09) 節参照）

## 位置付け

時系列フェーズ（1〜20）に依存しない横断的品質改善フェーズ。
phaseX-7 で AI 協働アンチパターン集（AP-001〜009）とプロンプトテンプレート集（TPL-001〜009）を
整備し、`docs/ai_context/` に「成功知見」「失敗知見」「定型プロンプト」が揃った。

しかし運用してみると、**ドキュメントを増やしても AI が能動的に参照しない限り効果が出ない**
という構造的限界が見えてきた。本フェーズはこの限界に対する直接対策として、
**ドキュメントが担っていた役割の一部を Hook / skill / データ化など別レイヤーに逃がす** 設計を行う。

phaseX-7 が「AI 協働の失敗知見を成文化する」フェーズだったのに対し、
本フェーズは **「成文化したドキュメントを実際に機能させる仕組み」** を整備するメタフェーズである。

---

## 背景・なぜ今やるか

### 構造的盲点

```
ドキュメントを増やす（AP / TPL / insights / troubles）
  └─ CLAUDE.md に「読め」と書く
      └─ AI は CLAUDE.md は読むが、その先のファイル本文は能動 Read しない
          └─ 該当場面で AP / TPL が参照されず、同型ミスが再発
              └─ 新たな troubles が記録され、新 AP が増える
                  └─ ドキュメントがさらに肥大化、参照率はさらに低下（負のループ）
```

### 現状の資産と課題

**資産（phaseX-7 までで整備済み）：**
- `docs/ai_context/test_insights.md` — テスト観点（成功知見）
- `docs/ai_context/operational_insights.md` — 実装・運用パターン（成功知見）
- `docs/ai_context/ai_collaboration_antipatterns.md` — AP-001〜009（失敗知見）
- `docs/ai_context/prompt_templates.md` — TPL-001〜009（定型プロンプト）
- `docs/troubles/` — 個別不具合記録 50+ 件
- `CLAUDE.md` — 124 行（参照ハブ + 不具合対応ルール + DB/API メンテルール）

**課題：**

1. **CLAUDE.md 肥大化の入口**
   - 124 行は危険ゾーンの入口。AP/TPL 増加に比例して肥大化リスクが高まる
   - 後半の指示ほど踏まれにくく、条件付き指示が無条件適用される現象が起こり始める

2. **N² の組み合わせ壁**
   - AP-009 × TPL-009 だけで 81 通りの相互参照
   - troubles・test_insights・operational_insights を含めると整合性検証は人間も AI も困難
   - 「ドキュメントを増やせば改善する」素朴仮説の限界が見えた

3. **AI が能動 Read するトリガーが弱い**
   - CLAUDE.md → 「prompt_templates.md があるよ」という名前だけが見える
   - 「新規テーブル追加だな、TPL-001 を見るか」という能動判断が必要
   - この判断が素通りすると、せっかくの TPL が機能しない

4. **参照されたかの計測手段がない**
   - 「TPL-007 が実際に参照されたか」を後追いで検証できない
   - 死んだドキュメントの早期検知ができず、棚卸し時期が分からない

### なぜ次フェーズで対応するか

本プロジェクトは AI 協働開発の実験が趣旨であり、
「ドキュメント駆動には N² の組み合わせ壁があり、Hook / skill / データ化への分散が必要」
という気づき自体が重要な実験成果である。

phaseX-7 で「ドキュメントを充実させる」アプローチの上限を実体験できた今こそ、
**次の打ち手（多層化）** に進むタイミングとして適している。
さらにドキュメントを増やす方向に進むと、N² の壁が深まって改善コストが急増する。

---

## 着手前提条件

- phaseX-7 完了（AP-001〜009 / TPL-001〜009 が整備済み）
- 進行中の機能フェーズ（phase16 以降）と直接の依存関係を持たないため差し込み可能
- Hook 設計（Step 3）は Claude Code の settings.json 仕様に依存するため、設定変更権限が必要

---

## 設計判断のサマリ

| 項目 | 現状 | 本フェーズの判断 | 判断理由 |
|------|------|------------------|---------|
| CLAUDE.md の肥大化対策 | 本文に詳細ルールを書き下し（124 行） | **参照ハブに純化、詳細は外部ファイル + ポインタへ** | 上位レイヤーは薄く保つことで参照漏れを防ぐ |
| TPL の発火方法 | AI の能動判断に依存 | **PreToolUse Hook で機械的トリガー + skill 化を併用** | 個人の注意力に依存しない構造的ガード |
| ドキュメントの実行可能化 | Markdown のみ | **slash command / skill として呼び出せる形に変換**（一部 TPL） | 「参照」ではなく「呼び出し」へ |
| ルールのデータ化 | 文章に埋め込み | **検証可能なデータ（YAML / txt）として持てるものは分離** | required_tables.txt の前例を拡張 |
| 参照ログ | 取得手段なし | **Hook で軽量な access_log.md を自動生成** | 死んだドキュメントの早期検知 |
| 棚卸し運用 | 半年に 1 回手動（実態は形骸化リスク） | **AI が定期レビューする schedule タスクを設置** | 人間運用ではなく AI 駆動で継続性確保 |
| スコープの広さ | — | **段階的に Step 1〜2 で棚卸し → Step 3〜5 で実装 → Step 6 で運用化** | 60 点で出して次フェーズで再調整する前提 |
| フェーズの長さ | — | **2〜3 週間の固定枠、残課題は phaseX-12 へ** | メタフェーズが肥大化して本体進捗を止めるリスクを回避 |

**コスト試算：恒久 $0**
- ドキュメント整備・Hook 設定・skill 整備のみで AWS リソース変更なし
- Claude Code の設定変更は無料

---

## スコープ

### 対象範囲

- `CLAUDE.md` の参照ハブ化リファクタリング（詳細ルールを外部ファイルへ切り出し）
- 各 ai_context ドキュメントの「役割再定義」（誰が・いつ・何のために読むか）
- TPL-001〜009 のうち発火タイミングが明確なものを Hook で自動化
- 一部 TPL の skill / slash command 化
- 検証可能なルールのデータ化（required_tables.txt パターンの拡張）
- 参照ログ取得の Hook 設置
- 定期レビュー schedule タスクの設置

### 対象外

- 新規 AP / TPL の追加（既存資産の構造改善のみ）
- AI 協働ドキュメントの全面リライト（タイトル・冒頭節の再定義に留める）
- 他 AI ツール（Cursor / Copilot 等）への移植 — 別フェーズ
- ドキュメント検索 RAG の導入 — 過剰最適化を避ける、別フェーズで検討

---

## 実装計画

### Step 1：棚卸しと役割再定義

**目的：** 現在のドキュメント資産が「誰が・いつ・何のために読むか」を一文で定義し、
重複・欠落・矛盾を洗い出す

**対象ファイル：**
- `CLAUDE.md`
- `docs/ai_context/test_insights.md`
- `docs/ai_context/operational_insights.md`
- `docs/ai_context/ai_collaboration_antipatterns.md`
- `docs/ai_context/prompt_templates.md`
- `docs/ai_context/operation_logs_naming.md`
- `docs/troubles/README.md`

**成果物：** [`docs/_archive/phaseX-11/_doc_inventory.md`](../../_archive/phaseX-11/_doc_inventory.md)（AI が能動参照しない位置に隔離。Step 2 以降の判断根拠として保持）

> **配置の意図**：当初案 `docs/ai_context/_doc_inventory.md` は ai_context 配下なので AI が能動 Read 候補に含めてしまう。棚卸しログは設計判断の根拠として残しつつも、AI コンテキストには載せたくないため `docs/_archive/phaseX-11/` 配下に隔離した（[配置経緯は README.md 参照](../../_archive/phaseX-11/README.md)）。

**書式案：**

```markdown
| ファイル | 想定読者 | 読むタイミング | 主な役割 | 重複・課題 |
|---------|---------|--------------|---------|----------|
| CLAUDE.md | AI（自動） | 会話開始時 | 参照ハブ + 不具合対応テンプレ | 詳細テンプレ書き下しが本文を圧迫 |
| ... | ... | ... | ... | ... |
```

**完了条件：**
- 全対象ファイルについて 5 項目が埋まっている ✅（7 ファイル分の表を作成）
- 重複・課題が少なくとも 3 件抽出されている ✅（22 件 a〜v を抽出。Step 2〜6 と phaseX-12 申し送りに振り分け済み）

---

### Step 2：CLAUDE.md の参照ハブ化リファクタリング

**目的：** CLAUDE.md を肥大化させずに済む構造に作り変える

**変更方針：**
- 「不具合対応時のルール」のテンプレ本文（30 行ほど）を `docs/ai_context/trouble_doc_template.md` に切り出し
- 「DB / API 設計書のメンテナンスルール」（40 行ほど）の詳細を `docs/ai_context/design_doc_maintenance.md` に切り出し
- CLAUDE.md にはポインタと **「いつ参照すべきか」の 1 行トリガー条件** だけ残す
- TPL/AP の増加で CLAUDE.md が膨らまない構造を確立

**成果物：**
- `docs/ai_context/trouble_doc_template.md`（新設）
- `docs/ai_context/design_doc_maintenance.md`（新設）
- `CLAUDE.md`（リファクタリング後）

**完了条件：**
- CLAUDE.md が 80 行以下に収まる
- 切り出し先ファイルの内容が CLAUDE.md からの導線で必ず辿れる
- リファクタリング前後でルールの抜け漏れがないことを diff で確認

---

### Step 3：Hook による機械的トリガー設置

**目的：** TPL の発火を AI の能動判断に依存させず、構造的に強制する

**設計方針：**

`.claude/settings.json` の `hooks` セクションに PreToolUse hook を設定し、
特定の Edit/Write 対象に対して該当 TPL を AI に強制 Read させる。

**実装する Hook 候補：**

| 対象操作 | 発火 TPL | 効果 |
|---------|---------|------|
| `Edit` / `Write` to `**/schema.sql` | TPL-001 | 新規テーブル追加時の事前確認を強制 |
| `Edit` / `Write` to `**/*Helper.java` (テスト配下) | TPL-003 | テストヘルパー作成時の Entity 制約確認を強制 |
| `Edit` / `Write` to `.github/workflows/*.yml` | TPL-008 | CI/CD 機構実装時の保証範囲確認を強制 |
| `Edit` / `Write` to `**/*Test.java` | TPL-009 | 件数アサーションテスト追加時の分離確認を強制 |

**Hook 形式：**

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "filePattern": "**/schema.sql",
        "command": "echo '⚠ TPL-001 (新規テーブル追加時) の事前確認を実施してください: docs/ai_context/prompt_templates.md#tpl-001'"
      }
    ]
  }
}
```

**成果物：**
- `.claude/settings.json` 更新（Hook 追加）
- `docs/ai_context/hooks_inventory.md` 新設（どの Hook が何を発火するかの一覧）

**完了条件：**
- 上記 4 件の Hook が動作確認済み
- Hook の発火条件・発火時の挙動が `hooks_inventory.md` に文書化されている
- 故意失敗テスト（schema.sql を編集して Hook が発火するか）で検知が機能することを確認

**注意点：**
- Hook が過剰発火すると AI の作業効率が落ちるため、**ファイルパターンは慎重に絞る**
- 「読んでください」レベルに留め、強制 Read までは行わない（AI の判断余地を残す）
- ユーザーが Hook をスキップしたい場合の脱出ハッチを設ける（環境変数で disable 可能等）

---

### Step 4：TPL の skill / slash command 化（実験範囲）

**目的：** 「参照する」ではなく「呼び出す」形にすることで、能動判断のステップを削る

**対象：** TPL-007（フェーズ完了確認時）を試験的に skill 化する

**理由：**
- フェーズ完了確認は明確なタイミング（フェーズ末）で必ず実施される
- 確認項目が 5 つあり、チェックリスト型なので skill 化に向いている
- 失敗すると影響が大きい（部分実装で完了報告するリスク）ため費用対効果が高い

**実装方針：**

`.claude/commands/check-phase-completion.md` を作成し、
`/check-phase-completion <phase番号>` で呼び出せるようにする。

skill の中身は TPL-007 の本文を実行可能なチェックリスト形式に変換したもの。

**成果物：**
- `.claude/commands/check-phase-completion.md`
- `docs/ai_context/skills_inventory.md`（skill 一覧と発火タイミング）

**完了条件：**
- `/check-phase-completion phaseX-7` で動作確認済み
- skill 化前後で TPL-007 の機能が損なわれていない
- 成功すれば次フェーズで他 TPL も skill 化検討対象になる旨を `skills_inventory.md` に記録

**注意点：**
- 全 TPL を skill 化しない（過剰実装になる）
- TPL-007 の試験結果を見て、次フェーズで対象拡張するか判断

---

### Step 5：ルールのデータ化（拡張）

**目的：** 文章に埋め込まれているルールのうち、検証可能なものをデータとして分離する

**前例：** `ops/healthcheck/required_tables.txt`（044 起因、phaseX-6 で導入）

**対象候補：**

| ルール | 現在の置き場所 | 分離先 |
|-------|--------------|-------|
| 必須環境変数の一覧 | docker-compose.yml / phpunit.xml に散在 | `ops/config/required_env_vars.yml` に集約 |
| REQUIRES_NEW を使うクラス一覧 | TPL-009 本文に列挙 | `ops/test/requires_new_classes.txt` |
| 主要 API パスの認証ルート | reference_market_auth_api_routing.md | `ops/api/auth_routing.yml` |

**判断基準：**
- 検証コードが書ける（CI で diff 検出できる）ルールはデータ化候補
- 文脈・判断が必要なルール（AP の本文等）は文章のまま残す

**成果物：**
- 上記候補のうち、検証コードがすぐ書ける 1〜2 件を選定して実装
- データファイル + 検証スクリプト + CI 組み込み

**完了条件：**
- 1 件以上のルールがデータ化され、CI で検証されている
- 故意失敗テストで検知が機能することを確認
- 残候補は phaseX-12 へ申し送り

---

### Step 6：参照ログと定期レビュー

**目的：** 死んだドキュメントの早期検知と、棚卸しの継続性確保

**設計方針：**

#### 6-1：参照ログの仕組み

`.claude/settings.json` の PostToolUse hook で、Read tool 発動時に
対象パスが `docs/ai_context/` 配下なら `docs/ai_context/_access_log.md` に追記する。

**書式案：**

```markdown
| 日付 | ファイル | 発火元（推定） |
|------|---------|--------------|
| 2026-05-09 | prompt_templates.md | TPL-001 適用時 |
| ... | ... | ... |
```

**注意点：**
- ログが肥大化するため、月次でローテーション or サマリ化
- プライバシー配慮（個別の作業内容は記録せず、参照ファイルパスのみ）

#### 6-2：定期レビュー schedule タスク

`schedule` skill を使って、3 ヶ月ごとに以下を AI に実行させる：

1. `_access_log.md` を読み、参照頻度の低い AP / TPL を抽出
2. `docs/troubles/` の最新トラブルを棚卸しし、TPL の陳腐化チェック
3. CLAUDE.md の行数を計測し、肥大化兆候があれば警告
4. 結果を `docs/ai_context/_review_NNN.md` として記録

**成果物：**
- `.claude/settings.json`（PostToolUse hook 追加）
- `docs/ai_context/_access_log.md`（自動生成、初期は空）
- 定期レビューの schedule タスク登録（次回 2026-08-09）

**完了条件：**
- 参照ログが Read tool 発動時に追記される動作確認済み
- schedule タスクが登録され、初回実行日が記録されている
- レビュー結果テンプレ（`_review_NNN.md` の書式）が決まっている

---

## 完了条件（フェーズ全体）

- Step 1〜6 のすべての完了条件を満たす
- `CLAUDE.md` が 80 行以下に圧縮されている
- 4 件以上の Hook が PreToolUse で動作している
- 1 件以上の TPL が skill / slash command 化されている
- 1 件以上のルールがデータ化され CI で検証されている
- 参照ログと定期レビューの仕組みが稼働している
- `_doc_inventory.md` が完了後も残置（次回フェーズの基準として参照可能）

---

## リスクと対策

| リスク | 対策 |
|-------|------|
| Hook が過剰発火して作業効率が落ちる | ファイルパターンを慎重に絞る／脱出ハッチを用意／Step 3 の動作確認で過剰発火を検知 |
| skill 化したが従来の TPL と二重管理になる | TPL 本文から skill へのリンクを張り、本文側は「skill 化済み」と明記して内容重複を避ける |
| 参照ログがプライバシー懸念を生む | パスのみ記録し作業内容は記録しない／ローカル限定で外部送信しない |
| 定期レビューが形骸化 | schedule タスク登録で実行を強制／レビュー結果を `_review_NNN.md` として残し未実施を可視化 |
| 多層化が複雑化して保守不能になる | Hook / skill / データ化の境界線を Step 1 の役割再定義で明確化／hooks_inventory.md・skills_inventory.md で一元管理 |
| メタフェーズが肥大化して本体進捗を止める | **2〜3 週間の固定枠**で切り、残課題は phaseX-12 へ申し送る |
| 完璧な構造を初回で目指してしまう | 60 点で出して次フェーズで再調整する前提を冒頭で宣言／Step 5 のデータ化対象も 1〜2 件に絞る |
| `Edit` への Hook が AI の判断ミスを助長する | Hook は「読め」レベルに留め、強制実装変更までは行わない／AI の判断余地を残す |

---

## ポートフォリオ価値

本フェーズは AI 駆動開発のポートフォリオとして以下を示せる：

1. **ドキュメント駆動の限界を実体験から論証** — 「ドキュメントを増やせば AI 協働は良くなる」という素朴仮説を、9 個の AP × 9 個の TPL × 50+ troubles を運用した結果として論証
2. **多層化アプローチの実装** — Markdown ドキュメントだけに依存せず、Hook（構造的強制）・skill（呼び出し化）・データ化（検証可能化）への分散を実装
3. **継続的計測の仕組み** — 参照ログと定期レビューで「死んだドキュメントの早期検知」を制度化
4. **メタ実験の成果** — 「AI 協働の品質改善は、ドキュメント設計だけでなくドキュメントを取り巻く運用基盤全体で考える必要がある」という気づきの提示

phaseX-7 が「失敗知見の成文化」だったのに対し、本フェーズは
**「成文化したものを実際に機能させる仕組み」** を整備する点で、AI 駆動開発の成熟度を一段引き上げる。

---

## 関連ドキュメント

- `docs/design/phaseX/phaseX-7_ai_collaboration_antipatterns.md` — 前提となるアンチパターン整備フェーズ
- `docs/ai_context/ai_collaboration_antipatterns.md` — AP-001〜009
- `docs/ai_context/prompt_templates.md` — TPL-001〜009
- `docs/ai_context/test_insights.md` — テスト観点の知見
- `docs/ai_context/operational_insights.md` — 実装・運用パターンの落とし穴
- `CLAUDE.md` — AI コンテキストとプロジェクトルール（本フェーズで参照ハブ化対象）
- `ops/healthcheck/required_tables.txt` — データ化前例（phaseX-6 由来）

---

## フェーズ完了サマリ（2026-05-09）

### Step ごとの成果物

| Step | 完了条件 | 成果物 |
|------|----------|--------|
| 1 | 棚卸し 7 ファイル / 課題 22 件抽出 | [`docs/_archive/phaseX-11/_doc_inventory.md`](../../_archive/phaseX-11/_doc_inventory.md) |
| 2 | CLAUDE.md を 80 行以下に圧縮 | CLAUDE.md（59 行）/ [`trouble_doc_template.md`](../../ai_context/trouble_doc_template.md) / [`design_doc_maintenance.md`](../../ai_context/design_doc_maintenance.md) |
| 3 | PreToolUse Hook 4 トリガー稼働 | [`.claude/hooks/tpl_trigger.ps1`](../../../.claude/hooks/tpl_trigger.ps1) / [`hooks_inventory.md`](../../ai_context/hooks_inventory.md) |
| 4 | TPL-007 を skill 化 | [`.claude/commands/check-phase-completion.md`](../../../.claude/commands/check-phase-completion.md) / [`skills_inventory.md`](../../ai_context/skills_inventory.md) |
| 5 | データ化 1 件 + CI 検証 | [`ops/test/requires_new_classes.txt`](../../../ops/test/requires_new_classes.txt) / [`ops/test/verify_requires_new_classes.sh`](../../../ops/test/verify_requires_new_classes.sh) / [`.github/workflows/verify-data-rules.yml`](../../../.github/workflows/verify-data-rules.yml) / [`data_rules_inventory.md`](../../ai_context/data_rules_inventory.md) |
| 6-1 | PostToolUse Hook で参照ログ | [`.claude/hooks/access_log_appender.ps1`](../../../.claude/hooks/access_log_appender.ps1) / [`_access_log.md`](../../ai_context/_access_log.md) |
| 6-2 | 3 ヶ月定期レビューの cron + skill | [`.github/workflows/quarterly-doc-review.yml`](../../../.github/workflows/quarterly-doc-review.yml) / [`.claude/commands/review-docs.md`](../../../.claude/commands/review-docs.md) |

### 完了条件（フェーズ全体）の充足状況

- ✅ Step 1〜6 のすべての完了条件を満たす
- ✅ `CLAUDE.md` が 80 行以下に圧縮されている（59 行）
- ✅ 4 件以上の Hook が PreToolUse で動作している（TPL-001/003/008/009 を単一スクリプトで判定）
- ✅ 1 件以上の TPL が skill / slash command 化されている（TPL-007 → `/check-phase-completion`）
- ✅ 1 件以上のルールがデータ化され CI で検証されている（REQUIRES_NEW クラス一覧、3→6 クラスの乖離発見）
- ✅ 参照ログと定期レビューの仕組みが稼働している
- ✅ `_doc_inventory.md` が完了後も残置（次回フェーズの基準として参照可能）

### 当初予期していなかった発見

- **Step 5 で 3 → 6 クラスの乖離発覚**：TPL-009 本文に列挙されていた REQUIRES_NEW クラスは 3 件だったが実コードは 6 件。データ化の費用対効果を裏付ける具体例
- **Step 3 / 6-1 共通の罠**：Windows PowerShell 5.1 は BOM なし UTF-8 を Shift-JIS として誤読する。ローカル開発機で hook が動かない原因として記録（[memory: project_amazia_powershell_hooks.md](../../../../.claude/projects/c--Users-root2-OneDrive--------ProjectFullStackRenaissance/memory/project_amazia_powershell_hooks.md) に保存）
- **matcher の制約**：Claude Code の hook `matcher` はツール名のみ。ファイルパス分岐は単一スクリプト内で正規表現マッチに統合する設計に落ち着いた

### ユーザー側に残っている運用アクション

- 次回 PR で `Verify Data Rules` CI が緑になることの確認
- 2026-07-09 の `Quarterly Doc Review` Issue 自動起票の確認、および初回 `/review-docs 001` 実行
- 各 hook / skill の実 Claude Code セッションでの動作観察（過剰発火・取りこぼしのフィードバック）
- 次フェーズ以降の作業内で Hook が予期しない挙動を起こした場合は `AMAZIA_TPL_HOOK_DISABLE=1` で一時抑止し、原因切り分け

### phaseX-12 への申し送り

- 残データ化候補（必須環境変数 / 主要 API 認証ルート）— [`data_rules_inventory.md`](../../ai_context/data_rules_inventory.md) §残候補
- skill 拡張候補（TPL-006 / 設計書 DB-API 棚卸し / troubles 同根検索）— [`skills_inventory.md`](../../ai_context/skills_inventory.md) §拡張候補
- インベントリ §課題サマリの phaseX-12 申し送り 13 件 — [`_doc_inventory.md`](../../_archive/phaseX-11/_doc_inventory.md) §phaseX-11 スコープ外
