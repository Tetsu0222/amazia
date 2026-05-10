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
| [TBL_password_histories.md](TBL_password_histories.md) | password_histories | パスワード履歴 | Core |

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

### Core システム（ワークフロー）※フェーズ12追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_workflow_requests.md](TBL_workflow_requests.md) | workflow_requests | ワークフロー申請 | Core |
| [TBL_workflow_requests_detail.md](TBL_workflow_requests_detail.md) | workflow_requests_detail | ワークフロー申請詳細（段階別承認） | Core |

### Core システム（Market 認証・会員）※フェーズ13追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_market_customers.md](TBL_market_customers.md) | market_customers | Market 顧客マスタ | Core |
| [TBL_market_customer_password_histories.md](TBL_market_customer_password_histories.md) | market_customer_password_histories | Market 顧客パスワード履歴 | Core |
| [TBL_market_customers_password_reset_tokens.md](TBL_market_customers_password_reset_tokens.md) | market_customers_password_reset_tokens | Market 顧客パスワードリセットトークン | Core |
| [TBL_market_sessions.md](TBL_market_sessions.md) | market_sessions | Market セッション（CSRF含む） | Core |
| [TBL_postal_addresses.md](TBL_postal_addresses.md) | postal_addresses | 郵便番号→住所マスタ（KEN_ALL） | Core |

### Core システム（購入・配送）※フェーズ14追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_sales.md](TBL_sales.md) | sales | 売上・注文 | Core |
| [TBL_sales_return.md](TBL_sales_return.md) | sales_return | 返品管理 | Core |
| [TBL_address.md](TBL_address.md) | address | 配送先住所スナップショット | Core |
| [TBL_payment_methods.md](TBL_payment_methods.md) | payment_methods | 決済方法マスタ | Core |
| [TBL_shipping_statuses.md](TBL_shipping_statuses.md) | shipping_statuses | 配送ステータスマスタ | Core |
| [TBL_operation_logs.md](TBL_operation_logs.md) | operation_logs | 操作履歴 | Core |

### Core システム（配送管理・在庫並行運用）※フェーズ15追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_shipping_methods.md](TBL_shipping_methods.md) | shipping_methods | 配送方法マスタ | Core |
| [TBL_warehouses.md](TBL_warehouses.md) | warehouses | 倉庫マスタ | Core |
| [TBL_inventories.md](TBL_inventories.md) | inventories | 商品×倉庫の現在在庫（並行運用書き込み正本） | Core |
| [TBL_inbounds.md](TBL_inbounds.md) | inbounds | 商品入荷ヘッダ | Core |
| [TBL_deliveries.md](TBL_deliveries.md) | deliveries | 配送実体（注文確定と同時に sales 1:1 で生成） | Core |
| [TBL_shipping_lead_times.md](TBL_shipping_lead_times.md) | shipping_lead_times | 都道府県別リードタイムマスタ（フェーズX-5） | Core |

### Core システム（カート機能）※フェーズ16.5追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_carts.md](TBL_carts.md) | carts | Market 顧客のカート（1顧客1カート） | Core |
| [TBL_cart_items.md](TBL_cart_items.md) | cart_items | カート明細（同一 SKU・同一 is_preorder は1行に集約） | Core |

### Core システム（バッチ処理基盤）※フェーズ17追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_batch_executions.md](TBL_batch_executions.md) | batch_executions | バッチ実行履歴 | Core |
| [TBL_console_notifications.md](TBL_console_notifications.md) | console_notifications | Console 通知センター | Core |
| [TBL_notification_subscriptions.md](TBL_notification_subscriptions.md) | notification_subscriptions | 通知購読設定 | Core |
| [TBL_fault_injection_logs.md](TBL_fault_injection_logs.md) | fault_injection_logs | フォルトインジェクション実行履歴（dev / staging のみ） | Core |
| [TBL_monthly_sales_reports.md](TBL_monthly_sales_reports.md) | monthly_sales_reports | 月次売上レポート | Core |
| [TBL_yearly_sales_reports.md](TBL_yearly_sales_reports.md) | yearly_sales_reports | 年次売上レポート | Core |
| [TBL_product_sku_scheduled_prices.md](TBL_product_sku_scheduled_prices.md) | product_sku_scheduled_prices | SKU 価格変更予約 | Core |
| [TBL_operation_logs_archive.md](TBL_operation_logs_archive.md) | operation_logs_archive | 操作履歴アーカイブ（年次） | Core |
| [TBL_console_notifications_archive.md](TBL_console_notifications_archive.md) | console_notifications_archive | 通知センター アーカイブ（年次） | Core |

### Core システム（問い合わせ管理）※フェーズ18追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_inquiries.md](TBL_inquiries.md) | inquiries | 問い合わせ親 | Core |
| [TBL_inquiry_messages.md](TBL_inquiry_messages.md) | inquiry_messages | 問い合わせスレッドメッセージ | Core |

### Core システム（お知らせ機能）※フェーズ19追加

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_notice_categories.md](TBL_notice_categories.md) | notice_categories | お知らせ分類マスタ | Core |
| [TBL_notices.md](TBL_notices.md) | notices | お知らせ本体 | Core |
| [TBL_notice_reads.md](TBL_notice_reads.md) | notice_reads | お知らせ既読履歴 | Core |

### Console システム（Laravel 標準）

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_sessions.md](TBL_sessions.md) | sessions | Laravel セッション | Console |
| [TBL_personal_access_tokens.md](TBL_personal_access_tokens.md) | personal_access_tokens | Sanctum APIトークン | Console |

> Laravel フレームワーク標準テーブル（`cache`, `jobs` 等）は設計書対象外。

## ER図

- [ER_diagram.md](ER_diagram.md) — Mermaid記法によるER図

## 追加ルール

- テーブル定義書はテーブル追加のたびに作成し、このREADMEに追記する。
- ファイル名は `TBL_<テーブル名>.md` 形式とする。
- マイグレーションファイル（または schema.sql）との対応を各定義書に明記する。
- フェーズ完了の定義として、当該フェーズで触ったテーブルの定義書がすべて追加・更新済みであることを確認する（`Amazia/CLAUDE.md` の「DB / API 設計書のメンテナンスルール」参照）。

## テーブル定義書 整備履歴

P11〜P14 で追加された14テーブル分の定義書を整備した記録。

| 優先度 | テーブル | 出現フェーズ | 状態 |
|------|--------|-----------|------|
| 🔴 CRITICAL | sales / address / payment_methods / shipping_statuses | P14 | ✅ 作成済（2026-05-06） |
| 🟠 HIGH | market_customers / market_sessions / postal_addresses / sales_return / operation_logs / market_customer_password_histories / market_customers_password_reset_tokens | P13〜P14 | ✅ 作成済（2026-05-06） |
| 🟡 MEDIUM | workflow_requests / workflow_requests_detail | P12 | ✅ 作成済（2026-05-06） |
| 🔵 LOW | password_histories | P11 | ✅ 作成済（2026-05-06） |
