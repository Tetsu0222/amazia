-- フェーズ12: ワークフロー（承認フロー）機能用テーブル

CREATE TABLE workflow_requests (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type   VARCHAR(20)  NOT NULL,                  -- product / price / stock
    target_id     BIGINT       NOT NULL,
    requested_by  BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL,                  -- pending / approved / rejected / canceled
    payload       JSON         NOT NULL,
    completed_at  DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workflow_status (status),
    INDEX idx_workflow_target (target_type, target_id),
    INDEX idx_workflow_requester (requested_by),
    FOREIGN KEY (requested_by) REFERENCES users(id)
);

CREATE TABLE workflow_requests_detail (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_requests_id BIGINT       NOT NULL,
    step_number          INT          NOT NULL,
    target_role          VARCHAR(30)  NOT NULL,
    destination_user_id  BIGINT       NULL,
    destination_name     VARCHAR(100) NULL,
    approver_user_id     BIGINT       NULL,
    approver_name        VARCHAR(100) NULL,
    status               VARCHAR(20)  NOT NULL,           -- waiting / pending / approved / rejected / canceled
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wfd_parent_step (workflow_requests_id, step_number),
    INDEX idx_wfd_destination (destination_user_id),
    FOREIGN KEY (workflow_requests_id) REFERENCES workflow_requests(id) ON DELETE CASCADE
);

-- 楽観ロック用 version カラム追加
ALTER TABLE products            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE product_sku_prices  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE product_sku_stocks  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
