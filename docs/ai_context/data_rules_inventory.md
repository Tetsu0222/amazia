# データ化ルール一覧（phaseX-11 Step 5）

## このファイルの位置付け

文章に埋め込まれていたルールのうち、検証可能なものをデータファイルとして分離し、
CI で実コードとの差分を機械的に検出する仕組みを一元管理する。

phaseX-11 Step 5 では「ドキュメント駆動を Hook / skill / **データ化** の 3 レイヤーに分散する」
というメタフェーズの一翼として、検証可能な 1 件をデータ化した。

---

## 全体像

| 項目 | 値 |
|------|-----|
| データファイル配置 | `ops/<カテゴリ>/<name>.{txt,yml}` |
| 検証スクリプト | `ops/<カテゴリ>/verify_<name>.sh`（POSIX bash） |
| CI 組み込み | `.github/workflows/verify-data-rules.yml`（軽量、関連ファイル変更時のみ走行） |
| 前例 | `ops/healthcheck/required_tables.txt`（phaseX-6 / 044 起因） |

---

## 登録済みデータルール

### 1. REQUIRES_NEW クラス一覧

| 項目 | 値 |
|------|-----|
| データファイル | [`ops/test/requires_new_classes.txt`](../../ops/test/requires_new_classes.txt) |
| 検証スクリプト | [`ops/test/verify_requires_new_classes.sh`](../../ops/test/verify_requires_new_classes.sh) |
| CI ジョブ | `.github/workflows/verify-data-rules.yml` の `requires-new-classes` |
| 元の置き場所 | TPL-009 本文に文章で 3 クラス列挙（実態との乖離あり） |
| データ化前のクラス数 | 3（`FaultInjectionLogger` / `BatchAlertNotifier` / `BatchExecutionRecorder`） |
| データ化後のクラス数 | 6（上記 + `DigestDispatchService` / `DeliveryStatusTransitionService` / `TriggerBatchManualService`） |
| 関連 TPL | TPL-009（件数アサーションを伴うテスト追加・改修時） |
| 関連 AP | AP-009（テスト分離不足 + 単発 PR で類似クラス見落とし） |

**検証ロジック：**

- 期待値：`requires_new_classes.txt` のコメント・空行を除いたクラス名一覧（ソート・重複除去）
- 実測値：`amazia-core/src/main/**/*.java` を grep で `Propagation.REQUIRES_NEW` 検索 → ファイル名から `.java` 拡張子を取り単純クラス名にする（ソート・重複除去）
- 不一致なら exit 1。出力は「期待にあって実測にない」「実測にあって期待にない」を別記

**直接の動機（データ化で見つかった乖離）：**

phaseX-11 Step 5 着手時、TPL-009 本文には **3 クラス** しか列挙されていなかったが、
実コードの grep では **6 クラス** が REQUIRES_NEW を使用していた。
これは AP-009「テスト分離不足 + 単発 PR で類似クラス見落とし」が
ドキュメント側でも発生していた具体例であり、データ化の費用対効果を裏付ける。

---

## 設計判断

### なぜ「全部のルール」ではなく一部からデータ化するか

設計書 §判断基準：
- **検証コードが書ける** ルール（CI で diff 検出できる）はデータ化候補
- **文脈・判断が必要なルール**（AP の本文等）は文章のまま残す

REQUIRES_NEW は「クラス名 = 検証可能な識別子」なので grep + diff で機械化が容易。
一方、AP の本文（落とし穴の説明）は文脈を要するためデータ化に向かない。

### CI ジョブを weekly ではなく PR / push トリガーにした理由

`weekly-test-random-order.yml` に同居させる案もあったが、以下の理由で別ファイルに：

1. weekly は「flaky テストの早期検知」が目的。データ化検証は flaky でなく即座に確定的
2. PR 中で REQUIRES_NEW を新規付与した場合、weekly まで気づけないと TPL-009 漏れを 1 週間以上抱える
3. `paths` フィルタで関連ファイル変更時のみ走行 → 毎 PR 走らせてもコスト増は無視できる

### 検証スクリプトを Bash にした理由

Windows ローカル開発と Ubuntu CI の両方で動かすため、PowerShell ではなく POSIX bash を採用。
`required_tables.txt` を CD で読む既存スクリプトも Bash 系で揃っており一貫性がある。

---

## 動作確認手順

ローカルで：

```bash
# ベースライン（一致）
bash ops/test/verify_requires_new_classes.sh

# デバッグ表示（期待値・実測値の双方を見る）
AMAZIA_VERIFY_DEBUG=1 bash ops/test/verify_requires_new_classes.sh
```

故意失敗テスト（手元で確認済 — phaseX-11 Step 5 完了条件）：

```bash
# 1) txt から 1 行削除して、新規追加検知が exit 1 で発火することを確認
sed -i '/^DigestDispatchService$/d' ops/test/requires_new_classes.txt
bash ops/test/verify_requires_new_classes.sh   # exit 1, "+ DigestDispatchService"

# 2) 復元後、txt に幽霊クラスを追加して廃止検知が exit 1 で発火することを確認
echo "GhostService" >> ops/test/requires_new_classes.txt
bash ops/test/verify_requires_new_classes.sh   # exit 1, "- GhostService"

# 復元
git checkout -- ops/test/requires_new_classes.txt
```

---

## phaseX-12 以降への申し送り（残候補）

設計書 Step 5 §対象候補から残された 2 件：

| 候補 | 想定先 | データ化の難所 |
|------|--------|--------------|
| 必須環境変数の一覧 | `ops/config/required_env_vars.yml` | docker-compose.yml と phpunit.xml の両方を YAML パースで突合する必要あり。3 ソース合流のため設計に時間がかかる |
| 主要 API パスの認証ルート | `ops/api/auth_routing.yml` | Spring Boot のアノテーション（`@RequestMapping` / `@PreAuthorize`）を AST レベルで抽出する必要あり。grep ベースだと取りこぼしリスク |

phaseX-11 はメタフェーズなので 2〜3 週間で締める方針。残候補は phaseX-12 で再評価する。

---

## 関連ドキュメント

- [hooks_inventory.md](hooks_inventory.md) — Hook（Step 3）。ファイル編集 = TPL の機械的トリガー
- [skills_inventory.md](skills_inventory.md) — skill（Step 4）。slash command による「呼び出し」化
- [prompt_templates.md](prompt_templates.md) — TPL-009 本文（データ化対象を txt 参照に置換済み）
- [ai_collaboration_antipatterns.md](ai_collaboration_antipatterns.md) — AP-009（本データ化が予防する落とし穴）
- [../../ops/healthcheck/required_tables.txt](../../ops/healthcheck/required_tables.txt) — データ化前例（phaseX-6 / 044 起因）
- [../../.github/workflows/verify-data-rules.yml](../../.github/workflows/verify-data-rules.yml) — CI 組み込み
- [../design/phaseX/phaseX-11_doc_driven_layering.md](../design/phaseX/phaseX-11_doc_driven_layering.md) — Step 5 の設計書
