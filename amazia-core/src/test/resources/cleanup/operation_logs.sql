-- phaseX-9 Step 4: BatchManualTriggerControllerTest.Enabled 用 cleanup
-- OperationLogger は REQUIRES_NEW で operation_logs に書き込むため、他テストの残置が
-- ロールバックを貫通して件数アサーションを崩す。trigger_batch_manual action 件数を
-- 正確に観測できるよう各テスト前に当該テーブルを TRUNCATE する。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE operation_logs;
SET REFERENTIAL_INTEGRITY TRUE;
