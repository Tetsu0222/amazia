-- phaseX-9 Step 4: MonthlySalesReportJobTest / YearlySalesReportJobTest 共有 cleanup
-- 月次・年次レポートテーブルの他テスト残置を除去する。@Transactional ロールバックでは
-- 他クラス由来の REQUIRES_NEW 経由貫通や、scheduler-enabled で同 ApplicationContext の
-- 他バッチが書き込んだ集計行が消えないため、各テスト前に当該テーブルを TRUNCATE する。
-- 子→親順は H2 の TRUNCATE では不要だが、念のため yearly → monthly の順で記述。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE yearly_sales_reports;
TRUNCATE TABLE monthly_sales_reports;
SET REFERENTIAL_INTEGRITY TRUE;
