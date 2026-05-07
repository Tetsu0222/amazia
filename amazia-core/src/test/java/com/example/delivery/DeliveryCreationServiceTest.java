package com.example.delivery;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.delivery.service.DeliveryCreationService;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step B-1: DeliveryCreationService 単体検証（RR-3 / RR-4 / RRRR-3）。
 *
 * <p>{@link com.example.order.service.OrderConfirmationService} 経由ではなく
 * 直接呼び出し、deliveries 生成と防御的バリデーションを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class DeliveryCreationServiceTest {

    @Autowired private DeliveryCreationService deliveryCreationService;
    @Autowired private SalesRepository salesRepository;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}")
    private long pendingStatusId;

    @Value("${amazia.delivery.shipping-methods.home-delivery-id}")
    private long homeDeliveryMethodId;

    @Value("${amazia.delivery.lead-time-days.home-delivery}")
    private int homeDeliveryDays;

    private Long customerId;
    private Long addressId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setNameLast("配送");
        c.setNameFirst("太郎");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("delivery-buyer-" + System.nanoTime() + "@example.com");
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
        p.setName("配送テスト商品");
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-DLV-" + System.nanoTime());
        sku.setColor("赤");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(10);
        skuStockRepository.save(stock);
    }

    @Test
    void 通常購入_では_deliveries_が_PENDING_で生成され_scheduled_dateが算出される() {
        Sales sales = persistSales(false, "01967c10-aaaa-7d8e-9c5f-deadbeef0001");

        Delivery delivery = deliveryCreationService.createForSales(sales.getId(), homeDeliveryMethodId);

        assertNotNull(delivery.getId());
        assertEquals(sales.getId(), delivery.getSalesId());
        assertEquals(pendingStatusId, delivery.getShippingStatusId());
        assertEquals(homeDeliveryMethodId, delivery.getShippingMethodId());
        assertEquals(addressId, delivery.getShippingAddressId());
        assertEquals(sales.getSalesDate().plusDays(homeDeliveryDays), delivery.getScheduledDate());
        assertNull(delivery.getTrackingCode());
        assertNull(delivery.getShippedDate());
        assertNull(delivery.getDeliveredDate());
    }

    @Test
    void 予約購入_では_scheduled_date_は_NULL_で生成される() {
        Sales sales = persistSales(true, "01967c10-bbbb-7d8e-9c5f-deadbeef0002");

        Delivery delivery = deliveryCreationService.createForSales(sales.getId(), homeDeliveryMethodId);

        assertEquals(pendingStatusId, delivery.getShippingStatusId());
        assertNull(delivery.getScheduledDate());
    }

    @Test
    void 存在しない_sales_id_は_404_で拒否される() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryCreationService.createForSales(999_999L, homeDeliveryMethodId));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void 同じ_sales_id_での二重呼び出しは_UNIQUE違反_になる() {
        Sales sales = persistSales(false, "01967c10-cccc-7d8e-9c5f-deadbeef0003");
        deliveryCreationService.createForSales(sales.getId(), homeDeliveryMethodId);

        // 二度目の呼び出しは UNIQUE(sales_id) 違反 → DataIntegrityViolationException 系で失敗
        assertThrows(Exception.class,
                () -> deliveryCreationService.createForSales(sales.getId(), homeDeliveryMethodId),
                "UNIQUE(sales_id) 違反となるはず");
    }

    private Sales persistSales(boolean preorder, String paymentId) {
        Sales s = new Sales();
        s.setUserId(customerId);
        s.setSkuId(skuId);
        s.setQuantity(1);
        s.setAmount(3000);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(homeDeliveryMethodId);
        s.setShippingAddressId(addressId);
        s.setShippingStatusId(pendingStatusId);
        s.setPaymentId(paymentId);
        s.setPreorder(preorder);
        s.setSalesDate(LocalDate.of(2026, 5, 7));
        return salesRepository.saveAndFlush(s);
    }
}
