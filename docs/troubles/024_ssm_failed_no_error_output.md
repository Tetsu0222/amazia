# 024: SSM デプロイ Failed 時にエラー内容が空でログから原因特定不能

## ステータス
✅ 解決済（2026-05-06）— phaseX-3 HTTPS 化のデプロイで本機構（StatusDetails / StandardOutputContent / StandardErrorContent の3点出力）が稼働。026 のカナリア方式と組み合わせて Undeliverable 含む失敗パターンが正しくログから判別できることを実環境で確認済み

## 発症箇所
GitHub Actions `Deploy to EC2` ワークフロー
`EC2 - Docker停止・ECR pull・Docker起動` ステップ
（[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) L186 付近）

## 症状
SSM `send-command` のステータスが `Failed` で返ってきてジョブが失敗するが、
ログの末尾が以下のようになり **エラー出力が空** で原因特定ができない。

```
CommandId: 271a9be3-755f-4085-bb1e-935049a2c1e4
[1] Status: Failed

Error: Process completed with exit code 1.
```

通常 `[1] Status: Failed` の直後に `StandardErrorContent` の内容（docker pull のエラー等）が
出るはずだが、空行のみ。023 の `--remove-orphans` 対応後にも本事象が再発し、
その時の根本原因が分からないまま終わってしまった。

## 根本原因
失敗時のエラー収集ロジックが `StandardErrorContent` のみを取得していた。

```yaml
if [ "$STATUS" = "Failed" ] || [ "$STATUS" = "TimedOut" ]; then
  aws ssm get-command-invocation \
    --command-id "$COMMAND_ID" \
    --instance-id ${{ secrets.EC2_INSTANCE_ID }} \
    --region ap-southeast-2 \
    --query "StandardErrorContent" --output text  ← これだけ
  exit 1
fi
```

しかし以下のいずれかに該当すると `StandardErrorContent` が空になる：

| ケース | stderr が空になる理由 |
|--------|--------------------|
| `docker pull` / `docker-compose` 系の失敗 | エラーメッセージを stdout に出すケースが多い（特に `docker pull` の `repository not found` 系） |
| `set -e` 相当の即時 exit | コマンド実行自体が走らずに終了するため stdout/stderr に何も出ない |
| `Undeliverable` ステータス（022 系） | SSM Agent にコマンドが届く前に失敗するため、当然出力なし |
| `cd` 失敗で後続が走らない | エラーは表示されるが軽微な1行のみ・別チャンネル |

今回（2026-05-05）のケースでは `[1] Status: Failed` が **15秒以内** に確定しており、
SSM コマンドが受領後に即失敗 → `Undeliverable` か `cd /home/ssm-user/amazia` の失敗が疑わしいが、
**stderr のみ取得していたため確証が取れない**。

## なぜ CI で検知できなかったか
- 「失敗時のログ取得が不十分」という診断系の不具合は、デプロイが通常動作している間は表面化しない
- ローカル CI では SSM を経由せず docker-compose を直接叩くため、SSM 起因の Failed パターンが再現しない
- `Failed` 時のリカバリは人手で `aws ssm get-command-invocation` を打つ前提だったが、
  時間が経つと invocation 自体が消えて事後調査も困難になる

## 修正内容
[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) の Failed 時ハンドラで
`StatusDetails`・`StandardOutputContent`・`StandardErrorContent` を **すべて** 出力するように変更。
`Cancelled` ステータスも明示的にハンドリング対象に追加。

```diff
- if [ "$STATUS" = "Failed" ] || [ "$STATUS" = "TimedOut" ]; then
+ if [ "$STATUS" = "Failed" ] || [ "$STATUS" = "TimedOut" ] || [ "$STATUS" = "Cancelled" ]; then
+   echo "===== StatusDetails ====="
+   aws ssm get-command-invocation ... --query "StatusDetails" --output text
+   echo "===== StandardOutputContent ====="
+   aws ssm get-command-invocation ... --query "StandardOutputContent" --output text
+   echo "===== StandardErrorContent ====="
    aws ssm get-command-invocation ... --query "StandardErrorContent" --output text
    exit 1
  fi
```

`EC2 - Nginx設定とフロントエンド配置` ステップにも同じ問題があったため同時に修正。

### 即時復旧手順（手動）
GitHub Actions のジョブログから `CommandId` を控え、ローカルから以下を実行する。

```bash
aws ssm get-command-invocation \
  --command-id <COMMAND_ID> \
  --instance-id <EC2_INSTANCE_ID> \
  --region ap-southeast-2
```

これで `Status` `StatusDetails` `StandardOutputContent` `StandardErrorContent` がすべて返る。
ただし invocation には保持期限があるため、**Failed 直後に取得すること**。

## 再発防止
| 観点 | 対策 |
|------|------|
| Failed 時のログ収集 | stdout / stderr / StatusDetails を **必ず3点セット** で出力する |
| ステータス分岐 | `Failed` `TimedOut` に加えて `Cancelled` も拾う |
| SSM 経路の他ステップ | 同パターンを使う全ステップで揃える（今回は2箇所修正） |
| 事後調査の可能性確保 | ジョブログ自体に出力させる方針（invocation 消失後でも辿れる） |

## 次のアクション
本修正後の次回デプロイで **再度** Failed が出た場合、新しいログで根本原因を確定し、
本ドキュメントに「実際の原因」を追記する。023 の系譜（孤児コンテナ）か、
022 の系譜（Undeliverable）か、別系統かを判別する。
