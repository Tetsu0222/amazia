# 032: Console と Core の JWT 署名アルゴリズム不一致で API が 401

## ステータス
✅ 解決済（2026-05-06）

## 発症箇所
- `https://www.amazia-portfolio.dedyn.io/console/api/admin/products`（GET）
- 認証必須エンドポイント全般

## 症状
- Console ログイン成功 → ダッシュボード遷移
- 商品一覧取得 API（`/console/api/admin/products`）が **401 Unauthorized**
- レスポンス本文：`{"message":"Unauthorized"}`
- ブラウザは Authorization ヘッダに正しく Bearer トークンを付与している
- Core 直接（`/api/admin/products`）は 200 を返す

## 根本原因

### Spring（Core）側
`JwtService.generateAccessToken` は JJWT ライブラリの `Keys.hmacShaKeyFor(secret.getBytes())` で署名鍵を生成。このメソッドはシークレットキーの**バイト長**に応じてアルゴリズムを自動選択する：

| キー長 | 採用アルゴリズム |
|--------|-----------------|
| 32 バイト | HS256 |
| 48 バイト | HS384 |
| **64 バイト以上** | **HS512** |

本番の `JWT_SECRET` は 64 バイト以上のため、Spring は **HS512** で署名していた。発行された JWT のヘッダ：
```json
{ "alg": "HS512" }
```

### Laravel（Console）側
`JwtVerifyService::verify` が `hash_hmac('sha256', ...)` を**ハードコード**していた。

```php
// 修正前
$expected = hash_hmac('sha256', "{$headerB64}.{$payloadB64}", $this->secret, true);
```

そのため Spring が HS512 で署名した JWT を Console が SHA-256 で検証 → 必ず署名不一致 → 401。

## なぜローカルで気付かなかったか
- ローカルの `docker-compose.local.yml` の `JWT_SECRET` は短い（`local-dev-secret-key-change-before-production!!` = 47 バイト → HS384、もしくは別の値で 32 バイト = HS256 になっていた可能性）
- 短い鍵では HS256 になり、Console の SHA-256 と一致して動作していた
- 本番では強度のため長い鍵を採用 → HS512 に変わり不整合

## なぜ HTTPS 化（X-3）まで気付かなかったか
- 本番環境で **Console ログイン後の認証必須 API を実際に叩いたのは X-3 完了後が初めて**
- 直前のトラブル031（Cookie 中継不備）の修正後にこの問題が顕在化

## なぜ CI で検知できなかったか
- Console 側の PHPUnit は JwtVerifyService を直接呼ぶテストでは固定の HS256 トークンを生成して検証していた（Spring が実際に発行する JWT を使っていない）
- Core 側の JUnit は同じ JJWT ライブラリで自己完結しているため不整合に気付かない

## 修正内容
`JwtVerifyService::verify` を **JWT ヘッダの `alg` クレームに応じて検証アルゴリズムを切替** に変更。

```php
// 修正後
$header = json_decode($this->base64UrlDecode($headerB64), true);
$alg    = $header['alg'] ?? '';
$hashAlgo = match ($alg) {
    'HS256' => 'sha256',
    'HS384' => 'sha384',
    'HS512' => 'sha512',
    default => throw new \RuntimeException("Unsupported JWT alg: {$alg}"),
};
$expected = hash_hmac($hashAlgo, "{$headerB64}.{$payloadB64}", $this->secret, true);
```

これで Spring 側の鍵長に依存せず、Console は発行されたトークンの `alg` に追従できる。

変更ファイル:
- `amazia-console/app/Shared/Service/JwtVerifyService.php`

## 関連トラブル
- [030: HTTPS化（X-3）の経緯](030_https_via_cloudfront_duckdns_single_domain.md)
- [031: Cookie 中継で Set-Cookie が落ちる](031_console_cookie_relay_drops_set_cookie.md)

## 再発防止
| 観点 | 対策 |
|------|------|
| アルゴリズム整合性 | JWT ヘッダの `alg` に追従する実装を**規約として固定**。固定アルゴリズムでハードコードしない |
| Spring の鍵生成 | `Keys.hmacShaKeyFor()` の挙動（鍵長で alg が変わる）をコメントとして JwtService に明記 |
| Console 側テスト | Spring が発行する JWT 文字列をフィクスチャとしてコミットし、Console 側 PHPUnit で検証する E2E 寄りのテストを追加 |
| 環境変数の規約 | `JWT_SECRET` は 64 バイト以上を本番標準として `setup.md` 等で明示。短い鍵でローカル動作と本番動作が乖離する罠を回避 |
