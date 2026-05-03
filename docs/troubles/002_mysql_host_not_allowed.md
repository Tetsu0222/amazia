## 問題
docker-compose up でMySQLを起動したあと、amazia-core から接続しようとすると以下のエラーが出た。

```
Host 'xxx' is not allowed to connect to this MySQL server
```

## 原因
MySQLのユーザー `amazia` が `amazia@localhost` としか登録されていなかった。
Dockerネットワーク内では別コンテナからのアクセスはlocalhostではなくコンテナのIPになるため、接続が拒否された。

docker-compose.yml で `MYSQL_USER` を指定した場合、MySQLは `ユーザー@%`（どこからでも接続可）ではなく
`ユーザー@localhost` として作成することがある。
これは既存のvolumeに古い設定が残っていた場合に特に起きやすい。

## 解決策
volumeごと削除して再作成する。

```bash
docker-compose down -v
docker-compose up -d
```

`-v` オプションで named volume（mysql_data）も削除されるため、MySQLが初期化し直され
`amazia@%` として正しく作成される。

## 結果
削除・再作成後、amazia-core からの接続が成功した。

## 補足
- 本番データがある場合は `-v` で消してはいけない。今回は開発環境のため問題なし。
- 既存volumeが残っていると `MYSQL_USER` などの環境変数が再実行されないため、設定変更が反映されないことがある。
