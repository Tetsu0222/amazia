package com.example.delivery;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.delivery.service.DeliveryRescheduleService;
import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step B-4: DeliveryRescheduleService 検証（RRR-4 / RRRR-4）。
 *
 * sales.created_at 昇順 FIFO で在庫切れ deliveries の scheduled_date が再計算されることを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class DeliveryRescheduleServiceTest {

    @Autowired private DeliveryRescheduleService service;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private InventoriesRepository inventoriesRepository;
    @Autowired private OperationLogRepository operationLogRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}")
    private long pendingId;

    @Value("${amazia.delivery.shipping-methods.home-delivery-id}")
    private long homeDeliveryMethodId;

    @Value("${amazia.delivery.lead-time-days.home-delivery}")
    private int homeDeliveryDays;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    @Value("${amazia.delivery.scheduled-date-reasons.inbound-recalc}")
    private String inboundRecalcPrefix;

    private Long customerId;
    private Long addressId;
    private Long actorUserId;
    private Long productId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        actorUserId = 1L;

        Customer c = new Customer();
        c.setNameLast("再計算");
        c.setNameFirst("テスト");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("recalc-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        customerId = customerRepository.save(c).getId();

        Address a = new Address();
        a.setUserId(customerId);
        a.setAddressLine("東京都千代田区千代田1-1");
        a.setActive(true);
        addressId = addressRepository.save(a).getId();

        Product p = new Product();
        p.setName("再計算テスト商品");
        p.setStatusCode("ON_SALE");
        productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-RCL-" + System.nanoTime());
        sku.setColor("緑");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(0);
        skuStockRepository.save(stock);
    }

    @Test
    void 在庫切れの_deliveries_が_FIFO_で_scheduled_date_を埋められる() {
        // 在庫 5（入荷想定）
        persistInventories(5);
        // 在庫切れ deliveries を 3 件、created_at 昇順で投入（quantity=1 ずつ）
        Sales s1 = persistSales("01967c40-aaaa-7d8e-9c5f-deadbeef0001", 1, LocalDateTime.of(2026, 5, 1, 10, 0));
        Sales s2 = persistSales("01967c40-bbbb-7d8e-9c5f-deadbeef0002", 1, LocalDateTime.of(2026, 5, 2, 10, 0));
        Sales s3 = persistSales("01967c40-cccc-7d8e-9c5f-deadbeef0003", 1, LocalDateTime.of(2026, 5, 3, 10, 0));
        Delivery d1 = persistDelivery(s1, null);
        Delivery d2 = persistDelivery(s2, null);
        Delivery d3 = persistDelivery(s3, null);

        service.recalculateForProduct(productId, actorUserId);

        // すべて scheduled_date が埋められる（在庫 5 >= 3 件）
        assertEquals(s1.getSalesDate().plusDays(homeDeliveryDays),
                deliveryRepository.findById(d1.getId()).orElseThrow().getScheduledDate());
        assertEquals(s2.getSalesDate().plusDays(homeDeliveryDays),
                deliveryRepository.findById(d2.getId()).orElseThrow().getScheduledDate());
        assertEquals(s3.getSalesDate().plusDays(homeDeliveryDays),
                deliveryRepository.findById(d3.getId()).orElseThrow().getScheduledDate());

        // 各更新で operation_logs が [inbound_recalc] プレフィックス付きで記録される
        List<OperationLog> logs = operationLogRepository.findAll().stream()
                .filter(l -> "update_scheduled_date".equals(l.getAction())
                          && "deliveries".equals(l.getTargetType()))
                .toList();
        assertEquals(3, logs.size());
        logs.forEach(l -> assertTrue(l.getComment().startsWith(inboundRecalcPrefix),
                "comment 先頭が " + inboundRecalcPrefix + " であるべき: " + l.getComment()));
        logs.forEach(l -> assertEquals("core.batch.inbound_recalc", l.getScreenName()));
        logs.forEach(l -> assertNull(l.getApiName(), "バッチ起点なので api_name は NULL"));
    }

    @Test
    void 在庫が不足する場合は古い順に充足し残りはNULLのまま維持される() {
        // 在庫 1 のみ（入荷想定）
        persistInventories(1);
        Sales s1 = persistSales("01967c40-1111-7d8e-9c5f-deadbeef0011", 1, LocalDateTime.of(2026, 5, 1, 10, 0));
        Sales s2 = persistSales("01967c40-2222-7d8e-9c5f-deadbeef0012", 1, LocalDateTime.of(2026, 5, 2, 10, 0));
        Delivery d1 = persistDelivery(s1, null);
        Delivery d2 = persistDelivery(s2, null);

        service.recalculateForProduct(productId, actorUserId);

        // 古い d1 のみ充足、d2 は NULL のまま
        assertNotNull(deliveryRepository.findById(d1.getId()).orElseThrow().getScheduledDate());
        assertNull(deliveryRepository.findById(d2.getId()).orElseThrow().getScheduledDate());
    }

    @Test
    void inventories行が無い商品では_IllegalStateException_で停止する() {
        // inventories 行を作らない
        assertThrows(IllegalStateException.class,
                () -> service.recalculateForProduct(productId, actorUserId));
    }

    @Test
    void 候補が0件のときは何も更新せず例外も投げない() {
        persistInventories(10);
        // 在庫切れ delivery は無し。すでに scheduled_date 埋まっている delivery のみ。
        Sales s1 = persistSales("01967c40-3333-7d8e-9c5f-deadbeef0021", 1, LocalDateTime.of(2026, 5, 1, 10, 0));
        Delivery d1 = persistDelivery(s1, LocalDate.of(2026, 5, 5));

        service.recalculateForProduct(productId, actorUserId);

        // 既存 scheduled_date は変わらず
        assertEquals(LocalDate.of(2026, 5, 5),
                deliveryRepository.findById(d1.getId()).orElseThrow().getScheduledDate());
        // ログも増えていない
        long logCount = operationLogRepository.findAll().stream()
                .filter(l -> "update_scheduled_date".equals(l.getAction())
                          && d1.getId().equals(l.getTargetId()))
                .count();
        assertEquals(0, logCount);
    }

    // ---- helpers ----

    private void persistInventories(int quantity) {
        Inventories inv = new Inventories();
        inv.setProductId(productId);
        inv.setWarehouseId(defaultWarehouseId);
        inv.setQuantity(quantity);
        inventoriesRepository.saveAndFlush(inv);
    }

    private Sales persistSales(String paymentId, int quantity, LocalDateTime createdAt) {
        Sales s = new Sales();
        s.setUserId(customerId);
        s.setSkuId(skuId);
        s.setQuantity(quantity);
        s.setAmount(3000 * quantity);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(homeDeliveryMethodId);
        s.setShippingAddressId(addressId);
        s.setShippingStatusId(pendingId);
        s.setPaymentId(paymentId);
        s.setPreorder(true); // 予約購入扱い（在庫切れでも sales 成立）
        s.setSalesDate(createdAt.toLocalDate());
        Sales saved = salesRepository.saveAndFlush(s);
        // created_at は @PrePersist で LocalDateTime.now() が入るため、テスト用に上書き不可。
        // FIFO 順序は saveAndFlush の呼び出し順序で安定するので（ID 採番順）、
        // 期待動作：呼び出し順 = created_at 昇順 = 想定通り。
        return saved;
    }

    private Delivery persistDelivery(Sales sales, LocalDate scheduledDate) {
        Delivery d = new Delivery();
        d.setSalesId(sales.getId());
        d.setShippingAddressId(addressId);
        d.setShippingMethodId(homeDeliveryMethodId);
        d.setShippingStatusId(pendingId);
        d.setScheduledDate(scheduledDate);
        return deliveryRepository.saveAndFlush(d);
    }
}
