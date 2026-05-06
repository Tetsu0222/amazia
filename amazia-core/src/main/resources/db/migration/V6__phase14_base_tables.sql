-- フェーズ14: 購入機能 ベーステーブル（Step 0-5）
--
-- 設計書: docs/design/phase11_20/phase14_shipping.md（r4）
-- 実装計画: docs/implementation/phase14_implementation_plan.md
--
-- 方針:
--  - 業務テーブルは Core 側 Flyway に集約（既存 V1〜V5 と整合）
--  - market_customers.id は BIGINT UNSIGNED のため、FK 側も BIGINT UNSIGNED で揃える
--  - 拡張カラム（sales.sku_id / quantity / payment_id UNIQUE / shipping_method_id 等）は Step A で別 SQL ファイル追加
--  - shipping_statuses は base 形のみ INSERT。CANCELED / DELIVERY_FAILED / RESCHEDULED は Step A で追加

-- 配送先住所スナップショットマスタ
-- 注文時に market_customers の現住所から複製した1行を保持。会員住所と完全に分離。
CREATE TABLE address (
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

-- 決済方法マスタ
CREATE TABLE payment_methods (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO payment_methods (id, name, description) VALUES
    (1, 'credit_card',      'クレジットカード'),
    (2, 'd_payment',        'd払い'),
    (3, 'cash_on_delivery', '代引き');

-- 配送ステータスマスタ（base 形）
-- CANCELED / DELIVERY_FAILED / RESCHEDULED は Step A で追加
CREATE TABLE shipping_statuses (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO shipping_statuses (id, code, name, description) VALUES
    (1, 'PENDING',          '配送準備中',  '注文確定後・出荷前'),
    (2, 'SHIPPED',           '配送済',      '発送完了'),
    (3, 'DELIVERED',         '配送完了',    '配達完了'),
    (4, 'RETURN_REQUESTED',  '返品申請中',  '返品申請を受付中'),
    (5, 'RETURNED',          '返品完了',    '返品処理完了');

-- 売上テーブル（base 形）
-- sku_id / quantity / amount / payment_method_id / shipping_method_id / shipping_address_id /
-- shipping_status_id / payment_id UNIQUE / is_preorder は Step A で追加
CREATE TABLE sales (
    id           BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT UNSIGNED NOT NULL,
    sales_date   DATE            NOT NULL,
    shipping_date DATE           NULL,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_user_id (user_id),
    INDEX idx_sales_sales_date (sales_date),
    CONSTRAINT fk_sales_user FOREIGN KEY (user_id) REFERENCES market_customers(id)
);

-- 返品テーブル（base 形）
-- quantity は Step A で追加
CREATE TABLE sales_return (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    sales_id        BIGINT       NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    reason          TEXT         NULL,
    approver_id     BIGINT       NULL,
    approved_at     DATETIME     NULL,
    notified_user   BOOLEAN      NOT NULL DEFAULT FALSE,
    notified_admin  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_return_sales_id (sales_id),
    INDEX idx_sales_return_status (status),
    CONSTRAINT fk_sales_return_sales FOREIGN KEY (sales_id) REFERENCES sales(id),
    CONSTRAINT fk_sales_return_approver FOREIGN KEY (approver_id) REFERENCES users(id)
);

-- 操作履歴テーブル（base 形）
-- screen_name / api_name は Step A で追加
CREATE TABLE operation_logs (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(50)  NULL,
    target_id   BIGINT       NULL,
    comment     TEXT         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operation_logs_user_id (user_id),
    INDEX idx_operation_logs_action (action),
    INDEX idx_operation_logs_target (target_type, target_id),
    INDEX idx_operation_logs_created_at (created_at),
    CONSTRAINT fk_operation_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);
