## 問題
EC2インスタンスを reboot したあと、SSMエージェントのステータスが `ConnectionLost` のまま回復しなかった。
SSM send-command を送っても届かず、SSH鍵もないためインスタンスに入る手段がなかった。

## 原因（当初記録）
`reboot` はOSの再起動であり、同じ物理ホスト上で再起動される。
SSMエージェントのプロセスが異常終了したままの場合、rebootしても状態が持ち越されることがある。

## 根本原因（ログ調査により判明）
`/var/log/amazon/ssm/amazon-ssm-agent.log` を調査した結果、以下のエラーが繰り返し記録されていた。

```
ERROR messaging worker encountered error: ipc messaging received timeout signal
ERROR document state during messaging worker error: InProgress
ERROR unable to wait pty: signal: killed
```

**原因は `docker-compose up --build` による長時間SSMコマンド実行**だった。

SSMエージェントは内部IPCソケットでワーカープロセスと通信するが、Javaのコンパイルを含む `--build` が数分〜十数分かかる間にIPCタイムアウトが発生し続けた。さらに `signal: killed` はメモリ逼迫によるOSのプロセス強制終了の痕跡であり、これが累積してSSMエージェント自体が不安定になりConnectionLostに至った。

「意図しないreboot」ではなく、CI/CDの設計ミスによる累積的な破壊が原因だった。根本的な対策は `docker-compose up --build` をCI側（GitHub Actions）でのビルドに置き換えること（→ 003_ssm_command_queue_stuck.md 参照）。

## 解決策
stop/start はインスタンスが別の物理ホストに移動するため、SSMエージェントが初期化し直される。AWS CLIで完全自動化できる。

```bash
aws ec2 stop-instances --instance-ids <instance-id> --region ap-southeast-2
aws ec2 wait instance-stopped --instance-ids <instance-id> --region ap-southeast-2
aws ec2 start-instances --instance-ids <instance-id> --region ap-southeast-2
aws ec2 wait instance-running --instance-ids <instance-id> --region ap-southeast-2

# 新しいIPを確認
aws ec2 describe-instances --instance-ids <instance-id> --region ap-southeast-2 \
  --query "Reservations[0].Instances[0].PublicIpAddress" --output text

# SSMがOnlineになるまで待機
for i in $(seq 1 12); do
  STATUS=$(aws ssm describe-instance-information \
    --region ap-southeast-2 \
    --query "InstanceInformationList[?InstanceId=='<instance-id>'].PingStatus" \
    --output text)
  echo "[$i] SSM Status: $STATUS"
  if [ "$STATUS" = "Online" ]; then break; fi
  sleep 10
done
```

## 結果
stop/start 後、SSMエージェントが即座に `Online` に回復した（今回は1回目のポーリングで確認）。

## 恒久対策（2026-05-03 実施）
`--build` 除去だけでは不十分だった。`docker-compose up -d` でも `build:` ディレクティブが残っている限りソース変更時にイメージを再ビルドするため、同様のIPC破壊が再発した。

根本解決として **DockerイメージのビルドをGitHub Actions側に移行し、ECR（AWS Elastic Container Registry）経由でEC2にデプロイする構成に変更**した。

```
Actions → docker build → ECR push
EC2     → docker pull → docker-compose up -d（ビルド一切なし）
```

EC2上での重い処理がゼロになり、SSMが不安定になる要因が完全に排除された。

## 補足
- SSH（ポート22）を開けなくてもSSMだけで運用できる。むしろ開けない方がセキュリティ的に正しい。
- stop/startによるIP変動はElastic IPの割り当てで恒久解決済み（2026-05-03）。
