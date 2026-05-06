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
CREATE TABLE IF NOT EXISTS sales_return (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    sales_id        BIGINT       NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    reason          TEXT         NULL,
    quantity        INT          NOT NULL,
    approver_id     BIGINT       NULL,
    approved_at     DATETIME     NULL,
    notified_user   BOOLEAN      NOT NULL DEFAULT FALSE,
    notified_admin  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_sales_return_quantity_positive CHECK (quantity > 0),
    INDEX idx_sales_return_sales_id (sales_id),
    INDEX idx_sales_return_status (status),
    CONSTRAINT fk_sales_return_sales FOREIGN KEY (sales_id) REFERENCES sales(id),
    CONSTRAINT fk_sales_return_approver FOREIGN KEY (approver_id) REFERENCES users(id)
);

-- 操作履歴（V6 + V10 相当：screen_name / api_name 含む）
CREATE TABLE IF NOT EXISTS operation_logs (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
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
