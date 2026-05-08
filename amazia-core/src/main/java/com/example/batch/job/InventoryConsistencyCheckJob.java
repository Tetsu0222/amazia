package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.batch.repository.InventoryConsistencyCheckRepository;
import com.example.notification.service.BatchAlertNotifier;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * フェーズ17 Step 3-1: 入荷数と在庫数の整合性チェック（設計書 §3.1 ① / H-2 / R-4）。
 *
 * <p>SKU TX 累積（SKU → 商品ロールアップ）と倉庫合算した {@code inventories.quantity}
 * を 2 段ロールアップで突合し、不一致を {@code inventory_alerts} 通知 + {@code operation_logs}
 * へ記録する。**自動補正は行わない**（設計書 §3.1 ① の運用原則）。
 *
 * <p>{@link Scheduled} は本ジョブクラスに直接付与する（設計書 §2.2 / M-2）。
 * {@link ConditionalOnProperty} は業務バッチ個別に付与する。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class InventoryConsistencyCheckJob extends AbstractBatchJob {

    public static final String JOB_NAME = "InventoryConsistencyCheckJob";
    public static final String SUBSCRIPTION_TAG = "inventory_alerts";
    public static final String OPERATION_ACTION = "inventory_inconsistency_detected";
    public static final String SCREEN_NAME = "BatchScheduler";
    public static final long SYSTEM_USER_ID = 0L;

    private final InventoryConsistencyCheckRepository checkRepository;
    private final OperationLogRepository operationLogRepository;
    private final BatchAlertNotifier alertNotifier;

    @Value("${amazia.batch.sales-reconciliation.target-warehouse-ids}")
    private String targetWarehouseIdsCsv;

    public InventoryConsistencyCheckJob(InventoryConsistencyCheckRepository checkRepository,
                                        OperationLogRepository operationLogRepository,
                                        BatchAlertNotifier alertNotifier) {
        this.checkRepository = checkRepository;
        this.operationLogRepository = operationLogRepository;
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
        List<Object[]> rows = checkRepository.findInconsistencies(warehouseIds);

        for (Object[] row : rows) {
            long productId = ((Number) row[0]).longValue();
            int currentQty = ((Number) row[1]).intValue();
            int expectedQty = ((Number) row[2]).intValue();
            recordInconsistency(productId, currentQty, expectedQty);
        }

        // 検査対象は全商品（products 数）だが、本ジョブでは「不一致だけが業務的な対象数」と扱う。
        return BatchResult.of(rows.size(), rows.size(), 0);
    }

    private void recordInconsistency(long productId, int currentQty, int expectedQty) {
        OperationLog log = new OperationLog();
        log.setUserId(SYSTEM_USER_ID);
        log.setAction(OPERATION_ACTION);
        log.setScreenName(SCREEN_NAME);
        log.setTargetType("product");
        log.setTargetId(productId);
        log.setComment(String.format("[inventory_check] product_id=%d expected=%d actual=%d",
                productId, expectedQty, currentQty));
        operationLogRepository.save(log);

        alertNotifier.dispatch(
                "WARN",
                SUBSCRIPTION_TAG,
                "在庫整合性チェック：不一致検知",
                String.format("商品 ID %d で SKU TX 累積=%d / inventories 合算=%d の乖離を検知しました。",
                        productId, expectedQty, currentQty),
                "product_id=" + productId,
                JOB_NAME,
                null);
    }

    static List<Long> parseWarehouseIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of(1L);
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
