-- フェーズ14 Step A: operation_logs テーブル拡張
--
-- 設計書: docs/design/phase11_20/phase14_shipping.md（r4）
-- 命名規約: docs/ai_context/operation_logs_naming.md
--
-- r4 で追加するカラム：
--   - screen_name VARCHAR(100) NULL（画面名／例: console.delivery.update_status）
--   - api_name    VARCHAR(100) NULL（API名／例: PATCH /api/deliveries/:id/status）
--
-- バッチ起点等で screen_name のみ・api_name のみとなるケースもあるため両方 NULL 許容。

ALTER TABLE operation_logs
    ADD COLUMN screen_name VARCHAR(100) NULL AFTER target_id,
    ADD COLUMN api_name    VARCHAR(100) NULL AFTER screen_name;

CREATE INDEX idx_operation_logs_screen_name ON operation_logs (screen_name);
CREATE INDEX idx_operation_logs_api_name ON operation_logs (api_name);
