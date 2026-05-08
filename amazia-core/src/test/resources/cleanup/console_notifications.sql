-- phaseX-9 Step 4: DeliveryStatusAdvanceJobTest / SalesReconciliationJobTest 共有 cleanup
-- BatchAlertNotifier は REQUIRES_NEW で console_notifications に書き込むため、
-- @Transactional ロールバックを貫通する。SUBSCRIPTION_TAG フィルタで件数差分を見ているが、
-- 規約統一のため各テスト前に当該テーブルを TRUNCATE して観測ノイズを最小化する。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE console_notifications;
SET REFERENTIAL_INTEGRITY TRUE;
