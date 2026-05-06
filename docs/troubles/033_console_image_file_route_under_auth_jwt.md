# 033: Console 経由の SKU 画像配信が auth.jwt 配下にあり 401

## ステータス
✅ 解決済（2026-05-06）

## 発症箇所
- `https://www.amazia-portfolio.dedyn.io/console/api/skus/{id}/image-file/{filename}`
- Console の画像管理画面（`/console/skus`）でサムネイル枠が壊れアイコンになる

## 症状
- ログイン成功・JWT も正しく検証されている状態
- SKU 一覧と画像メタデータの取得（`/skus/{id}/images`）は 200 で成功
- 画像実体取得（`/skus/{id}/image-file/{filename}`）だけが **401 Unauthorized**
- DevTools のリクエストヘッダに **`Authorization` ヘッダが付いていない**
- Core 直接（`https://www.amazia-portfolio.dedyn.io/api/skus/{id}/image-file/{filename}`）は 200 を返す

## 根本原因
ブラウザの `<img src="...">` は **fetch API ではない** ため、JS で `axios.defaults.headers.common['Authorization']` を設定していても **Authorization ヘッダが付与されない**。

一方、Console（Laravel）の `/skus/{id}/image-file/{path}` ルートは `routes/api.php` の `auth.jwt` ミドルウェアグループの中で定義されていたため、Authorization 付きでないリクエストは必ず 401 で弾かれていた。

```php
// 修正前（routes/api.php）
Route::middleware('auth.jwt')->group(function () {
    require __DIR__.'/api/Sku.php';   // ここに image-file ルートが含まれる
    ...
});
```

`<img src>` は同一オリジンであっても Authorization を運ばないため、Bearer トークン認証だけに頼ったルート設計と相性が悪い。

## なぜローカルで気付かなかったか
- ローカルの Vue dev server（`localhost:5174`）では Vite のプロキシ設定で API を叩いており、ブラウザキャッシュが効きやすかった
- 過去のローカル動作確認で画像が表示されていた記憶がある場合でも、**実は別経路（Core 直叩き）でキャッシュされた画像が表示されていた**可能性
- いずれにせよ「Console 経由 / Authorization なしで画像を取得する」シナリオを E2E で確認していなかった

## なぜ HTTPS 化（X-3）まで気付かなかったか
- 本番で Console 経由の画像配信を初めて使ったのが X-3 完了後
- 同じく潜在不具合だった 030 / 031 / 032 と同じ「本番動作確認漏れ」の系譜

## なぜ CI で検知できなかったか
- Console 側の API テストは Authorization ヘッダ付きリクエストでしか検証していなかった
- 「Authorization なしで `<img src>` 経由のアクセスが通るか」という観点はテストになかった

## 修正内容
`/skus/{id}/image-file/{path}` ルートを `routes/api.php` の `auth.jwt` ミドルウェア**外**に移動して、認証不要として扱う。

```php
// 修正後（routes/api.php）
// 画像配信は <img src> から呼ばれるため Authorization ヘッダを付けられず、
// auth.jwt ミドルウェアの中に置くと必ず 401 になる。Market の /api/skus/*/image-file/*
// が公開なのと同じ思想で、Console 経由の image-file 取得も認証不要として扱う。
Route::get('/skus/{id}/image-file/{path}',
    \App\Sku\Controller\ProxySkuImageController::class
)->where('path', '.+');

Route::middleware('auth.jwt')->group(function () {
    require __DIR__.'/api/Sku.php';   // image-file は除いた
    ...
});
```

`routes/api/Sku.php` 側の同 URL 定義はコメントだけ残して削除（誤解防止）。

### セキュリティ上の判断
- 商品 SKU 画像は Market（公開）でも同じ画像 URL を返している（`/api/skus/{id}/image-file/{filename}` で 200）
- Console 経由 URL（`/console/api/...`）と Market 経由 URL（`/api/...`）でアクセス制御を変えても**実質的な情報差はない**
- UUID ファイル名で推測困難 + 商品画像は表示されることが前提のリソース
- したがって認証不要として扱うことは現実的な妥当性がある

変更ファイル:
- `amazia-console/routes/api.php`
- `amazia-console/routes/api/Sku.php`

## 関連トラブル
- [019: Console ログイン 500 / Market 商品取得 401](019_console_login_500_market_401.md) — 公開ルートを auth グループ外に置く規約の起源
- [030 / 031 / 032](030_https_via_cloudfront_duckdns_single_domain.md) — X-3 動作確認の延長で発見された連鎖

## 再発防止
| 観点 | 対策 |
|------|------|
| `<img src>` 配信ルート | Bearer トークンを必要とするミドルウェアの**外**に明示的に置く規約とする（019 で公開エンドポイントの規約として記録済み） |
| ルート設計レビュー観点 | 新規ルート追加時に「ブラウザの非 fetch コンテキスト（`<img>` `<video>` `<a download>`）から呼ばれる可能性があるか」をチェック |
| E2E テスト | Console の画像表示シナリオを Playwright 等で覆い、Authorization 不要で 200 が返ることを保証する |
| Console / Market のルート設計統一 | 公開可能な GET ルートは「Console / Market 共通の規約」で auth 外に置くことを明文化 |
