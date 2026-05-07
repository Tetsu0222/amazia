package com.example.delivery;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズ15 Step B-6-β: PATCH /api/deliveries/{id}/status の検証。
 * 注：本テストは @Transactional を付けない（在庫不足経路の REQUIRES_NEW を実 DB で検証するため）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UpdateShippingStatusControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private OperationLogRepository operationLogRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}")  private long pendingId;
    @Value("${amazia.sales.shipping-statuses.shipped-id}")  private long shippedId;
    @Value("${amazia.sales.shipping-statuses.delivered-id}") private long deliveredId;
    @Value("${amazia.delivery.shipping-methods.home-delivery-id}") private long homeDeliveryId;

    @Test
    void 通常購入でPENDINGからSHIPPEDへ正常遷移する() throws Exception {
        Long deliveryId = persistDelivery(false, pendingId, 5,
                "01967c70-aaaa-7d8e-9c5f-deadbeef0001");

        Map<String, Object> body = new HashMap<>();
        body.put("shippingStatusId", shippedId);
        body.put("reason", "出荷完了");

        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/status")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // クリーンアップ
        cleanup();
    }

    @Test
    void 不正遷移は400を返す() throws Exception {
        Long deliveryId = persistDelivery(false, pendingId, 5,
                "01967c70-bbbb-7d8e-9c5f-deadbeef0002");

        Map<String, Object> body = new HashMap<>();
        body.put("shippingStatusId", deliveredId); // PENDING → DELIVERED は不正

        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/status")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        cleanup();
    }

    @Test
    void 予約購入で在庫不足のSHIPPED遷移は409を返しPENDING維持_かつshipping_blocked_logが記録される() throws Exception {
        // 予約購入で SKU 在庫 0
        Long deliveryId = persistDelivery(true, pendingId, 0,
                "01967c70-cccc-7d8e-9c5f-deadbeef0003");

        Map<String, Object> body = new HashMap<>();
        body.put("shippingStatusId", shippedId);

        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/status")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());

        // PENDING のまま維持
        Delivery reloaded = deliveryRepository.findById(deliveryId).orElseThrow();
        assertEquals(pendingId, reloaded.getShippingStatusId());

        // shipping_blocked_insufficient_stock が REQUIRES_NEW で記録されている
        long blockedCount = operationLogRepository.findAll().stream()
                .filter(l -> "shipping_blocked_insufficient_stock".equals(l.getAction())
                          && deliveryId.equals(l.getTargetId()))
                .count();
        assertEquals(1, blockedCount);

        cleanup();
    }

    private Long persistDelivery(boolean preorder, Long initialStatusId, int skuStockQty, String paymentId) {
        Customer c = new Customer();
        c.setNameLast("ステータス");
        c.setNameFirst("テスト");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("status-api-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        Long customerId = customerRepository.save(c).getId();

        Address a = new Address();
        a.setUserId(customerId);
        a.setAddressLine("東京都千代田区千代田1-1");
        a.setActive(true);
        Long addressId = addressRepository.save(a).getId();

        Product p = new Product();
        p.setName("ステータス遷移テスト商品");
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-USC-" + System.nanoTime());
        sku.setColor("赤");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(skuStockQty);
        skuStockRepository.save(stock);

        Sales s = new Sales();
        s.setUserId(customerId);
        s.setSkuId(skuId);
        s.setQuantity(1);
        s.setAmount(1000);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(homeDeliveryId);
        s.setShippingAddressId(addressId);
        s.setShippingStatusId(initialStatusId);
        s.setPaymentId(paymentId);
        s.setPreorder(preorder);
        s.setSalesDate(LocalDate.of(2026, 5, 7));
        Sales savedSales = salesRepository.saveAndFlush(s);

        Delivery d = new Delivery();
        d.setSalesId(savedSales.getId());
        d.setShippingAddressId(addressId);
        d.setShippingMethodId(homeDeliveryId);
        d.setShippingStatusId(initialStatusId);
        return deliveryRepository.saveAndFlush(d).getId();
    }

    /**
     * @Transactional を付けないため、テストデータを物理クリーンアップする。
     * H2 はテストごとに独立した DB が立ち上がるが、
     * @SpringBootTest 内では同一コンテキストを使い回すため累積を避ける。
     */
    private void cleanup() {
        operationLogRepository.deleteAll();
        deliveryRepository.deleteAll();
        salesRepository.deleteAll();
        skuStockRepository.deleteAll();
        skuRepository.deleteAll();
        productRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
    }
}
