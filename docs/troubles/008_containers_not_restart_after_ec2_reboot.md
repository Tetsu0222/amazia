## 問題
EC2 stop/start後、画面は表示される（Nginxは起動）がコンテンツがクルクルしたまま応答しない。
ブラウザのNetworkタブでは `GET /api/products` が 502 Bad Gateway を返していた。

## ログ

### Nginxエラーログ
```
[error] connect() failed (111: Connection refused) while connecting to upstream,
  request: "GET /api/products HTTP/1.1",
  upstream: "http://127.0.0.1:8000/api/products"
```

### docker ps
```
amazia-console   Exited (255)
amazia-core      Exited (255)
amazia-mysql     Exited (255)
```

EC2再起動後、Dockerデーモンは起動しているがコンテナがExited状態のまま。
Nginxはsystemdで自動起動するため応答するが、バックエンドコンテナが起動していないため502になる。

## 原因1：docker-compose.yml に restart ポリシーが未設定

`restart: unless-stopped` が設定されていなかったため、EC2が再起動してDockerデーモンが起動しても
コンテナは自動起動しなかった。

## 原因2：SSMコマンドキューの詰まり（副次的問題）

調査中にSSMコマンドがPendingのまま進まない状態になっていた。
`aws ssm list-commands` でInProgressが3件残留しており、前回デプロイのSSMコマンドが
完了しないままキューを塞いでいた。

```
597298e0  InProgress  14:26
33b54b26  InProgress  14:21
8e2126d1  InProgress  14:15
```

SSMはOnlineだがコマンドが届かないという状態はキュー詰まりのサイン。
EC2 stop/startでSSMエージェントが初期化されキューがクリアされる。

## 解決策

### 即時対応
EC2 stop/start（SSMキュークリア兼コンテナ再起動トリガー）後、手動で `docker-compose up -d` を実行。

### 恒久対応① docker-compose.yml に restart ポリシーを追加
```yaml
services:
  mysql:
    restart: unless-stopped
  amazia-core:
    restart: unless-stopped
  amazia-console:
    restart: unless-stopped
```

`unless-stopped` は「手動で `docker stop` した場合を除き、常に再起動する」ポリシー。
EC2再起動後にDockerデーモンが起動すると同時にコンテナも自動起動する。

### 恒久対応② systemdサービスの登録（2026-05-03 実施）
`/etc/systemd/system/amazia.service` を作成し `systemctl enable` で自動起動を登録済み。
`restart: unless-stopped` と二重の安全網になる。

```ini
[Unit]
Description=Amazia Docker Compose
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ssm-user/amazia
EnvironmentFile=-/home/ssm-user/amazia/.env.production
ExecStart=/usr/bin/docker compose up -d --remove-orphans
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=300

[Install]
WantedBy=multi-user.target
```

> 補足：`EnvironmentFile=-` の先頭ハイフンは「ファイルが無くてもエラーにしない」指定。
> `.env.production` を運用する前提だが、不在時もサービスは起動を試みる。
> 実運用では `docker compose` が `WorkingDirectory` 配下の `.env` を自動で読むため、
> `.env`（または `.env.production`）のどちらに値が入っていても compose 内の `${VAR}` に展開される。
> 詳細経緯は [029](029_compose_plugin_lost_and_users_schema_drift.md) を参照。

## 結果
- 全コンテナが正常起動（Up状態）
- curl localhost:8000、localhost:8080、localhost:80 すべて HTTP 200 を確認
- 次回デプロイ以降は `restart: unless-stopped` が適用され恒久解消

## 補足
- Nginxはsystemdで自動起動するため、コンテナが落ちていても画面は「表示される」。  
  「クルクル」=「Nginxは生きているがバックエンドが死んでいる」と読むこと。
- SSMキューが詰まる根本原因は別にあることが多い（003参照）。キュー詰まりに気づいたら  
  `aws ssm list-commands` でInProgress件数を確認し、EC2 stop/startで対処する。
- `restart: on-failure` ではなく `unless-stopped` を使う理由：  
  デプロイ時に `docker-compose down` で明示的に停止した場合に再起動しないようにするため。
