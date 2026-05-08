package com.example.faultinjection;

import com.example.batch.service.RandomGeneratorAdapter;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.faultinjection.entity.FaultInjectionLog;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.faultinjection.service.DeliveryTroubleInjector;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * フェーズ17 Step 5-4: DeliveryTroubleInjector の動作検証（R-2 / H-7 / G-1）。
 *
 * <p>RandomGeneratorAdapter の戻り値を引数で識別して制御する：
 * <ul>
 *   <li>{@code nextIntBetween(0, 2)} → ステータス選択（0=CANCELED / 1=DELIVERY_FAILED / 2=RESCHEDULED）</li>
 *   <li>{@code nextIntBetween(0, sizeMinus1)} → PENDING 配列のインデックス選択</li>
 * </ul>
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class DeliveryTroubleInjectorTest {

    @Autowired private DeliveryTroubleInjector injector;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuStockTransactionRepository txRepository;
    @Autowired private FaultInjectionLogRepository faultLogRepository;

    @MockBean private RandomGeneratorAdapter random;

    @Value("${amazia.sales.shipping-statuses.pending-id}")  private long pendingId;
    @Value("${amazia.sales.shipping-statuses.canceled-id}") private long canceledId;
    @Value("${amazia.sales.shipping-statuses.delivery-failed-id}") private long deliveryFailedId;
    @Value("${amazia.sales.shipping-statuses.rescheduled-id}") private long rescheduledId;

    @BeforeEach
    void cleanupPendingDeliveries() {
        // 同じ ApplicationContext を共有するテストの遺物を排除
        for (Delivery d : deliveryRepository.findByShippingStatusId(pendingId)) {
            deliveryRepository.delete(d);
        }
        faultLogRepository.deleteAll(faultLogRepository
                .findByInjectorNameOrderByCreatedAtDesc(DeliveryTroubleInjector.INJECTOR_NAME));
    }

    @Test
    void DTI_1_PENDING_の_delivery_が許容される_3_状態のいずれかへ遷移する() {
        Long skuId = persistProductSkuWithStock(10);
        Sales sales = persistSales(skuId, 1);
        Long deliveryId = persistPendingDelivery(sales.getId());

        // PENDING は 1 件のみなので index は必ず 0 → status は 0 = CANCELED
        stubStatusPick(0);
        stubDeliveryIndexPick(0, 0);

        int affected = injector.injectOnce("manual:test");

        assertEquals(deliveryId.intValue(), affected);
        Delivery after = deliveryRepository.findById(deliveryId).orElseThrow();
        assertEquals(canceledId, after.getShippingStatusId(),
                "status pick=0 のとき pickNextStatus は CANCELED を返す");
    }

    @Test
    void DTI_2_補償_SKU_TX_が接頭辞付き_quantity_1_固定で記録される() {
        Long skuId = persistProductSkuWithStock(10);
        Sales sales = persistSales(skuId, 1);
        persistPendingDelivery(sales.getId());

        stubStatusPick(1); // DELIVERY_FAILED
        stubDeliveryIndexPick(0, 0);

        injector.injectOnce("scheduler");

        List<ProductSkuStockTransaction> txs = txRepository.findBySkuIdOrderByCreatedAtDesc(skuId);
        assertFalse(txs.isEmpty());
        ProductSkuStockTransaction latest = txs.get(0);
        assertEquals("adjust", latest.getType());
        assertEquals(DeliveryTroubleInjector.COMPENSATION_QUANTITY, latest.getQuantity(),
                "補償 SKU TX は +1 固定（quantity_dummy）");
        assertEquals("fault_injection", latest.getReferenceType());
        assertEquals(sales.getId(), latest.getReferenceId());
        assertNotNull(latest.getComment());
        assertTrue(latest.getComment().startsWith("[fault_injection][delivery][quantity_dummy]"),
                "comment は [fault_injection][delivery][quantity_dummy] 接頭辞で始まる必要がある");

        List<FaultInjectionLog> logs = faultLogRepository
                .findByInjectorNameOrderByCreatedAtDesc(DeliveryTroubleInjector.INJECTOR_NAME);
        assertEquals(1, logs.size());
    }

    @Test
    void DTI_3_PENDING_が無ければ_0_を返してスキップ() {
        // BeforeEach で PENDING を空にしている
        stubStatusPick(0);

        int affected = injector.injectOnce("scheduler");

        assertEquals(0, affected);
    }

    @Test
    void DTI_4_tryInject_は_enabled_false_なら発火しない() {
        Long skuId = persistProductSkuWithStock(10);
        Sales sales = persistSales(skuId, 1);
        Long deliveryId = persistPendingDelivery(sales.getId());

        ReflectionTestUtils.setField(injector, "enabled", false);
        when(random.nextDouble()).thenReturn(0.001);

        int affected = injector.tryInject("scheduler");

        assertEquals(0, affected);
        Delivery untouched = deliveryRepository.findById(deliveryId).orElseThrow();
        assertEquals(pendingId, untouched.getShippingStatusId(),
                "enabled=false の状態では PENDING のままで遷移しない");
    }

    @Test
    void DTI_5_pickNextStatus_の遷移先は許容3状態のいずれか() {
        Long skuId = persistProductSkuWithStock(10);
        Sales sales = persistSales(skuId, 1);
        Long deliveryId = persistPendingDelivery(sales.getId());

        stubStatusPick(2); // RESCHEDULED
        stubDeliveryIndexPick(0, 0);

        injector.injectOnce("scheduler");

        Delivery after = deliveryRepository.findById(deliveryId).orElseThrow();
        Set<Long> allowed = Set.of(canceledId, deliveryFailedId, rescheduledId);
        assertTrue(allowed.contains(after.getShippingStatusId()),
                "遷移先は CANCELED / DELIVERY_FAILED / RESCHEDULED のいずれかでなくてはならない");
        assertEquals(rescheduledId, after.getShippingStatusId(), "status pick=2 → RESCHEDULED");
    }

    private void stubStatusPick(int value) {
        when(random.nextIntBetween(eq(0), eq(2))).thenReturn(value);
    }

    private void stubDeliveryIndexPick(int min, int max) {
        when(random.nextIntBetween(eq(min), eq(max))).thenReturn(min);
    }

    private long uniqueId() {
        return System.nanoTime();
    }

    private Long persistProductSkuWithStock(int initialStock) {
        Product p = new Product();
        p.setName("DTI 商品-" + uniqueId());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-DTI-" + uniqueId());
        sku.setColor("白");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(initialStock);
        skuStockRepository.save(stock);

        return skuId;
    }

    private Sales persistSales(Long skuId, int quantity) {
        Sales sales = new Sales();
        sales.setUserId(1L);
        sales.setSkuId(skuId);
        sales.setQuantity(quantity);
        sales.setAmount(1000);
        sales.setPaymentMethodId(1L);
        sales.setShippingMethodId(1L);
        sales.setShippingAddressId(1L);
        sales.setShippingStatusId(pendingId);
        sales.setPaymentId("pay-DTI-" + uniqueId());
        sales.setSalesDate(LocalDate.now());
        return salesRepository.saveAndFlush(sales);
    }

    private Long persistPendingDelivery(Long salesId) {
        Delivery d = new Delivery();
        d.setSalesId(salesId);
        d.setShippingAddressId(1L);
        d.setShippingMethodId(1L);
        d.setShippingStatusId(pendingId);
        return deliveryRepository.saveAndFlush(d).getId();
    }
}
