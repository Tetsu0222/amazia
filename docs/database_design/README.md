# DB設計書

## 概要

テーブル単位のテーブル定義書とER図を格納する。

## ファイル一覧

### Core システム（認証・認可）※フェーズ11追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_users.md](TBL_users.md) | users | ユーザー | Core |
| [TBL_roles.md](TBL_roles.md) | roles | ロール | Core |
| [TBL_permissions.md](TBL_permissions.md) | permissions | パーミッション | Core |
| [TBL_role_permissions.md](TBL_role_permissions.md) | role_permissions | ロール・パーミッション中間テーブル | Core |
| [TBL_refresh_tokens.md](TBL_refresh_tokens.md) | refresh_tokens | リフレッシュトークン | Core |
| [TBL_password_reset_tokens.md](TBL_password_reset_tokens.md) | password_reset_tokens | パスワードリセットトークン | Core |

### Core システム（商品管理）※フェーズ8〜10追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_products.md](TBL_products.md) | products | 商品 | Core |
| [TBL_product_images.md](TBL_product_images.md) | product_images | 商品画像 | Core |
| [TBL_product_skus.md](TBL_product_skus.md) | product_skus | SKU | Core |
| [TBL_product_sku_prices.md](TBL_product_sku_prices.md) | product_sku_prices | SKU現行価格 | Core |
| [TBL_product_sku_price_history.md](TBL_product_sku_price_history.md) | product_sku_price_history | SKU価格履歴 | Core |
| [TBL_product_sku_stocks.md](TBL_product_sku_stocks.md) | product_sku_stocks | SKU現在在庫 | Core |
| [TBL_product_sku_stock_transactions.md](TBL_product_sku_stock_transactions.md) | product_sku_stock_transactions | SKU在庫履歴 | Core |
| [TBL_product_sku_images.md](TBL_product_sku_images.md) | product_sku_images | SKU画像 | Core |

## ER図

- [ER_diagram.md](ER_diagram.md) — Mermaid記法によるER図（Core システム全体）

## 追加ルール

- テーブル定義書はテーブル追加のたびに作成し、このREADMEに追記する。
- ファイル名は `TBL_<テーブル名>.md` 形式とする。
- マイグレーションファイルとの対応を各定義書に明記する。
