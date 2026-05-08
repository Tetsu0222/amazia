# phaseX-9 Step 5 — ユーザー側手動作業手順

## 概要
- 親実装計画: [phaseX-9_implementation_plan.md](phaseX-9_implementation_plan.md)
- 対象ワークフロー: [.github/workflows/weekly-test-random-order.yml](../../.github/workflows/weekly-test-random-order.yml)
- 作成日: 2026-05-09
- 目的: Step 5 完了条件のうち、Claude では実行できない GitHub UI / API 操作をユーザー側で実施する手順を整理する。
- 想定所要時間: 15〜20 分

## 前提
- weekly-test-random-order.yml は新規作成済み（コミット未）
- ローカル random 5 回 PASS 済み（Step 4 で 180/188/212/218/188s, 平均 197s）
- GitHub Actions の `permissions: issues: write` を YAML 内で明示済み（Issue 自動起票に必要）

---

## 手順 1: 事前準備（ラベル作成）

GitHub リポジトリ設定でラベル 2 個を事前作成。`gh` CLI またはリポジトリ Settings → Labels から作成する。

### 1-A: gh CLI で作成（推奨）

```powershell
# Amazia リポジトリのルートで実行（要 gh auth login 済み）
gh label create flaky-test --color "fbca04" --description "ローカルでは PASS、CI で flaky な順序依存バグ"
gh label create random-order --color "0366d6" --description "weekly-test-random-order ワークフロー由来"
```

### 1-B: GitHub UI で作成

1. リポジトリの **Issues** タブ → **Labels**（右上）
2. **New label** で以下 2 個を作成：
   - `flaky-test`（色: 黄系 `#fbca04`）
   - `random-order`（色: 青系 `#0366d6`）

### 1-C: 確認

```powershell
gh label list | Select-String -Pattern "flaky-test|random-order"
```

---

## 手順 2: ワークフロー追加 PR の作成・マージ

### 2-A: ブランチ作成・コミット

```powershell
git checkout -b phaseX-9/step5-weekly-random-order
git add .github/workflows/weekly-test-random-order.yml
git add docs/implementation/phaseX-9_step5_user_actions.md
git commit -m "phaseX-9 Step 5: weekly random-order test workflow"
git push -u origin phaseX-9/step5-weekly-random-order
```

### 2-B: PR 作成

