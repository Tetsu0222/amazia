## 問題
GitHub Actions から SSM send-command で `docker-compose up --build -d` を実行したところ、
コマンドが `InProgress` のまま長時間終わらず、その後に送った SSM コマンドがすべて `Pending（Delayed）` または `Failed` になった。
ブラウザでEC2のURLにアクセスしてもクルクルしたまま応答がなかった。

## 原因
SSM send-command はコマンドを1つのシェルセッションとして実行する。
`docker-compose up --build` はDockerイメージのビルド（Javaのコンパイル、composerインストール等）を含むため、
完了まで数分〜十数分かかる場合がある。

この間、SSMのコマンドキューが詰まり、後続のコマンド（Nginxの設定コピーやreload）が実行されなかった。
結果として、Nginxはファイルなしの状態で起動したままになった。

## 解決策（暫定）
詰まったコマンドを `cancel-command` でキャンセルし、EC2を reboot して状態をリセットした。

```bash
aws ssm cancel-command --command-id <CommandId> --region ap-southeast-2
aws ec2 reboot-instances --instance-ids <InstanceId> --region ap-southeast-2
```

## 解決策（恒久）
`docker-compose up --build` を deploy.yml から切り離す。
具体的には以下のいずれかを採用する：

1. **ECRを使う**: GitHub Actions でDockerイメージをビルド → ECRにpush → EC2では `docker pull` するだけにする
2. **--build を外す**: zipにビルド済みJARを含めてEC2に送り、EC2では `docker-compose up -d`（ビルドなし）で起動する

## 結果
現在対応中。

## 補足
- SSM send-command のデフォルトタイムアウトは3600秒（1時間）。長いコマンドを実行する場合は注意。
- SSMコマンドキューは1インスタンスあたり同時実行数に制限があるため、詰まると後続がすべてブロックされる。
