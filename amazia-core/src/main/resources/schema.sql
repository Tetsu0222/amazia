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
