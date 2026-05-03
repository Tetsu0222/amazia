## 問題
GitHub Actions から SSM send-command で `docker-compose up --build -d` を実行したところ、
コマンドが `InProgress` のまま長時間終わらず、その後に送った SSM コマンドがすべて `Pending（Delayed）` または `Failed` になった。
ブラウザでEC2のURLにアクセスしてもクルクルしたまま応答がなかった。

## 原因1：send-commandは「送るだけ」で完了を待たない
SSM send-command はコマンドをキューに投げるだけで、実行完了を待たずに次のステップへ進む。
そのため Actions のログ上は「成功」になるが、EC2上のコマンドはまだ実行中という状態になる。

## 原因2：docker-compose up --build がキューを詰まらせた
`docker-compose up --build` はDockerイメージのビルド（Javaのコンパイル、composerインストール等）を含むため、
完了まで数分〜十数分かかる。
この間、後続のSSMコマンド（Nginxの設定コピー・reload）がキューで待たされ、
結果としてdistのコピーとNginxのreloadが一切実行されなかった。

## 解決策
以下の2点を同時に対応した。

**① docker-compose up --build をやめる**
Actions側でビルド済みのzipをS3経由でEC2に送るため、EC2上でのビルドは不要。
`docker-compose up -d`（`--build`なし）に変更した。

**② SSMコマンドを分割し、完了をポーリングで待機する**
1つのsend-commandに全コマンドを詰め込むのをやめ、以下の2つに分割した。
- コマンド①：S3からzip取得 → unzip → docker-compose up -d
- コマンド②：Nginx設定コピー → distコピー → nginx reload

さらに各コマンドの完了をポーリングループで待機してから次のステップへ進むようにした。

```yaml
COMMAND_ID=$(aws ssm send-command ... --query "Command.CommandId" --output text)
for i in $(seq 1 24); do
  sleep 15
  STATUS=$(aws ssm get-command-invocation --command-id "$COMMAND_ID" ...)
  if [ "$STATUS" = "Success" ]; then break; fi
  if [ "$STATUS" = "Failed" ]; then exit 1; fi
done
```

## 結果
- docker-compose up が完了してからNginx配置が実行されるようになった
- Actions のログで各ステップの実行状況が確認できるようになった
- Console UIのdistが正しくEC2に配置された

## 補足
- SSM send-command のデフォルトタイムアウトは3600秒（1時間）
- SSMコマンドキューは詰まると後続がすべてブロックされるため、重いコマンドは分割するのが正しい設計
- EC2上でのDockerビルドはCI/CDのアンチパターン。ビルドはCIサーバー（Actions）で行い、成果物だけをデプロイするのが正しい
