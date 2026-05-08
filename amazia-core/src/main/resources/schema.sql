-- 冪等なスキーマ定義（MySQL 本番環境向け）
-- 既存環境向けに、足りないテーブル・カラムを起動時に保証する。
-- 重複実行エラー（カラム/インデックス既存など）は spring.sql.init.continue-on-error=true で無視する。
--
-- テスト環境(H2)では application-test.properties で schema-locations を空にして
-- このスクリプトを読み込まない。テストは ddl-auto=create-drop で JPA が生成する。

-- リフレッシュトークン（V1 マイグレーション相当）
-- 本番 MySQL に過去のいずれかの段階で作られていなかった場合のフォールバック。
-- users.id が Laravel 由来で BIGINT UNSIGNED のため、FK の型を合わせる必要がある（029 と同種の経緯）。
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(255)    NOT NULL UNIQUE,
    expires_at DATETIME        NOT NULL,
    revoked    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- パスワード再発行トークン（V1 マイグレーション相当）
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(255)    NOT NULL UNIQUE,
    expires_at DATETIME        NOT NULL,
    used       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_password_reset_tokens_user (user_id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- パスワード変更履歴（V1 マイグレーション相当 / 044・045 同型対策）
-- V1__create_auth_tables.sql に DDL があるが本番は Flyway 未使用（037）。
-- schema.sql にも書かないと本番でテーブルが作られず、社員パスワード変更時に 1146 で 500。
-- users.id が BIGINT UNSIGNED のため FK 列も UNSIGNED に揃える（044・045 同型）。
CREATE TABLE IF NOT EXISTS password_histories (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_password_histories_user (user_id),
    CONSTRAINT fk_password_histories_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS workflow_requests (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type   VARCHAR(20)  NOT NULL,
    target_id     BIGINT       NOT NULL,
    requested_by  BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    payload       JSON         NOT NULL,
    completed_at  DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workflow_status (status),
    INDEX idx_workflow_target (target_type, target_id),
    INDEX idx_workflow_requester (requested_by)
);

CREATE TABLE IF NOT EXISTS workflow_requests_detail (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_requests_id BIGINT       NOT NULL,
    step_number          INT          NOT NULL,
    target_role          VARCHAR(30)  NOT NULL,
    destination_user_id  BIGINT       NULL,
    destination_name     VARCHAR(100) NULL,
    approver_user_id     BIGINT       NULL,
    approver_name        VARCHAR(100) NULL,
    status               VARCHAR(20)  NOT NULL,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wfd_parent_step (workflow_requests_id, step_number),
    INDEX idx_wfd_destination (destination_user_id)
);

-- 楽観ロック用 version カラム（重複実行は continue-on-error で許容）
ALTER TABLE products            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE product_sku_prices  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE product_sku_stocks  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ============================================================================
-- フェーズ13: Amazia Market ログイン・会員登録機能用テーブル
--   設計書: docs/design/phase11_20/phase13_market_auth.md
--   migration: V5__phase13_market_auth_tables.sql と同内容（IF NOT EXISTS 版）
-- ============================================================================

CREATE TABLE IF NOT EXISTS market_customers (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name_last       VARCHAR(100)  NOT NULL,
    name_first      VARCHAR(100)  NOT NULL,
    postal_code     VARCHAR(8)    NOT NULL,
    address         VARCHAR(255)  NOT NULL,
    birthday        DATE          NOT NULL,
    email           VARCHAR(255)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    payment_method  VARCHAR(20)   NOT NULL,
    card_token      VARCHAR(255)  NULL,
    active_flag     BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_attempts INT           NOT NULL DEFAULT 0,
    locked_until    DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS market_customer_password_histories (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    customer_id   BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mcph_customer (customer_id),
    CONSTRAINT fk_mcph_customer FOREIGN KEY (customer_id) REFERENCES market_customers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS market_customers_password_reset_tokens (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT UNSIGNED NOT NULL,
    token_hash  VARCHAR(255)    NOT NULL UNIQUE,
    expires_at  DATETIME        NOT NULL,
    used        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mcprt_customer (customer_id),
    CONSTRAINT fk_mcprt_customer FOREIGN KEY (customer_id) REFERENCES market_customers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS market_sessions (
    session_id       VARCHAR(64)     NOT NULL PRIMARY KEY,
    customer_id      BIGINT UNSIGNED NOT NULL,
    csrf_token       VARCHAR(64)     NOT NULL,
    expires_at       DATETIME        NOT NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ms_customer (customer_id),
    INDEX idx_ms_expires (expires_at),
    CONSTRAINT fk_ms_customer FOREIGN KEY (customer_id) REFERENCES market_customers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS postal_addresses (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    postal_code VARCHAR(8)   NOT NULL,
    prefecture  VARCHAR(20)  NOT NULL,
    city        VARCHAR(100) NOT NULL,
    town        VARCHAR(200) NOT NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pa_postal_code (postal_code),
    INDEX idx_pa_pref_city (prefecture, city)
);

-- ============================================================================
-- フェーズ14: 購入機能（設計書 r4 / Step 0 + Step A 相当）
--   設計書: docs/design/phase11_20/phase14_shipping.md
--   migration: db/migration/V6_V11 と同内容（IF NOT EXISTS / continue-on-error 版）
--   注: 本プロジェクトは Flyway 未導入で schema.sql を spring.sql.init で起動時実行する方式（037 起因）
-- ============================================================================

-- 配送先住所スナップショット（V6 相当）
CREATE TABLE IF NOT EXISTS address (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT UNSIGNED NOT NULL,
    postal_code   VARCHAR(20)     NULL,
    prefecture    VARCHAR(50)     NULL,
    city          VARCHAR(100)    NULL,
    address_line  VARCHAR(255)    NOT NULL,
    building      VARCHAR(255)    NULL,
    is_active     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_address_user_id (user_id),
    INDEX idx_address_user_active (user_id, is_active),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES market_customers(id)
);

-- 決済方法マスタ（V6 相当）
CREATE TABLE IF NOT EXISTS payment_methods (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO payment_methods (id, name, description) VALUES
    (1, 'credit_card',      'クレジットカード'),
    (2, 'd_payment',        'd払い'),
    (3, 'cash_on_delivery', '代引き');

-- 配送ステータスマスタ（V6 + V9 相当：CANCELED / DELIVERY_FAILED / RESCHEDULED 含む 8 件）
CREATE TABLE IF NOT EXISTS shipping_statuses (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO shipping_statuses (id, code, name, description) VALUES
    (1, 'PENDING',          '配送準備中',         '注文確定後・出荷前'),
    (2, 'SHIPPED',          '配送済',             '発送完了'),
    (3, 'DELIVERED',        '配送完了',           '配達完了'),
    (4, 'RETURN_REQUESTED', '返品申請中',         '返品申請を受付中'),
    (5, 'RETURNED',         '返品完了',           '返品処理完了'),
    (6, 'CANCELED',         '発送前キャンセル',   '将来 phase21'),
    (7, 'DELIVERY_FAILED',  '配達失敗',           '将来 phase21'),
    (8, 'RESCHEDULED',      '再配達手配中',       '将来 phase21');

-- 売上テーブル（V6 + V7 相当：base + Step A 拡張カラムをすべて含む）
CREATE TABLE IF NOT EXISTS sales (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT UNSIGNED NOT NULL,
    sku_id              BIGINT          NOT NULL,
    quantity            INT             NOT NULL,
    amount              INT             NOT NULL,
    payment_method_id   BIGINT          NOT NULL,
    shipping_method_id  BIGINT          NOT NULL,
    shipping_address_id BIGINT UNSIGNED NOT NULL,
    shipping_status_id  BIGINT          NOT NULL,
    payment_id          VARCHAR(100)    NOT NULL,
    is_preorder         BOOLEAN         NOT NULL DEFAULT FALSE,
    sales_date          DATE            NOT NULL,
    shipping_date       DATE            NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_sales_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uk_sales_payment_id UNIQUE (payment_id),
    INDEX idx_sales_user_id (user_id),
    INDEX idx_sales_sales_date (sales_date),
    INDEX idx_sales_sku_id (sku_id),
    INDEX idx_sales_payment_method_id (payment_method_id),
    INDEX idx_sales_shipping_method_id (shipping_method_id),
    INDEX idx_sales_shipping_status_id (shipping_status_id),
    CONSTRAINT fk_sales_user FOREIGN KEY (user_id) REFERENCES market_customers(id),
    CONSTRAINT fk_sales_sku FOREIGN KEY (sku_id) REFERENCES product_skus(id),
    CONSTRAINT fk_sales_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id),
    CONSTRAINT fk_sales_shipping_status FOREIGN KEY (shipping_status_id) REFERENCES shipping_statuses(id),
    CONSTRAINT fk_sales_shipping_address FOREIGN KEY (shipping_address_id) REFERENCES address(id)
    -- shipping_method_id の FK は phase15 で shipping_methods 作成後に追加
);

-- 返品テーブル（V6 + V8 相当）
-- approver_id は users.id（BIGINT UNSIGNED）と型を揃える必要がある（trouble 045）
CREATE TABLE IF NOT EXISTS sales_return (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    sales_id        BIGINT          NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    reason          TEXT            NULL,
    quantity        INT             NOT NULL,
    approver_id     BIGINT UNSIGNED NULL,
    approved_at     DATETIME        NULL,
    notified_user   BOOLEAN         NOT NULL DEFAULT FALSE,
    notified_admin  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_sales_return_quantity_positive CHECK (quantity > 0),
    INDEX idx_sales_return_sales_id (sales_id),
    INDEX idx_sales_return_status (status),
    CONSTRAINT fk_sales_return_sales FOREIGN KEY (sales_id) REFERENCES sales(id),
    CONSTRAINT fk_sales_return_approver FOREIGN KEY (approver_id) REFERENCES users(id)
);

-- 操作履歴（V6 + V10 相当：screen_name / api_name 含む）
-- user_id は users.id（BIGINT UNSIGNED）と型を揃える必要がある（trouble 044）
CREATE TABLE IF NOT EXISTS operation_logs (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(50)  NULL,
    target_id   BIGINT       NULL,
    screen_name VARCHAR(100) NULL,
    api_name    VARCHAR(100) NULL,
    comment     TEXT         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operation_logs_user_id (user_id),
    INDEX idx_operation_logs_action (action),
    INDEX idx_operation_logs_target (target_type, target_id),
    INDEX idx_operation_logs_created_at (created_at),
    INDEX idx_operation_logs_screen_name (screen_name),
    INDEX idx_operation_logs_api_name (api_name),
    CONSTRAINT fk_operation_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- product_sku_stock_transactions の拡張（V11 相当）
-- 既存テーブルに不足カラムを追加。重複実行は continue-on-error で許容。
ALTER TABLE product_sku_stock_transactions ADD COLUMN reference_type     VARCHAR(50) NULL;
ALTER TABLE product_sku_stock_transactions ADD COLUMN reference_id       BIGINT      NULL;
ALTER TABLE product_sku_stock_transactions ADD COLUMN created_by_user_id BIGINT      NULL;
ALTER TABLE product_sku_stock_transactions ADD COLUMN comment            TEXT        NULL;
CREATE INDEX idx_sku_stock_tx_reference  ON product_sku_stock_transactions (reference_type, reference_id);
CREATE INDEX idx_sku_stock_tx_created_by ON product_sku_stock_transactions (created_by_user_id);
CREATE INDEX idx_sku_stock_tx_created_at ON product_sku_stock_transactions (created_at);

-- ============================================================================
-- フェーズ14.5: 予約ステータス判定（設計書 phase14_5_preorder_status.md §2-3）
--   既存 products テーブルに 4 カラム追加。
--   release_date / preorder_start_date は NULL 許容（NULL 時の意味は設計書参照）。
--   accept_preorder / accept_backorder は NOT NULL DEFAULT FALSE。
--   重複実行は spring.sql.init.continue-on-error=true で許容。
-- ============================================================================
ALTER TABLE products ADD COLUMN release_date         DATE    NULL;
ALTER TABLE products ADD COLUMN preorder_start_date  DATE    NULL;
ALTER TABLE products ADD COLUMN accept_preorder      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN accept_backorder     BOOLEAN NOT NULL DEFAULT FALSE;

-- ----------------------------------------------------------------------------
-- フェーズ14.5 P2: products.price / products.stock を NULL 許容に揃える
--   フェーズ10で価格・在庫は SKU 側へ移行済（TBL_products.md §カラム定義 #4-5）。
--   旧 NOT NULL 制約が本番 MySQL に残ったままで Console UI 経由の登録が
--   500 (MySQL 1048 Column 'price' cannot be null) になる不具合を解消する。
--   詳細: docs/troubles/038_products_price_stock_not_null_drift.md
--   MODIFY は冪等。重複実行は continue-on-error で許容。
-- ----------------------------------------------------------------------------
ALTER TABLE products MODIFY COLUMN price INT NULL;
ALTER TABLE products MODIFY COLUMN stock INT NULL;

-- ============================================================================
-- フェーズ15: 配送管理（設計書 phase15_delivery_management.md r5）
--   1. shipping_methods マスタ（P5-1）
--   2. warehouses マスタ + ダミー1行（RRR-3）
--   3. inventories（並行運用書き込み正本／RRRR-1）
--   4. 既存 products.stock を inventories に初期複製（RRRR-1）
--   5. inbounds（入荷管理／R-3）
--   6. deliveries（配送実体／RR-3 / R-1 / R-9）
--   注: H2 互換のため CREATE INDEX は分離・ALTER ADD CONSTRAINT も分離。
--       重複実行は spring.sql.init.continue-on-error で許容（test_insights カテゴリ7-2）。
-- ============================================================================

-- 1. shipping_methods マスタ（P5-1）
CREATE TABLE IF NOT EXISTS shipping_methods (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) NULL
);
INSERT IGNORE INTO shipping_methods (id, name, description) VALUES
    (1, 'home_delivery',  '宅配'),
    (2, 'konbini_pickup', 'コンビニ受取'),
    (3, 'dropoff',        '置き配');

-- 2. warehouses マスタ + ダミー1行（RRR-3）
CREATE TABLE IF NOT EXISTS warehouses (
    id          BIGINT NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL
);
INSERT IGNORE INTO warehouses (id, name, description) VALUES (1, 'default', '全社単一倉庫');

-- 3. inventories（並行運用書き込み正本／RRRR-1）
CREATE TABLE IF NOT EXISTS inventories (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL DEFAULT 1,
    quantity     INT NOT NULL,
    updated_at   DATETIME NOT NULL,
    CONSTRAINT uk_inventories_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT fk_inventories_product   FOREIGN KEY (product_id)   REFERENCES products(id),
    CONSTRAINT fk_inventories_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);
CREATE INDEX idx_inventories_product_id ON inventories (product_id);
ALTER TABLE inventories ADD CONSTRAINT chk_inventories_quantity_nonneg CHECK (quantity >= 0);

-- 4. 既存 products.stock を inventories に初期複製（並行運用初期同期 / RRRR-1）
--    INSERT IGNORE で UNIQUE 制約により再実行しても二重投入されない。
INSERT IGNORE INTO inventories (product_id, warehouse_id, quantity, updated_at)
SELECT p.id, 1, COALESCE(p.stock, 0), CURRENT_TIMESTAMP
FROM products p;

-- 5. inbounds（入荷管理／R-3）
CREATE TABLE IF NOT EXISTS inbounds (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL DEFAULT 1,
    supplier_id  BIGINT NULL,
    quantity     INT NOT NULL,
    inbounded_at DATE NOT NULL,
    created_at   DATETIME NOT NULL,
    updated_at   DATETIME NOT NULL,
    CONSTRAINT fk_inbounds_product   FOREIGN KEY (product_id)   REFERENCES products(id),
    CONSTRAINT fk_inbounds_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);
CREATE INDEX idx_inbounds_product_id   ON inbounds (product_id);
CREATE INDEX idx_inbounds_inbounded_at ON inbounds (inbounded_at);
ALTER TABLE inbounds ADD CONSTRAINT chk_inbounds_quantity_pos CHECK (quantity > 0);

-- 6. deliveries（配送実体／RR-3 / R-1 / R-9）
CREATE TABLE IF NOT EXISTS deliveries (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sales_id             BIGINT NOT NULL,
    shipping_address_id  BIGINT UNSIGNED NOT NULL,
    shipping_method_id   BIGINT NOT NULL,
    shipping_status_id   BIGINT NOT NULL,
    tracking_code        VARCHAR(100) NULL,
    scheduled_date       DATE NULL,
    shipped_date         DATE NULL,
    delivered_date       DATE NULL,
    created_at           DATETIME NOT NULL,
    updated_at           DATETIME NOT NULL,
    CONSTRAINT uk_deliveries_sales_id UNIQUE (sales_id),
    CONSTRAINT fk_deliveries_sales            FOREIGN KEY (sales_id)            REFERENCES sales(id),
    CONSTRAINT fk_deliveries_address          FOREIGN KEY (shipping_address_id) REFERENCES address(id),
    CONSTRAINT fk_deliveries_shipping_method  FOREIGN KEY (shipping_method_id)  REFERENCES shipping_methods(id),
    CONSTRAINT fk_deliveries_shipping_status  FOREIGN KEY (shipping_status_id)  REFERENCES shipping_statuses(id)
);
CREATE INDEX idx_deliveries_shipping_status_id ON deliveries (shipping_status_id);
CREATE INDEX idx_deliveries_tracking_code      ON deliveries (tracking_code);
CREATE INDEX idx_deliveries_scheduled_date     ON deliveries (scheduled_date);

-- 7. sales.shipping_method_id への FK 追加
--    schema.sql L221 で「phase15 で shipping_methods 作成後に追加」と保留されていた制約。
--    shipping_methods マスタ実体化により FK を有効化。
ALTER TABLE sales ADD CONSTRAINT fk_sales_shipping_method FOREIGN KEY (shipping_method_id) REFERENCES shipping_methods(id);

-- ============================================================================
-- フェーズ16 Step1: 商品 Market 露出スイッチ
--   設計書: docs/design/phase11_20/phase16_ui_ux_improvement.md §Step 1
--   公開期間 (publish_start / publish_end) とは独立した、Market 露出 ON/OFF の手動スイッチ。
--   既存全件は TRUE 既定で挙動互換。重複実行は continue-on-error で許容。
-- ============================================================================
ALTER TABLE products ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- ============================================================================
-- フェーズ16 Step6-6: 入荷の追跡番号
--   設計書: docs/design/phase11_20/phase16_ui_ux_improvement.md §Step 6-6
--   Excel 一括入荷で取り込んだ配送追跡番号を inbounds に保持する。
--   手動入荷では NULL のまま。既存行は NULL で互換。重複実行は continue-on-error で許容。
-- ============================================================================
ALTER TABLE inbounds ADD COLUMN tracking_code VARCHAR(255) NULL;

-- ============================================================================
-- フェーズ16.5 Step 5: カート機能（正式実装）
--   設計書: docs/design/phase11_20/phase16_5_market_ui_improvement.md §Step 5
--   Market が単品購入のみだった状態から、複数 SKU をまとめて Checkout する一般 EC 体験へ。
--   - carts: 1顧客1カート（UNIQUE customer_id）
--   - cart_items: 同一 SKU・同一 is_preorder は1行に集約（UNIQUE 制約）。数量加算で重複回避。
--   FK 型注意: market_customers.id は BIGINT UNSIGNED、product_skus.id は BIGINT。
-- ============================================================================
CREATE TABLE IF NOT EXISTS carts (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_carts_customer UNIQUE (customer_id),
    CONSTRAINT fk_carts_customer FOREIGN KEY (customer_id) REFERENCES market_customers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cart_items (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY,
    cart_id     BIGINT   NOT NULL,
    sku_id      BIGINT   NOT NULL,
    quantity    INT      NOT NULL DEFAULT 1,
    is_preorder BOOLEAN  NOT NULL DEFAULT FALSE,
    added_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cart_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uk_cart_items_cart_sku_preorder UNIQUE (cart_id, sku_id, is_preorder),
    INDEX idx_cart_items_cart (cart_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_sku FOREIGN KEY (sku_id) REFERENCES product_skus(id)
);

-- ============================================================================
-- フェーズ17 Step 1: バッチ処理基盤
--   設計書: docs/design/phase11_20/phase17_batch_processing.md (r8 / 2026-05-08)
--   実装計画: docs/implementation/phase17_implementation_plan.md §2
--   重複実行は spring.sql.init.continue-on-error=true で許容（test_insights カテゴリ7-2）。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1-1: batch_executions（バッチ実行履歴 / 設計書 §5.1）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS batch_executions (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_name        VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    started_at      DATETIME     NOT NULL,
    finished_at     DATETIME     NULL,
    target_count    INT          NULL,
    success_count   INT          NULL,
    failure_count   INT          NULL,
    error_summary   TEXT         NULL,
    triggered_by    VARCHAR(50)  NOT NULL,
    created_at      DATETIME     NOT NULL
);
CREATE INDEX idx_batch_executions_job_started ON batch_executions (job_name, started_at);
CREATE INDEX idx_batch_executions_status      ON batch_executions (status);

-- ----------------------------------------------------------------------------
-- 1-2: console_notifications（通知センター / 設計書 §5.2）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS console_notifications (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    level                       VARCHAR(10)  NOT NULL,
    target_subscription_tag     VARCHAR(50)  NOT NULL,
    target_user_id              BIGINT       NULL,
    title                       VARCHAR(200) NOT NULL,
    body                        TEXT         NOT NULL,
    payload_hash                VARCHAR(64)  NOT NULL,
    suppressed                  BOOLEAN      NOT NULL DEFAULT FALSE,
    digest_sent_at              DATETIME     NULL,
    read_by_user_id             BIGINT       NULL,
    read_at                     DATETIME     NULL,
    source_job                  VARCHAR(100) NULL,
    source_batch_execution_id   BIGINT       NULL,
    created_at                  DATETIME     NOT NULL,
    CONSTRAINT fk_console_notifications_batch FOREIGN KEY (source_batch_execution_id) REFERENCES batch_executions(id)
);
CREATE INDEX idx_cn_tag_unread        ON console_notifications (target_subscription_tag, read_by_user_id, created_at);
CREATE INDEX idx_cn_user_unread       ON console_notifications (target_user_id, read_by_user_id, created_at);
CREATE INDEX idx_cn_payload_hash      ON console_notifications (payload_hash, created_at);
CREATE INDEX idx_cn_suppressed_digest ON console_notifications (suppressed, digest_sent_at, created_at);

-- ----------------------------------------------------------------------------
-- 1-3: notification_subscriptions（設計書 §6.2.1）
--   user_id は users(id) に合わせて BIGINT UNSIGNED。
--   既存 admin/senior_admin/eternal_advisor の users 全員に全タグ自動購読
--   （CSV 環境変数で外出しする値だが、bootstrap は素直に IN 句で記述）。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_subscriptions (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT UNSIGNED NOT NULL,
    subscription_tag  VARCHAR(50) NOT NULL,
    email_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    in_app_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        DATETIME    NOT NULL,
    updated_at        DATETIME    NOT NULL,
    CONSTRAINT uk_ns_user_tag UNIQUE (user_id, subscription_tag),
    CONSTRAINT fk_ns_user     FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_ns_subscription_tag ON notification_subscriptions (subscription_tag);

INSERT IGNORE INTO notification_subscriptions (user_id, subscription_tag, email_enabled, in_app_enabled, created_at, updated_at)
SELECT u.id, t.tag, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM users u
  CROSS JOIN (
       SELECT 'inventory_alerts' AS tag UNION ALL
       SELECT 'sales_alerts'           UNION ALL
       SELECT 'delivery_alerts'        UNION ALL
       SELECT 'postal_alerts'          UNION ALL
       SELECT 'batch_failure'
  ) t
 WHERE u.role_id IN (
       SELECT id FROM roles WHERE code IN ('admin', 'senior_admin', 'eternal_advisor')
 );

-- ----------------------------------------------------------------------------
-- 1-4: fault_injection_logs（設計書 §5.3 / 五重防御の DB CHECK 層）
--   environment は dev / staging のみ許可。本番 INSERT は CHECK 制約で物理拒否。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fault_injection_logs (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    injector_name   VARCHAR(100) NOT NULL,
    triggered_at    DATETIME     NOT NULL,
    triggered_by    VARCHAR(50)  NOT NULL,
    environment     VARCHAR(20)  NOT NULL,
    target_summary  TEXT         NULL,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT chk_fault_logs_no_prod CHECK (environment IN ('dev', 'staging'))
);
CREATE INDEX idx_fil_injector_created ON fault_injection_logs (injector_name, created_at);

-- ----------------------------------------------------------------------------
-- 1-5: monthly_sales_reports / yearly_sales_reports（設計書 §5.4 / R-15）
--   集計軸 NULL 運用（r8）。UNIQUE + UPSERT で同月二重 INSERT を防止。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS monthly_sales_reports (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `year`              SMALLINT NOT NULL,
    `month`             TINYINT  NOT NULL,
    product_id          BIGINT   NULL,
    payment_method_id   BIGINT   NULL,
    shipping_method_id  BIGINT   NULL,
    is_preorder         BOOLEAN  NULL,
    total_amount        BIGINT   NOT NULL,
    total_quantity      INT      NOT NULL,
    created_at          DATETIME NOT NULL,
    CONSTRAINT uk_msr_axes UNIQUE (`year`, `month`, product_id, payment_method_id, shipping_method_id, is_preorder)
);

CREATE TABLE IF NOT EXISTS yearly_sales_reports (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `year`              SMALLINT NOT NULL,
    product_id          BIGINT   NULL,
    payment_method_id   BIGINT   NULL,
    shipping_method_id  BIGINT   NULL,
    is_preorder         BOOLEAN  NULL,
    total_amount        BIGINT   NOT NULL,
    total_quantity      INT      NOT NULL,
    created_at          DATETIME NOT NULL,
    CONSTRAINT uk_ysr_axes UNIQUE (`year`, product_id, payment_method_id, shipping_method_id, is_preorder)
);

-- ----------------------------------------------------------------------------
-- 1-6: 価格スケジュール（設計書 §3.1 ⑥ / §13.5）
--   product_sku_prices.is_active 追加 + product_sku_scheduled_prices 新設。
-- ----------------------------------------------------------------------------
ALTER TABLE product_sku_prices ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_product_sku_prices_active ON product_sku_prices (sku_id, is_active);

CREATE TABLE IF NOT EXISTS product_sku_scheduled_prices (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku_id           BIGINT  NOT NULL,
    scheduled_price  INT     NOT NULL,
    apply_date       DATE    NOT NULL,
    is_pending       BOOLEAN NOT NULL DEFAULT TRUE,
    applied_at       DATETIME NULL,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    CONSTRAINT fk_pssp_sku FOREIGN KEY (sku_id) REFERENCES product_skus(id),
    CONSTRAINT chk_pssp_price_nonneg CHECK (scheduled_price >= 0)
);
CREATE INDEX idx_pssp_apply_pending ON product_sku_scheduled_prices (apply_date, is_pending);
CREATE INDEX idx_pssp_sku_pending   ON product_sku_scheduled_prices (sku_id, is_pending);

-- ----------------------------------------------------------------------------
-- 1-8: アーカイブテーブル 2 種（設計書 §3.3 ② / ③）
--   Step 4-4 (OperationLogArchiveJob) / Step 4-5 (ConsoleNotificationsArchiveJob)
--   t3.micro のディスク圧迫を抑えるため、アーカイブ先は PK + 最低限のインデックス 1 本のみ。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS operation_logs_archive (
    id           BIGINT          NOT NULL PRIMARY KEY,        -- 元 operation_logs.id を保持（INSERT → DELETE で同 id）
    user_id      BIGINT UNSIGNED NOT NULL,
    action       VARCHAR(100)    NOT NULL,
    target_type  VARCHAR(50)     NULL,
    target_id    BIGINT          NULL,
    screen_name  VARCHAR(100)    NULL,
    api_name     VARCHAR(100)    NULL,
    comment      TEXT            NULL,
    created_at   DATETIME        NOT NULL,
    archived_at  DATETIME        NOT NULL
);
CREATE INDEX idx_ola_created_at ON operation_logs_archive (created_at);

CREATE TABLE IF NOT EXISTS console_notifications_archive (
    id                          BIGINT       NOT NULL PRIMARY KEY,  -- 元 console_notifications.id を保持
    level                       VARCHAR(10)  NOT NULL,
    target_subscription_tag     VARCHAR(50)  NOT NULL,
    target_user_id              BIGINT       NULL,
    title                       VARCHAR(200) NOT NULL,
    body                        TEXT         NOT NULL,
    payload_hash                VARCHAR(64)  NOT NULL,
    suppressed                  BOOLEAN      NOT NULL,
    digest_sent_at              DATETIME     NULL,
    read_by_user_id             BIGINT       NULL,
    read_at                     DATETIME     NULL,
    source_job                  VARCHAR(100) NULL,
    source_batch_execution_id   BIGINT       NULL,
    created_at                  DATETIME     NOT NULL,
    archived_at                 DATETIME     NOT NULL
);
CREATE INDEX idx_cna_tag_created ON console_notifications_archive (target_subscription_tag, created_at);

-- ----------------------------------------------------------------------------
-- 1-7: SKU TX bootstrap 投入（H-9 / 設計書 §13.2）
--   既存 product_sku_stocks.quantity を SKU TX に type='adjust' で1件ずつ初期反映。
--   schema.sql L357-361 (inventories 初期複製 / phase15 RRRR-1) と対になる初期化。
--   INSERT IGNORE + reference_type='bootstrap' の二重保証で再実行で重複しない（J-7）。
--   type の実値は application.properties amazia.sales.sku-stock-tx-types.adjust と同じ
--   'adjust'（G-1）。schema.sql 内では @Value を使えないためリテラルで記述。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO product_sku_stock_transactions
    (sku_id, type, quantity, reference_type, reference_id, created_by_user_id, comment, created_at)
SELECT s.sku_id, 'adjust', s.quantity, 'bootstrap', NULL, NULL,
       '[bootstrap] initial inventory', CURRENT_TIMESTAMP
  FROM product_sku_stocks s
 WHERE s.quantity > 0;
