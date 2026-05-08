package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.InventoryConsistencyCheckJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 3-1: InventoryConsistencyCheckJob の TDD（設計書 §3.1 ① / 計画書 §4-1）。
 *
 * <p>{@code BatchExecutionRecorder} が REQUIRES_NEW で履歴を残すため、本テストでは
 * クラスレベル {@code @Transactional} を付けない（テスト TX 内では Recorder が見えない）。
 * 共有データの汚染を避けるため「自分が作った product 起因の検知件数」を最新 batch_execution の
 * 前後差分で観測する。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class InventoryConsistencyCheckJobTest {

    @Autowired private InventoryConsistencyCheckJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private OperationLogRepository operationLogRepository;
    @Autowired private ConsoleNotificationRepository consoleNotificationRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockTransactionRepository txRepository;
    @Autowired private InventoriesRepository inventoriesRepository;

    @Value("${amazia.delivery.default-warehouse-id}") private long defaultWarehouseId;

    @Test
    void INV_2_inventoriesを1ずらすと操作ログとconsole通知に対象product_idが残る() {
        long productId = persistProductWithSku(10);
        persistInventories(productId, 11);

        // before スナップショットは「自テスト productId に絞った件数」「自テスト body 部分一致件数」で取る。
        // 全件 size() をスナップショットしてから productId フィルタ後と比較すると、
        // 他テストが REQUIRES_NEW で残した同 action / 同 tag のレコードがあると assertEquals が崩れる（051 派生③）。
        long opLogsBefore = operationLogRepository
                .findByActionOrderByCreatedAtDesc(InventoryConsistencyCheckJob.OPERATION_ACTION)
                .stream()
                .filter(l -> l.getTargetId() != null && l.getTargetId() == productId)
                .count();
        long notificationsBefore = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        InventoryConsistencyCheckJob.SUBSCRIPTION_TAG)
                .stream()
                .filter(n -> n.getBody() != null && n.getBody().contains("商品 ID " + productId))
                .count();

        job.run("scheduler");

        BatchExecution exec = latestExecution();
        assertEquals("SUCCESS", exec.getStatus());
        assertTrue(exec.getTargetCount() >= 1, "少なくとも本 product の 1 件は不一致");

        List<OperationLog> logs = operationLogRepository
                .findByActionOrderByCreatedAtDesc(InventoryConsistencyCheckJob.OPERATION_ACTION);
        assertEquals(opLogsBefore + 1L,
                logs.stream().filter(l -> l.getTargetId() != null && l.getTargetId() == productId).count(),
                "本 product 起因の operation_logs が 1 件追加されている");

        OperationLog mine = logs.stream()
                .filter(l -> l.getTargetId() != null && l.getTargetId() == productId)
                .findFirst().orElseThrow();
        assertTrue(mine.getComment().contains("expected=10"));
        assertTrue(mine.getComment().contains("actual=11"));

        List<ConsoleNotification> notifications = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        InventoryConsistencyCheckJob.SUBSCRIPTION_TAG);
        long myNotificationCount = notifications.stream()
                .filter(n -> n.getBody() != null && n.getBody().contains("商品 ID " + productId))
                .count();
        assertEquals(notificationsBefore + 1L, myNotificationCount,
                "本 product 起因の console_notifications が 1 件追加されている");
        ConsoleNotification myNotice = notifications.stream()
                .filter(n -> n.getBody() != null && n.getBody().contains("商品 ID " + productId))
                .findFirst().orElseThrow();
        assertEquals("WARN", myNotice.getLevel());
    }

    @Test
    void INV_3_inventoriesがゼロかつ_SKU_TX_もゼロなら検知されない() {
        long productId = persistProductWithSku(0);

        job.run("scheduler");

        // 本 product 起因の不一致は出ていないこと
        List<OperationLog> logs = operationLogRepository
                .findByActionOrderByCreatedAtDesc(InventoryConsistencyCheckJob.OPERATION_ACTION);
        long minorCount = logs.stream()
                .filter(l -> l.getTargetId() != null && l.getTargetId() == productId).count();
        assertEquals(0L, minorCount,
                "SKU TX=0 / inventories 行なし は構造的限界（K-8）で検知できない");
    }

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(InventoryConsistencyCheckJob.JOB_NAME)
                .get(0);
    }

    private long persistProductWithSku(int initialQuantity) {
        Product p = new Product();
        p.setName("InvCheck テスト商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-IC-" + System.nanoTime());
        sku.setColor("白"); sku.setSize("M"); sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        if (initialQuantity != 0) {
            ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
            tx.setSkuId(skuId);
            tx.setType("adjust");
            tx.setQuantity(initialQuantity);
            tx.setReferenceType("test");
            txRepository.save(tx);
        }
        return productId;
    }

    private void persistInventories(Long productId, int quantity) {
        Inventories inv = new Inventories();
        inv.setProductId(productId);
        inv.setWarehouseId(defaultWarehouseId);
        inv.setQuantity(quantity);
        inventoriesRepository.saveAndFlush(inv);
    }
}
