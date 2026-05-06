-- フェーズ13: Amazia Market ログイン・会員登録機能用テーブル
--
-- 設計書: docs/design/phase11_20/phase13_market_auth.md
--
-- 方針:
--  - 既存 users（社員）テーブルとは責務分離のため、Market 顧客は market_customers として独立管理
--  - セッションストアは Redis を採用せず DB 直（無料枠完走方針 / X-4 メモリ枠の保護）
--  - パスワード再発行トークンは bcrypt ハッシュ化して保存（生トークンはメール内 URL のみに存在）
--  - users.id（Console）は BIGINT UNSIGNED のため、Market 側 FK も BIGINT UNSIGNED で揃える
--  - 027 教訓に従い、本ファイルは MySQL 専用構文を許容（テスト H2 では schema-locations 空で除外済）

-- 顧客マスタ
CREATE TABLE market_customers (
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

-- パスワード履歴（過去 5 回分の再利用不可検証用）
CREATE TABLE market_customer_password_histories (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    customer_id   BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mcph_customer (customer_id),
    CONSTRAINT fk_mcph_customer FOREIGN KEY (customer_id) REFERENCES market_customers(id) ON DELETE CASCADE
);

-- パスワード再発行トークン
CREATE TABLE market_customers_password_reset_tokens (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT UNSIGNED NOT NULL,
    token_hash  VARCHAR(255)    NOT NULL UNIQUE,
    expires_at  DATETIME        NOT NULL,
    used        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mcprt_customer (customer_id),
    CONSTRAINT fk_mcprt_customer FOREIGN KEY (customer_id) REFERENCES market_customers(id) ON DELETE CASCADE
);

-- セッションストア
-- session_id は UUID v4 文字列（Cookie 値）。csrf_token もセッションに紐付ける。
CREATE TABLE market_sessions (
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

-- 住所マスタ（郵便局 KEN_ALL.CSV 取込先）
-- 1 郵便番号に複数町域が紐づくため (postal_code) は UNIQUE にしない。
CREATE TABLE postal_addresses (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    postal_code VARCHAR(8)   NOT NULL,
    prefecture  VARCHAR(20)  NOT NULL,
    city        VARCHAR(100) NOT NULL,
    town        VARCHAR(200) NOT NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pa_postal_code (postal_code),
    INDEX idx_pa_pref_city (prefecture, city)
);
