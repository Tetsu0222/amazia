-- フェーズ14 Step A: product_sku_stock_transactions テーブル拡張
--
-- 設計書: docs/design/phase11_20/phase14_shipping.md（r4）
--
-- 背景：
--   フェーズ10 で実装された product_sku_stock_transactions は
--   id / sku_id / type / quantity / created_at の base 形のみ。
--   phase14 r4 では「sale」「return」等の販売系増減もこのテーブルに記録するため、
--   逆引き用カラムと監査用カラムを追加する。
--
-- r4 で追加するカラム：
--   - reference_type VARCHAR(50) NULL  …元レコード種別（sales / sales_return / inbound 等）
--   - reference_id   BIGINT NULL       …元レコード ID
--   - created_by_user_id BIGINT NULL   …操作した管理者の users.id（NULL = バッチ自動 or 注文者起点）
--   - comment TEXT NULL                …棚卸補正等の自由記述
--
-- type の許容値拡張：
--   既存: receive / adjust
--   追加: sale / return / cancel
--   ※ 既存 ProductSkuStockTransaction Entity は type を String で持つため、
--      DB 側で enum 制約は付けない（Service 層で許容値チェックする）

ALTER TABLE product_sku_stock_transactions
    ADD COLUMN reference_type     VARCHAR(50) NULL AFTER quantity,
    ADD COLUMN reference_id       BIGINT      NULL AFTER reference_type,
    ADD COLUMN created_by_user_id BIGINT      NULL AFTER reference_id,
    ADD COLUMN comment            TEXT        NULL AFTER created_by_user_id;

-- 逆引き用インデックス（reference から元レコードを引く）
CREATE INDEX idx_sku_stock_tx_reference ON product_sku_stock_transactions (reference_type, reference_id);

-- 監査用インデックス
CREATE INDEX idx_sku_stock_tx_created_by ON product_sku_stock_transactions (created_by_user_id);
CREATE INDEX idx_sku_stock_tx_created_at ON product_sku_stock_transactions (created_at);
