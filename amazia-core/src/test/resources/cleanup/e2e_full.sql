-- phase17 Step 8 E2E 用 cleanup（phaseX-9 規約 7-2 準拠）
-- 不整合→バッチ→SES→通知センターの 1 シナリオを再現可能に保つため、
-- 観測対象のテーブル群（batch_executions / console_notifications /
-- notification_subscriptions / operation_logs / fault_injection_logs）を
-- 各テスト前に TRUNCATE する。products / inventories / SKU TX は
-- 自テスト ID で fixture を発番する（System.nanoTime() ベース）ため対象外。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE batch_executions;
TRUNCATE TABLE console_notifications;
TRUNCATE TABLE notification_subscriptions;
TRUNCATE TABLE operation_logs;
TRUNCATE TABLE fault_injection_logs;
SET REFERENTIAL_INTEGRITY TRUE;
