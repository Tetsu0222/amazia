# 016: EC2 上で SKU 画像登録エンドポイントが 404

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
`http://13.54.203.95:8001/skus?productId=10` → 画像登録操作  
エンドポイント: `GET/POST /api/skus/{id}/images`

## 症状

ローカルでは正常に動作するが、EC2 では画像登録画面を開くと 404 が返る。  
画面には「200KB制限かつ PNG にしてください」というバリデーションメッセージが表示された（フロントエンドのフォールバック）。

```
HTTP 404  http://13.54.203.95:8001/api/skus/1/images
```

## 根本原因

**EC2 上の Docker イメージが古く、SKU 画像機能のコードが含まれていなかった。**

### 問題の連鎖

| レイヤー | 状態 | 詳細 |
|---|---|---|
| nginx | 正常 | `/api/` → `localhost:8000` プロキシは正しく動作 |
| amazia-console (Laravel) | イメージが古い | `routes/api/Sku.php` に画像ルートが存在しなかった |
| amazia-core (Spring Boot) | イメージが古い | `/api/skus/{id}/images` エンドポイントが存在しなかった |

amazia-console の 404 を直すと、次に amazia-core が 404 を返すという二段階の問題だった。

### EC2 ディスク満杯の副次問題

amazia-console の pull 実行時に EC2 のルートディスクが満杯（8GB 中 7.9GB 使用、残り 28MB）で pull が途中失敗した。  
古いイメージ 48 個とビルドキャッシュ 1.165GB が蓄積していた。

## なぜ CI で検知できなかったか

- EC2 へのデプロイ手順が手動であり、イメージのビルド＆プッシュが未実施のまま放置されていた
- amazia-console と amazia-core を同時にデプロイする仕組みがなく、片方だけ更新が漏れやすい

## 修正内容

1. **ディスク確保**: EC2 上で `docker system prune -af` を実行 → 3.597GB 回収（残り 4.4GB）
2. **amazia-console 更新**: ローカルでビルド → ECR プッシュ → EC2 で pull & 再起動
3. **amazia-core 更新**: ローカルでビルド → ECR プッシュ → EC2 で pull & 再起動

すべて SSM Session Manager 経由（SSH ポート不要）で実施。

## 再発防止

| 観点 | 対策 |
|------|------|
| デプロイ漏れ | コード変更時は amazia-console・amazia-core 両方を必ず ECR プッシュする |
| ディスク管理 | 定期的に `docker system prune` を実行、または EC2 のディスク容量を増設する |
| 検証手順 | EC2 デプロイ後は `curl http://127.0.0.1:8000/api/skus/1/images` と `curl http://127.0.0.1:8080/api/skus/1/images` で両レイヤーを直接確認する |
