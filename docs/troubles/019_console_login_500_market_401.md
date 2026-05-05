# 019: Consoleログイン500 / Market商品取得401

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
- `http://localhost:5174/login` → ログインボタン押下で「メールアドレスまたはパスワードが正しくありません」
- `http://localhost:5173` → 「商品データの取得に失敗しました」

## 症状
1. Console ログイン: Laravel `/api/auth/login` が 500 を返す
2. Market 商品一覧: Laravel `/api/products/market` が 401 を返す

## 根本原因

### 問題1: LoginController の Cookie メソッド名誤り
`LoginController.php` で Guzzle の `SetCookie` オブジェクトに対して `isSecure()` / `isHttpOnly()` を呼んでいた。
しかし Guzzle の `SetCookie` クラスが持つメソッドは `getSecure()` / `getHttpOnly()` であり、`is*` 系メソッドは存在しない。
→ `Error: Call to undefined method GuzzleHttp\Cookie\SetCookie::isSecure()`

### 問題2: market ルートが auth.jwt ミドルウェアに守られていた
`routes/api/Product.php` に定義された `/products/market` が `routes/api.php` の `Route::middleware('auth.jwt')->group()` 内に include されていたため、JWT なしでアクセスすると 401 になった。
マーケット向けエンドポイントは公開エンドポイントなので認証不要。

### 問題3: コンテナはボリュームマウントなし
`amazia-console` はイメージビルド（COPY）方式のため、ホストでファイルを編集しても `docker cp` または再ビルドしないとコンテナに反映されない。

## 修正内容

1. `app/Auth/Controller/LoginController.php`
   - `isSecure()` → `getSecure()`
   - `isHttpOnly()` → `getHttpOnly()`

2. `routes/api.php`
   - `/products/market` と `/products/{id}/market` を `auth.jwt` グループの外に移動

3. `routes/api/Product.php`
   - 上記2ルートを削除（api.php に移動したため）

4. `docker cp` でコンテナに即時反映 + `docker compose build` で次回再起動にも反映

## 再発防止
| 観点 | 対策 |
|------|------|
| Guzzle Cookie API | `isSecure()/isHttpOnly()` ではなく `getSecure()/getHttpOnly()` を使う |
| 公開API設計 | マーケット向けエンドポイントは routes/api.php の auth グループ外に定義する |
| ファイル編集の反映 | コンテナ再ビルドが必要。編集後は `docker compose build` を実行する |
