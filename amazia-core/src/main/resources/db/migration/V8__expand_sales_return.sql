-- フェーズ14 Step A: sales_return テーブル拡張
--
-- 設計書: docs/design/phase11_20/phase14_shipping.md（r4）
--
-- r4 の sales_return 拡張：
--   - quantity INT NOT NULL CHECK (quantity > 0)（返品数量）

ALTER TABLE sales_return
    ADD COLUMN quantity INT NOT NULL AFTER reason,
    ADD CONSTRAINT chk_sales_return_quantity_positive CHECK (quantity > 0);
