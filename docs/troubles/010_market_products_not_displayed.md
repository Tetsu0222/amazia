# 010: Amazia Market に商品が表示されない

## ステータス
✅ 解決済（2026-05-04、追加修正）

## 発症箇所
`http://<host>:5173/`（Amazia Market 商品一覧）→ 「現在表示できる商品がありません。」

## 症状
- Market にアクセスしても商品が1件も表示されない
- ブラウザの Network タブで `/api/products/market` が 502 Bad Gateway または接続エラー
- amazia-core・amazia-console は正常稼働中

## 根本原因

`docker-compose.yml` に `amazia-market` サービスが定義されていない。

フェーズ10 では Market 向け SKU 集約 API（`/api/products/market`）が amazia-core に追加され、  
Market 側の React アプリも `getMarketProducts()` へ切り替え済みだが、  
**Market コンテナ自体が docker-compose に存在しないため本番環境では起動不可能**。

```yaml
# docker-compose.yml（修正前）
services:
  mysql:    ...
  amazia-core:    ...
  amazia-console: ...
  # amazia-market が存在しない ← ここが欠落
```

### Market が依存する amazia-core エンドポイント
| エンドポイント | 用途 |
|---|---|
| `GET /api/products/market` | 商品一覧（SKU集約） |
| `GET /api/products/{id}/market` | 商品詳細（SKU集約） |

## なぜ CI で検知できなかったか
- amazia-market は Vite（フロントエンド）のため単体テスト未整備
- `docker-compose up` ローカル検証フローが存在しなかった

## 修正内容

### docker-compose.yml に amazia-market サービスを追加
```yaml
amazia-market:
  image: 741011674945.dkr.ecr.ap-southeast-2.amazonaws.com/amazia-market:latest
  container_name: amazia-market
  restart: unless-stopped
  ports:
    - "5173:80"
  depends_on:
    - amazia-core
```

※ Vite のプロダクションビルドは nginx で配信するため、コンテナは nginx ベースで 80 番ポートを公開。  
　ホスト側は既存ポートと競合しない `5173` にマッピング。

## 再発防止

| 観点 | 対策 |
|------|------|
| サービス追加時の漏れ | 新サービスを実装したら docker-compose.yml への追加を PR チェックリストに含める |
| デプロイ後確認 | amazia-market の HTTP 200 レスポンスをデプロイ後ヘルスチェックに追加 |

---

## 追加調査（2026-05-04）：上記修正後も症状継続

### 症状
docker-compose.yml に amazia-market を追加した後も、商品一覧が表示されないまま。

### 追加調査で判明した原因

docker-compose.yml への追加だけでは不十分だった。**nginx のプロキシ先が Docker ネットワーク上で到達不能なアドレスになっている**ことが本質的な原因。

#### 原因1: nginx の proxy_pass が Docker 環境で機能しない

`nginx/amazia.conf`（EC2 本番用）は `/api/` を `http://127.0.0.1:8080` にプロキシしている。

```nginx
# nginx/amazia.conf（現状）
location /api/ {
    proxy_pass http://127.0.0.1:8080/api/;  # ← EC2本番では動くが…
}
```

本番 EC2 では amazia-core が同一ホスト上で動くため `127.0.0.1` で到達できる。  
しかし **Docker Compose 環境では各サービスが別コンテナに分離されており、amazia-market コンテナ内の `127.0.0.1` は amazia-core ではなくコンテナ自身を指す。**  
結果、`/api/` へのリクエストは amazia-core に届かず 502 Bad Gateway になる。

Docker 環境では `http://amazia-core:8080` （サービス名）で参照する必要がある。

#### 原因2: docker-compose.yml に nginx 設定の注入がない

```yaml
# docker-compose.yml（現状）
amazia-market:
  image: ...
  ports:
    - "5173:80"
  depends_on:
    - amazia-core
  # ← nginx 設定のボリュームマウントがないため、コンテナ内の nginx は
  #    127.0.0.1:8080 を参照するデフォルト設定のまま起動する
```

amazia-market のプロダクションイメージは内部に nginx を持つが、  
そのデフォルト設定が EC2 本番用の `nginx/amazia.conf` と同一（`127.0.0.1:8080`）になっているため、  
Docker Compose 起動時にも同じ誤ったプロキシ先を参照する。

### 環境ごとの差異まとめ

| 環境 | amazia-core の参照先 | 動作 |
|------|----------------------|------|
| EC2 本番 | `http://127.0.0.1:8080` | ✅ 同一ホストなので到達可能 |
| Docker Compose | `http://127.0.0.1:8080` (現状) | ❌ コンテナ内ループバック、到達不可 |
| Docker Compose | `http://amazia-core:8080` (修正後) | ✅ Docker 内部 DNS で解決可能 |

### 修正内容

Docker Compose 専用の nginx 設定ファイルを用意し、コンテナ起動時にマウントするよう修正した。

#### 1. `nginx/amazia-market.docker.conf` を新規作成

```nginx
server {
    listen 80 default_server;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://amazia-core:8080/api/;  # サービス名で参照
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

#### 2. `docker-compose.yml` の amazia-market にボリュームマウントを追加

```yaml
amazia-market:
  image: ...
  ports:
    - "5173:80"
  volumes:
    - ./nginx/amazia-market.docker.conf:/etc/nginx/conf.d/default.conf:ro
  depends_on:
    - amazia-core
```

`nginx/amazia.conf`（EC2 本番用）は変更しない。Docker 環境専用の設定ファイルを別途用意することで、本番・開発環境の差異を明示的に管理する。
