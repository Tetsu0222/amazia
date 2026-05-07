package com.example.delivery;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.delivery.service.DeliveryStatusTransitionService;
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
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step B-2: DeliveryStatusTransitionService 検証。
 *
 * 設計書 §配送ステータス遷移ルール / §出荷時の在庫処理（P5-3 / P5-4）を網羅する。
 * すべての ID 値は config 経由で取得（規約 4-1 / RRRR-8）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class DeliveryStatusTransitionServiceTest {

    @Autowired private DeliveryStatusTransitionService transitionService;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuStockTransactionRepository skuStockTransactionRepository;
    @Autowired private OperationLogRepository operationLogRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}")          private long pendingId;
    @Value("${amazia.sales.shipping-statuses.shipped-id}")          private long shippedId;
    @Value("${amazia.sales.shipping-statuses.delivered-id}")        private long deliveredId;
    @Value("${amazia.sales.shipping-statuses.return-requested-id}") private long returnRequestedId;
    @Value("${amazia.sales.shipping-statuses.returned-id}")         private long returnedId;
    @Value("${amazia.delivery.shipping-methods.home-delivery-id}")  private long homeDeliveryMethodId;
    @Value("${amazia.delivery.sku-stock-tx-types.sale-preorder-shipment}") private String txTypePreorderShipment;

    private Long customerId;
    private Long addressId;
    private Long actorUserId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        actorUserId = 1L; // users.id 参照（test-data.sql で投入されている前提ではないが
                          // operation_logs は H2 では FK が遅延チェックされるため通る）

        Customer c = new Customer();
        c.setNameLast("配送状態");
        c.setNameFirst("テスト");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("delivery-trans-" + System.nanoTime() + "@example.com");
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
        p.setName("配送遷移テスト商品");
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-TRN-" + System.nanoTime());
        sku.setColor("青");
        sku.setSize("L");
        sku.setStatus("ACTIVE");
        skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(5);
        skuStockRepository.save(stock);
    }

    // ---- 正常系遷移 ----

    @Test
    void PENDINGからSHIPPEDへ遷移できshipped_dateがセットされる() {
        Delivery delivery = persistDelivery(false, pendingId, "01967c20-aaaa-7d8e-9c5f-deadbeef0001");

        Delivery updated = transitionService.transition(delivery.getId(), shippedId, actorUserId);

        assertEquals(shippedId, updated.getShippingStatusId());
        assertEquals(LocalDate.now(), updated.getShippedDate());
        assertNull(updated.getDeliveredDate());

        // 操作履歴
        List<OperationLog> logs = operationLogRepository.findAll().stream()
                .filter(l -> "update_shipping_status".equals(l.getAction())
                          && delivery.getId().equals(l.getTargetId()))
                .toList();
        assertEquals(1, logs.size());
        assertEquals("deliveries", logs.get(0).getTargetType());
    }

    @Test
    void SHIPPEDからDELIVEREDへ遷移できdelivered_dateがセットされる() {
        Delivery delivery = persistDelivery(false, shippedId, "01967c20-bbbb-7d8e-9c5f-deadbeef0002");

        Delivery updated = transitionService.transition(delivery.getId(), deliveredId, actorUserId);

        assertEquals(deliveredId, updated.getShippingStatusId());
        assertEquals(LocalDate.now(), updated.getDeliveredDate());
    }

    @Test
    void DELIVEREDからRETURN_REQUESTEDからRETURNEDまで連鎖遷移できる() {
        Delivery delivery = persistDelivery(false, deliveredId, "01967c20-cccc-7d8e-9c5f-deadbeef0003");

        Delivery toReq = transitionService.transition(delivery.getId(), returnRequestedId, actorUserId);
        assertEquals(returnRequestedId, toReq.getShippingStatusId());

        Delivery toRet = transitionService.transition(delivery.getId(), returnedId, actorUserId);
        assertEquals(returnedId, toRet.getShippingStatusId());
    }

    // ---- 異常系遷移 ----

    @Test
    void DELIVEREDからPENDINGへの巻き戻しは400で拒否される() {
        Delivery delivery = persistDelivery(false, deliveredId, "01967c20-dddd-7d8e-9c5f-deadbeef0004");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transitionService.transition(delivery.getId(), pendingId, actorUserId));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void PENDINGから直接DELIVEREDへの飛び越しは400で拒否される() {
        Delivery delivery = persistDelivery(false, pendingId, "01967c20-eeee-7d8e-9c5f-deadbeef0005");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transitionService.transition(delivery.getId(), deliveredId, actorUserId));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void RETURNEDからの遷移はすべて400で拒否される() {
        Delivery delivery = persistDelivery(false, returnedId, "01967c20-ffff-7d8e-9c5f-deadbeef0006");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transitionService.transition(delivery.getId(), pendingId, actorUserId));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 存在しない_delivery_id_は404で拒否される() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transitionService.transition(999_999L, shippedId, actorUserId));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ---- 出荷時の在庫処理 (P5-3 / P5-4) ----

    @Test
    void 通常購入のSHIPPED遷移は在庫を変更しない() {
        Delivery delivery = persistDelivery(false, pendingId, "01967c20-1111-7d8e-9c5f-deadbeef0007");
        int beforeQty = skuStockRepository.findBySkuId(skuId).orElseThrow().getQuantity();

        transitionService.transition(delivery.getId(), shippedId, actorUserId);

        int afterQty = skuStockRepository.findBySkuId(skuId).orElseThrow().getQuantity();
        assertEquals(beforeQty, afterQty, "通常購入の SHIPPED 遷移は在庫を変えない");

        long preorderTxCount = skuStockTransactionRepository.findAll().stream()
                .filter(t -> t.getSkuId().equals(skuId)
                          && txTypePreorderShipment.equals(t.getType()))
                .count();
        assertEquals(0, preorderTxCount);
    }

    @Test
    void 予約購入のSHIPPED遷移は在庫を減算しtransactionを記録する() {
        // 予約購入：注文時に在庫減算されていないので、出荷時に減算される
        Delivery delivery = persistDelivery(true, pendingId, "01967c20-2222-7d8e-9c5f-deadbeef0008");
        int beforeQty = skuStockRepository.findBySkuId(skuId).orElseThrow().getQuantity();

        transitionService.transition(delivery.getId(), shippedId, actorUserId);

        int afterQty = skuStockRepository.findBySkuId(skuId).orElseThrow().getQuantity();
        assertEquals(beforeQty - 1, afterQty, "予約購入の SHIPPED 遷移で在庫が減算される");

        List<ProductSkuStockTransaction> txs = skuStockTransactionRepository.findAll().stream()
                .filter(t -> t.getSkuId().equals(skuId)
                          && txTypePreorderShipment.equals(t.getType()))
                .toList();
        assertEquals(1, txs.size());
        assertEquals(-1, txs.get(0).getQuantity());
        assertEquals("sales", txs.get(0).getReferenceType());
    }

    @Test
    void 予約購入で在庫不足の場合は409でPENDINGのまま維持される() {
        // 在庫を 0 にする
        ProductSkuStock stock = skuStockRepository.findBySkuId(skuId).orElseThrow();
        stock.setQuantity(0);
        skuStockRepository.save(stock);

        Delivery delivery = persistDelivery(true, pendingId, "01967c20-3333-7d8e-9c5f-deadbeef0009");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transitionService.transition(delivery.getId(), shippedId, actorUserId));
        assertEquals(409, ex.getStatusCode().value());

        // delivery は別トランザクションで再取得しても PENDING のまま
        // （@Transactional テスト内なので例外発生時のロールバックは Spring が制御）
        Delivery reloaded = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(pendingId, reloaded.getShippingStatusId(), "PENDING のまま維持される");

        int afterQty = skuStockRepository.findBySkuId(skuId).orElseThrow().getQuantity();
        assertEquals(0, afterQty, "在庫は変動なし");
    }

    // ---- helpers ----

    private Delivery persistDelivery(boolean preorder, Long initialStatusId, String paymentId) {
        Sales s = new Sales();
        s.setUserId(customerId);
        s.setSkuId(skuId);
        s.setQuantity(1);
        s.setAmount(3000);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(homeDeliveryMethodId);
        s.setShippingAddressId(addressId);
        s.setShippingStatusId(initialStatusId);
        s.setPaymentId(paymentId);
        s.setPreorder(preorder);
        s.setSalesDate(LocalDate.of(2026, 5, 7));
        Sales saved = salesRepository.saveAndFlush(s);

        Delivery d = new Delivery();
        d.setSalesId(saved.getId());
        d.setShippingAddressId(addressId);
        d.setShippingMethodId(homeDeliveryMethodId);
        d.setShippingStatusId(initialStatusId);
        return deliveryRepository.saveAndFlush(d);
    }
}
