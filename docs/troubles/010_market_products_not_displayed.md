# 010: Amazia Market に商品が表示されない

## ステータス
✅ 解決済（2026-05-04）

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
