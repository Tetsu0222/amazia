-- phaseX-9 Step 4: ApplyScheduledPricesJobTest 用 cleanup
-- product_sku_scheduled_prices に他テスト残置（051 派生②起因の汚染）が残ると
-- 件数アサーションが破綻するため、各テスト前に当該テーブルを TRUNCATE する。
-- product_sku_prices / product_skus / products は他テスト fixture で利用されるため対象外。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE product_sku_scheduled_prices;
SET REFERENTIAL_INTEGRITY TRUE;
