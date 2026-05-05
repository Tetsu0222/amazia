-- 冪等なスキーマ定義
-- 既存環境向けに、足りないテーブル・カラムを起動時に保証する。
-- ALTER の重複実行エラーは spring.sql.init.continue-on-error=true で無視する。

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
