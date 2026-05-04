# 015: amazia-market の ECR pull 失敗によるデプロイエラー

## ステータス
✅ 解決済（2026-05-04）

## 発症箇所
GitHub Actions `Deploy to EC2` ワークフロー → EC2 - Docker停止・ECR pull・Docker起動 ステップ

## 症状

```
Image 741011674945.dkr.ecr.ap-southeast-2.amazonaws.com/amazia-market:latest Error
repository 741011674945.dkr.ecr.ap-southeast-2.amazonaws.com/amazia-market not found:
name unknown: The repository with name 'amazia-market' does not exist in the registry with id '741011674945'
Error response from daemon: repository ... not found
failed to run commands: exit status 1
```

EC2 上での `docker-compose up` が失敗し、デプロイが完了しない。

## 根本原因

`docker-compose.yml` に `amazia-market` サービスが存在しない ECR イメージを参照する定義が残っていた。

### 経緯

| コミット | 内容 |
|---|---|
| `0c409e24` | amazia-core・amazia-console を ECR 経由デプロイに移行。この時点で amazia-market は docker-compose.yml に存在せず対象外 |
| `d8228ee1` | amazia-market を docker-compose.yml に追加 → ECR pull 失敗が判明したため即削除（正しい対応） |
| 手動編集（未コミット） | amazia-market サービスに `volumes` マウントを追加する目的で再追加。ECR リポジトリが存在しないままのため pull が再び失敗 |

### 手動編集の意図

Docker Compose 環境向けの nginx 設定ファイル（`nginx/amazia-market.docker.conf`）をコンテナにマウントするため、amazia-market サービスを復活させた。しかし ECR に `amazia-market` リポジトリは存在しないため、イメージの pull 自体が失敗する。

## amazia-market の正しい配信構成

amazia-market は Vite でビルドした静的ファイルを EC2 上の Nginx が直接配信する構成であり、Docker コンテナ管理の対象外。

| 項目 | 内容 |
|---|---|
| ビルド | GitHub Actions 上で `npm run build` → `dist/` 生成 |
| 転送 | `dist/` を含む zip を S3 経由で EC2 に展開 |
| 配信 | EC2 の Nginx が `/var/www/amazia-market/` を直接 serve |
| ECR | 使用しない（リポジトリ未作成） |

## 修正内容

`docker-compose.yml` から `amazia-market` サービス定義を削除。

```yaml
# 削除したブロック
amazia-market:
  image: 741011674945.dkr.ecr.ap-southeast-2.amazonaws.com/amazia-market:latest
  container_name: amazia-market
  restart: unless-stopped
  ports:
    - "5173:80"
  volumes:
    - ./nginx/amazia-market.docker.conf:/etc/nginx/conf.d/default.conf:ro
  depends_on:
    - amazia-core
```

## 再発防止

| 観点 | 対策 |
|------|------|
| 方針の明確化 | amazia-market は Nginx 静的配信であり docker-compose.yml に定義しない |
| 編集時の確認 | docker-compose.yml を編集する際は ECR リポジトリの存在を事前確認する |
