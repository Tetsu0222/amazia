# 020: リロードでログアウト / 商品取得失敗（refresh_token Cookie が保存されない）

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
- `localhost:5174` にてログイン後、ページリロードするとログイン画面に戻る
- `localhost:5174/` にてログイン直後に「商品の取得に失敗しました」が表示される

## 症状
1. ログインは成功し、accessToken が localStorage に保存される
2. しかし 15 分経過後（アクセストークン期限切れ）または ページリロード後にログイン画面へ飛ばされる
3. 商品一覧が表示されず「商品の取得に失敗しました」エラーが出る

## 根本原因

amazia-core（Spring Boot）がリフレッシュトークンを `HttpOnly Cookie` で返す際、
Cookie に `domain=amazia-core`（Docker コンテナ名）を設定していた。

```
Set-Cookie: refresh_token=...; path=/api/auth/refresh; domain=amazia-core; httponly; samesite=lax
```

amazia-console（Laravel）はこの Cookie をそのままブラウザに転送していたため、
ブラウザは `localhost` でアクセスしているにもかかわらず `domain=amazia-core` の Cookie を受け取り、
ドメインが一致しないため **保存できない**。

その結果：
- アクセストークン（15分）が期限切れになると自動リフレッシュできない
- `authApi.js` の 401 ハンドラが `/api/auth/refresh` を呼ぶが、Cookie が送られないため 401 が返る
- `authStore.clear()` が呼ばれ、`window.location.href = '/login'` でログイン画面へ遷移
- 前回の期限切れトークンが localStorage に残っている状態でリロードすると同様の現象が起きる

## なぜ CI で検知できなかったか

- ローカル開発環境でのみ発生する問題（Docker コンテナ名が Cookie domain に使われる）
- API テストは Bearer トークンを直接ヘッダーに付けて検証しており、Cookie の保存フローをテストしていなかった

## 修正内容

`LoginController.php` と `RefreshTokenController.php` で Cookie を転送する際に `domain` を `null` に設定。

**LoginController.php**（修正前）:
```php
$httpResponse->cookie(
    $cookie->getName(),
    $cookie->getValue(),
    $cookie->getMaxAge() / 60,
    $cookie->getPath(),
    $cookie->getDomain(),   // "amazia-core" がそのまま渡される
    $cookie->getSecure(),
    $cookie->getHttpOnly()
);
```

**LoginController.php**（修正後）:
```php
$httpResponse->cookie(
    $cookie->getName(),
    $cookie->getValue(),
    $cookie->getMaxAge() / 60,
    $cookie->getPath(),
    null,                   // domain=amazia-core（コンテナ名）をブラウザに渡さない
    $cookie->getSecure(),
    $cookie->getHttpOnly()
);
```

**RefreshTokenController.php** にも同様の Cookie 転送処理を追加（元々 Cookie 転送自体がなかった）。

変更ファイル:
- `app/Auth/Controller/LoginController.php`
- `app/Auth/Controller/RefreshTokenController.php`

反映方法: `docker cp` でコンテナに即時反映

## 再発防止

| 観点 | 対策 |
|------|------|
| Cookie のドメイン転送 | Core API から受け取った Cookie を Console がブラウザに転送する際は必ず `domain` を `null` に設定する |
| RefreshToken の Cookie 転送 | リフレッシュエンドポイントでも同じパターンで Cookie を返す |
| E2E テスト | ログイン→リロード→再ログイン不要、のフローを検証するテストを追加する |
| Core の Cookie 設定 | Core で Cookie を発行する際、`domain` はリバースプロキシ側（Console）で上書きするため、空または省略することが望ましい |
