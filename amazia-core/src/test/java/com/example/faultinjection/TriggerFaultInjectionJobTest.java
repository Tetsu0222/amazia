package com.example.faultinjection;

import com.example.batch.controller.BatchManualTriggerController;
import com.example.batch.entity.BatchExecution;
import com.example.batch.job.TriggerFaultInjectionJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.batch.service.RandomGeneratorAdapter;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * フェーズ17 Step 5-5: TriggerFaultInjectionJob の動作検証（M-4 / 設計書 §3.4）。
 *
 * <ul>
 *   <li>TFI_1：3 つの Injector が一括発火する（{@code fault_injection_logs} に 3 件以上）</li>
 *   <li>TFI_2：Bean 名 = JOB_NAME で {@link BatchManualTriggerController} から起動できる</li>
 *   <li>TFI_3：未登録 jobName は 404 を返す（M-4 の自然な拡張）</li>
 * </ul>
 *
 * <p>本番プロファイルでの Bean 不在検証は
 * {@link TriggerFaultInjectionJobProductionProfileTest} で別ファイルに分離。
 *
 * <p>phaseX-9 Step 4: cleanup.sql + クラスレベル @Sql(BEFORE_TEST_METHOD) で
 * fault_injection_logs の他テスト残置を除去する（FaultInjectionLogRepositoryTest と共有）。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
@Sql(
        scripts = "/cleanup/fault_injection_logs.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class TriggerFaultInjectionJobTest {

    @Autowired private TriggerFaultInjectionJob job;
    @Autowired private BatchManualTriggerController controller;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private FaultInjectionLogRepository faultLogRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private DeliveryRepository deliveryRepository;

    @MockBean private RandomGeneratorAdapter random;

    @Value("${amazia.sales.shipping-statuses.pending-id}") private long pendingId;

    @Test
    void TFI_1_3_つの_Injector_が一括発火する() {
        seedDataForAllInjectors();
        when(random.nextDouble()).thenReturn(0.99);
        when(random.nextIntBetween(anyInt(), anyInt())).thenReturn(0);

        long beforeLogs = faultLogRepository.count();

        job.run("manual:user_id=99");

        BatchExecution exec = batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(TriggerFaultInjectionJob.JOB_NAME).get(0);
        assertEquals("SUCCESS", exec.getStatus());

        long afterLogs = faultLogRepository.count();
        assertTrue(afterLogs >= beforeLogs + 3,
                "Sales / Inventory / Delivery の 3 つ全てが履歴を残す");
    }

    @Test
    void TFI_2_Controller_経由で_jobName_指定で起動できる() {
        seedDataForAllInjectors();
        when(random.nextDouble()).thenReturn(0.99);
        when(random.nextIntBetween(anyInt(), anyInt())).thenReturn(0);

        ResponseEntity<Map<String, Object>> response =
                controller.trigger(TriggerFaultInjectionJob.JOB_NAME, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("triggered", response.getBody().get("message"));
    }

    @Test
    void TFI_3_未登録_jobName_は_404_を返す() {
        // Step 6-3 改修以降、Controller は Service が throw した ResponseStatusException を
        // Spring の例外ハンドラ経由で 404 に変換する設計（規約 1-1）。
        // Controller を直接呼ぶ JUnit では例外を assert する。
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.trigger("UnknownJob", 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    private void seedDataForAllInjectors() {
        Product p = new Product();
        p.setName("TFI 商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-TFI-" + System.nanoTime());
        sku.setColor("白");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(10);
        skuStockRepository.save(stock);

        Sales sales = new Sales();
        sales.setUserId(1L);
        sales.setSkuId(skuId);
        sales.setQuantity(1);
        sales.setAmount(1000);
        sales.setPaymentMethodId(1L);
        sales.setShippingMethodId(1L);
        sales.setShippingAddressId(1L);
        sales.setShippingStatusId(pendingId);
        sales.setPaymentId("pay-TFI-" + System.nanoTime());
        sales.setSalesDate(LocalDate.now());
        Long salesId = salesRepository.saveAndFlush(sales).getId();

        Delivery d = new Delivery();
        d.setSalesId(salesId);
        d.setShippingAddressId(1L);
        d.setShippingMethodId(1L);
        d.setShippingStatusId(pendingId);
        deliveryRepository.saveAndFlush(d);
    }
}
