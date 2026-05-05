# 022: デプロイ中にSSMコマンドが Undeliverable で失敗

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
GitHub Actions `Deploy to EC2` ワークフロー → `EC2 - Docker停止・ECR pull・Docker起動` ステップ

## 症状

ワークフローログには ECR pull 失敗を示唆する以下のみ表示され、デプロイが exit 1 で停止した。

```
CommandId: f5873f05-fff6-4ef2-992b-4aeee54b188c
[1] Status: Failed
Error: Process completed with exit code 1.
```

`StandardErrorContent` は空で、ECR pull が EC2 上で実行された痕跡もなかった。

## 根本原因

`get-command-invocation` を確認したところ:

```json
{
  "Status": "Failed",
  "StatusDetails": "Undeliverable",
  "ResponseCode": -1,
  "StandardOutputContent": "",
  "StandardErrorContent": ""
}
```

**SSM Agent が `ConnectionLost` 状態で、コマンドが EC2 に配信されていなかった**。
EC2 自体は `running` だが、`describe-instance-information` の `PingStatus` が `ConnectionLost` のまま長時間経過していた（既知不具合 001 と同根の SSM Agent ハング）。

ECR pull の失敗ではなく、**コマンドが届かないまま「Failed」だけが返った** ことが本質。
`StandardErrorContent` が空のため、ワークフローログだけでは原因特定ができなかった。

## なぜ CI で検知できなかったか

`deploy.yml` の `SSMキュー健全性確認・自動リカバリ` ステップは
**「15分以上 InProgress のコマンドが残っている」場合のみ** stop/start を実行する仕組みで、
`PingStatus = ConnectionLost`（キューに何もないが Agent 死亡）のケースを検知できなかった。

結果として、SSM 不調のままデプロイ本体が走り、配信不能で失敗していた。

## 修正内容

### 1. EC2 stop/start で SSM を即時復旧
001 と同じ手順:

```bash
aws ec2 stop-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2
aws ec2 wait instance-stopped --instance-ids i-024a0748df78fc93e --region ap-southeast-2
aws ec2 start-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2
```

stop/start により Agent が再初期化され `PingStatus=Online` に復帰。

### 2. `deploy.yml` に PingStatus チェックを追加
`SSMキュー健全性確認・自動リカバリ` ステップで、
`describe-instance-information` の `PingStatus` を取得し、
`Online` でない場合も同じ stop/start リカバリ経路に乗せるよう変更。

```yaml
PING=$(aws ssm describe-instance-information \
  --region ap-southeast-2 \
  --query "InstanceInformationList[?InstanceId=='${{ secrets.EC2_INSTANCE_ID }}'].PingStatus" \
  --output text)

if [ "$PING" != "Online" ]; then
  NEEDS_RECOVERY=true
fi
```

これにより、デプロイ実行前に SSM 接続不調を自動検知・リカバリできる。

## 再発防止

| 観点 | 対策 |
|------|------|
| 事前検知 | デプロイ前に `PingStatus=Online` を必須条件として確認 |
| 自動リカバリ | `ConnectionLost` を検知したら stop/start を自動実行 |
| ログ可視化 | Undeliverable 時は `StandardErrorContent` が空になるため、`StatusDetails` と `PingStatus` をワークフローログに残す |
| 関連 | 001（SSM ConnectionLost 全般）, 003（キュー詰まり）。本件は両者の隙間ケース |
