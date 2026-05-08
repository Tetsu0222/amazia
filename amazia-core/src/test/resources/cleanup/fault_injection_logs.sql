-- phaseX-9 Step 2 PoC: FaultInjectionLogRepositoryTest 用 cleanup
-- REQUIRES_NEW 経由でロールバックを貫通する fault_injection_logs を確実にクリアする
-- FK 解決順: fault_injection_logs は他テーブルから参照されないため単独 TRUNCATE で OK
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE fault_injection_logs;
SET REFERENTIAL_INTEGRITY TRUE;
