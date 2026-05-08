package com.example.batch.e2e;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.batch.entity.BatchExecution;
import com.example.batch.job.InventoryConsistencyCheckJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * phase17 Step 8 / E2E-1（設計書 §12.3）：
 * 「不整合データ → バッチ実行 → SES 到達 + 通知センター未読 +1」を一気通貫で検証する。
 *
 * <p>シナリオ：
 * <ol>
 *   <li>商品＋SKU を作り、SKU TX に初期 quantity=10 を投入</li>
 *   <li>{@code inventories.quantity = 11} を直接書き込んで「+1 ずれ」を作る</li>
 *   <li>{@code inventory_alerts} 購読の管理者ユーザを 1 名作成（email_enabled=true）</li>
 *   <li>{@link InventoryConsistencyCheckJob#run} を実行</li>
 *   <li>SES に購読者宛て 1 通送信されること、console_notifications に未読 1 件が残ることを確認</li>
 * </ol>
 *
 * <p>phaseX-9 規約：cleanup.sql + クラスレベル {@code @Sql(BEFORE_TEST_METHOD)} + 自テスト
 * productId フィルタで観測する。{@code BatchAlertNotifier} は REQUIRES_NEW で
 * console_notifications に書き込むため、@Transactional ロールバックを貫通する。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Sql(
        scripts = "/cleanup/e2e_full.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class InventoryInconsistencyToNotificationE2ETest {

    @Autowired private InventoryConsistencyCheckJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private ConsoleNotificationRepository consoleNotificationRepository;
    @Autowired private NotificationSubscriptionRepository subscriptionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockTransactionRepository txRepository;
    @Autowired private InventoriesRepository inventoriesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private SesClient sesClient;

    @Value("${amazia.delivery.default-warehouse-id}") private long defaultWarehouseId;

    @Test
    void E2E_1_不整合データから_SES送信_と_通知センター未読_が同時発火する() {
        long productId = persistProductWithSku(10);
        // +1 ずらした inventories（SKU TX 合計 10 vs inventories 11 で「actual=11 / expected=10」）
        persistInventories(productId, 11);

        long subscriberUserId = persistAdminWithSubscription("e2e1@example.com");

        job.run("scheduler");

        BatchExecution exec = latestExecution();
        assertEquals("SUCCESS", exec.getStatus(), "ジョブ自体は成功（不整合検知が成果物）");

        List<ConsoleNotification> myNotices = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        InventoryConsistencyCheckJob.SUBSCRIPTION_TAG)
                .stream()
                .filter(n -> n.getBody() != null && n.getBody().contains("商品 ID " + productId))
                .toList();
        assertEquals(1, myNotices.size(), "本 product 起因の未読通知が 1 件存在");
        ConsoleNotification myNotice = myNotices.get(0);
        assertEquals("WARN", myNotice.getLevel(), "WARN レベル通知");
        assertFalse(myNotice.getSuppressed(), "初回発火は suppressed=false で SES 飛ぶ");

        // SES に購読者宛 1 通飛んだ（個別 to / BCC 集約しない）
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, atLeastOnce()).sendEmail(captor.capture());
        boolean sentToSubscriber = captor.getAllValues().stream()
                .flatMap(r -> r.destination().toAddresses().stream())
                .anyMatch("e2e1@example.com"::equals);
        assertTrue(sentToSubscriber, "購読者の e2e1@example.com に SES が送られていること");

        // 後始末：購読者ユーザは UNIQUE 制約に引っかかるため、テストの最後でクリア
        subscriptionRepository.deleteAll(
                subscriptionRepository.findByUserId(subscriberUserId));
        userRepository.deleteById(subscriberUserId);
    }

    private long persistProductWithSku(int initialQuantity) {
        Product p = new Product();
        p.setName("E2E-1 テスト商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-E2E1-" + System.nanoTime());
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

    private long persistAdminWithSubscription(String email) {
        Role admin = roleRepository.findByCode("admin").orElseThrow();
        User u = new User();
        u.setEmail(email);
        u.setName("E2E-1 admin");
        u.setPasswordHash("$2y$dummy");
        u.setRole(admin);
        u.setActiveFlag(true);
        Long userId = userRepository.save(u).getId();

        NotificationSubscription sub = new NotificationSubscription();
        sub.setUserId(userId);
        sub.setSubscriptionTag(InventoryConsistencyCheckJob.SUBSCRIPTION_TAG);
        sub.setEmailEnabled(Boolean.TRUE);
        sub.setInAppEnabled(Boolean.TRUE);
        subscriptionRepository.save(sub);
        return userId;
    }

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(InventoryConsistencyCheckJob.JOB_NAME)
                .get(0);
    }
}
