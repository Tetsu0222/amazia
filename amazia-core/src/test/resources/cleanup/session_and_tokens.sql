-- phaseX-9 Step 4: SessionAndTokenSweepJobTest 用 cleanup
-- @Transactional 不在のためテスト終了後も market_sessions /
-- market_customers_password_reset_tokens にレコードが残置する。各テスト前に当該テーブルを
-- TRUNCATE して、削除/残存の検証をクリーンな状態で行う。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE market_sessions;
TRUNCATE TABLE market_customers_password_reset_tokens;
SET REFERENTIAL_INTEGRITY TRUE;
