# 009: CORE_BASE_URL 未設定による 500 Internal Server Error

## ステータス
✅ 解決済（2026-05-03）

## 発生エンドポイント
`GET http://13.54.203.95:8001/api/admin/products` → 500

## 症状
- Phase 8 デプロイ後、商品マスタ画面（`/api/admin/products` / `/api/product-statuses`）が 500 を返す
- CI（PHPUnit）はグリーンのまま

## 根本原因
Phase 8 で `CORE_BASE_URL` 環境変数を新規追加したが、`docker-compose.yml` に追記していなかった。

```
# docker-compose.yml（修正前）
CORE_API_URL: http://amazia-core:8080/api/products
# CORE_BASE_URL が未設定 → config のデフォルト値 http://localhost:8080/api が使用される
# コンテナ内の localhost はコンテナ自身を指すため amazia-core に到達できない
```

## なぜ CI で検知できなかったか

PHPUnit テストの URL が `http://localhost:8080` ハードコードになっており、`Http::fake()` でモックしていたため、実際の環境変数設定と無関係にグリーンになっていた。

つまり **「テストがどの URL を叩くか」と「コンテナが実際にどの URL を叩くか」が乖離していた**。

## 修正内容

### 1. docker-compose.yml に CORE_BASE_URL を追加
```yaml
CORE_API_URL: http://amazia-core:8080/api/products
CORE_BASE_URL: http://amazia-core:8080/api   # ← 追加
```

### 2. phpunit.xml にテスト用環境変数を明示
```xml
<env name="CORE_API_URL"  value="http://amazia-core-test:8080/api/products"/>
<env name="CORE_BASE_URL" value="http://amazia-core-test:8080/api"/>
```

### 3. テストを config() 経由に書き換え
```php
// 修正前（ハードコード）
Http::fake(['http://localhost:8080/api/admin/products' => ...]);

// 修正後（設定値参照）
$this->coreBaseUrl = config('services.amazia_core.base_url');
Http::fake(["{$this->coreBaseUrl}/admin/products" => ...]);
```

### 4. Core API 異常系テストを追加
- Core が 500 を返すとき Console も 500 を返すこと（全エンドポイント）

## 再発防止

| 観点 | 対策 |
|------|------|
| 新規環境変数の漏れ | `docker-compose.yml` と `phpunit.xml` を必ずセットで更新する |
| テストの URL ハードコード | テストは `config()` 経由で URL を取得し、phpunit.xml の値を検証対象にする |
| 環境差異の未検知 | Core API が 500/404 を返す異常系テストを追加し、proxy の挙動を検証する |
