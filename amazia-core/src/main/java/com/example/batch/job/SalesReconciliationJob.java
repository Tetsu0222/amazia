package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.batch.repository.SalesReconciliationRepository;
import com.example.batch.service.BankTransferMockClient;
import com.example.notification.service.BatchAlertNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.example.batch.job.InventoryConsistencyCheckJob.parseWarehouseIds;

/**
 * フェーズ17 Step 3-3: 売上と在庫数の照合 + 振込確認疑似 API（設計書 §3.1 ③ / R-3 / R-1 / N-6）。
 *
 * <p>在庫照合（再構築 SQL）は常に実施。振込確認は
 * {@link BankTransferMockClient#mode()} が {@link BankTransferMockClient.Mode#DISABLED} の場合スキップ。
 *
 * <p>不整合通知：
 * <ul>
 *   <li>在庫照合不整合 → {@code inventory_alerts}</li>
 *   <li>振込不整合 → {@code sales_alerts}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class SalesReconciliationJob extends AbstractBatchJob {

    private static final Logger log = LoggerFactory.getLogger(SalesReconciliationJob.class);
    public static final String JOB_NAME = "SalesReconciliationJob";
    public static final String INVENTORY_TAG = "inventory_alerts";
    public static final String SALES_TAG = "sales_alerts";

    private final SalesReconciliationRepository reconciliationRepository;
    private final BankTransferMockClient bankClient;
    private final BatchAlertNotifier alertNotifier;

    @Value("${amazia.batch.sales-reconciliation.target-warehouse-ids}")
    private String targetWarehouseIdsCsv;

    public SalesReconciliationJob(SalesReconciliationRepository reconciliationRepository,
                                  BankTransferMockClient bankClient,
                                  BatchAlertNotifier alertNotifier) {
        this.reconciliationRepository = reconciliationRepository;
        this.bankClient = bankClient;
        this.alertNotifier = alertNotifier;
    }

    @Scheduled(cron = "${amazia.batch.daily.cron}", zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        List<Long> warehouseIds = parseWarehouseIds(targetWarehouseIdsCsv);
        List<Object[]> rows = reconciliationRepository.findReconciliationRows(warehouseIds);

        int inventoryMismatch = 0;
        for (Object[] row : rows) {
            long productId = ((Number) row[0]).longValue();
            int currentQty = ((Number) row[1]).intValue();
            int expectedQty = ((Number) row[2]).intValue();
            if (currentQty != expectedQty) {
                inventoryMismatch++;
                alertNotifier.dispatch("WARN", INVENTORY_TAG,
                        "売上整合：在庫不一致",
                        String.format("商品 ID %d で再計算=%d / 現在在庫=%d。", productId, expectedQty, currentQty),
                        "product_id=" + productId,
                        JOB_NAME, null);
            }
        }

        BankTransferMockClient.Result transferResult = bankClient.verify();
        int salesFailures = 0;
        if (transferResult == BankTransferMockClient.Result.MISMATCH) {
            salesFailures = 1;
            alertNotifier.dispatch("WARN", SALES_TAG,
                    "振込確認：MISMATCH 検知",
                    "振込確認モック API が MISMATCH を返しました。実取引との突合を行ってください。",
                    "transfer_check",
                    JOB_NAME, null);
        }
        log.info("[{}] inventory mismatch={} / transfer={}", JOB_NAME, inventoryMismatch, transferResult);

        int target = rows.size();
        int failures = inventoryMismatch + salesFailures;
        return BatchResult.of(target, target - inventoryMismatch, failures);
    }
}
