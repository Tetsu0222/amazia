-- フェーズ14 Step A: sales テーブル拡張
--
-- 設計書: docs/design/phase11_20/phase14_shipping.md（r4）
-- 実装計画: docs/implementation/phase14_implementation_plan.md
--
-- r4 の sales 仕様：
--   - sku_id BIGINT NOT NULL（FK to product_skus.id／r4 で product_id から SKU 化）
--   - quantity INT NOT NULL CHECK (quantity > 0)
--   - amount INT NOT NULL（amount = product_sku_prices.price × quantity）
--   - payment_method_id BIGINT NOT NULL（FK to payment_methods.id）
--   - shipping_method_id BIGINT NOT NULL（FK to shipping_methods.id）
--     ※ shipping_methods テーブル本体は phase15 で作成。FK 制約は phase15 完了時に追加
--   - shipping_address_id BIGINT NOT NULL（FK to address.id）
--   - shipping_status_id BIGINT NOT NULL（FK to shipping_statuses.id）
--   - payment_id VARCHAR(100) NOT NULL UNIQUE
--   - is_preorder BOOLEAN NOT NULL

ALTER TABLE sales
    ADD COLUMN sku_id              BIGINT       NOT NULL AFTER user_id,
    ADD COLUMN quantity            INT          NOT NULL AFTER sku_id,
    ADD COLUMN amount              INT          NOT NULL AFTER quantity,
    ADD COLUMN payment_method_id   BIGINT       NOT NULL AFTER amount,
    ADD COLUMN shipping_method_id  BIGINT       NOT NULL AFTER payment_method_id,
    ADD COLUMN shipping_address_id BIGINT UNSIGNED NOT NULL AFTER shipping_method_id,
    ADD COLUMN shipping_status_id  BIGINT       NOT NULL AFTER shipping_address_id,
    ADD COLUMN payment_id          VARCHAR(100) NOT NULL AFTER shipping_status_id,
    ADD COLUMN is_preorder         BOOLEAN      NOT NULL DEFAULT FALSE AFTER payment_id,
    ADD CONSTRAINT chk_sales_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT uk_sales_payment_id UNIQUE (payment_id);

-- インデックス追加（売上集計用）
CREATE INDEX idx_sales_sku_id ON sales (sku_id);
CREATE INDEX idx_sales_payment_method_id ON sales (payment_method_id);
CREATE INDEX idx_sales_shipping_method_id ON sales (shipping_method_id);
CREATE INDEX idx_sales_shipping_status_id ON sales (shipping_status_id);

-- FK 追加
-- sku_id: product_skus.id（既存テーブル／フェーズ10）
ALTER TABLE sales
    ADD CONSTRAINT fk_sales_sku FOREIGN KEY (sku_id) REFERENCES product_skus(id);

-- payment_method_id: payment_methods.id（V6 で作成）
ALTER TABLE sales
    ADD CONSTRAINT fk_sales_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id);

-- shipping_status_id: shipping_statuses.id（V6 で作成）
ALTER TABLE sales
    ADD CONSTRAINT fk_sales_shipping_status FOREIGN KEY (shipping_status_id) REFERENCES shipping_statuses(id);

-- shipping_address_id: address.id（V6 で作成）
ALTER TABLE sales
    ADD CONSTRAINT fk_sales_shipping_address FOREIGN KEY (shipping_address_id) REFERENCES address(id);

-- shipping_method_id の FK は phase15 で shipping_methods テーブル作成後に追加（暫定で FK 制約なし）
