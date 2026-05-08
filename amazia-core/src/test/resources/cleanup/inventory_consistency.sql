-- phaseX-9 Step 4: InventoryConsistencyCheckJobTest 用 cleanup
-- 本クラスは「自テスト productId フィルタ + 前後差分」で既に分離設計済みだが、
-- 規約統一のため operation_logs / console_notifications の他テスト残置を除去して
-- 観測ノイズを最小化する。products / product_skus / inventories は他テストの
-- fixture が共存しうるため対象外。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE operation_logs;
TRUNCATE TABLE console_notifications;
SET REFERENTIAL_INTEGRITY TRUE;
