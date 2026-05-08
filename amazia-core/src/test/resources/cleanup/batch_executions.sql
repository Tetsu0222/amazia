-- phaseX-9 Step 4: batch_executions の他テスト残置を掃除
-- @SpringBootTest の H2 共有 DB で scheduler-enabled 系の他テストが REQUIRES_NEW 経由で
-- batch_executions に残置を撒く。件数アサーションを行うクラスでは、自テストの記録を
-- クリーンな状態で観測できるよう各テスト前に当該テーブルを TRUNCATE する。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE batch_executions;
SET REFERENTIAL_INTEGRITY TRUE;