```powershell
gh pr create --title "phaseX-9 Step 5: weekly random-order test workflow" --body "## Summary
- 週次 cron (毎週月曜 03:00 JST) で amazia-core を random 順序で実行
- 失敗時は flaky-test / random-order ラベル付き Issue を自動起票
- 同タイトル Open Issue があればコメント追加（重複起票防止）

## Test plan
- [ ] mainマージ後 workflow_dispatch で手動実行 → BUILD SUCCESS 確認
- [ ] 別ブランチで故意失敗テストを仕込み Issue 自動起票を確認
- [ ] 重複防止（既存 Open Issue 状態でコメント追加のみ）を確認

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

### 2-C: PR レビュー・マージ

通常の PR フローでマージ。CI が `weekly-test-random-order.yml` 自体には反応しない（cron / workflow_dispatch のみ）ので、deploy.yml が走るのみ。

---

## 手順 3: workflow_dispatch で手動実行 → PASS 確認

main にマージ後、手動トリガーでワークフローを 1 回実行する。

### 3-A: gh CLI で実行（推奨）

```powershell
gh workflow run "Weekly Test Random Order"
# 実行 ID を確認
gh run list --workflow="Weekly Test Random Order" --limit 5
# ログを追跡（実行 ID は上記コマンドで確認したものに置換）
gh run watch <run-id>
```

### 3-B: GitHub UI で実行

1. リポジトリの **Actions** タブ → 左サイドバーから **Weekly Test Random Order**
2. 右上 **Run workflow** → ブランチ `main` を選択 → **Run workflow**
3. 実行中のジョブ名をクリックして進捗確認

### 3-C: 確認ポイント

- BUILD SUCCESS（緑チェック）になること
- 所要時間が `timeout-minutes: 20`（1200s）に余裕があること
  - ローカル平均 197s × CI Linux 1.5〜2 倍見込み = 300〜400s 想定
- Issue が自動起票**されない**こと（PASS 時は起票しないのが正しい挙動）

---

## 手順 4: 故意失敗テストで Issue 自動起票を確認

phaseX-6 の故意失敗デプロイ確認と同じ運用。**main にマージしない別ブランチ**で実施する。

### 4-A: 故意失敗の仕込み

```powershell
git checkout -b phaseX-9/step5-fault-injection-test main
```

`amazia-core/src/test/java/com/example/faultinjection/FaultInjectionLogRepositoryTest.java` の任意のテストメソッド冒頭に、一時的に `org.junit.jupiter.api.Assertions.fail("phaseX-9 Step 5 故意失敗テスト");` を仕込む。

例：
```java
@Test
void dev_および_staging_は_保存できる() {
    org.junit.jupiter.api.Assertions.fail("phaseX-9 Step 5 故意失敗テスト");
    // ... 既存コード ...
}
```

```powershell
git add amazia-core/src/test/java/com/example/faultinjection/FaultInjectionLogRepositoryTest.java
git commit -m "phaseX-9 Step 5: temporary fault injection (DO NOT MERGE)"
git push -u origin phaseX-9/step5-fault-injection-test
```

### 4-B: 手動実行で Issue 起票を確認

```powershell
gh workflow run "Weekly Test Random Order" --ref phaseX-9/step5-fault-injection-test
gh run list --workflow="Weekly Test Random Order" --limit 3
```

実行終了後（FAILURE になる）：

```powershell
# 起票された Issue を確認
gh issue list --label "flaky-test,random-order" --limit 5
```

**確認ポイント**:
- Issue が新規作成されている
- タイトル: `[Flaky] Weekly random-order test failed: <runId>`
- ラベル: `flaky-test`, `random-order` が付与されている
- Body に Run URL / Commit SHA / 調査手順が含まれている

### 4-C: 重複起票防止を確認

同じブランチで再度手動実行：

```powershell
gh workflow run "Weekly Test Random Order" --ref phaseX-9/step5-fault-injection-test
```

**確認ポイント**:
- 新規 Issue が**作成されない**こと
- 既存 Issue に「再発: <Run URL>」のコメントが追加されていること

```powershell
# 既存 Issue のコメントを確認
gh issue view <issue-number> --comments
```

### 4-D: 後始末（重要）

```powershell
# 仕込んだ故意失敗を revert
git checkout main
git branch -D phaseX-9/step5-fault-injection-test
git push origin --delete phaseX-9/step5-fault-injection-test
# 起票された Issue は手動で close（動作確認用なのでクローズコメントを残す）
gh issue close <issue-number> --comment "phaseX-9 Step 5 動作確認完了。故意失敗ブランチは削除済み。"
```

---

## Step 5 完了条件チェック

すべて満たしたら親実装計画書 [phaseX-9_implementation_plan.md](phaseX-9_implementation_plan.md) Step 5 完了条件にチェックを入れる。

- [ ] `weekly-test-random-order.yml` が main にマージされている（手順 2）
- [ ] `workflow_dispatch` 手動実行で PASS 確認済み（手順 3）
- [ ] 別ブランチでの故意失敗テストで Issue 自動起票・重複防止が動作確認済み（手順 4）
- [ ] ラベル `flaky-test` / `random-order` がリポジトリに作成されている（手順 1）

---

## 補足

### 週次 cron スケジュール

`'0 18 * * 0'` = 毎週日曜 18:00 UTC = **毎週月曜 03:00 JST**

理由:
- 開発が止まっている時間帯で flaky 検知 → 月曜朝に Issue を確認できる
- GitHub Actions の cron は遅延（数分〜数十分）あり得るが、週次なので無視できる

### 月次 GitHub Actions 消費量見込み

- 1 回あたり 5〜10 分（CI Linux ランナー）× 月 4〜5 回（cron） + 手動実行 数回
- 月 30〜60 分程度。GitHub Actions 無料枠（月 2,000 分）に対して 3% 未満
- 計画書の試算「月 25 分以内」よりはやや多いが、Step 4 で 12 クラスに `@Sql` を追加した分の増分内
