-- 冪等なスキーマ定義（MySQL 本番環境向け）
-- 既存環境向けに、足りないテーブル・カラムを起動時に保証する。
-- 重複実行エラー（カラム/インデックス既存など）は spring.sql.init.continue-on-error=true で無視する。
--
-- テスト環境(H2)では application-test.properties で schema-locations を空にして
-- このスクリプトを読み込まない。テストは ddl-auto=create-drop で JPA が生成する。

-- ============================================================================
-- 認証ドメイン（V1 マイグレーション相当 / 044 派生節 (2)(3) で発覚した未移植 DDL）
--   過去は永続ボリュームに残った Hibernate ddl-auto=update 作成済テーブルに依存していたが、
--   docker compose down -v でボリュームを破棄すると消失する設計欠陥があったため schema.sql に書き起こす。
-- users.id が BIGINT UNSIGNED で他テーブルから参照されている前提（044 派生節 (1) との整合）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50)  NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS permissions (
    id        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    screen_id VARCHAR(100) NOT NULL UNIQUE,
    name      VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_rp_permission (permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles(id),
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
);

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id     VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role_id         BIGINT UNSIGNED NOT NULL,
    active_flag     BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- ============================================================================
-- 商品ドメイン（044 派生節 (3) で発覚した未移植 DDL）
--   Entity: Product / ProductImage / ProductStatus / ProductSku / ProductSkuImage
--           ProductSkuPrice / ProductSkuPriceHistory / ProductSkuStock /
--           ProductSkuStockTransaction
--   旧来は ddl-auto=update で生成されていたものを Hibernate 出力からそのまま書き起こす。
--   FK は Hibernate が自動付与しないため idx_*_id インデックスのみ確保。
-- ============================================================================

CREATE TABLE IF NOT EXISTS product_statuses (
    code       VARCHAR(50)  NOT NULL PRIMARY KEY,
    name       VARCHAR(100) NULL,
    sort_order INT          NULL
);

CREATE TABLE IF NOT EXISTS products (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NULL,
    description   VARCHAR(255) NULL,
    price         INT          NULL,
    stock         INT          NULL,
    status_code   VARCHAR(50)  NULL,
    publish_start DATETIME(6)  NULL,
    publish_end   DATETIME(6)  NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NULL,
    updated_at    DATETIME(6)  NULL,
    INDEX idx_products_status (status_code)
);

CREATE TABLE IF NOT EXISTS product_images (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    image_path VARCHAR(300) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    INDEX idx_product_images_product (product_id)
);

CREATE TABLE IF NOT EXISTS product_skus (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    sku_code   VARCHAR(255) NOT NULL UNIQUE,
    color      VARCHAR(255) NULL,
    size       VARCHAR(255) NULL,
    status     VARCHAR(50)  NOT NULL,
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    UNIQUE KEY uk_skus_product_color_size (product_id, color, size),
    INDEX idx_skus_product (product_id)
);

CREATE TABLE IF NOT EXISTS product_sku_images (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    sku_id     BIGINT       NOT NULL,
    image_path VARCHAR(300) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    INDEX idx_sku_images_sku (sku_id)
);

CREATE TABLE IF NOT EXISTS product_sku_prices (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    sku_id     BIGINT      NOT NULL,
    price      INT         NOT NULL,
    start_date DATE        NULL,
    end_date   DATE        NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    INDEX idx_sku_prices_sku (sku_id)
);

CREATE TABLE IF NOT EXISTS product_sku_price_history (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    sku_id     BIGINT       NOT NULL,
    price      INT          NOT NULL,
    start_date DATE         NULL,
    end_date   DATE         NULL,
    status     VARCHAR(50)  NOT NULL,
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    INDEX idx_sku_price_history_sku (sku_id)
);

CREATE TABLE IF NOT EXISTS product_sku_stocks (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    sku_id     BIGINT      NOT NULL UNIQUE,
    quantity   INT         NOT NULL DEFAULT 0,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL
);

CREATE TABLE IF NOT EXISTS product_sku_stock_transactions (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    sku_id     BIGINT       NOT NULL,
    quantity   INT          NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    created_at DATETIME(6)  NULL,
    INDEX idx_sku_tx_sku (sku_id),
    INDEX idx_sku_tx_type (type),
    INDEX idx_sku_tx_created (created_at)
);

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

-- ============================================================================
-- フェーズ18: 問い合わせ管理（設計書 phase18_inquiry_management.md r3 / 規約 4-1）
--   実装計画: docs/implementation/phase18_implementation_plan.md §2
--   spring.sql.init.continue-on-error=true で再実行を許容（既存セクションと同方針）。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- フェーズ18 Step 1-1: inquiries（問い合わせ親 / 設計書 §3.1）
--   target_type / target_id は多態参照（FK は張らず Service 層で整合性検証）。
--   pair NULL CHECK + status / target_type の値域 CHECK で本番 MySQL 側に最後の砦を置く。
-- ----------------------------------------------------------------------------
-- 045 / 044 同型対策（型の一致）：
--   market_customers.id は BIGINT UNSIGNED（schema.sql §238）。MySQL の FK は型が完全
--   一致していないと作成失敗するため、user_id も BIGINT UNSIGNED で揃える必要がある。
--   当初 BIGINT（unsigned 無し）で書いた版が本番にデプロイされ、CREATE TABLE が
--   `continue-on-error=true` で WARN 化 → テーブル不在の不具合（045）を発生させた。
CREATE TABLE IF NOT EXISTS inquiries (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    subject     VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    target_type VARCHAR(20)  NULL,
    target_id   BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES market_customers(id),
    CONSTRAINT chk_inquiries_status      CHECK (status IN ('NEW', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT chk_inquiries_target_type CHECK (target_type IN ('delivery', 'product', 'sales') OR target_type IS NULL),
    CONSTRAINT chk_inquiries_target_pair CHECK (
        (target_type IS NULL     AND target_id IS NULL)
        OR (target_type IS NOT NULL AND target_id IS NOT NULL)
    )
);
CREATE INDEX idx_inquiries_status_updated_at  ON inquiries (status, updated_at);
CREATE INDEX idx_inquiries_user_id_updated_at ON inquiries (user_id, updated_at);
CREATE INDEX idx_inquiries_target             ON inquiries (target_type, target_id);

-- ----------------------------------------------------------------------------
-- フェーズ18 Step 1-2: inquiry_messages（スレッドメッセージ / 設計書 §3.2）
--   sender_type / sender_id は多態参照。is_internal_note は admin のみ（CHECK で物理担保）。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inquiry_messages (
    id               BIGINT  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inquiry_id       BIGINT  NOT NULL,
    sender_type      VARCHAR(20) NOT NULL,
    sender_id        BIGINT  NOT NULL,
    message          TEXT    NOT NULL,
    is_internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       DATETIME NOT NULL,
    CONSTRAINT fk_inquiry_messages_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id) ON DELETE CASCADE,
    CONSTRAINT chk_inquiry_messages_sender_type CHECK (sender_type IN ('market_customer', 'admin_user')),
    CONSTRAINT chk_inquiry_messages_internal_note_admin CHECK (is_internal_note = FALSE OR sender_type = 'admin_user')
);
CREATE INDEX idx_inquiry_messages_inquiry_id_created_at ON inquiry_messages (inquiry_id, created_at);

-- ----------------------------------------------------------------------------
-- フェーズ18 Step 1-3: notification_subscriptions への inquiry_alerts 自動投入（設計書 §3.3）
--   phase17 §1-1-3 の自動購読対象ロール CSV と整合（admin / senior_admin / eternal_advisor）。
--   schema.sql 内では @Value を使えないため対象ロール名をリテラルで列挙する。
--   INSERT IGNORE で再実行冪等。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO notification_subscriptions
    (user_id, subscription_tag, email_enabled, in_app_enabled, created_at, updated_at)
SELECT u.id, 'inquiry_alerts', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM users u
 WHERE u.role_id IN (
       SELECT id FROM roles WHERE code IN ('admin', 'senior_admin', 'eternal_advisor')
 );

-- ============================================================================
-- フェーズ19: お知らせ機能（設計書 phase19_notice_management.md r2）
--   - notice_categories（分類マスタ）/ notices（本体）/ notice_reads（既読管理）
--   - PK 型ポリシー（R19-1）：本テーブル群 PK / FK は BIGINT、users / market_customers
--     を参照する FK は BIGINT UNSIGNED で揃える（044 / 045 と同型対策）。
--   - H2 互換（test_insights カテゴリ7-2）：
--       * `ON UPDATE CURRENT_TIMESTAMP` は使わず JPA @PreUpdate で更新
--       * `CHECK (publish_start <= publish_end)` は H2 / MySQL 双方で通る構文
--       * INDEX はインライン宣言ではなく `CREATE INDEX IF NOT EXISTS` で別文化
-- ============================================================================

-- ----------------------------------------------------------------------------
-- フェーズ19 Step A-1: notice_categories（分類マスタ / 設計書 §DB 設計）
--   important / normal の 2 件を INSERT IGNORE で初期投入。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notice_categories (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(20) NOT NULL,
    label         VARCHAR(50) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_notice_categories_code UNIQUE (code)
);
INSERT IGNORE INTO notice_categories (id, code, label, display_order) VALUES
    (1, 'important', '重要', 1),
    (2, 'normal',    '普通', 2);

-- ----------------------------------------------------------------------------
-- フェーズ19 Step A-2: notices（お知らせ本体 / 設計書 §DB 設計）
--   - publish_start <= publish_end は CHECK で物理担保（Service 二重防御）。
--   - deleted_at NULL = アクティブ、NOT NULL = 論理削除済（YAGNI / deleted_flag 廃止）。
--   - author_id は users.id (BIGINT UNSIGNED) を参照。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notices (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject       VARCHAR(255) NOT NULL,
    category_id   BIGINT NOT NULL,
    body          TEXT NOT NULL,
    author_id     BIGINT UNSIGNED NOT NULL,
    publish_start DATETIME NOT NULL,
    publish_end   DATETIME NOT NULL,
    deleted_at    DATETIME NULL,
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    CONSTRAINT fk_notices_category FOREIGN KEY (category_id) REFERENCES notice_categories(id),
    CONSTRAINT fk_notices_author   FOREIGN KEY (author_id)   REFERENCES users(id),
    CONSTRAINT chk_notices_publish_period CHECK (publish_start <= publish_end)
);
CREATE INDEX IF NOT EXISTS idx_notices_publish_period ON notices (publish_start, publish_end);
CREATE INDEX IF NOT EXISTS idx_notices_category_id    ON notices (category_id);
CREATE INDEX IF NOT EXISTS idx_notices_deleted_at     ON notices (deleted_at);
CREATE INDEX IF NOT EXISTS idx_notices_author_id      ON notices (author_id);

-- ----------------------------------------------------------------------------
-- フェーズ19 Step A-3: notice_reads（既読管理 / 設計書 §DB 設計）
--   - (notice_id, market_customer_id) UNIQUE で重複既読を物理担保。
--   - お知らせ論理削除時も notice_reads は維持（参照履歴 / CASCADE DELETE 不採用）。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notice_reads (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    notice_id           BIGINT NOT NULL,
    market_customer_id  BIGINT UNSIGNED NOT NULL,
    read_at             DATETIME NOT NULL,
    created_at          DATETIME NOT NULL,
    CONSTRAINT uk_notice_reads_notice_customer UNIQUE (notice_id, market_customer_id),
    CONSTRAINT fk_notice_reads_notice   FOREIGN KEY (notice_id)          REFERENCES notices(id),
    CONSTRAINT fk_notice_reads_customer FOREIGN KEY (market_customer_id) REFERENCES market_customers(id)
);
CREATE INDEX IF NOT EXISTS idx_notice_reads_market_customer_id ON notice_reads (market_customer_id);

-- ============================================================================
-- フェーズX-5: 都道府県別リードタイムマスタ
--   設計書: docs/design/phaseX/phaseX-5_prefecture_based_lead_time.md
--   shipping_methods × 都道府県（address.prefecture と厳密一致）でリードタイム日数を保持。
--   未登録の組合せは application.properties の amazia.delivery.lead-time-days.* にフォールバック。
--   標準値: home_delivery=3 / konbini_pickup=4 / dropoff=2
--   離島加算 +2: 北海道 / 長崎県 / 鹿児島県 / 沖縄県（厳格4県）
--   重複実行は spring.sql.init.continue-on-error / INSERT IGNORE で冪等化。
-- ============================================================================
CREATE TABLE IF NOT EXISTS shipping_lead_times (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shipping_method_id BIGINT NOT NULL,
    prefecture         VARCHAR(20) NOT NULL,
    lead_time_days     INT NOT NULL,
    created_at         DATETIME NOT NULL,
    updated_at         DATETIME NOT NULL,
    CONSTRAINT uk_shipping_lead_times_method_pref UNIQUE (shipping_method_id, prefecture),
    CONSTRAINT fk_shipping_lead_times_method FOREIGN KEY (shipping_method_id) REFERENCES shipping_methods(id)
);
ALTER TABLE shipping_lead_times ADD CONSTRAINT chk_shipping_lead_times_days_nonneg CHECK (lead_time_days >= 0);

-- 47都道府県 × 3配送方法 = 141行
INSERT IGNORE INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES
    (1, '北海道',   5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '北海道',   6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '北海道',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '青森県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '青森県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '青森県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '岩手県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '岩手県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '岩手県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '宮城県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '宮城県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '宮城県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '秋田県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '秋田県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '秋田県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '山形県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '山形県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '山形県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '福島県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '福島県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '福島県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '茨城県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '茨城県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '茨城県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '栃木県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '栃木県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '栃木県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '群馬県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '群馬県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '群馬県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '埼玉県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '埼玉県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '埼玉県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '千葉県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '千葉県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '千葉県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '東京都',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '東京都',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '東京都',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '神奈川県', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '神奈川県', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '神奈川県', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '新潟県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '新潟県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '新潟県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '富山県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '富山県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '富山県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '石川県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '石川県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '石川県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '福井県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '福井県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '福井県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '山梨県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '山梨県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '山梨県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '長野県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '長野県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '長野県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '岐阜県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '岐阜県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '岐阜県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '静岡県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '静岡県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '静岡県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '愛知県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '愛知県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '愛知県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '三重県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '三重県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '三重県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '滋賀県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '滋賀県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '滋賀県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '京都府',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '京都府',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '京都府',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '大阪府',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '大阪府',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '大阪府',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '兵庫県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '兵庫県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '兵庫県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '奈良県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '奈良県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '奈良県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '和歌山県', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '和歌山県', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '和歌山県', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '鳥取県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '鳥取県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '鳥取県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '島根県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '島根県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '島根県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '岡山県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '岡山県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '岡山県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '広島県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '広島県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '広島県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '山口県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '山口県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '山口県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '徳島県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '徳島県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '徳島県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '香川県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '香川県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '香川県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '愛媛県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '愛媛県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '愛媛県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '高知県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '高知県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '高知県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '福岡県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '福岡県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '福岡県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '佐賀県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '佐賀県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '佐賀県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '長崎県',   5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '長崎県',   6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '長崎県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '熊本県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '熊本県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '熊本県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '大分県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '大分県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '大分県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '宮崎県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '宮崎県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '宮崎県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '鹿児島県', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '鹿児島県', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '鹿児島県', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, '沖縄県',   5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '沖縄県',   6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '沖縄県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_shipping_lead_times_method_id ON shipping_lead_times (shipping_method_id);
