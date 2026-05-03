## 問題
Nginx経由で `http://<EC2-IP>:8001`（Amazia Console UI）にアクセスすると 403 Forbidden が返った。
Amazia Market（port 80）は正常に表示されていた。

## 原因1：/var/www/amazia-console が空だった
SSMコマンドキュー詰まりにより、distコピーが実行されなかった。
（→ 003_ssm_command_queue_stuck.md 参照）

## 原因2：Vueのビルド先パスの認識ミス
deploy.ymlでコピー元を `resources/vue/dist/` と指定していたが、
実際のビルド先は `vite.config.js` の `outDir` 設定により `public/vue/` だった。

```js
// amazia-console/resources/vue/vite.config.js
build: {
  outDir: '../../public/vue',  // ← public/vue に出力される
}
```

S3のzipにも `amazia-console/public/vue/` としてファイルが含まれていたが、
コピー元パスが間違っていたためEC2への配置が失敗していた。

## 解決策
deploy.ymlのコピー元パスを修正した。

```yaml
# 修正前
sudo cp -r /home/ssm-user/amazia/amazia-console/resources/vue/dist/. /var/www/amazia-console/

# 修正後
sudo cp -r /home/ssm-user/amazia/amazia-console/public/vue/. /var/www/amazia-console/
```

## 結果
`/var/www/amazia-console` に `assets/` と `index.html` が配置され、403が解消した。
`http://<EC2-IP>:8001` でAmazia Console UIが表示された。

## 補足
- Viteのビルド先は `vite.config.js` の `outDir` で決まる。デフォルトは `dist/` だが変更されている場合がある
- デプロイ前に `ls` でdistの存在を確認するステップをzipコマンドの前に追加することで早期検出できる
