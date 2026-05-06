# 031: Console（Laravel）の Cookie 中継で Set-Cookie が落ちる

## ステータス
✅ 解決済（2026-05-06）

## 発症箇所
- `https://www.amazia-portfolio.dedyn.io/console/api/auth/login`（POST）
- 関連: `https://www.amazia-portfolio.dedyn.io/console/api/auth/refresh`（POST）

## 症状
- Console（`/console/login`）から正しい admin 認証情報でログイン → 200 でアクセストークンが返る
- しかし**ブラウザに `refresh_token` Cookie が保存されない**
- 結果、Console 画面に遷移しても認証必須 API が 401 → ログイン画面へリダイレクトされ「ログインできない」体験になる
- DevTools のレスポンスヘッダにも `Set-Cookie` が**存在しない**

## 根本原因
Console（Laravel）の `LoginController` / `RefreshTokenController` が Core からの Cookie を**再構築**する仕様だったが、Laravel の HTTP クライアント（Guzzle ラッパー）はデフォルトで CookieJar を有効化していないため、`$response->cookies()` が空配列を返し、結果としてブラウザへ転送される `Set-Cookie` が**何も出ない**。

```php
// 修正前
foreach ($response->cookies() as $cookie) {   // ← デフォルトでは空
    $httpResponse->cookie(
        $cookie->getName(),
        $cookie->getValue(),
        ...
        null,                  // domain を null で上書き（020 の対処）
        ...
    );
}
```

トラブル020 では `domain=amazia-core`（コンテナ名）を `null` に上書きする目的で同じパターンを採用していたが、その時点では CookieJar の有効化を別途行っていたか、別経路で Cookie が流れていたため気付けなかった。

## なぜ HTTPS 化（X-3）まで気付かなかったか
- ローカル（HTTP）では別の Cookie 流通経路が機能しており、開発時に Console ログイン → リロード維持が動いていた
- 本番環境で Console ログインを実際に試したのは X-3 完了後が初めてだったため、X-3 のデプロイで初めて顕在化した
- API テスト（PHPUnit）はモック化された Guzzle レスポンスで、実 Cookie 中継の挙動を検証していなかった

## なぜ CI で検知できなかったか
- Console 側の API テストはレスポンス JSON のみ検証していた
- Cookie 中継は実際の Spring と通信しないと再現しないが、CI では amazia-core を起動していない

## 修正内容
`LoginController.php` / `RefreshTokenController.php` で **Spring が返した `Set-Cookie` ヘッダを生のまま透過**する方針へ変更。

```php
// 修正後
foreach ($response->getHeaders()['Set-Cookie'] ?? [] as $rawCookie) {
    $httpResponse->headers->set('Set-Cookie', $rawCookie, false);
}
```

理由：
- Spring 側で `Domain=www.amazia-portfolio.dedyn.io / Path=/console/api/auth/refresh / Secure / HttpOnly` を環境変数で正しく組んでいるため、Laravel が再構築すると属性ズレのリスクがある
- `getHeaders()` はレスポンスの生 HTTP ヘッダを返すため、CookieJar 有効化の有無に依存しない
- 第3引数 `false` で**ヘッダ追加**（複数 `Set-Cookie` を保持可能）

変更ファイル:
- `amazia-console/app/Auth/Controller/LoginController.php`
- `amazia-console/app/Auth/Controller/RefreshTokenController.php`

## 関連トラブル
- [020: refresh_token Cookie が保存されない（domain=コンテナ名）](020_refresh_cookie_domain_container_name.md)
  - 同じパターンの問題への対処だが、Cookie 中継の仕組み自体は不安定なまま残っていた
- [030: HTTPS化（X-3）の経緯](030_https_via_cloudfront_duckdns_single_domain.md)
  - X-3 動作確認の延長で本問題を踏み抜いた

## 再発防止
| 観点 | 対策 |
|------|------|
| Cookie 中継方式 | Console は Core からの `Set-Cookie` を**生ヘッダで透過**することを規約化（Domain/Path/Secure を Core 側の環境変数で完全に組む） |
| 本番動作確認 | フェーズ完了の定義に「**本番環境**でログイン → リロード維持の E2E 確認」を追加（`docs/ai_context/test_insights.md` カテゴリ8 と接続） |
| Cookie 中継テスト | Console 側 PHPUnit で「Spring からの `Set-Cookie` 文字列をモックして、ブラウザ転送時に `Set-Cookie` ヘッダがそのまま含まれること」を検証 |
| 属性責務の分離 | Cookie 属性（Domain/Path/Secure/HttpOnly）の**唯一の正本**は Core 側 application properties。Console は属性を一切変更しない |
